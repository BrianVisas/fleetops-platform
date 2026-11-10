
**Follow-up, 2026-11-10.** The fix was incomplete. Squash merges still put each
pull request's individual commit messages into the commit body, and
release-please reads Conventional Commit lines in a body as further changes.
The body of #30 began with Dependabot's own commit message,
`build(deps): bump the maven-build group`, which differs from the pull request
title, so the pending v0.2.1 changelog listed #30 twice. Squash commits now get
a blank body (`squash_merge_commit_message=BLANK`), so a pull request
contributes exactly its title. #30, already on `main`, has a
`BEGIN_COMMIT_OVERRIDE` block in its description, which release-please uses
instead of the commit message; the release pull request then listed it once.
The first commit merged with the new setting, `1e91c58`, has a title and no
body.
