package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.models.Bookmark;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.repositories.BookmarkRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.CommentService;
import com.BlogApplication.Blog.services.PostReactionService;
import com.BlogApplication.Blog.services.PostViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// The "Bookmarks" sidebar item, now real - published posts the viewer has saved for later, most
// recently bookmarked first. Reuses fragments/postRows.html wholesale for the card markup, same
// as FollowingFeedController/DraftController's own pages.
@Controller
public class BookmarkPageController {

    private static final int PAGE_SIZE = 15;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private BookmarkRepo bookmarkRepo;

    @Autowired
    private PostViewService postViewService;

    @Autowired
    private PostReactionService postReactionService;

    @Autowired
    private CommentService commentService;

    @GetMapping("/bookmarks")
    public String myBookmarks(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        addBookmarksToModel(page, authentication, model);
        return "bookmarksPage";
    }

    // "Load more" batch (js/loadMoreFeed.js, same click-to-fetch pattern already used by the
    // Following feed) - reuses postRows.html for markup.
    @GetMapping("/bookmarks/fragment")
    public String myBookmarksFragment(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        addBookmarksToModel(page, authentication, model);
        return "fragments/postRows :: postRows";
    }

    private void addBookmarksToModel(int page, Authentication authentication, Model model) {
        User viewer = (authentication != null && authentication.isAuthenticated())
                ? userRepo.findByEmail(authentication.getName()).orElse(null)
                : null;

        model.addAttribute("currentPage", page);

        if (viewer == null) {
            model.addAttribute("posts", List.of());
            model.addAttribute("viewCounts", Map.of());
            model.addAttribute("postReactions", Map.of());
            model.addAttribute("commentCounts", Map.of());
            model.addAttribute("bookmarkedPostIds", Set.of());
            model.addAttribute("hasNextPage", false);
            return;
        }

        Page<Bookmark> bookmarkPage = bookmarkRepo.findVisibleByUserId(viewer.getId(), PageRequest.of(page, PAGE_SIZE));
        List<Post> posts = bookmarkPage.getContent().stream().map(Bookmark::getPost).toList();
        List<Integer> postIds = posts.stream().map(Post::getId).toList();

        model.addAttribute("posts", posts);
        model.addAttribute("viewCounts", postViewService.countViewsForPosts(postIds));
        model.addAttribute("postReactions", postReactionService.getSummaries(postIds, null));
        model.addAttribute("commentCounts", commentService.countCommentsForPosts(postIds));
        // Every post on this page IS a bookmark by definition - the icon always renders filled
        // here, same set-membership contract postRows.html's toggle button checks everywhere else.
        model.addAttribute("bookmarkedPostIds", new HashSet<>(postIds));
        model.addAttribute("hasNextPage", bookmarkPage.hasNext());
    }
}
