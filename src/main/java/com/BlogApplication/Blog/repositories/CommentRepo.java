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
}
