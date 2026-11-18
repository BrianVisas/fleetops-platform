#!/usr/bin/env sh
# Deletes the local kind cluster and everything in it.
set -eu
kind delete cluster --name fleetops
