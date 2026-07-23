package com.BlogApplication.Blog.RestController;

import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.PostDto;
import com.BlogApplication.Blog.repositories.CommentRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.CommentService;
import com.BlogApplication.Blog.services.PostService;
import com.BlogApplication.Blog.services.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    private CommentRepo commentRepo;

    @GetMapping("/posts")
    public ResponseEntity<Page<Post>> getAllPosts(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) String query,
                                                  @RequestParam(required = false) List<String> author,
                                                  @RequestParam(required = false) List<String> tag,
                                                  @RequestParam(required = false) String order) {
        return new ResponseEntity<>(postService.searchPosts(query, author, tag, order, page, size), HttpStatus.OK);
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
    public ResponseEntity<Page<Post>> sortingOrder(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "10") int size,
                                                   @RequestParam String order) {
        return new ResponseEntity<>(postService.searchPosts(null, null, null, order, page, size), HttpStatus.OK);
    }

    @GetMapping("/posts/search")
    public ResponseEntity<Page<Post>> searchPosts(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam String query) {
        return new ResponseEntity<>(postService.searchPosts(query, null, null, null, page, size), HttpStatus.OK);
    }

    @GetMapping("/posts/filter-author")
    public ResponseEntity<Page<Post>> filterPostsByAuthor(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @RequestParam List<String> author) {
        return new ResponseEntity<>(postService.searchPosts(null, author, null, null, page, size), HttpStatus.OK);
    }

    @GetMapping("/posts/filter-tag")
    public ResponseEntity<Page<Post>> filterPostsByTag(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "10") int size,
                                                       @RequestParam List<String> tag) {
        return new ResponseEntity<>(postService.searchPosts(null, null, tag, null, page, size), HttpStatus.OK);
    }

    @PostMapping("/posts/{id}/comments/add")
    public ResponseEntity<Void> addComment(@PathVariable int id,
                                           @RequestBody Comment comment,
                                           Authentication authentication) {
        commentService.save(id, comment.getContent(), authentication.getName());
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @DeleteMapping("/posts/comments/{id}/delete")
    public ResponseEntity<Void> deleteComment(@PathVariable int id, Authentication authentication) {
        Comment comment = commentRepo.findById(id);
        if (comment == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        if (!isAuthorizedForComment(authentication, comment)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        commentRepo.deleteById(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/posts/comments/{commentId}/reply")
    public ResponseEntity<Void> saveReply(@PathVariable int commentId,
                                          @RequestBody Comment reply,
                                          Authentication authentication) {
        Comment parentComment = commentRepo.findById(commentId);
        if (parentComment == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        commentService.saveReply(commentId, reply.getContent(), authentication.getName());
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    private boolean isAuthorizedForComment(Authentication authentication, Comment comment) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        String commentAuthorEmail = comment.getUser() != null ? comment.getUser().getEmail() : null;
        String postOwnerEmail = comment.getPost() != null && comment.getPost().getUser() != null
                ? comment.getPost().getUser().getEmail()
                : null;
        return isAdmin
                || (commentAuthorEmail != null && commentAuthorEmail.equals(authentication.getName()))
                || (postOwnerEmail != null && postOwnerEmail.equals(authentication.getName()));
    }
}
