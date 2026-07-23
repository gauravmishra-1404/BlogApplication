package com.BlogApplication.Blog.controllers;

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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
public class PostController {
    @Autowired
    private PostService postService;

    @Autowired
    private CommentRepo commentRepo;

    @Autowired
    private TagService tagService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private UserRepo userRepo;

    @GetMapping("/posts")
    public String getAllPosts(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size,
                              @RequestParam(required = false) String query,
                              @RequestParam(required = false) List<String> author,
                              @RequestParam(required = false) List<String> tag,
                              @RequestParam(required = false) String order,
                              Model model) {
        return listPosts(query, author, tag, order, page, size, model);
    }

    @GetMapping("/posts/createForm")
    public String showPostForm(Model model, Authentication authentication) {
        PostDto postDto = new PostDto();
        Optional<User> userOptional = userRepo.findByEmail(authentication.getName());

        if (userOptional.isEmpty()) {
            throw new UsernameNotFoundException("Could not found user !!");
        }
        String authorName = userOptional.get().getName();
        postDto.setAuthor(authorName);

        model.addAttribute("role", userOptional.get().getRole());
        model.addAttribute("postDto", postDto);
        return "newPost";
    }


    @PostMapping("/post/publish")
    public String publishPost(@ModelAttribute("postDto") PostDto postDto, Principal principal) {
        postDto.setCreatedAt(LocalDateTime.now());
        postService.save(postDto, principal);
        return "redirect:/posts";
    }

    @GetMapping("/post/viewPost")
    public String viewPostByID(@RequestParam("id") int id, Model model, Authentication authentication) {
        PostDto postDtoById = postService.getPostById(id);

        if (postDtoById == null) {
            return "redirect:/posts";
        }
        model.addAttribute("comment", new Comment());
        model.addAttribute("post", postDtoById);
        userRepo.findByEmail(authentication.getName()).ifPresent(u -> model.addAttribute("currentUserName", u.getName()));
        return "viewPostByID";
    }

    //editPostByID(){}
    @GetMapping("/posts/edit")
    public String editPostByID(@RequestParam("id") int id, Model model){
        PostDto postDto = postService.getPostById(id);
        postDto.setId(id);
        model.addAttribute("post", postDto);
        return  "editByPostID";
    }

    //rePublishByID(){}
    @PostMapping("/post/republish")
    public String rePublishPostByID(@ModelAttribute("postDto") PostDto postDto){
        postService.updatePostByID(postDto, postDto.getId());
        return "redirect:/posts";
    }

    //deletePostByID
    @PostMapping("/posts/delete")
    public String deletePost(@RequestParam("id") int id, RedirectAttributes redirectAttributes){
        postService.isDeleted(id);
        redirectAttributes.addFlashAttribute("message", "Post deleted successfully");
        return  "redirect:/posts";
    }

    //sorting post
    @GetMapping("/posts/sort")
    public String sortingOrder(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               @RequestParam(required = false) String query,
                               @RequestParam(required = false) List<String> author,
                               @RequestParam(required = false) List<String> tag,
                               @RequestParam String order,
                               Model model){
        return listPosts(query, author, tag, order, page, size, model);
    }

    //searching
    @GetMapping("/posts/search")
    public String searchController(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size,
                                   @RequestParam(required = false) String query,
                                   @RequestParam(required = false) List<String> author,
                                   @RequestParam(required = false) List<String> tag,
                                   @RequestParam(required = false) String order,
                                   Model model){
        return listPosts(query, author, tag, order, page, size, model);
    }

    //filtering
    @GetMapping("/posts/filter-author")
    public String filteredPostAuthor(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(required = false) String query,
                                     @RequestParam(required = false) List<String> author,
                                     @RequestParam(required = false) List<String> tag,
                                     @RequestParam(required = false) String order,
                                     Model model){
        return listPosts(query, author, tag, order, page, size, model);
    }

    @GetMapping("/posts/filter-tag")
    public String filteredPostTag(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(required = false) String query,
                                  @RequestParam(required = false) List<String> author,
                                  @RequestParam(required = false) List<String> tag,
                                  @RequestParam(required = false) String order,
                                  Model model){
        return listPosts(query, author, tag, order, page, size, model);
    }

