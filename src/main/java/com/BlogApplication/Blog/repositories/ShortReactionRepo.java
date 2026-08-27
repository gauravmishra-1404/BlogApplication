package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.ReactionType;
import com.BlogApplication.Blog.models.ShortReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Mirrors PostReactionRepo exactly, against short_reactions instead of post_reactions.
@Repository
public interface ShortReactionRepo extends JpaRepository<ShortReaction, Long> {
    Optional<ShortReaction> findByShortVideoIdAndUserId(int shortId, int userId);

    long countByShortVideoIdAndReactionType(int shortId, ReactionType reactionType);

    @Query("SELECT sr.shortVideo.id AS shortId, sr.reactionType AS reactionType, COUNT(sr) AS count " +
           "FROM ShortReaction sr WHERE sr.shortVideo.id IN :shortIds GROUP BY sr.shortVideo.id, sr.reactionType")
    List<ShortReactionCount> countGroupedByShortIds(@Param("shortIds") List<Integer> shortIds);

    List<ShortReaction> findByShortVideoIdInAndUserId(List<Integer> shortIds, int userId);
}
