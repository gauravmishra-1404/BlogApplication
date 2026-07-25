package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.CommentReaction;
import com.BlogApplication.Blog.models.ReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommentReactionRepo extends JpaRepository<CommentReaction, Long> {
    Optional<CommentReaction> findByCommentIdAndUserId(int commentId, int userId);

    long countByCommentIdAndReactionType(int commentId, ReactionType reactionType);

    // One query for a whole comment thread instead of a COUNT per comment.
    @Query("SELECT cr.comment.id AS commentId, cr.reactionType AS reactionType, COUNT(cr) AS count " +
            "FROM CommentReaction cr WHERE cr.comment.id IN :commentIds GROUP BY cr.comment.id, cr.reactionType")
    List<CommentReactionCount> countGroupedByCommentIds(@Param("commentIds") List<Integer> commentIds);

    // Likewise, this user's reactions across the whole thread in one query.
    List<CommentReaction> findByCommentIdInAndUserId(List<Integer> commentIds, int userId);
}
