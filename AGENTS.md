# AGENTS.md

Spring Boot 3.4.2 monolith ("Bodh Sea") — Java 21, Thymeleaf server-rendered + vanilla JS
(one file per feature in `static/js/`), no frontend build step. Entrypoint:
`src/main/java/com/BlogApplication/Blog/BlogApplication.java`. Page controllers live in
`controllers/`, JSON/AJAX mirrors of the same features in `RestController/` (yes, capitalized).

## Commands

- Run locally: `./mvnw spring-boot:run -Dspring-boot.run.profiles=docker` → http://localhost:8080
- Tests: `./mvnw test` (H2-based — no database or external service needed).
  Single test: `./mvnw test -Dtest=ClassName`.
- Build jar: `./mvnw clean package`
- No lint/formatter/typecheck config exists — compilation plus tests is the whole gate.

**CI does not run tests before deploying** (`package-beanstalk.sh` builds with `-DskipTests`),
so run `./mvnw test` yourself before finishing any change.

## Deploying is automatic — do not push without being asked

Every push to `master` auto-deploys to production Elastic Beanstalk via
`.github/workflows/deploy.yml` (paths-ignore covers only `infra/**` and `*.md`). Committing or
merging to master = shipping to real users at bodhsea.in.

- Infra changes (`infra/terraform/`) are manual Terraform applies — never run them unprompted.
  That directory also holds gitignored local files containing real secrets
  (`terraform.tfvars`, `terraform.tfstate`) — never commit or print them.
- `infra/lambdas/*` are three independent Maven modules (email/push/in-app workers) with their
  own poms — not part of this root build.

## Schema discipline

There is **no Flyway/Liquibase**. Production runs `ddl-auto=update` against live data on every
deploy (dedicated AWS RDS since the 2026-08-17 hosting migration). Therefore:

- New columns must be nullable; existing rows get NULL. Use null-safe getters
  (`Boolean` field, `isX() { return x == null || x; }`) instead of NOT NULL + default.
- Backfill lazily on read rather than batch-migrating — reference pattern:
  `GlobalModelAttributes.currentUser()` → `UserService.ensureUsername()`.

## Authorization discipline

"Authenticated" ≠ "authorized". Every endpoint that mutates a specific record needs an explicit
ownership-or-admin check in controller/service code, regardless of what the UI hides. Reuse the
existing patterns instead of re-deriving:

- `util/PostAuthorization.isOwnerOrAdmin()` (posts)
- `PostController.isAuthorizedForComment()` (comments)

Verify by hitting the endpoint directly as a *different* logged-in user — UI click-throughs
cannot catch a missing check.

## Regression minimums before calling work done

- Pre-existing rows through new code paths — columns added by `ddl-auto=update` are NULL for
  every row created before the feature existed.
- Both branches of every new `th:if`/`th:unless`, producing identical layout classes — the
  fallback branch silently stacking inline content was a real bug.
- Mobile width (~360–420px) for anything changed — `flex-wrap`/`order`/`justify-content`
  genuinely change shape at that width, not just size. Absolutely-positioned elements need
  their anchor's position re-verified per breakpoint, or an overflow safety net. Extend the
  existing `@media (max-width: 600px)` blocks in `static/CSS/` rather than inventing new
  breakpoints, and remember `templates/fragments/nav.html` is shared by every page.

## Local `docker` profile behavior

- H2 in-memory, wiped on every restart. Console at `/h2-console` (`jdbc:h2:mem:blogdb`,
  user `sa`, empty password) — only enabled in this profile.
- Outbound email is stubbed: registration/verification links print to the console as
  `[DEV MAIL STUB]` lines. That's how you activate test accounts locally.
- All external integrations (S3 media upload, SNS/SQS notifications, SendGrid, Cloudinary)
  have disabled fallback beans — the app runs fully with zero credentials.
- Do **not** remove `spring.session.store-type=none` from the docker/test profiles:
  `spring-session-data-redis` is on the classpath and silently defaults store-type to `redis`
  when unset, so startup tries to reach a Redis that doesn't exist locally.

## Other instruction sources

- `CLAUDE.md` — the full working agreement (regression policy rationale with real incidents,
  UI design principles, hands-off production DB/data rule). Note its pre-August-2026 hosting
  notes describe the old Render deployment; trust this file and `infra/terraform/` for current
  deployment reality.
- `docs/feature_future.md` — designed-but-unscheduled feature ideas.
