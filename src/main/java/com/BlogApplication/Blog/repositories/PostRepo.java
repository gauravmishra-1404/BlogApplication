package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepo extends JpaRepository<Post,Integer>, JpaSpecificationExecutor<Post> {
    @Query("SELECT DISTINCT UPPER(p.author) FROM Post p")
    List<String> distinctAuthor();

    // Used by the profile page's "Posts" tab - same soft-delete visibility rule as the
    // dashboard listing (deleted IS NULL treated as "not deleted", for rows from before that
    // column existed), plus the same null-safe published check (isPublished IS NULL reads as
    // published, for rows predating the draft feature). Drafts are deliberately excluded even
    // when the viewer IS the profile's own owner - this tab represents what other people see
    // when they visit you, so a draft never appears here at all, only in the dedicated Drafts
    // section (DraftController.myDrafts).
    @Query("SELECT p FROM Post p WHERE p.user = :user AND (p.deleted IS NULL OR p.deleted = false) " +
           "AND (p.isPublished IS NULL OR p.isPublished = true) ORDER BY p.updatedAt DESC")
    List<Post> findVisibleByUser(@Param("user") User user);

    // Dashboard "Active writers" widget - real users with a post count, not just the plain
    // author-name strings distinctAuthor() gives, so the widget can show an actual avatar/profile
    // link. Excludes posts with no user (legacy/orphaned author string) since there's no profile
    // to link to for those. Drafts don't count toward this - an unpublished post shouldn't inflate
    // a public "how active is this author" signal nobody else can actually see the post behind.
    @Query("SELECT p.user AS user, COUNT(p) AS postCount FROM Post p WHERE p.user IS NOT NULL " +
           "AND (p.deleted IS NULL OR p.deleted = false) AND (p.isPublished IS NULL OR p.isPublished = true) " +
           "GROUP BY p.user ORDER BY COUNT(p) DESC")
    List<AuthorPostCount> topAuthorsByPostCount(Pageable pageable);

    // "Following" feed candidates - a post qualifies if its author is someone the viewer
    // follows, OR someone the viewer follows commented on it (regardless of who wrote it).
    // Unsorted/unpaginated here on purpose - FollowingFeedController computes each post's
    // relevance timestamp (own activity vs. latest qualifying comment) and paginates in Java,
    // since that "whichever is more recent" comparison isn't cleanly expressible as portable
    // JPQL ORDER BY across both H2 (local) and Postgres (production). Drafts are excluded even
    // from the author's own followers' feed - unpublished means unpublished, full stop.
    @Query("SELECT DISTINCT p FROM Post p WHERE (p.deleted IS NULL OR p.deleted = false) " +
           "AND (p.isPublished IS NULL OR p.isPublished = true) AND (" +
           "p.user.id IN :followedIds OR p.id IN (" +
           "SELECT c.post.id FROM Comment c WHERE (c.deleted IS NULL OR c.deleted = false) AND c.user.id IN :followedIds" +
           "))")
    List<Post> findFollowingFeedCandidates(@Param("followedIds") List<Integer> followedIds);

    // The "Drafts" section - a user's own unpublished posts only, most recently edited first so
    // work-in-progress naturally floats to the top. isPublished must be explicitly false here
    // (not the null-safe "true or null" pattern above) - a draft is only ever created with
    // isPublished explicitly set false by PostServiceImpl.save(), so there's no legacy-row case
    // to account for the way there is for "is this published".
    @Query("SELECT p FROM Post p WHERE p.user = :user AND (p.deleted IS NULL OR p.deleted = false) " +
           "AND p.isPublished = false ORDER BY p.updatedAt DESC")
    List<Post> findDraftsByUser(@Param("user") User user);
}
