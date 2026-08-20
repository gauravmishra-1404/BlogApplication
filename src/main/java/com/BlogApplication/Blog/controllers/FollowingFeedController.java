package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.Repost;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.FeedItem;
import com.BlogApplication.Blog.payloads.FollowedCommentAnnotation;
import com.BlogApplication.Blog.repositories.BookmarkRepo;
import com.BlogApplication.Blog.repositories.CommentRepo;
import com.BlogApplication.Blog.repositories.FollowRepo;
import com.BlogApplication.Blog.repositories.PostRepo;
import com.BlogApplication.Blog.repositories.RepostCount;
import com.BlogApplication.Blog.repositories.RepostRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.CommentService;
import com.BlogApplication.Blog.services.PostReactionService;
import com.BlogApplication.Blog.services.PostService;
import com.BlogApplication.Blog.services.PostViewService;
import com.BlogApplication.Blog.services.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The "Following" sidebar item, now real: posts by people the viewer follows, posts anyone else
// wrote that someone the viewer follows commented on, and posts anyone else wrote that someone
// the viewer follows reposted. Reuses fragments/postRows.html wholesale for the actual card
// markup (same model attribute names the main feed already populates: posts/viewCounts/
// postReactions/commentCounts/repostCounts/hasNextPage) - postAnnotations is the one extra
// attribute layered on top for the "X commented" line (repost attribution instead renders off
// each FeedItem's own repostedBy, no separate map needed for that one).
@Controller
public class FollowingFeedController {

    private static final int PAGE_SIZE = 15;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private FollowRepo followRepo;

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private CommentRepo commentRepo;

    @Autowired
    private PostViewService postViewService;

    @Autowired
    private PostReactionService postReactionService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private PostService postService;

    @Autowired
    private TagService tagService;

    @Autowired
    private BookmarkRepo bookmarkRepo;

    @Autowired
    private RepostRepo repostRepo;

    @GetMapping("/following")
    public String followingFeed(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        addFeedToModel(page, authentication, model);
        return "followingFeed";
    }

    // "Load more" batch (js/followDirectoryLoadMore.js's same click-to-fetch pattern, reused
    // as-is since both pages paginate the same way) - reuses postRows.html for markup.
    @GetMapping("/following/fragment")
    public String followingFeedFragment(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        addFeedToModel(page, authentication, model);
        return "fragments/postRows :: postRows";
    }

