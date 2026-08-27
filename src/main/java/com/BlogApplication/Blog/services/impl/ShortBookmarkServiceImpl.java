package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.ShortBookmark;
import com.BlogApplication.Blog.models.ShortVideo;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.BookmarkSummary;
import com.BlogApplication.Blog.repositories.ShortBookmarkRepo;
import com.BlogApplication.Blog.repositories.ShortRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.ShortBookmarkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

// Mirrors BookmarkServiceImpl exactly, against Shorts instead of Posts.
@Service
public class ShortBookmarkServiceImpl implements ShortBookmarkService {

    @Autowired
    private ShortBookmarkRepo shortBookmarkRepo;

    @Autowired
    private ShortRepo shortRepo;

    @Autowired
    private UserRepo userRepo;

    @Override
    public Optional<BookmarkSummary> toggle(String userEmail, int shortId) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Could not find user !!"));

        ShortVideo shortVideo = shortRepo.findById(shortId).orElse(null);
        if (shortVideo == null || shortVideo.isDeleted() || !shortVideo.isPublished()) {
            return Optional.empty();
        }

        boolean bookmarked;
        Optional<ShortBookmark> existing = shortBookmarkRepo.findByUserIdAndShortVideoId(user.getId(), shortId);
        if (existing.isPresent()) {
            shortBookmarkRepo.delete(existing.get());
            bookmarked = false;
        } else {
            ShortBookmark bookmark = new ShortBookmark();
            bookmark.setUser(user);
            bookmark.setShortVideo(shortVideo);
            bookmark.setCreatedAt(LocalDateTime.now());
            try {
                shortBookmarkRepo.save(bookmark);
            } catch (DataIntegrityViolationException e) {
                // Race handling, same as BookmarkServiceImpl.
            }
            bookmarked = true;
        }

        return Optional.of(new BookmarkSummary(bookmarked));
    }
}
