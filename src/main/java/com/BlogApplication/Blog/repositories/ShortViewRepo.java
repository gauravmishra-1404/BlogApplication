package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.ShortView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

// Mirrors PostViewRepo exactly, against short_views instead of post_views.
@Repository
public interface ShortViewRepo extends JpaRepository<ShortView, Long> {
    boolean existsByShortVideoIdAndViewerToken(int shortId, String viewerToken);

    long countByShortVideoId(int shortId);

    @Query("SELECT sv.shortVideo.id AS shortId, COUNT(sv) AS viewCount FROM ShortView sv WHERE sv.shortVideo.id IN :shortIds GROUP BY sv.shortVideo.id")
    List<ShortViewCount> countGroupedByShortIds(@Param("shortIds") List<Integer> shortIds);
}
