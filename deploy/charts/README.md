# Helm charts

One chart per deployable part of FleetOps. Phase 4 installs them on the local
kind cluster (`infra/local`); Argo CD takes over the installing in package 4.

| Chart | What it installs |
|-------|------------------|
| `fleetops-postgres` | the database: a CloudNativePG `Cluster` with PostgreSQL 17 |

The CloudNativePG operator itself is part of the cluster, like Envoy Gateway:
`infra/local/cluster-up.sh` installs it.

## The database

CloudNativePG creates, for a cluster named `fleetops-db`:

| Name | What it is |
|------|------------|
| service `fleetops-db-rw` | the primary, for reads and writes |
| secret `fleetops-db-app` | the owner's credentials, generated in the cluster: `username`, `password`, `dbname`, `host`, `port`, `uri`, `jdbc-uri` |

No password is in git or in values files. The service charts read them from
that secret.
