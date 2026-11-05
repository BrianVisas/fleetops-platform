# ---- build ----
# The Go toolchain version here decides which stdlib is compiled into the binary,
# so it is a security input, not a convenience. This service has no third-party
# dependencies at all, which is why every one of its 22 findings was stdlib.
FROM golang:1.27-alpine AS build
WORKDIR /build

COPY go.mod ./
COPY *.go ./

RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /vehicle-sim .

# ---- run ----
FROM alpine:3.21

# Alpine publishes package fixes to the release branch faster than it rebuilds
# the image tag, so the tag alone leaves patched packages on the table
# (libcrypto3 r1 in the image, r2 in the repository). One layer, no new packages.
RUN apk upgrade --no-cache

RUN adduser --system --uid 10001 fleetops
USER 10001

COPY --from=build /vehicle-sim /usr/local/bin/vehicle-sim

# No health check and no port: this is a client, not a server. Flags are passed
# as the command, so compose and k6 runs can vary the fleet size and rate.
ENTRYPOINT ["vehicle-sim"]
CMD ["-vehicles", "10", "-rate", "1"]
