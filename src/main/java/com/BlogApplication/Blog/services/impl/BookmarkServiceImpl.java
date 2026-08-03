package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.Bookmark;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.BookmarkSummary;
import com.BlogApplication.Blog.repositories.BookmarkRepo;
import com.BlogApplication.Blog.repositories.PostRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.BookmarkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class BookmarkServiceImpl implements BookmarkService {

    @Autowired
    private BookmarkRepo bookmarkRepo;

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private UserRepo userRepo;

    @Override
    public Optional<BookmarkSummary> toggle(String userEmail, int postId) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Could not find user !!"));

        Post post = postRepo.findById(postId).orElse(null);
        // Same visibility rule as PostController.canViewPost, narrowed to "published only" -
        // not even the post's own owner can bookmark their own draft, since there's nothing to
        // save "for later" that isn't already sitting in Drafts.
        if (post == null || post.isDeleted() || !post.isPublished()) {
            return Optional.empty();
        }

        boolean bookmarked;
        Optional<Bookmark> existing = bookmarkRepo.findByUserIdAndPostId(user.getId(), postId);
        if (existing.isPresent()) {
            bookmarkRepo.delete(existing.get());
            bookmarked = false;
        } else {
            Bookmark bookmark = new Bookmark();
            bookmark.setUser(user);
            bookmark.setPost(post);
            bookmark.setCreatedAt(LocalDateTime.now());
            try {
                bookmarkRepo.save(bookmark);
            } catch (DataIntegrityViolationException e) {
                // Two near-simultaneous clicks both passed the findBy check before either
                // inserted - the unique constraint caught it, same race handling as
                // FollowServiceImpl/PostReactionServiceImpl. Either way the end state is
                // "bookmarked".
            }
            bookmarked = true;
        }

        return Optional.of(new BookmarkSummary(bookmarked));
    }
}
