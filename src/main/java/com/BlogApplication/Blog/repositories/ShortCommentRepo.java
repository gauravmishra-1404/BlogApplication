package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.ShortComment;
import com.BlogApplication.Blog.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

// Mirrors CommentRepo exactly, against short_comments instead of comments.
@Repository
public interface ShortCommentRepo extends JpaRepository<ShortComment, Integer> {
    @Override
    void deleteById(Integer integer);

    ShortComment findById(int id);

    // Profile "Replies" tab equivalent - replies received on Shorts this user authored.
    @Query("SELECT c FROM ShortComment c WHERE c.shortVideo.user = :shortAuthor " +
           "AND (c.user IS NULL OR c.user <> :shortAuthor) " +
           "AND (c.deleted IS NULL OR c.deleted = false) " +
           "AND (c.shortVideo.deleted IS NULL OR c.shortVideo.deleted = false) " +
           "AND (c.shortVideo.isPublished IS NULL OR c.shortVideo.isPublished = true) " +
           "ORDER BY c.createdAt DESC")
    List<ShortComment> findRepliesReceivedByShortAuthor(@Param("shortAuthor") User shortAuthor);

    @Query("SELECT c.shortVideo.id AS shortId, COUNT(c) AS commentCount FROM ShortComment c " +
           "WHERE c.shortVideo.id IN :shortIds AND (c.deleted IS NULL OR c.deleted = false) GROUP BY c.shortVideo.id")
    List<ShortCommentCount> countGroupedByShortIds(@Param("shortIds") List<Integer> shortIds);
}
