package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.Tags;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TagRepo extends JpaRepository<Tags, Integer> {
    Optional<Tags> findByName(String tagName);

    @Query("SELECT DISTINCT UPPER(t.name) FROM Tags t")
    List<String> distinctTag();

    // Dashboard "Trending tags" widget - deleted posts don't count toward a tag's popularity,
    // same soft-delete visibility rule used everywhere else (deleted IS NULL treated as "not
    // deleted", for posts predating that column) - nor do drafts, since a tag only used on
    // someone's unpublished draft shouldn't look trending to everyone else. Pageable caps it to
    // a top-N without a second query - Spring Data applies it as a LIMIT since the return type
    // is a plain List, not Page.
    @Query("SELECT t.name AS name, COUNT(p) AS postCount FROM Tags t JOIN t.postList p " +
           "WHERE (p.deleted IS NULL OR p.deleted = false) AND (p.isPublished IS NULL OR p.isPublished = true) " +
           "GROUP BY t.name ORDER BY COUNT(p) DESC")
    List<TagPostCount> topTagsByPostCount(Pageable pageable);
}
