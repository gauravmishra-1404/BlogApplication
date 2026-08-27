# Bodh Sea — Architecture Roadmap

**Written:** 2026-08-21, after a session that fixed session-scaling (Redis), the ALB health check,
and the bare-domain 404 — and after working through a batch of system-design notes (caching,
typeahead/estimation, Kafka/Zookeeper, ID generation, video streaming, microservices/Saga/circuit
breakers). This doc applies that material to Bodh Sea's *actual* current state, not generically —
every claim below was verified live against the running system, not assumed.

Read [README.md](../README.md) first for what the app *is* and its current architecture diagram.
This doc is about what changes next, in what order, and why.

---

## 1. Where things actually stand right now

| Layer | State | Verified how |
|---|---|---|
| App servers | Elastic Beanstalk ASG, min 1 / max 4, **real CPU-triggered scaling policies attached** (not just an unused ceiling) | `aws autoscaling describe-policies` |
| Sessions | Shared Redis (ElastiCache `cache.t3.micro`), fixed today after 3 outages | Live session inspection via `redis-cli` |
| Database | RDS `db.t4g.micro`, **single-AZ, no read replica**, 20GB fixed storage | `aws rds describe-db-instances` |
| App-data caching | **None** — Redis exists but only stores sessions | Verified: no `@Cacheable`, no app-level Redis usage beyond `spring.session.*` |
| Alerting | Only the default Beanstalk CPU High/Low alarms (they drive autoscaling, nothing else) — **nothing watches error rate or notifies a human** | `aws cloudwatch describe-alarms` |
| Health check | Dedicated `/healthz`, touches nothing (fixed today) | `redis-cli DBSIZE` flat across repeated hits |
| Root domain | `/` → `/login` redirect (fixed today, was a 404) | Live curl |
| ID scheme | Plain auto-increment `int`, exposed directly in post/comment URLs | `grep` against `Post.java`, `PostController.java` |

Three production outages happened today, all in the same feature (Redis sessions), all
root-caused from live evidence rather than assumption. That discipline — verify against the real
system, don't reason from what *should* be true — is the standard the rest of this doc holds to.

---

## 2. Priority-ordered gaps

Ordered by **actual risk × how cheap the fix is**, not by how interesting the topic is.

### P0 — Database is a single point of failure

Single-AZ, no replica. A DB restart, an AZ issue, or routine maintenance is a full outage today —
this predates and is unrelated to anything fixed this session.

**The fix is a checkbox, not a project.** The Zookeeper material worked through exactly this
problem by hand: an ephemeral node holding the current master's address, a race-to-claim-ownership
election on failure, watchers notified automatically so traffic resumes pointed at the new master.
**RDS Multi-AZ is that same mechanism, fully managed** — enable it, and AWS runs the standby
replica, the failure detection, and the promotion/re-pointing automatically. Nothing to build.

### P0 — DB connection budget doesn't match the app's own scaling capability

Each app instance holds a Hikari pool of 10 connections (`application.properties`). The ASG can
genuinely scale to 4 instances (confirmed real policies, not just an unused max). That's **up to
40 concurrent connections** against a `t4g.micro`'s fairly tight connection ceiling — meaning the
app scaling out under load is exactly the moment it could exhaust the DB's connection budget and
start failing anyway. Horizontal scaling isn't actually safe until this is closed.

**Fix:** RDS Proxy in front of the instance — connection pooling at the DB tier, so N app
instances share a bounded, DB-side pool instead of each opening its own.

### P1 — No cache for actual application data

Redis exists, is proven reliable (today's work), and is used for exactly one thing: sessions.
Every dashboard feed load, every follow-directory page, every notification list still round-trips
to Postgres on every single request.

**Apply the Contest Leaderboard case study's framework directly** (it's a near-exact structural
match — expensive-to-compute, cheap-to-serve, tolerant of a few minutes of staleness):

1. **Establish the need** — the dashboard feed query joins posts/users/reactions/reposts; the
   follow directory ranks every user by live follower count. Neither needs to be recomputed fresh
   on every page view.
2. **Backend, not CDN/browser** — this is authenticated, personalized, per-user data. CDN edge
   caching doesn't apply here the way it would to public static assets.
3. **Local vs. global** — global. Each page view only needs a small page of results (~20 rows),
   not a large payload — there's no "avoid re-downloading a huge file" justification for a local
   (per-instance-disk) cache the way the Code Judge case study had.
4. **Eviction** — LRU (same `volatile-lru` policy already set on this Redis instance for sessions;
   reuse it).
5. **Invalidation** — plain TTL, on the order of 1–5 minutes. A follower count or feed order being
   a few minutes stale is unnoticeable; recomputing it on every request is the actual cost today.

This is genuinely small effort relative to its payoff, specifically *because* the Redis
infrastructure, its eviction policy, and the operational familiarity with it already exist from
today's session work.

### P1 — No proactive alerting

The only CloudWatch alarms that exist are the default Beanstalk CPU High/Low pair, and their only
job is driving the ASG scaling policies. Nothing watches 5xx rate, target health, or latency, and
nothing notifies anyone. Today's three outages were all found by manually running `docker logs`
over SSM — not because anything paged anyone. (This was already flagged as a deferred follow-up in
one of today's own commit messages — recording it here properly rather than letting it stay an
implicit TODO.)

