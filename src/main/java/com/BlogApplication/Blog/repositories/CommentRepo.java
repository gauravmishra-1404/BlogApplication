package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepo extends JpaRepository<Comment,Integer> {
    @Override
    void deleteById(Integer integer);

    Comment findById(int id);

    // Used by the profile page's "Replies" tab - same soft-delete visibility rule as the post
    // page's comment thread.
    @Query("SELECT c FROM Comment c WHERE c.user = :user AND (c.deleted IS NULL OR c.deleted = false) ORDER BY c.createdAt DESC")
    List<Comment> findVisibleByUser(@Param("user") User user);

    // One query for a whole page of posts (e.g. the dashboard listing) instead of a separate
    // COUNT per row, same pattern as PostViewRepo.countGroupedByPostIds. Counts replies too, not
    // just top-level comments - the feed's count is "how big is this conversation", the same
    // thing #i-comment next to it represents.
    @Query("SELECT c.post.id AS postId, COUNT(c) AS commentCount FROM Comment c " +
            "WHERE c.post.id IN :postIds AND (c.deleted IS NULL OR c.deleted = false) GROUP BY c.post.id")
    List<CommentCount> countGroupedByPostIds(@Param("postIds") List<Integer> postIds);

    // Every comment a followed user left on any of these candidate posts, most recent first -
    // backs the "Following" feed's relevance ordering and its "X commented" annotation.
    // Most-recent-first + Java-side grouping (first match per post wins) gives the latest
    // qualifying comment per post without a separate MAX()-plus-join query.
    @Query("SELECT c FROM Comment c WHERE c.post.id IN :postIds AND c.user.id IN :followedIds " +
            "AND (c.deleted IS NULL OR c.deleted = false) ORDER BY c.createdAt DESC")
    List<Comment> findFollowedCommentsOnPosts(@Param("postIds") List<Integer> postIds, @Param("followedIds") List<Integer> followedIds);
}
