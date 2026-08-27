package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.ShortVideo;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.repositories.PostRepo;
import com.BlogApplication.Blog.repositories.ShortRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

// The "Drafts" sidebar item, now real - a user's own unpublished posts AND Shorts, visible only
// to them (PostRepo/ShortRepo.findDraftsByUser already scope to :user, so there's no id/other-user
// path to guard here the way viewPost/download need PostAuthorization - this endpoint only ever
// looks up the CALLER's own drafts, never a specific post/short id someone could substitute).
//
// Short drafts were a disclosed gap in the original Shorts v1 build - findDraftsByUser existed on
// ShortRepo from day one, but nothing ever called it from here, so a Short saved as a draft was
// fully editable/reachable but never actually SHOWED UP on this page. Fixed by just also querying
// ShortRepo, same shape as the existing Post query.
@Controller
public class DraftController {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private ShortRepo shortRepo;

    @GetMapping("/drafts")
    public String myDrafts(Authentication authentication, Model model) {
        User viewer = (authentication != null && authentication.isAuthenticated())
                ? userRepo.findByEmail(authentication.getName()).orElse(null)
                : null;

        List<Post> drafts = viewer == null ? List.of() : postRepo.findDraftsByUser(viewer);
        List<ShortVideo> shortDrafts = viewer == null ? List.of() : shortRepo.findDraftsByUser(viewer);
        model.addAttribute("drafts", drafts);
        model.addAttribute("shortDrafts", shortDrafts);
        return "draftsPage";
    }
}