**Fix:** a CloudWatch alarm on the ALB's target-group unhealthy-host count and 5xx rate, wired to
an SNS topic with a real subscription (email at minimum). Small, mechanical, high-value — this is
the difference between finding out from a user's screenshot and finding out before they notice.

### P2 — Enumerable post/comment IDs

Verified today: `Post.id` is `@GeneratedValue(strategy = GenerationType.AUTO)` — a plain
sequential `int` — and it's used directly in URLs (`/post/{id}/view`, `/post/{id}/edit`,
`/posts/comments/delete/{id}`, etc.). This is the exact enumeration pattern the ID-generation
notes warn about: anyone can walk `id=1,2,3...` and scrape every post, or infer total post count
and growth rate from the highest reachable ID.

**Severity, honestly:** low-to-moderate, not critical. The earlier-fixed IDOR work
(`PostAuthorization.isOwnerOrAdmin`) already means *mutating* a post you don't own is blocked
regardless of whether you can guess its ID — this is a read-side scraping/information-disclosure
concern, not an authorization bypass. Worth fixing eventually (opaque IDs — UUID, or a hashid
wrapper over the existing integer PK so the DB schema doesn't have to change), not urgent enough
to jump the queue above P0/P1.

### P3 — Frontend/backend coupling

Real, and the subject of the in-progress "separate frontend repo" decision — see §4. Confirmed
*not* the current bottleneck: rendering cost isn't what's failing today, the database and the
alerting gap are. Sequenced last on purpose.

---

## 3. Sizing method for what comes next (not current traffic — a planning tool)

Bodh Sea has no real production traffic yet (founder-stage). The Typeahead notes' estimation
method is still worth adopting *as a habit* before each future infra decision, rather than sizing
by guesswork:

```
users → DAU (rule of thumb: ~20% of registered users are active on a given day)
      → RPS per feature (avg actions/user/day × DAU ÷ 86,400s)
      → peak RPS (peak multiplier depends on traffic shape — smooth global traffic
        multiplies less than a single scheduled event like a launch or a notification blast)
      → data volume (rows × avg row size)
      → THEN pick DB tier / cache size / replica count from the actual numbers,
        not from what tier "feels right"
```

Worth re-running this for real once there's real usage data, specifically to answer: at what DAU
does `db.t4g.micro` + the P0/P1 fixes above stop being enough, and what's the next tier
(`db.t4g.small`/`medium`) actually bought.

---

## 4. The frontend split

Decision in progress: separate GitHub repo, deployed to AWS, same domain (`bodhsea.in`) as the
existing backend — not a different subdomain the user has to learn.

**Target architecture, if CloudFront is available on this account** (status unconfirmed —
CloudFront was blocked earlier this year by AWS's new-account fraud gate; the account has since
accumulated real usage history, but this hasn't been re-tested):

```
bodhsea.in → CloudFront
             ├── default origin: S3 (serves the new frontend's static build)
             └── /api/*, /login, etc. → existing ALB (backend untouched)
```

One domain, one cert, path-based origin routing. This is the standard AWS pattern for exactly this
split and doesn't require touching the existing backend's DNS/cert setup at all.

**Fallback, if CloudFront is still gated:** route static asset requests through the existing ALB
instead (less elegant — no edge caching/CDN benefit, but achieves the same single-domain
requirement using infrastructure already proven working today).

**One thing to build regardless of which path is chosen, straight from the Microservices/Circuit
Breaker notes:** once the frontend is a genuinely separate deployable calling the backend's
`/api/*` over the network, it should degrade gracefully if the backend is unhealthy — the
cascading-failure/thundering-herd problem those notes walk through (a downstream outage causing
upstream connection buildup, then a slow-recovery pile-up once the downstream comes back) is a
real risk the moment these become two independently-deployed, independently-failing services
instead of one process. A simple client-side circuit breaker (open the circuit and fail fast after
N consecutive failures, half-open to test recovery) is cheap insurance against that.

**What this split does *not* need, on purpose:** the frontend won't have its own database and
won't run multi-step transactions spanning frontend+backend — it just calls existing REST
endpoints. That means 2PC, the Saga pattern, and orchestration-vs-choreography (the heaviest
material from the Microservices notes) genuinely don't apply here. Worth saying explicitly,
because the temptation after learning a pattern is to reach for it everywhere — this split is a
presentation-layer separation, not a distributed-transaction problem, and treating it as the
latter would be pure overhead with no corresponding benefit.

---

## 5. Recommended sequencing

1. **RDS Multi-AZ** — a checkbox, closes the single-AZ outage risk immediately.
2. **RDS Proxy / connection pooling** — makes the ASG's existing scaling capability actually safe
   to use, not just theoretically present.
3. **CloudWatch alarms + SNS ops alerts** — mechanical, cheap, closes the "found out from a
   screenshot" gap.
4. **Extend Redis to cache hot reads** (dashboard feed, follow directory, notifications) — reuses
   infrastructure and operational knowledge that already exists from today.
5. **Frontend split** — settle the CloudFront-availability question first (cheap, reversible test),
   then scaffold the new repo against whichever path that resolves to.
6. **Opaque post/comment IDs** — lowest urgency of the six, real but not blocking anything else.

Items 1–3 are all small, well-understood, low-risk changes with outsized payoff relative to
effort — worth doing before the frontend split, not after, since none of them depend on it and all
of them reduce the chance of a repeat of today's incident pattern while a bigger change is in
flight.
