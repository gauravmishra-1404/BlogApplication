package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.Repost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RepostRepo extends JpaRepository<Repost, Long> {
    Optional<Repost> findByUserIdAndPostId(int userId, int postId);

    boolean existsByUserIdAndPostId(int userId, int postId);

    long countByPostId(int postId);

    // One query for "which of these candidate posts have I reposted" instead of an existsBy
    // call per row - same batching pattern BookmarkRepo.findBookmarkedPostIdsAmong already uses.
    @Query("SELECT r.post.id FROM Repost r WHERE r.user.id = :userId AND r.post.id IN :postIds")
    List<Integer> findRepostedPostIdsAmong(@Param("userId") int userId, @Param("postIds") List<Integer> postIds);

    // One grouped query for a whole page's repost counts instead of a COUNT per post - same
    // reasoning PostReactionRepo.countGroupedByPostIds already establishes for likes/dislikes.
    @Query("SELECT r.post.id AS postId, COUNT(r) AS count FROM Repost r WHERE r.post.id IN :postIds GROUP BY r.post.id")
    List<RepostCount> countGroupedByPostIds(@Param("postIds") List<Integer> postIds);

    // The Reposts profile tab - only posts still published and not soft-deleted, most recently
    // reposted first. A post reposted while published and later unpublished/deleted just quietly
    // drops out here, same "visible" rule BookmarkRepo.findVisibleByUserId already enforces.
    // JOIN FETCH r.post - profile.html reads repost.post.title/author/excerpt/updatedAt/id per
    // row; without this, each row lazily loads its own Post in a separate query (N+1) once
    // there's more than one repost on the page.
    @Query("SELECT r FROM Repost r JOIN FETCH r.post WHERE r.user.id = :userId " +
           "AND (r.post.deleted IS NULL OR r.post.deleted = false) " +
           "AND (r.post.isPublished IS NULL OR r.post.isPublished = true) " +
           "ORDER BY r.createdAt DESC")
    Page<Repost> findVisibleByUserId(@Param("userId") int userId, Pageable pageable);

    // FollowingFeedController's own source for repost candidates - every visible repost made by
    // any of the viewer's followed users, unpaged (that controller already loads its whole
    // candidate set into memory and paginates in Java, to interleave with its existing
    // bump-by-followed-comment relevance sort; this just gives it the repost half of that set in
    // the same shape). Caller guards the empty-following case before calling, same as
    // PostRepo.findFollowingFeedCandidates already assumes. JOIN FETCH both r.post and r.user -
    // FeedItem.repostOf(r.getPost(), r.getUser()) needs both, and postRows.html's repost tag
    // reads several fields off each (post's own content, reposter's avatar/name) - same N+1
    // reasoning as findVisibleByUserId above, just for two associations instead of one.
    //
    // Deliberately the ONLY feed reposts merge into - the main dashboard feed (PostServiceImpl.
    // getListing()) is a single global timeline shown identically to every viewer, with no notion
    // of "whose activity"; merging reposts into it would mean a user sees their OWN reposts
    // reflected back on their own /home visit, redundant with their own profile's Reposts tab.
    // Reposts only ever surface to the reposting user's OWN followers, here.
    @Query("SELECT r FROM Repost r JOIN FETCH r.post JOIN FETCH r.user WHERE r.user.id IN :reposterIds " +
           "AND (r.post.deleted IS NULL OR r.post.deleted = false) " +
           "AND (r.post.isPublished IS NULL OR r.post.isPublished = true)")
    List<Repost> findVisibleByReposterIds(@Param("reposterIds") List<Integer> reposterIds);
}
