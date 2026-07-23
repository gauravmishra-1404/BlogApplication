package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.Tags;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TagRepo extends JpaRepository<Tags, Integer> {
    Optional<Tags> findByName(String tagName);

    @Query("SELECT DISTINCT UPPER(t.name) FROM Tags t")
    List<String> distinctTag();
}
