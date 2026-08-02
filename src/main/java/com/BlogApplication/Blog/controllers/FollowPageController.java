package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.FollowCandidate;
import com.BlogApplication.Blog.repositories.FollowRepo;
import com.BlogApplication.Blog.repositories.UserFollowerCount;
import com.BlogApplication.Blog.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// "Follow" directory (sidebar nav item, styled after X's own Follow page) - every user on the
// platform ranked by follower count, most-followed first, so people can find and follow someone
// new rather than only ever seeing their existing network. Deliberately separate from the
// (still unbuilt) "Following" nav item, which would filter the FEED to posts from people already
// followed - this page is about finding people, not reading posts.
@Controller
public class FollowPageController {

    private static final int PAGE_SIZE = 20;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private FollowRepo followRepo;

    @GetMapping("/follow")
    public String followDirectory(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        addCandidatesToModel(page, authentication, model);
        return "followDirectory";
    }

    // Infinite-scroll-style "Load more" batch (js/followDirectoryLoadMore.js) - same fragment
    // endpoint shape as PostController.postsFragment, just for this page's own row markup.
    @GetMapping("/follow/fragment")
    public String followDirectoryFragment(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        addCandidatesToModel(page, authentication, model);
        return "fragments/followRows :: followRows";
    }

    private void addCandidatesToModel(int page, Authentication authentication, Model model) {
        User viewer = (authentication != null && authentication.isAuthenticated())
                ? userRepo.findByEmail(authentication.getName()).orElse(null)
                : null;

        Page<UserFollowerCount> resultPage = userRepo.findAllOrderByFollowerCountDesc(PageRequest.of(page, PAGE_SIZE));

        List<Integer> candidateIds = resultPage.getContent().stream().map(row -> row.getUser().getId()).toList();
        Set<Integer> viewerFollows = Set.of();
        if (viewer != null && !candidateIds.isEmpty()) {
            viewerFollows = new HashSet<>(followRepo.findFollowedIdsAmong(viewer.getId(), candidateIds));
        }

        List<FollowCandidate> candidates = new ArrayList<>();
        for (UserFollowerCount row : resultPage.getContent()) {
            User user = row.getUser();
            boolean self = viewer != null && viewer.getId() == user.getId();
            candidates.add(FollowCandidate.builder()
                    .user(user)
                    .followerCount(row.getFollowerCount())
                    .followingThisUser(viewerFollows.contains(user.getId()))
                    .self(self)
                    .build());
        }

        model.addAttribute("candidates", candidates);
        model.addAttribute("currentPage", page);
        model.addAttribute("hasNextPage", resultPage.hasNext());
    }
}
