package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.Tags;
import com.BlogApplication.Blog.repositories.TagRepo;
import com.BlogApplication.Blog.services.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TagServiceImpl implements TagService {
    @Autowired
    private TagRepo tagRepo;

    @Override
    public void savePost(Tags tag){
        tagRepo.save(tag);
    }

    @Override
    public void deleteTag(int id) {
        tagRepo.deleteById(id);
    }

    @Override
    public Optional<Tags> findByName(String tagName) {
        return tagRepo.findByName(tagName);
    }

    @Override
    public List<String> getAllUniqueTags() {
        return this.tagRepo.distinctTag();
    }
}
