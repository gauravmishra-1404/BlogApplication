package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FollowRepo extends JpaRepository<Follow, Long> {
    Optional<Follow> findByFollowerIdAndFollowedId(int followerId, int followedId);

    boolean existsByFollowerIdAndFollowedId(int followerId, int followedId);

    long countByFollowedId(int followedId);

    long countByFollowerId(int followerId);

    // Followers list (people who follow this profile) / Following list (people this profile
    // follows), most recent first - backs the followers/following modal.
    List<Follow> findByFollowedIdOrderByCreatedAtDesc(int followedId);

    List<Follow> findByFollowerIdOrderByCreatedAtDesc(int followerId);

    // One query for "which of these candidate users am I already following" instead of an
    // existsBy call per candidate - used by the dashboard's Active-writers widget.
    @Query("SELECT f.followed.id FROM Follow f WHERE f.follower.id = :followerId AND f.followed.id IN :followedIds")
    List<Integer> findFollowedIdsAmong(@Param("followerId") int followerId, @Param("followedIds") List<Integer> followedIds);

    // The full, unfiltered list of ids one user follows - backs the "Following" feed, which
    // needs to know every followed id up front (not just which of a pre-known candidate set is
    // followed, unlike findFollowedIdsAmong above).
    @Query("SELECT f.followed.id FROM Follow f WHERE f.follower.id = :followerId")
    List<Integer> findAllFollowedIdsByFollowerId(@Param("followerId") int followerId);
}
