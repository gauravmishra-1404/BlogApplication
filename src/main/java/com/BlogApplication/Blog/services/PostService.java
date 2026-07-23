package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.payloads.PostDto;
import org.springframework.data.domain.Page;

import java.security.Principal;
import java.util.List;

public interface PostService {

       void save(PostDto postDto, Principal principal);

       PostDto getPostById(int id);

       void isDeleted(int id);

       void updatePostByID(PostDto postDto, int id);

       List<String> getAllUniqueAuthor();

       Page<Post> searchPosts(String query, List<String> authors, List<String> tags, String order, int page, int size);

       List<Comment> getComment(int postId);
}
