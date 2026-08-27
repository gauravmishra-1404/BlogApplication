package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.ShortBookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Mirrors BookmarkRepo exactly, against short_bookmarks instead of post_bookmarks.
@Repository
public interface ShortBookmarkRepo extends JpaRepository<ShortBookmark, Long> {
    Optional<ShortBookmark> findByUserIdAndShortVideoId(int userId, int shortId);

    boolean existsByUserIdAndShortVideoId(int userId, int shortId);

    @Query("SELECT b.shortVideo.id FROM ShortBookmark b WHERE b.user.id = :userId AND b.shortVideo.id IN :shortIds")
    List<Integer> findBookmarkedShortIdsAmong(@Param("userId") int userId, @Param("shortIds") List<Integer> shortIds);

    @Query("SELECT b FROM ShortBookmark b WHERE b.user.id = :userId " +
           "AND (b.shortVideo.deleted IS NULL OR b.shortVideo.deleted = false) " +
           "AND (b.shortVideo.isPublished IS NULL OR b.shortVideo.isPublished = true) " +
           "ORDER BY b.createdAt DESC")
    Page<ShortBookmark> findVisibleByUserId(@Param("userId") int userId, Pageable pageable);
}
