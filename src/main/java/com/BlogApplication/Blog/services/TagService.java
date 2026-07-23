package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.models.Tags;

import java.util.List;
import java.util.Optional;


public interface TagService {
    void savePost(Tags tag);

    void deleteTag(int id);

    Optional<Tags> findByName(String tagName);

    List<String> getAllUniqueTags();
}
