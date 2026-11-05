# ---- build ----
# The Go toolchain version here decides which stdlib is compiled into the binary,
# so it is a security input, not a convenience. Building with 1.24 shipped a
# binary carrying every net/http, crypto/tls and crypto/x509 CVE fixed since.
FROM golang:1.27-alpine AS build
WORKDIR /build

# Module files first, so dependencies are cached independently of the source.
COPY go.mod go.sum* ./
RUN go mod download

COPY *.go ./

# CGO off gives a static binary that needs no libc in the runtime image.
# -trimpath keeps build paths out of the binary, which makes builds reproducible.
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /telemetry-ingest .

# ---- run ----
FROM alpine:3.21

# Alpine publishes package fixes to the release branch faster than it rebuilds
# the image tag, so the tag alone leaves patched packages on the table
# (libcrypto3 r1 in the image, r2 in the repository). One layer, no new packages.
RUN apk upgrade --no-cache

RUN adduser --system --uid 10001 fleetops
USER 10001

COPY --from=build /telemetry-ingest /usr/local/bin/telemetry-ingest

EXPOSE 8081

# wget comes from busybox, so the health check costs nothing extra here.
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
  CMD wget -qO- http://localhost:8081/healthz | grep -q UP

ENTRYPOINT ["telemetry-ingest"]
