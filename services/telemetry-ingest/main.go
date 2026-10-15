package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"
)

func main() {
	log := slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))
	slog.SetDefault(log)

	if err := run(log); err != nil {
		log.Error("service stopped", "error", err)
		os.Exit(1)
	}
}

func run(log *slog.Logger) error {
	dbURL := env("DB_URL", "postgres://fleetops:fleetops@localhost:5432/fleetops")
	addr := ":" + env("PORT", "8081")
	maxConns, err := strconv.Atoi(env("DB_MAX_CONNS", "10"))
	if err != nil {
		return err
	}

	startCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	store, err := newPostgresStore(startCtx, dbURL, int32(maxConns))
	if err != nil {
		return err
	}
	defer store.close()

	server := &http.Server{
		Addr:              addr,
		Handler:           newAPI(store, log).routes(),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      10 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	// Serve in the background so the main goroutine can wait for a signal.
	errs := make(chan error, 1)
	go func() {
		log.Info("listening", "addr", addr)
		if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			errs <- err
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)

	select {
	case err := <-errs:
		return err
	case sig := <-stop:
		log.Info("shutting down", "signal", sig.String())
	}

	// Let in-flight requests finish. Kubernetes sends SIGTERM before removing the
	// pod from the service, so this window is what makes a rolling update clean.
	shutdownCtx, cancelShutdown := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancelShutdown()
	return server.Shutdown(shutdownCtx)
}

func env(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
