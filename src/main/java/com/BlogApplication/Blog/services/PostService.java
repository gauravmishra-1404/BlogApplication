package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.payloads.PostDetail;
import com.BlogApplication.Blog.payloads.PostDto;
import com.BlogApplication.Blog.payloads.PostListing;
import com.BlogApplication.Blog.repositories.AuthorPostCount;
import org.springframework.data.domain.Page;

import java.security.Principal;
import java.util.List;

public interface PostService {

       void save(PostDto postDto, Principal principal);

       PostDto getPostById(int id);

       // Everything the full post page and the dashboard's modal post view need beyond the raw
       // post - a fresh view count plus reaction summaries (post + every comment). Call this
       // *after* the caller has already recorded the view via PostViewService.recordView (that
       // needs VisitorIdentityService's cookie/request handling, a web-layer concern that stays
       // in the controller) so the count returned here reflects the just-recorded view. Returns
       // null if the post doesn't exist or is soft-deleted, same contract as getPostById.
       PostDetail getPostDetail(int id, String userEmail);

       void isDeleted(int id);

       void updatePostByID(PostDto postDto, int id);

       List<String> getAllUniqueAuthor();

       // Dashboard "Active writers" widget - top `limit` authors by visible post count.
       List<AuthorPostCount> getTopAuthors(int limit);

       Page<Post> searchPosts(String query, List<String> authors, List<String> tags, String order, int page, int size);

       // Everything the dashboard (full page) and its infinite-scroll fragment endpoint need to
       // render one batch of posts - posts themselves plus view counts, reaction summaries,
       // paging metadata and hasNextPage, all assembled here instead of in the controller.
       PostListing getListing(String query, List<String> authors, List<String> tags, String order, int page, int size);

       List<Comment> getComment(int postId);
}
