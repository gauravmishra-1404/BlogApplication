package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.FollowedCommentAnnotation;
import com.BlogApplication.Blog.repositories.CommentRepo;
import com.BlogApplication.Blog.repositories.FollowRepo;
import com.BlogApplication.Blog.repositories.PostRepo;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The "Following" sidebar item, now real: posts by people the viewer follows, plus posts
// anyone else wrote that someone the viewer follows commented on. Reuses fragments/postRows.html
// wholesale for the actual card markup (same model attribute names the main feed already
// populates: posts/viewCounts/postReactions/commentCounts/hasNextPage) - postAnnotations is the
// one new, optional attribute layered on top for the "X commented" line.
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
            model.addAttribute("postAnnotations", Map.of());
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

        // Most recent activity first - a post's own timestamp, bumped forward if a followed
        // person commented on it more recently than that.
        candidates.sort(Comparator.comparing((Post p) -> {
            LocalDateTime ownTime = p.getUpdatedAt() != null ? p.getUpdatedAt() : p.getCreatedAt();
            Comment latest = latestFollowedCommentByPost.get(p.getId());
            if (latest != null && latest.getCreatedAt() != null && latest.getCreatedAt().isAfter(ownTime)) {
                return latest.getCreatedAt();
            }
            return ownTime;
        }).reversed());

        int fromIndex = Math.min(page * PAGE_SIZE, candidates.size());
        int toIndex = Math.min(fromIndex + PAGE_SIZE, candidates.size());
        List<Post> pagePosts = candidates.subList(fromIndex, toIndex);
        List<Integer> pagePostIds = pagePosts.stream().map(Post::getId).toList();

        Map<Integer, FollowedCommentAnnotation> annotations = new HashMap<>();
        for (Post p : pagePosts) {
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

        model.addAttribute("posts", pagePosts);
        model.addAttribute("viewCounts", postViewService.countViewsForPosts(pagePostIds));
        model.addAttribute("postReactions", postReactionService.getSummaries(pagePostIds, null));
        model.addAttribute("commentCounts", commentService.countCommentsForPosts(pagePostIds));
        model.addAttribute("postAnnotations", annotations);
        model.addAttribute("hasNextPage", toIndex < candidates.size());
    }
}
