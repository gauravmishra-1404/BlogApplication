package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.PostView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostViewRepo extends JpaRepository<PostView, Long> {
    boolean existsByPostIdAndViewerToken(int postId, String viewerToken);

    long countByPostId(int postId);

    // One query for a whole page of posts (e.g. the dashboard listing) instead of a separate
    // COUNT per row - posts with zero views simply don't appear in the result.
    @Query("SELECT pv.post.id AS postId, COUNT(pv) AS viewCount FROM PostView pv WHERE pv.post.id IN :postIds GROUP BY pv.post.id")
    List<PostViewCount> countGroupedByPostIds(@Param("postIds") List<Integer> postIds);
}
