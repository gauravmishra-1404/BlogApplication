package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.Tags;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.PostDto;
import com.BlogApplication.Blog.payloads.UserDto;
import com.BlogApplication.Blog.repositories.CommentRepo;
import com.BlogApplication.Blog.repositories.PostRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.CommentService;
import com.BlogApplication.Blog.services.PostService;
import com.BlogApplication.Blog.services.TagService;
import org.hibernate.annotations.Comments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
public class PostController {
    @Autowired
    private PostService postService;

    @Autowired
    private CommentRepo commentRepo;

    @Autowired
    private PostRepo postRepo;

    @Autowired
    private TagService tagService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private UserRepo userRepo;

    List<Post> filteredPostByAuthor = new ArrayList<>();
    List<Post> filteredPostByTag = new ArrayList<>();

//    @GetMapping("/posts")
//    public String getAllPosts(@RequestParam(defaultValue = "0") int page,
//                              @RequestParam(defaultValue = "10") int size, Model model) {
//        List<Post> posts = postService.getAllPost();
//
//        List<String> allUniqueAuthors = postService.getAllUniqueAuthor();
//        model.addAttribute("authors",allUniqueAuthors);
//
//        List<String> allUniqueTags = tagService.getAllUniqueTags();
//        model.addAttribute("tags", allUniqueTags);
//        System.out.println("alll taggggggggggggggggggggggggs :  "+allUniqueTags);
//        System.out.println(posts);
//        model.addAttribute("posts", posts);
//        return "postDashboard";
//    }

