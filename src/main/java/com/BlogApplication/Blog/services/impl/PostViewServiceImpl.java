package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.PostView;
import com.BlogApplication.Blog.repositories.PostRepo;
import com.BlogApplication.Blog.repositories.PostViewCount;
import com.BlogApplication.Blog.repositories.PostViewRepo;
import com.BlogApplication.Blog.services.PostViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PostViewServiceImpl implements PostViewService {

    @Autowired
    private PostViewRepo postViewRepo;

    @Autowired
    private PostRepo postRepo;

    @Override
    public void recordView(int postId, String viewerToken) {
        if (postViewRepo.existsByPostIdAndViewerToken(postId, viewerToken)) {
            return;
        }

        Post post = postRepo.findById(postId).orElse(null);
        if (post == null) {
            return;
        }

        PostView view = new PostView();
        view.setPost(post);
        view.setViewerToken(viewerToken);
        view.setCreatedAt(LocalDateTime.now());

        try {
            postViewRepo.save(view);
        } catch (DataIntegrityViolationException e) {
            // Two near-simultaneous requests from the same visitor both passed the existsBy
            // check before either inserted - the unique constraint catches it, and losing this
            // race just means the view was already recorded by the other request.
        }
    }

    @Override
    public long countViews(int postId) {
        return postViewRepo.countByPostId(postId);
    }

    @Override
    public Map<Integer, Long> countViewsForPosts(List<Integer> postIds) {
        Map<Integer, Long> counts = new HashMap<>();
        for (PostViewCount row : postViewRepo.countGroupedByPostIds(postIds)) {
            counts.put(row.getPostId(), row.getViewCount());
        }
        return counts;
    }
}
