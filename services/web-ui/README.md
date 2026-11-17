# web-ui

A small dashboard for the fleet operator: the vehicle list, the releases, and
one rollout at a time with its counts, pause reason and pause, resume and raise
controls. Plain HTML, CSS and JavaScript in `public/`, with no build step.

nginx serves the files and proxies `/api/` to `fleet-api:8080`, so the browser
talks to a single origin and needs no CORS. With the stack running (`make up`),
it is at <http://localhost:3000>.

Pages are addressed by the URL fragment: `#/vehicles`, `#/releases` and
`#/rollouts/<id>`. A rollout page refreshes itself every 5 seconds.
