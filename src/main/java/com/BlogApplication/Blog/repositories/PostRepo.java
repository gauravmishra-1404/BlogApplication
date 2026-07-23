package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PostRepo extends JpaRepository<Post,Integer>, JpaSpecificationExecutor<Post> {
    @Query("SELECT DISTINCT UPPER(p.author) FROM Post p")
    List<String> distinctAuthor();
}