    private String listPosts(String query, List<String> author, List<String> tag, String order,
                             int page, int size, Model model) {
        Page<Post> postPage = postService.searchPosts(query, author, tag, order, page, size);

        model.addAttribute("posts", postPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", postPage.getTotalPages());
        model.addAttribute("totalItems", postPage.getTotalElements());
        model.addAttribute("pageSize", size);

        model.addAttribute("authors", postService.getAllUniqueAuthor());
        model.addAttribute("tags", tagService.getAllUniqueTags());

        model.addAttribute("activeQuery", query);
        model.addAttribute("activeAuthors", author == null ? List.of() : author);
        model.addAttribute("activeTags", tag == null ? List.of() : tag);
        model.addAttribute("activeOrder", order);

        return "postDashboard";
    }

    //    add comments on post by id
    @PostMapping("/posts/{id}/comments/add")
    public String addComment(@PathVariable("id") int postId,
                             @RequestParam String content,
                             Authentication authentication) {
        commentService.save(postId, content, authentication.getName());
        return "redirect:/post/viewPost?id=" + postId;
    }

    @PostMapping("/posts/comments/delete/{id}")
    public String deleteComment(@PathVariable("id") int commentId, Authentication authentication) {
        Comment com = commentRepo.findById(commentId);
        if (com == null) {
            return "redirect:/posts";
        }

        Post postCom = com.getPost();
        if (postCom == null) {
            return "redirect:/posts";
        }

        if (!isAuthorizedForComment(authentication, com)) {
            return "redirect:/post/viewPost?id=" + postCom.getId();
        }

        deleteCommentWithReplies(com);

        return "redirect:/post/viewPost?id=" + postCom.getId();
    }

    private void deleteCommentWithReplies(Comment comment) {
        if (comment.getReplies() != null && !comment.getReplies().isEmpty()) {
            List<Comment> replies = new ArrayList<>(comment.getReplies());
            for (Comment reply : replies) {
                deleteCommentWithReplies(reply);
            }
        }
        commentRepo.delete(comment);
    }

    @GetMapping("/posts/comments/edit")
    public String editCommentPage(@RequestParam("id") int commentId, Model model, Authentication authentication) {
        Comment comment = commentRepo.findById(commentId);
        if (comment == null || comment.getPost() == null) {
            return "redirect:/posts";
        }

        if (!isAuthorizedForComment(authentication, comment)) {
            return "redirect:/post/viewPost?id=" + comment.getPost().getId();
        }

        model.addAttribute("comment", comment);
        model.addAttribute("postId", comment.getPost().getId());
        return "editComment";
    }

    @PostMapping("/posts/comments/edit")
    public String editComment(@RequestParam("id") int commentId,
                              @RequestParam("content") String content,
                              Authentication authentication) {
        Comment comment = commentRepo.findById(commentId);
        if (comment == null || comment.getPost() == null) {
            return "redirect:/posts";
        }

        if (!isAuthorizedForComment(authentication, comment)) {
            return "redirect:/post/viewPost?id=" + comment.getPost().getId();
        }

        comment.setContent(content);
        comment.setEdited(true);
        commentRepo.save(comment);
        return "redirect:/post/viewPost?id=" + comment.getPost().getId();
    }

    private boolean isAuthorizedForComment(Authentication authentication, Comment comment) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        String commentAuthorEmail = comment.getUser() != null ? comment.getUser().getEmail() : null;
        String postOwnerEmail = comment.getPost() != null && comment.getPost().getUser() != null
                ? comment.getPost().getUser().getEmail()
                : null;

        return isAdmin
                || (commentAuthorEmail != null && commentAuthorEmail.equals(authentication.getName()))
                || (postOwnerEmail != null && postOwnerEmail.equals(authentication.getName()));
    }

    @PostMapping("/posts/comments/{commentId}/reply")
    public String saveReply(@PathVariable("commentId") int commentId,
                            @RequestParam("content") String content,
                            Authentication authentication) {
        Comment parentComment = commentRepo.findById(commentId);

        if (parentComment == null || parentComment.getPost() == null) {
            return "redirect:/posts";
        }

        commentService.saveReply(commentId, content, authentication.getName());

        return "redirect:/post/viewPost?id=" + parentComment.getPost().getId();
    }

}
