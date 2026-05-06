package com.BlogApplication.Blog.RestController;

import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.PostDto;
import com.BlogApplication.Blog.repositories.CommentRepo;
import com.BlogApplication.Blog.repositories.PostRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.CommentService;
import com.BlogApplication.Blog.services.PostService;
import com.BlogApplication.Blog.services.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class RestPostController {
    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PostService postService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private TagService tagService;

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private CommentRepo commentRepo;

    private List<Post> filteredPostByAuthor = new ArrayList<>();
    private List<Post> filteredPostByTag = new ArrayList<>();

    @GetMapping("/posts")
    public ResponseEntity<List<Post>> getAllPosts(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size) {
        List<Post> posts = postService.getPaginatedPosts(page, size).getContent();
        return new ResponseEntity<>(posts, HttpStatus.OK);
    }

    @GetMapping("/posts/createForm")
    public ResponseEntity<PostDto> showPostForm(Authentication authentication) {
        PostDto postDto = new PostDto();
        Optional<User> userOptional = userRepo.findByEmail(authentication.getName());

        if (userOptional.isEmpty()) {
            throw new UsernameNotFoundException("User not found");
        }

        String authorName = userOptional.get().getName();
        postDto.setAuthor(authorName);
        return new ResponseEntity<>(postDto, HttpStatus.OK);
    }

    @PostMapping("/post/publish")
    public ResponseEntity<Post> publishPost(@RequestBody PostDto postDto, Authentication authentication) {
        postDto.setCreatedAt(LocalDateTime.now());
        postService.save(postDto, authentication);

        filteredPostByAuthor.clear();
        filteredPostByTag.clear();

        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping("/post/{id}/view")
    public ResponseEntity<PostDto> viewPostByID(@PathVariable int id) {
        PostDto postDto = postService.getPostById(id);
        if (postDto == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(postDto, HttpStatus.OK);
    }

    @PutMapping("/posts/{id}/edit")
    public ResponseEntity<Post> editPostByID(@PathVariable int id, @RequestBody PostDto postDto) {
        postService.updatePostByID(postDto, id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @DeleteMapping("/posts/{id}/delete")
    public ResponseEntity<Void> deletePost(@PathVariable int id) {
        postService.isDeleted(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/posts/sort")
    public ResponseEntity<List<Post>> sortingOrder(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @RequestParam String order) {
        List<Post> sortedPosts;
        if (order == null || order.isBlank()) {
            sortedPosts = postService.getAllPost();
        } else if ("increase".equals(order)) {
            sortedPosts = postRepo.findAllByOrderByUpdatedAtAsc();
        } else {
            sortedPosts = postRepo.findAllByOrderByUpdatedAtDesc();
        }
        return new ResponseEntity<>(sortedPosts, HttpStatus.OK);
    }

    @GetMapping("/posts/search")
    public ResponseEntity<List<Post>> searchPosts(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam String query) {
        List<Post> searchResults = new ArrayList<>();
        if (query != null && !query.isBlank()) {
            searchResults.addAll(postService.searchByAuthor(query));
            searchResults.addAll(postService.searchByTitle(query));
            searchResults.addAll(postService.searchByContent(query));
            searchResults.addAll(tagService.searchByTag(query));
        }
        return new ResponseEntity<>(searchResults, HttpStatus.OK);
    }

    @GetMapping("/posts/filter-author")
    public ResponseEntity<List<Post>> filterPostsByAuthor(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @RequestParam String[] author) {
        if (filteredPostByTag.isEmpty()) {
            filteredPostByAuthor = postService.searchByMultipleAuthor(author);
        } else {
            filteredPostByAuthor = postService.searchByAuthorInFilteredPostByTag(filteredPostByTag, author);
        }
        return new ResponseEntity<>(filteredPostByAuthor, HttpStatus.OK);
    }

    @GetMapping("/posts/filter-tag")
    public ResponseEntity<List<Post>> filterPostsByTag(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size,
                                                       @RequestParam String[] tag) {
        if (filteredPostByAuthor.isEmpty()) {
            filteredPostByTag = tagService.searchByMultipleTag(tag);
        } else {
            filteredPostByTag = tagService.searchByTagInFilteredPostByAuthor(filteredPostByAuthor, tag);
        }
        return new ResponseEntity<>(filteredPostByTag, HttpStatus.OK);
    }

    @PostMapping("/posts/{id}/comments/add")
    public ResponseEntity<Void> addComment(@PathVariable int id,
                                           @RequestBody Comment comment) {
        commentService.save(id, comment.getContent(), comment.getName());
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @DeleteMapping("/posts/comments/{id}/delete")
    public ResponseEntity<Void> deleteComment(@PathVariable int id) {
        commentRepo.deleteById(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/posts/comments/{commentId}/reply")
    public ResponseEntity<Void> saveReply(@PathVariable int commentId,
                                          @RequestBody Comment reply) {
        Comment parentComment = commentRepo.findById(commentId);
        if (parentComment == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Post post = parentComment.getPost();
        Comment replyComment = new Comment();
        replyComment.setName(reply.getName() == null || reply.getName().isBlank() ? "Anonymous" : reply.getName());
        replyComment.setContent(reply.getContent());
        replyComment.setPost(post);
        replyComment.setParent(parentComment);
        parentComment.getReplies().add(commentRepo.save(replyComment));
        commentRepo.save(parentComment);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}