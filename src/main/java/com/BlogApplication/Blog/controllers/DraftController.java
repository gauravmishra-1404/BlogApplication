package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.repositories.PostRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

// The "Drafts" sidebar item, now real - a user's own unpublished posts, visible only to them
// (PostRepo.findDraftsByUser already scopes to :user, so there's no id/other-user path to guard
// here the way viewPost/download need PostAuthorization - this endpoint only ever looks up the
// CALLER's own drafts, never a specific post id someone could substitute).
@Controller
public class DraftController {

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PostRepo postRepo;

    @GetMapping("/drafts")
    public String myDrafts(Authentication authentication, Model model) {
        User viewer = (authentication != null && authentication.isAuthenticated())
                ? userRepo.findByEmail(authentication.getName()).orElse(null)
                : null;

        List<Post> drafts = viewer == null ? List.of() : postRepo.findDraftsByUser(viewer);
        model.addAttribute("drafts", drafts);
        return "draftsPage";
    }
}
