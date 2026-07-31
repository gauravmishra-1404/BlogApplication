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

## Rich media posts (photos, video) + modal post view

**The idea (2026-07-30):** two related UI changes, designed together as an artifact
(`bodhsea-rich-feed-modal.html` shown 2026-07-30) but not yet built:

1. Feed cards adapt to what a post actually contains — long text gets CSS-clamped to ~2 lines
   behind a "Show more" toggle (pure front-end, no new data needed), and if a post has photos they
   show as an inline collage (2-3 image grid), or a video shows as an inline player thumbnail with
   a duration badge.
2. Clicking a post opens it in a **modal over the dashboard** instead of navigating to
   `/post/viewPost` as a new page — the feed's scroll position and loaded infinite-scroll batches
   stay exactly as they were underneath, restored instantly on close since nothing there ever
   unmounted.

**What already exists as groundwork:** Cloudinary is already wired in (`cloudinary.enabled` +
`ImageStorageService`/`LocalImageStorageService`), currently only used for profile/cover photos -
the natural place to extend for post media rather than a new integration. `infiniteScroll.js`
already established the pattern of fetching a small HTML fragment on demand instead of a full page
reload, which the modal's comment-thread fetch would reuse.

**What actually building this needs:**

1. **Schema**: `Post` has no image/video fields at all today - needs something like a
   `PostMedia` child table (post_id, media_type [image|video], url, sort_order) supporting 0-N
   attachments per post, since a post can have multiple photos (the collage case) but the current
   model assumes text-only content.
2. **Upload**: the new-post form needs a file picker wired to the same Cloudinary path
   `ProfileController.updateAvatar`/`storeImage` already uses, extended to accept video (Cloudinary
   supports video upload on the same API, different resource_type) and multiple files at once.
3. **Modal fetch endpoint**: something like `GET /post/{id}/modal-fragment` returning post detail
   + comment thread as an HTML fragment (mirrors `fragments/postRows.html` /
   `PostController.postsFragment` exactly) - the post's own text/media render instantly from what
   the feed already has client-side; only the comment thread needs an actual request.
4. **The existing full `/post/viewPost` page stays** - the modal is an alternative fast path for
   people already in the feed, not a replacement. Anyone landing on that URL directly (a shared
   link, a search result) still gets the real page.
5. **Authorization/visibility**: same rule as everything else touching a specific post - the modal
   fetch endpoint needs the same soft-delete/ownership checks `PostController.viewPostByID` already
   has, not a fresh re-derivation.

**Not decided yet:**
- Exact collage layout rules beyond 3 photos (Instagram/X-style "+N more" overlay on a 4th+ image
  is the common pattern, not designed here yet).
- Whether video gets a real inline `<video>` player on click-to-play within the feed card itself,
  or always defers to the modal to actually play - inline autoplay-on-scroll has real bandwidth/UX
  tradeoffs worth a deliberate decision, not a default.
- Video file size/duration limits and transcoding - Cloudinary handles transcoding, but a max
  upload size needs picking before this is real.
