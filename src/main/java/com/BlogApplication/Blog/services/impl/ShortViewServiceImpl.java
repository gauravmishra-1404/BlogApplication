package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.ShortVideo;
import com.BlogApplication.Blog.models.ShortView;
import com.BlogApplication.Blog.repositories.ShortRepo;
import com.BlogApplication.Blog.repositories.ShortViewCount;
import com.BlogApplication.Blog.repositories.ShortViewRepo;
import com.BlogApplication.Blog.services.ShortViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Mirrors PostViewServiceImpl exactly, against Shorts instead of Posts. Reuses
// VisitorIdentityService unchanged for the viewerToken value - that service is already generic,
// not Post-specific.
@Service
public class ShortViewServiceImpl implements ShortViewService {

    @Autowired
    private ShortViewRepo shortViewRepo;

    @Autowired
    private ShortRepo shortRepo;

    @Override
    public void recordView(int shortId, String viewerToken) {
        if (shortViewRepo.existsByShortVideoIdAndViewerToken(shortId, viewerToken)) {
            return;
        }

        ShortVideo shortVideo = shortRepo.findById(shortId).orElse(null);
        if (shortVideo == null) {
            return;
        }

        ShortView view = new ShortView();
        view.setShortVideo(shortVideo);
        view.setViewerToken(viewerToken);
        view.setCreatedAt(LocalDateTime.now());

        try {
            shortViewRepo.save(view);
        } catch (DataIntegrityViolationException e) {
            // Race handling, same as PostViewServiceImpl.
        }
    }

    @Override
    public long countViews(int shortId) {
        return shortViewRepo.countByShortVideoId(shortId);
    }

    @Override
    public Map<Integer, Long> countViewsForShorts(List<Integer> shortIds) {
        Map<Integer, Long> counts = new HashMap<>();
        for (ShortViewCount row : shortViewRepo.countGroupedByShortIds(shortIds)) {
            counts.put(row.getShortId(), row.getViewCount());
        }
        return counts;
    }
}
