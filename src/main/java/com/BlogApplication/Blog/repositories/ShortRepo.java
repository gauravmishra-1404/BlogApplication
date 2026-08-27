package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.ShortVideo;
import com.BlogApplication.Blog.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ShortRepo extends JpaRepository<ShortVideo, Integer> {

    // The profile page's "Shorts" tab - exact mirror of PostRepo.findVisibleByUser, minus any
    // type filter since this table is Shorts-only.
    @Query("SELECT s FROM ShortVideo s WHERE s.user = :user AND (s.deleted IS NULL OR s.deleted = false) " +
           "AND (s.isPublished IS NULL OR s.isPublished = true) ORDER BY s.updatedAt DESC")
    List<ShortVideo> findVisibleByUser(@Param("user") User user);

    // The immersive Shorts feed - most recently published first, paginated. No search/author/tag
    // filters unlike PostRepo.searchPosts's Specification - the Shorts feed is a plain
    // reverse-chronological stream for v1, not a searchable one.
    @Query("SELECT s FROM ShortVideo s WHERE (s.deleted IS NULL OR s.deleted = false) " +
           "AND (s.isPublished IS NULL OR s.isPublished = true) ORDER BY s.publishedAt DESC")
    Page<ShortVideo> findAllVisible(Pageable pageable);

    // The "Drafts" section's Short-equivalent, same shape as PostRepo.findDraftsByUser.
    @Query("SELECT s FROM ShortVideo s WHERE s.user = :user AND (s.deleted IS NULL OR s.deleted = false) " +
           "AND s.isPublished = false ORDER BY s.updatedAt DESC")
    List<ShortVideo> findDraftsByUser(@Param("user") User user);

    // Exact mirror of PostRepo.publishDueScheduledPosts - same atomic single-UPDATE pattern, same
    // no-distributed-lock-needed reasoning, just against shorts instead of posts.
    @Modifying
    @Transactional
    @Query("UPDATE ShortVideo s SET s.isPublished = true, s.publishedAt = CURRENT_TIMESTAMP " +
           "WHERE s.isPublished = false AND s.scheduledAt IS NOT NULL AND s.scheduledAt <= CURRENT_TIMESTAMP")
    int publishDueScheduledShorts();
}