    private void addFeedToModel(int page, Authentication authentication, Model model) {
        User viewer = (authentication != null && authentication.isAuthenticated())
                ? userRepo.findByEmail(authentication.getName()).orElse(null)
                : null;

        model.addAttribute("currentPage", page);

        List<Integer> followedIds = viewer == null ? List.of() : followRepo.findAllFollowedIdsByFollowerId(viewer.getId());
        Set<Integer> followedIdSet = new HashSet<>(followedIds);

        // Right-rail widgets (fragments/rightSidebar.html) - same data PostController.listPosts
        // populates for /home, kept unconditional (even when the feed itself is empty) since
        // "Active writers" is exactly how someone with an empty Following feed finds people to
        // follow in the first place.
        model.addAttribute("trendingTags", tagService.getTrendingTags(5));
        model.addAttribute("topAuthors", postService.getTopAuthors(3));
        model.addAttribute("followedAuthorIds", followedIdSet);

        if (followedIds.isEmpty()) {
            model.addAttribute("posts", List.of());
            model.addAttribute("viewCounts", Map.of());
            model.addAttribute("postReactions", Map.of());
            model.addAttribute("commentCounts", Map.of());
            model.addAttribute("repostCounts", Map.of());
            model.addAttribute("postAnnotations", Map.of());
            model.addAttribute("bookmarkedPostIds", Set.of());
            model.addAttribute("repostedPostIds", Set.of());
            model.addAttribute("hasNextPage", false);
            return;
        }

        List<Post> candidates = postRepo.findFollowingFeedCandidates(followedIds);

        // Latest qualifying comment per post (used both for the relevance-sort bump and, only
        // where the post's own author isn't followed, the visible annotation).
        List<Integer> candidateIds = candidates.stream().map(Post::getId).toList();
        Map<Integer, Comment> latestFollowedCommentByPost = new HashMap<>();
        if (!candidateIds.isEmpty()) {
            for (Comment c : commentRepo.findFollowedCommentsOnPosts(candidateIds, followedIds)) {
                latestFollowedCommentByPost.putIfAbsent(c.getPost().getId(), c); // list is DESC by createdAt, so first wins
            }
        }

        // One merged timeline, from two different sources of "why this belongs in the feed":
        // a post whose author is followed (existing candidates, with its own bump-by-followed-
        // comment relevance time), and a repost made by someone followed (new) - a repost from
        // someone you follow surfaces even if you don't follow who wrote the original, same as
        // any real repost feature. Both sides carry their own effective sort time; merged and
        // re-sorted together rather than as two separate lists, so a repost of an old post
        // correctly jumps back to "now" instead of sorting by the original's stale timestamp.
        List<FeedCandidate> merged = new ArrayList<>();
        for (Post p : candidates) {
            LocalDateTime ownTime = p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt();
            Comment latest = latestFollowedCommentByPost.get(p.getId());
            LocalDateTime effective = (latest != null && latest.getCreatedAt() != null && latest.getCreatedAt().isAfter(ownTime))
                    ? latest.getCreatedAt() : ownTime;
            merged.add(new FeedCandidate(FeedItem.of(p), effective));
        }
        for (Repost r : repostRepo.findVisibleByReposterIds(followedIds)) {
            merged.add(new FeedCandidate(FeedItem.repostOf(r.getPost(), r.getUser()), r.getCreatedAt()));
        }
        merged.sort(Comparator.comparing(FeedCandidate::eventTime).reversed());

        int fromIndex = Math.min(page * PAGE_SIZE, merged.size());
        int toIndex = Math.min(fromIndex + PAGE_SIZE, merged.size());
        List<FeedItem> pageItems = merged.subList(fromIndex, toIndex).stream().map(FeedCandidate::item).toList();
        List<Integer> pagePostIds = pageItems.stream().map(item -> item.getPost().getId()).distinct().toList();

        // Comment annotation only ever built from an original-post entry, never a repost one -
        // postRows.html itself also gates the "commented" tag off whenever repostedBy is set, so
        // this is belt-and-suspenders, not load-bearing on its own.
        Map<Integer, FollowedCommentAnnotation> annotations = new HashMap<>();
        for (FeedItem item : pageItems) {
            if (item.getRepostedBy() != null) {
                continue;
            }
            Post p = item.getPost();
            if (followedIdSet.contains(p.getUser().getId())) {
                continue; // author already followed - the post speaks for itself, no annotation
            }
            Comment latest = latestFollowedCommentByPost.get(p.getId());
            if (latest != null) {
                annotations.put(p.getId(), FollowedCommentAnnotation.builder()
                        .commenter(latest.getUser())
                        .commentedAt(latest.getCreatedAt())
                        .build());
            }
        }

        Set<Integer> bookmarkedPostIds = pagePostIds.isEmpty()
                ? Set.of()
                : new HashSet<>(bookmarkRepo.findBookmarkedPostIdsAmong(viewer.getId(), pagePostIds));
        Set<Integer> repostedPostIds = pagePostIds.isEmpty()
                ? Set.of()
                : new HashSet<>(repostRepo.findRepostedPostIdsAmong(viewer.getId(), pagePostIds));

        model.addAttribute("posts", pageItems);
        model.addAttribute("viewCounts", postViewService.countViewsForPosts(pagePostIds));
        model.addAttribute("postReactions", postReactionService.getSummaries(pagePostIds, null));
        model.addAttribute("commentCounts", commentService.countCommentsForPosts(pagePostIds));
        model.addAttribute("repostCounts", repostRepo.countGroupedByPostIds(pagePostIds).stream()
                .collect(java.util.stream.Collectors.toMap(RepostCount::getPostId, RepostCount::getCount)));
        model.addAttribute("postAnnotations", annotations);
        model.addAttribute("bookmarkedPostIds", bookmarkedPostIds);
        model.addAttribute("repostedPostIds", repostedPostIds);
        model.addAttribute("hasNextPage", toIndex < merged.size());
    }

    // Local pairing of a feed entry with whichever timestamp it should sort by - a post's own
    // (possibly comment-bumped) time, or a repost's own createdAt. Not a payloads/ class since
    // nothing outside this one merge-and-sort step needs it.
    private record FeedCandidate(FeedItem item, LocalDateTime eventTime) {
    }
}
