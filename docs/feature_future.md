# Future features

Ideas that have been discussed and are worth building, but aren't designed or scheduled yet.
Each entry should have enough context that a future session can pick it up cold.

## Draft posts

**The idea:** writing a post shouldn't force an immediate choice between "finish it now" and
"lose it." Add a Save as Draft option alongside Publish — like Gmail's draft mail. A draft:

- Is only visible to its own author, nobody else (not in `/posts`, not in search, not reachable
  by guessing its URL).
- Lives somewhere the author can find it again — a dedicated "Drafts" list/component, separate
  from their published posts.
- Can be edited any number of times while still a draft.
- Gets published later via an explicit action, at which point it behaves exactly like a post
  created through today's single-step publish flow.

**What already exists as groundwork (2026-07-29):** `Post.isPublished` (nullable `Boolean`,
null-safe getter defaults to `true` — every post that exists today has no drafts concept, so it's
correctly "published" whether the column is `true` or still `null` from before this was wired up).
`PostServiceImpl.save()` now explicitly sets `published(true)` on every publish through today's
one-step flow. `publishedAt` already exists as a separate timestamp from `createdAt`/`updatedAt`.

**What actually building this needs:**

1. **A second save path.** Today `POST /post/publish` always publishes. A draft needs either a
   new endpoint (`POST /post/save-draft`) or a mode flag on the existing one that calls
   `setPublished(false)` instead and leaves `publishedAt` null until the post is actually
   published.
2. **A new visibility boundary — this is the part that needs the most care.** Right now *nothing*
   filters on `is_published` anywhere: `PostServiceImpl.searchPosts()`, the direct
   `GET /post/viewPost?id=`, the REST mirrors — none of them check it, because nothing has ever
   been unpublished before. The moment drafts exist, every one of those read paths needs an
   explicit check: unpublished + viewer isn't the author (or ADMIN) → treat exactly like a
   soft-deleted post (404 / redirect, not a permission error that reveals the post exists at all).
   This is the same class of gap as the post edit/delete IDOR fixed on 2026-07-29 — reuse
   `PostAuthorization.isOwnerOrAdmin` rather than re-deriving the check, and it needs the same
   "hit the endpoint as a different logged-in user" testing, not just a UI click-through.
3. **A drafts list.** Some new route (`/profile/drafts` or a tab on the dashboard) showing only
   the current user's own posts where `isPublished()` is `false`. Needs pagination/sort like the
   main feed eventually, but can start simple.
4. **The actual "Publish" action from a draft.** Sets `published(true)` and `publishedAt =
   now()` at that moment (not whenever the draft was first saved) — the same ownership check as
   above applies here too, since it's a mutating action on a specific post.
5. **Reactions/views on drafts** — moot while a draft is unpublished (nobody else can reach it to
   react/view), but worth a quick sanity check once built that the counts a draft accumulated
   don't do anything strange the moment it's published.

**Not decided yet:**
- Where the Drafts list lives in the nav (own page vs. a tab vs. folded into the profile).
- Whether drafts get their own distinct visual treatment in a list (a "DRAFT" badge, muted
  styling) — worth an artifact pass when this gets built, following the usual
  artifact-preview-first approach for anything visual.
- Whether editing a *published* post should ever be able to unpublish it back to draft, or
  whether that's a one-way door.
