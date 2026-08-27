package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.models.Bookmark;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.ShortBookmark;
import com.BlogApplication.Blog.models.ShortVideo;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.FeedItem;
import com.BlogApplication.Blog.repositories.BookmarkRepo;
import com.BlogApplication.Blog.repositories.RepostCount;
import com.BlogApplication.Blog.repositories.RepostRepo;
import com.BlogApplication.Blog.repositories.ShortBookmarkRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.CommentService;
import com.BlogApplication.Blog.services.PostReactionService;
import com.BlogApplication.Blog.services.PostViewService;
import com.BlogApplication.Blog.services.ShortViewService;
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
//
// Bookmarked Shorts were a disclosed gap (same shape as DraftController's own) - ShortBookmarkRepo
// .findVisibleByUserId existed from day one but nothing here ever called it, so a bookmarked Short
// saved fine but never actually showed up on this page. Fixed by also querying it and rendering a
// second tile-grid section (profile.html's own Shorts-tab tile markup, since a bookmarked Short is
// published and DOES have a real /shorts/{id} URL - unlike a draft, this is a real <a>, not a
// role="button" tile). Kept out of the /bookmarks/fragment "load more" path deliberately - that
// endpoint re-renders only postRows for the Post list's own infinite scroll, and Shorts aren't
// paginated the same way here, so re-including them on every "load more" call would duplicate the
// tile grid instead of extending it.
@Controller
public class BookmarkPageController {

    private static final int PAGE_SIZE = 15;

    // Bookmarked Shorts render as a single static tile grid (no "load more" of their own, see
    // class comment) - a generous single page is enough to close the gap without building a
    // second infinite-scroll list just for Shorts.
    private static final int SHORTS_PAGE_SIZE = 30;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private BookmarkRepo bookmarkRepo;

    @Autowired
    private ShortBookmarkRepo shortBookmarkRepo;

    @Autowired
    private PostViewService postViewService;

    @Autowired
    private PostReactionService postReactionService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private RepostRepo repostRepo;

    @Autowired
    private ShortViewService shortViewService;

    @GetMapping("/bookmarks")
    public String myBookmarks(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        addBookmarksToModel(page, authentication, model);
        addShortBookmarksToModel(authentication, model);
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
            model.addAttribute("repostCounts", Map.of());
            model.addAttribute("bookmarkedPostIds", Set.of());
            model.addAttribute("repostedPostIds", Set.of());
            model.addAttribute("hasNextPage", false);
            return;
        }

        Page<Bookmark> bookmarkPage = bookmarkRepo.findVisibleByUserId(viewer.getId(), PageRequest.of(page, PAGE_SIZE));
        List<Post> posts = bookmarkPage.getContent().stream().map(Bookmark::getPost).toList();
        List<Integer> postIds = posts.stream().map(Post::getId).toList();

        // A bookmark listing never carries repost attribution - FeedItem.of() (repostedBy null)
        // for every row, same wrapper postRows.html now expects everywhere, just always the
        // plain-post shape here.
        model.addAttribute("posts", posts.stream().map(FeedItem::of).toList());
        model.addAttribute("viewCounts", postViewService.countViewsForPosts(postIds));
        model.addAttribute("postReactions", postReactionService.getSummaries(postIds, null));
        model.addAttribute("commentCounts", commentService.countCommentsForPosts(postIds));
        model.addAttribute("repostCounts", repostRepo.countGroupedByPostIds(postIds).stream()
                .collect(java.util.stream.Collectors.toMap(RepostCount::getPostId, RepostCount::getCount)));
        // Every post on this page IS a bookmark by definition - the icon always renders filled
        // here, same set-membership contract postRows.html's toggle button checks everywhere else.
        model.addAttribute("bookmarkedPostIds", new HashSet<>(postIds));
        model.addAttribute("repostedPostIds", new HashSet<>(repostRepo.findRepostedPostIdsAmong(viewer.getId(), postIds)));
        model.addAttribute("hasNextPage", bookmarkPage.hasNext());
    }

    // Same shape as addBookmarksToModel above, scoped to Shorts - see class comment for why this
    // is a single page rather than its own "load more" list.
    private void addShortBookmarksToModel(Authentication authentication, Model model) {
        User viewer = (authentication != null && authentication.isAuthenticated())
                ? userRepo.findByEmail(authentication.getName()).orElse(null)
                : null;

        if (viewer == null) {
            model.addAttribute("shorts", List.of());
            model.addAttribute("shortViewCounts", Map.of());
            return;
        }

        Page<ShortBookmark> shortBookmarkPage = shortBookmarkRepo.findVisibleByUserId(viewer.getId(), PageRequest.of(0, SHORTS_PAGE_SIZE));
        List<ShortVideo> shorts = shortBookmarkPage.getContent().stream().map(ShortBookmark::getShortVideo).toList();
        List<Integer> shortIds = shorts.stream().map(ShortVideo::getId).toList();

        model.addAttribute("shorts", shorts);
        model.addAttribute("shortViewCounts", shortViewService.countViewsForShorts(shortIds));
    }
}
