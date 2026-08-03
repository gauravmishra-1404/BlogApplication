package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.Bookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookmarkRepo extends JpaRepository<Bookmark, Long> {
    Optional<Bookmark> findByUserIdAndPostId(int userId, int postId);

    boolean existsByUserIdAndPostId(int userId, int postId);

    // One query for "which of these candidate posts have I bookmarked" instead of an existsBy
    // call per row - same batching pattern FollowRepo.findFollowedIdsAmong already uses for the
    // feed/dashboard listings.
    @Query("SELECT b.post.id FROM Bookmark b WHERE b.user.id = :userId AND b.post.id IN :postIds")
    List<Integer> findBookmarkedPostIdsAmong(@Param("userId") int userId, @Param("postIds") List<Integer> postIds);

    // The Bookmarks feed - only posts still published and not soft-deleted, most recently
    // bookmarked first. A post bookmarked while published and later unpublished/deleted just
    // quietly drops out here, same "visible" rule PostRepo.findVisibleByUser already enforces
    // for a profile's post list.
    @Query("SELECT b FROM Bookmark b WHERE b.user.id = :userId " +
           "AND (b.post.deleted IS NULL OR b.post.deleted = false) " +
           "AND (b.post.isPublished IS NULL OR b.post.isPublished = true) " +
           "ORDER BY b.createdAt DESC")
    Page<Bookmark> findVisibleByUserId(@Param("userId") int userId, Pageable pageable);
}
