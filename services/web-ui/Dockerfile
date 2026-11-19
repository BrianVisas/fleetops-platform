# The dashboard is static files, so there is no build stage: nginx from Alpine's
# own packages, on the same Alpine release as the Go services. That keeps one
# base OS to patch and scan instead of adding a second one through an nginx image.
#
# Named "runtime" so that CI can rebuild this stage on every run while the
# build stage above comes from the cache: an upgrade taken from a cached layer
# is as old as the cache, and the scan found that layer missing fixes (EL-030).
FROM alpine:3.24 AS runtime

# Upgrade first for the same reason as the other images: the tag lags Alpine's
# package fixes. See docs/SECURITY_POLICY.md.
RUN apk upgrade --no-cache \
 && apk add --no-cache nginx

RUN adduser --system --uid 10001 fleetops

COPY nginx.conf /etc/nginx/nginx.conf
COPY public/ /usr/share/fleetops-ui/

USER 10001

# 8080 because a non-root process cannot bind ports below 1024.
EXPOSE 8080

# wget comes from busybox, so the health check costs nothing extra.
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/healthz | grep -q UP

# -e: log startup errors to stderr instead of the packaged log path, which a
# non-root user cannot open.
ENTRYPOINT ["nginx", "-e", "/dev/stderr", "-g", "daemon off;"]
