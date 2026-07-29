# Bodh Sea — working agreement

Spring Boot 3.4.2 / Java 21 / Thymeleaf / Spring Security 6.4.2 / Hibernate 6.6.5. H2 in-memory
locally (`docker` profile), PostgreSQL on Render in production — **that Postgres instance is
shared with a separate, unrelated app**, so schema changes must never assume this app owns the
whole table.

## Regression policy (read this before saying a feature is done)

A feature isn't finished when its own happy path works. Two real incidents on 2026-07-29 made
this concrete:

1. **The username backfill gap.** Adding a `username` column only backfilled it for brand-new
   registrations. Every *existing* verified user (including accounts already live in the shared
   production table) was silently left with `username = null` — which meant their nav dropdown
   printed the literal text `@null` and the "View profile" link vanished entirely, without a
   single error anywhere. Nothing about the new-user path was wrong; the gap was in what happens
   to users who already existed before the feature shipped.
2. **The post edit/delete IDOR.** `POST /post/republish` and `POST /posts/delete` never checked
   whether the logged-in user actually owned the post — any authenticated user could edit or
   delete *anyone else's* post by hitting the URL directly, even though the UI correctly hid the
   Edit/Delete buttons for non-owners. The UI-level hiding worked fine in every manual click-through
   test; the bug only showed up by testing the request directly, bypassing the UI.

Both bugs would have passed a test plan that only checks "does the new/changed thing work for a
fresh user clicking through the UI." Neither would have passed a test plan that also asks two
questions: **"what about data/users that already existed before this change?"** and **"what
happens if someone sends this request directly, not through the UI?"**

### The rule

After implementing or changing a feature, before calling it done, run a regression pass that
covers:

- **The new/changed behavior itself** — the obvious part, already usually done.
- **Existing data going through the new code path** — a row created before this feature existed,
  with whatever nulls/defaults that implies. `ddl-auto=update` adds new columns to *existing* rows
  as `NULL`; if the new feature doesn't tolerate that, it needs an explicit backfill (see
  `GlobalModelAttributes.currentUser()` → `UserService.ensureUsername()` for the pattern: lazily
  backfill on next load rather than a batch migration).
- **Authorization, not just authentication** — "must be logged in" is not the same claim as "must
  own this resource, or be an ADMIN." Every mutating endpoint that acts on a specific record
  (a post, a comment, a profile) needs an explicit ownership/role check in the controller or
  service, independent of whatever the UI shows or hides. `PostAuthorization.isOwnerOrAdmin` /
  `PostController.isAuthorizedForComment` are the reference pattern — reuse them, don't re-derive.
  Test this by literally hitting the endpoint as a *different* logged-in user, not by clicking
  through the UI as the "right" user.
- **Both branches of every `th:if`/`th:unless` pair** — when a template forks on a condition (has
  a username vs. doesn't, is the owner vs. isn't), both branches need to be exercised and need to
  produce the *same layout*, not just correct content. The dashboard byline stacking bug was
  exactly this: the `th:if` branch had `class="post-author-link"` (flex layout), the `th:unless`
  fallback had no class at all, so it silently stacked instead of sitting inline — invisible until
  a real account hit that fallback branch.
- **The shared-table constraint** — before adding/changing a `users`/`comments`/`posts` column,
  check whether the other app depends on it, whether a `NOT NULL` addition would need a default
  for existing rows, and prefer nullable + null-safe getters (`Boolean` not `boolean`, `isX() {
  return x == null || x; }`-style defaults) over a hard migration.

### Who does what

- **Claude does this automatically, every time, without being asked**: after a feature is built,
  spin up the local `docker` profile (H2, disposable) and actually exercise it — register/verify
  test accounts, hit the changed endpoints as the "wrong" user, check both branches of any new
  conditional, and confirm compiles + starts cleanly. This is cheap and safe since it's all
  throwaway local data, so there's no reason to wait for a request to do it.
- **Claude asks first, and never does unprompted**: anything that touches the actual production
  database (Render Postgres) — running a migration, reading/writing a real row, even a read-only
  query against production data. That connection is in `application.properties` and is technically
  reachable, but it's shared with another app and holds real users' data, so it stays hands-off
  unless explicitly requested for that specific action.
- **Only the user can do this**: verify that something behaves correctly *in production itself*
  (not just "the local regression passed") — e.g. confirming a specific production account's state,
  or confirming behavior after a real deploy. Local regression proves the code is correct; it
  can't prove the production database or environment agrees. When in doubt about a production-only
  symptom, the fastest path is telling Claude the specific account/email/id involved so it can be
  reasoned about against the code, or checking it directly via Render's dashboard/psql.
