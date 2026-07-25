package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.PostReaction;
import com.BlogApplication.Blog.models.ReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostReactionRepo extends JpaRepository<PostReaction, Long> {
    Optional<PostReaction> findByPostIdAndUserId(int postId, int userId);

    long countByPostIdAndReactionType(int postId, ReactionType reactionType);

    // One query for a whole page of posts (e.g. the dashboard listing) instead of a COUNT per post.
    @Query("SELECT pr.post.id AS postId, pr.reactionType AS reactionType, COUNT(pr) AS count " +
            "FROM PostReaction pr WHERE pr.post.id IN :postIds GROUP BY pr.post.id, pr.reactionType")
    List<PostReactionCount> countGroupedByPostIds(@Param("postIds") List<Integer> postIds);

    List<PostReaction> findByPostIdInAndUserId(List<Integer> postIds, int userId);
}