    @GetMapping("/posts")
    public String getAllPosts(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size, Model model) {
        Page<Post> postPage = postService.getPaginatedPosts(page, size);
        filteredPostByAuthor = new ArrayList<>();
        filteredPostByTag = new ArrayList<>();

        model.addAttribute("posts", postPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", postPage.getTotalPages());
        model.addAttribute("totalItems", postPage.getTotalElements());
        model.addAttribute("pageSize", size);

        List<String> allUniqueAuthors = postService.getAllUniqueAuthor();
        model.addAttribute("authors", allUniqueAuthors);

        List<String> allUniqueTags = tagService.getAllUniqueTags();
        model.addAttribute("tags", allUniqueTags);

        System.out.println("All Unique Tags: " + allUniqueTags);
        System.out.println("Posts: " + postPage.getContent());

        return "postDashboard";
    }

//    @GetMapping("/posts/createForm")
//    public String showPostForm(Model model, Principal principal) {
//        model.addAttribute("authorName", principal.getName());
//        System.out.println("author name asdf : "+ principal.getName());
//        model.addAttribute("postDto", new PostDto());
//        return "newPost";
//    }

    @GetMapping("/posts/createForm")
    public String showPostForm(Model model, Authentication authentication) {
        PostDto postDto = new PostDto();
        Optional<User> userOptional = userRepo.findByEmail(authentication.getName());

        if (userOptional.isEmpty()) {
            throw new UsernameNotFoundException("Could not found user !!");
        }
        String authorName = userOptional.get().getName();

        System.out.println("Role asdjkfhkjasdfhf :"+userOptional.get().getRole());
        postDto.setAuthor(authorName);  // Set author as the logged-in username

        model.addAttribute("role", userOptional.get().getRole());
        model.addAttribute("postDto", postDto);
        return "newPost";
    }


    @PostMapping("/post/publish")
    public String publishPost(@ModelAttribute("postDto") PostDto postDto, Principal principal) {
        postDto.setCreatedAt(LocalDateTime.now());
        postService.save(postDto, principal);

        filteredPostByAuthor = new ArrayList<>();
        filteredPostByTag = new ArrayList<>();
        return "redirect:/posts";
    }

    @GetMapping("/post/viewPost")
    public String viewPostByID(@RequestParam("id") int id, Model model) {
        PostDto postDtoById = postService.getPostById(id);
        System.out.println("Post found: " + postDtoById);

        if (postDtoById == null) {
            System.out.println("Post not found");
            return "redirect:/posts";
        }
        model.addAttribute("comment", new Comment());
        model.addAttribute("post", postDtoById);
        return "viewPostByID";
    }

    //editPostByID(){}
    @GetMapping("/posts/edit")
    public String editPostByID(@RequestParam("id") int id, Model model){
        System.out.println("Ia ma getting ID   : "+id);
        PostDto postDto = postService.getPostById(id);
        postDto.setId(id);
        System.out.println("this is the object sadkjfkjsdfhajkn: " + postDto);
        model.addAttribute("post", postDto);
        return  "editByPostID";
    }

    //rePublishByID(){}
    @PostMapping("/post/republish")
    public String rePublishPostByID(@ModelAttribute("postDto") PostDto postDto){
        filteredPostByAuthor = new ArrayList<>();
        filteredPostByTag = new ArrayList<>();

        System.out.println("IIIIIIIIIIIIIIIII : "+postDto);

        System.out.println("postDto id     : "+postDto.getId());
        postService.updatePostByID(postDto, postDto.getId());
        return "redirect:/posts";
    }

    //deletePostByID
    @GetMapping("/posts/delete")
    public String deletePost(@RequestParam("id") int id, RedirectAttributes redirectAttributes){
        System.out.println("deleted post id :          "+ id);
        postService.isDeleted(id);
        redirectAttributes.addFlashAttribute("message", "Post deleted successfully");
        return  "redirect:/posts";
    }

    //sorting post
    @GetMapping("/posts/sort")
    public String sortingOrder(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size, @RequestParam String order,Model model){
        System.out.println("Is i m getting order  :   "+order);
        Page<Post> postPage = postService.getPaginatedPosts(page, size);

        filteredPostByAuthor = new ArrayList<>();
        filteredPostByTag = new ArrayList<>();

        //model.addAttribute("posts", postPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", postPage.getTotalPages());
        model.addAttribute("totalItems", postPage.getTotalElements());
        model.addAttribute("pageSize", size);

        List<Post> sortedPost = new ArrayList<>();
        if(order.equals("")){
            sortedPost = postService.getAllPost();
        }
        else if(order.equals("decrease")){
            sortedPost = postRepo.findAllByOrderByUpdatedAtDesc();
        }
        else if(order.equals("increase")){
            sortedPost = postRepo.findAllByOrderByUpdatedAtAsc();
        }
        List<String> allUniqueAuthors = postService.getAllUniqueAuthor();
        model.addAttribute("authors",allUniqueAuthors);

        List<String> allUniqueTags = tagService.getAllUniqueTags();
        model.addAttribute("tags", allUniqueTags);

        model.addAttribute("posts", sortedPost);
        return "postDashboard";
    }

    //searching
    @GetMapping("/posts/search")
    public String searchController(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size, @RequestParam("query") String query, Model model){
        if(query.isEmpty()){
            return "/posts";
        }
        filteredPostByAuthor = new ArrayList<>();
        filteredPostByTag = new ArrayList<>();

        List<Post> searchResultByAuthor= postService.searchByAuthor(query);
        System.out.println(searchResultByAuthor+"    "+searchResultByAuthor.size());
        List<Post> searchResultByTitle = postService.searchByTitle(query);
        System.out.println(searchResultByTitle+" "+searchResultByTitle.size());
        List<Post> searchResultByContent = postService.searchByContent(query);
        System.out.println(searchResultByContent+"    "+searchResultByContent.size());
        List<Post> searchResultByTag = tagService.searchByTag(query);
        System.out.println(searchResultByTag+"   "+searchResultByTag.size());

        Page<Post> postPage = postService.getPaginatedPosts(page, size);

        List<String> allUniqueAuthors = postService.getAllUniqueAuthor();
        model.addAttribute("authors", allUniqueAuthors);

        List<String> allUniqueTags = tagService.getAllUniqueTags();
        model.addAttribute("tags", allUniqueTags);

        //model.addAttribute("posts", postPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", postPage.getTotalPages());
        model.addAttribute("totalItems", postPage.getTotalElements());
        model.addAttribute("pageSize", size);
        if(!searchResultByAuthor.isEmpty()){
            model.addAttribute("posts", searchResultByAuthor);
        }
        if(!searchResultByTitle.isEmpty()){
            model.addAttribute("posts", searchResultByTitle);
        }
        if(!searchResultByContent.isEmpty()){
            model.addAttribute("posts", searchResultByContent);
        }
        if(!searchResultByTag.isEmpty()){
            model.addAttribute("posts", searchResultByTag);
        }
        if(searchResultByTitle.isEmpty() && searchResultByAuthor.isEmpty() && searchResultByContent.isEmpty() && searchResultByTag.isEmpty() && searchResultByTag.isEmpty()){
            return "redirect:/posts";
        }
        return "postDashboard";
    }

    //filtering
    @GetMapping("/posts/filter-author")
    public String filteredPostAuthor(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size, @RequestParam String [] author, Model model){
        if(filteredPostByTag.isEmpty()){
            filteredPostByAuthor = postService.searchByMultipleAuthor(author);
        }
        else{
            // send pagable object
            filteredPostByAuthor = postService.searchByAuthorInFilteredPostByTag(filteredPostByTag, author);
        }
        System.out.println("authooooooorrrrr    post    : "+filteredPostByAuthor);

        Page<Post> postPage = postService.getPaginatedPosts(page, size);

        //model.addAttribute("posts", postPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", postPage.getTotalPages());
        model.addAttribute("totalItems", postPage.getTotalElements());
        model.addAttribute("pageSize", size);

        model.addAttribute("posts", filteredPostByAuthor);
        List<String> allUniqueAuthors = postService.getAllUniqueAuthor();
        model.addAttribute("authors",allUniqueAuthors);

        List<String> allUniqueTags = tagService.getAllUniqueTags();
        model.addAttribute("tags", allUniqueTags);
        return "postDashboard";
    }

    @GetMapping("/posts/filter-tag")
    public String filteredPostTag(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(defaultValue = "") String[] tag,
                                  Model model){
        if(filteredPostByAuthor.isEmpty()){
            filteredPostByTag = tagService.searchByMultipleTag(tag);
        }
        else{
            filteredPostByTag = tagService.searchByTagInFilteredPostByAuthor(filteredPostByAuthor, tag);
        }

        Page<Post> postPage = postService.getPaginatedPosts(page, size);

        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", postPage.getTotalPages());
        model.addAttribute("totalItems", postPage.getTotalElements());
        model.addAttribute("pageSize", size);

        System.out.println("fillllltered     tagsss : " + filteredPostByTag);
        model.addAttribute("posts", filteredPostByTag);

        List<String> allUniqueAuthors = postService.getAllUniqueAuthor();
        model.addAttribute("authors", allUniqueAuthors);

        List<String> allUniqueTags = tagService.getAllUniqueTags();
        model.addAttribute("tags", allUniqueTags);
        return "postDashboard";
    }

    //    add comments on post by id
    @PostMapping("/posts/{id}/comments/add")
    public String addComment(@PathVariable("id") int postId,
                             @RequestParam String content,
                             @RequestParam String name, Model model) {
        filteredPostByAuthor = new ArrayList<>();
        filteredPostByTag = new ArrayList<>();

        commentService.save(postId,content,name);
        List<Comment> comments = postService.getComment(postId);
        System.out.print("comments   list   : " + comments);
        //model.addAttribute("comments", comments);
        PostDto post = postService.getPostById(postId);
        List<Comment> com = post.getComments();
        System.out.println(com);
        model.addAttribute("post", post);
        return "viewPostByID";
    }

    @GetMapping("/posts/comments/delete/{id}")
    public String deleteComment(@PathVariable("id") int commentId, Model model) {
        System.out.println("Deleting comment with ID: " + commentId);
        Comment com = commentRepo.findById(commentId);
        filteredPostByAuthor = new ArrayList<>();
        filteredPostByTag = new ArrayList<>();
        if (com == null) {
            System.out.println("Comment not found with ID: " + commentId);
            return "redirect:/posts";
        }

        Post postCom = com.getPost();
        if (postCom != null) {
            PostDto postdto = postService.getPostById(postCom.getId());
            System.out.println("Post ID: " + postCom.getId());

            deleteCommentWithReplies(com);
            System.out.println("Deleted comment with ID: " + commentId);
            model.addAttribute("post", postdto);
        }

        return "viewPostByID";
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

        if (!isAuthorizedForCommentEdit(authentication, comment)) {
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

        if (!isAuthorizedForCommentEdit(authentication, comment)) {
            return "redirect:/post/viewPost?id=" + comment.getPost().getId();
        }

        comment.setContent(content);
        commentRepo.save(comment);
        return "redirect:/post/viewPost?id=" + comment.getPost().getId();
    }

    private boolean isAuthorizedForCommentEdit(Authentication authentication, Comment comment) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        String postOwnerEmail = comment.getPost() != null && comment.getPost().getUser() != null
                ? comment.getPost().getUser().getEmail()
                : null;

        return isAdmin || (postOwnerEmail != null && postOwnerEmail.equals(authentication.getName()));
    }

    @PostMapping("/posts/comments/{commentId}/reply")
    public String saveReply(@PathVariable("commentId") int commentId,
                            @RequestParam("content") String content, Model model) {
        System.out.println("comment idddddddddddd : "+commentId);
        Comment parentComment = commentRepo.findById(commentId);

        System.out.println("parentCommmmmmmmment :"+parentComment);

        if (parentComment == null) {
            System.out.println("Comment not found with ID: " + commentId);
            return "redirect:/posts";
        }

        Post post = parentComment.getPost();
        if (post != null) {
            Comment reply = new Comment();
            reply.setName("Anonymous");
            reply.setContent(content);
            reply.setPost(post);
            reply.setParent(parentComment);
            parentComment.getReplies().add(commentRepo.save(reply));
            System.out.println("replyyyyyyyyyyyyyy saved  :"+ reply);
            commentRepo.save(parentComment);

            PostDto postDto = postService.getPostById(post.getId());
            model.addAttribute("post", postDto);

            return "viewPostByID";
        } else {
            return "redirect:/posts";
        }
    }

}
