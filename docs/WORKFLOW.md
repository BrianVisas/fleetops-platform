# Workflow

I play two roles in this repo. Keep them separate.

## Customer

- Writes requirements as GitHub issues using the "Customer requirement" template.
- Every requirement has acceptance criteria that can be tested.
- Accepts a release by running the acceptance tests and filling in
  `docs/releases/<version>.md`.
- Reports problems with the "Bug report" template.

## Vendor (dev + ops)

1. Pick an issue from the board and move it to "In progress".
2. Create a branch: `feat/<issue-number>-short-name` or `fix/<issue-number>-short-name`.
3. Write code and tests together. Commit with Conventional Commits:
   - `feat: add vehicle registration endpoint`
   - `fix: reject telemetry with missing vehicle id`
   - `test:`, `ci:`, `docs:`, `refactor:`, `chore:`
4. Open a pull request, link the issue (`Closes #12`), fill in the template.
5. Merge only when CI is green.
6. At the end of a phase, tag the release: `git tag v0.1.0 && git push --tags`.

## Rules

- No direct commits to `main`.
- A broken pipeline is fixed before new work starts.
- Non-obvious decisions get an ADR in `docs/adr/`.
- Cloud resources are created and destroyed with Terraform only.
