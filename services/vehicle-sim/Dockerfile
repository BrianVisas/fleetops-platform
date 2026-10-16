# ---- build ----
FROM golang:1.24-alpine AS build
WORKDIR /build

COPY go.mod ./
COPY *.go ./

RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /vehicle-sim .

# ---- run ----
FROM alpine:3.21
RUN adduser --system --uid 10001 fleetops
USER 10001

COPY --from=build /vehicle-sim /usr/local/bin/vehicle-sim

# No health check and no port: this is a client, not a server. Flags are passed
# as the command, so compose and k6 runs can vary the fleet size and rate.
ENTRYPOINT ["vehicle-sim"]
CMD ["-vehicles", "10", "-rate", "1"]
