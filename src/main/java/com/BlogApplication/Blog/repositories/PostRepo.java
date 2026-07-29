package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.User;
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
}
