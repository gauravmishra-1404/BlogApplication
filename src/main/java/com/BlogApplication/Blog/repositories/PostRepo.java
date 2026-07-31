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
    // column existed).
    @Query("SELECT p FROM Post p WHERE p.user = :user AND (p.deleted IS NULL OR p.deleted = false) ORDER BY p.updatedAt DESC")
    List<Post> findVisibleByUser(@Param("user") User user);

    // Dashboard "Active writers" widget - real users with a post count, not just the plain
    // author-name strings distinctAuthor() gives, so the widget can show an actual avatar/profile
    // link. Excludes posts with no user (legacy/orphaned author string) since there's no profile
    // to link to for those.
    @Query("SELECT p.user AS user, COUNT(p) AS postCount FROM Post p WHERE p.user IS NOT NULL " +
           "AND (p.deleted IS NULL OR p.deleted = false) GROUP BY p.user ORDER BY COUNT(p) DESC")
    List<AuthorPostCount> topAuthorsByPostCount(Pageable pageable);
}
