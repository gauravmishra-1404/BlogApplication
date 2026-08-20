package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.Repost;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.RepostSummary;
import com.BlogApplication.Blog.repositories.PostRepo;
import com.BlogApplication.Blog.repositories.RepostRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.NotificationService;
import com.BlogApplication.Blog.services.RepostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class RepostServiceImpl implements RepostService {

    @Autowired
    private RepostRepo repostRepo;

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private NotificationService notificationService;

    @Override
    public Optional<RepostSummary> toggle(String userEmail, int postId) {
        User user = userRepo.findByEmail(userEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Could not find user !!"));

        Post post = postRepo.findById(postId).orElse(null);
        // Same visibility rule as BookmarkService.toggle - not even the post's own owner has a
        // repost button on their own draft (there's nothing public to repost yet).
        if (post == null || post.isDeleted() || !post.isPublished()) {
            return Optional.empty();
        }

        boolean reposted;
        Optional<Repost> existing = repostRepo.findByUserIdAndPostId(user.getId(), postId);
        if (existing.isPresent()) {
            repostRepo.delete(existing.get());
            reposted = false;
        } else {
            Repost repost = Repost.builder()
                    .user(user)
                    .post(post)
                    .createdAt(LocalDateTime.now())
                    .build();
            try {
                repostRepo.save(repost);
            } catch (DataIntegrityViolationException e) {
                // Two near-simultaneous clicks both passed the findBy check before either
                // inserted - the unique constraint caught it, same race handling as
                // BookmarkServiceImpl/FollowServiceImpl. Either way the end state is "reposted".
            }
            reposted = true;

            // Only on an actual new repost, never on un-repost or a self-repost - "you reposted
            // your own post" isn't a notification anyone wants, same reasoning
            // FollowServiceImpl only fires on an actual new follow.
            User author = post.getUser();
            if (author != null && author.getId() != user.getId()) {
                notificationService.notify(author, "POST_REPOSTED", user.getName(),
                        user.getName() + " reposted your post", null,
                        "/post/viewPost?id=" + postId);
            }
        }

        long repostCount = repostRepo.countByPostId(postId);
        return Optional.of(new RepostSummary(reposted, repostCount));
    }
}
