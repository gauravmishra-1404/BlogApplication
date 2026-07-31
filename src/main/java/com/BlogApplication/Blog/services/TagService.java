package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.models.Tags;
import com.BlogApplication.Blog.repositories.TagPostCount;

import java.util.List;
import java.util.Optional;


public interface TagService {
    void savePost(Tags tag);

    void deleteTag(int id);

    Optional<Tags> findByName(String tagName);

    List<String> getAllUniqueTags();

    // Dashboard "Trending tags" widget - top `limit` tags by visible post count.
    List<TagPostCount> getTrendingTags(int limit);
}
