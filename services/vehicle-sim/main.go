package main

import (
	"context"
	"flag"
	"fmt"
	"log/slog"
	"math/rand"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"
)

type config struct {
	Vehicles     int
	Rate         float64 // telemetry messages per vehicle per second
	APIURL       string
	IngestURL    string
	Model        string
	SwVersion    string
	VinPrefix    string
	FailRate     float64
	PollInterval time.Duration
	InstallMin   time.Duration
	InstallMax   time.Duration
	Duration     time.Duration // zero means run until interrupted
	Timeout      time.Duration
	Verbose      bool
}

func (c config) telemetryInterval() time.Duration {
	return time.Duration(float64(time.Second) / c.Rate)
}

func (c config) validate() error {
	if c.Vehicles < 1 {
		return fmt.Errorf("vehicles must be at least 1")
	}
	if c.Rate <= 0 || c.Rate > 100 {
		return fmt.Errorf("rate must be between 0 and 100 messages per second")
	}
	if c.FailRate < 0 || c.FailRate > 1 {
		return fmt.Errorf("fail-rate must be between 0 and 1")
	}
	if len(c.VinPrefix) != 3 {
		return fmt.Errorf("vin-prefix must be exactly 3 characters")
	}
	// Real VINs use neither I, O nor Q, to keep them apart from 1 and 0.
	for _, c := range c.VinPrefix {
		if c == 'I' || c == 'O' || c == 'Q' {
			return fmt.Errorf("vin-prefix must not contain I, O or Q")
		}
	}
	if c.InstallMax < c.InstallMin {
		return fmt.Errorf("install-max must not be smaller than install-min")
	}
	return nil
}

func main() {
	cfg := config{}
	flag.IntVar(&cfg.Vehicles, "vehicles", 10, "number of vehicles to simulate")
	flag.Float64Var(&cfg.Rate, "rate", 1, "telemetry messages per vehicle per second")
	flag.StringVar(&cfg.APIURL, "api", "http://localhost:8080", "fleet-api base url")
	flag.StringVar(&cfg.IngestURL, "ingest", "http://localhost:8081", "telemetry-ingest base url")
	flag.StringVar(&cfg.Model, "model", "ID.4", "vehicle model to register")
	flag.StringVar(&cfg.SwVersion, "sw-version", "1.0.0", "software version the fleet starts on")
	flag.StringVar(&cfg.VinPrefix, "vin-prefix", "SMX", "3-character VIN prefix (no I, O or Q)")
	flag.Float64Var(&cfg.FailRate, "fail-rate", 0, "share of installations that report failure (0 to 1)")
	flag.DurationVar(&cfg.PollInterval, "poll-interval", 10*time.Second, "how often a vehicle asks for an update")
	flag.DurationVar(&cfg.InstallMin, "install-min", 2*time.Second, "shortest simulated install time")
	flag.DurationVar(&cfg.InstallMax, "install-max", 5*time.Second, "longest simulated install time")
	flag.DurationVar(&cfg.Duration, "duration", 0, "stop after this long (0 means run until interrupted)")
	flag.DurationVar(&cfg.Timeout, "timeout", 5*time.Second, "per-request timeout")
	flag.BoolVar(&cfg.Verbose, "verbose", false, "log every rejected request")
	flag.Parse()

	level := slog.LevelInfo
	if cfg.Verbose {
		level = slog.LevelDebug
	}
	log := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: level}))

	if err := run(cfg, log); err != nil {
		log.Error("simulation stopped", "error", err)
		os.Exit(1)
	}
}

func run(cfg config, log *slog.Logger) error {
	if err := cfg.validate(); err != nil {
		return err
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	if cfg.Duration > 0 {
		var cancel context.CancelFunc
		ctx, cancel = context.WithTimeout(ctx, cfg.Duration)
		defer cancel()
	}

	api := newClient(cfg.APIURL, cfg.IngestURL, cfg.Timeout)
	counters := &stats{}
	runTag := newRunTag(rand.New(rand.NewSource(time.Now().UnixNano())))
	log.Info("registering fleet", "vehicles", cfg.Vehicles, "model", cfg.Model, "run_tag", runTag)

	fleet, err := registerFleet(ctx, cfg, api, counters, runTag, log)
	if err != nil {
		return err
	}

	log.Info("fleet running",
		"vehicles", len(fleet),
		"rate_per_vehicle", cfg.Rate,
		"fail_rate", cfg.FailRate)

	var wg sync.WaitGroup
	for _, vehicle := range fleet {
		wg.Add(1)
		go func(v *simVehicle) {
			defer wg.Done()
			v.run(ctx)
		}(vehicle)
	}

	started := time.Now()
	wg.Wait()

	elapsed := time.Since(started)
	log.Info("simulation finished",
		"elapsed", elapsed.Round(time.Second).String(),
		"summary", counters.summary(),
		"loss_rate", fmt.Sprintf("%.3f%%", counters.lossRate()*100))
	return nil
}

// registerFleet registers the vehicles up front, a few at a time so a large fleet
// does not open hundreds of connections in the same instant.
func registerFleet(ctx context.Context, cfg config, api *client, counters *stats,
	runTag string, log *slog.Logger) ([]*simVehicle, error) {

	const parallel = 8

	type result struct {
		vehicle *simVehicle
		err     error
	}

	jobs := make(chan int)
	results := make(chan result)

	var wg sync.WaitGroup
	for i := 0; i < parallel; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			rnd := rand.New(rand.NewSource(time.Now().UnixNano() + int64(i)))
			for index := range jobs {
				plate := vin(cfg.VinPrefix, runTag, index)
				id, err := api.register(ctx, plate, cfg.Model, cfg.SwVersion)
				if err != nil {
					results <- result{err: fmt.Errorf("registering %s: %w", plate, err)}
					continue
				}
				counters.vehiclesRegistered.Add(1)
				results <- result{vehicle: &simVehicle{
					id:     id,
					vin:    plate,
					state:  newVehicleState(rnd),
					rnd:    rand.New(rand.NewSource(time.Now().UnixNano() + int64(index))),
					client: api,
					cfg:    cfg,
					stats:  counters,
					log:    log,
				}}
			}
		}()
	}

	go func() {
		for index := 0; index < cfg.Vehicles; index++ {
			jobs <- index
		}
		close(jobs)
		wg.Wait()
		close(results)
	}()

	fleet := make([]*simVehicle, 0, cfg.Vehicles)
	for r := range results {
		if r.err != nil {
			// Registration failing means the API is not usable; stop rather than
			// running a half fleet and reporting misleading numbers.
			return nil, r.err
		}
		fleet = append(fleet, r.vehicle)
	}
	return fleet, nil
}
