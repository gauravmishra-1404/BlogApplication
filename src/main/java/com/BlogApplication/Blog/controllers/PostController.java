package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.PostDto;
import com.BlogApplication.Blog.util.PostAuthorization;
import com.BlogApplication.Blog.repositories.CommentRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.CommentReactionService;
import com.BlogApplication.Blog.services.CommentService;
import com.BlogApplication.Blog.services.PostPdfService;
import com.BlogApplication.Blog.services.PostReactionService;
import com.BlogApplication.Blog.services.PostService;
import com.BlogApplication.Blog.services.PostViewService;
import com.BlogApplication.Blog.services.TagService;
import com.BlogApplication.Blog.services.VisitorIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
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

    @Autowired
    private PostViewService postViewService;

    @Autowired
    private VisitorIdentityService visitorIdentityService;

    @Autowired
    private PostReactionService postReactionService;

    @Autowired
    private CommentReactionService commentReactionService;

    @Autowired
    private PostPdfService postPdfService;

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
    public String viewPostByID(@RequestParam("id") int id, Model model, Authentication authentication,
                               HttpServletRequest request, HttpServletResponse response) {
        PostDto postDtoById = postService.getPostById(id);

        if (postDtoById == null) {
            return "redirect:/posts";
        }

        // Anonymous visitors count too (this page is publicly viewable without login), tracked
        // via a long-lived cookie rather than an account - see VisitorIdentityService.
        String viewerToken = visitorIdentityService.resolveViewerToken(authentication, request, response);
        postViewService.recordView(id, viewerToken);
        postDtoById.setViewCount(postViewService.countViews(id));

        model.addAttribute("comment", new Comment());
        model.addAttribute("post", postDtoById);
        // currentUser is populated globally for every page by GlobalModelAttributes now.

        // Reaction counts are public (shown to everyone, like the view count); only reacting
        // itself requires login. userEmail is null for a logged-out viewer, which both reaction
        // services already treat as "no reaction of mine" rather than an error.
        boolean isLoggedIn = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
        String userEmail = isLoggedIn ? authentication.getName() : null;

        model.addAttribute("postReaction", postReactionService.getSummary(id, userEmail));

        List<Integer> commentIds = postDtoById.getComments() == null
                ? List.of()
                : postDtoById.getComments().stream().map(Comment::getId).toList();
        model.addAttribute("commentReactions", commentReactionService.getSummaries(commentIds, userEmail));

        return "viewPostByID";
    }

    // Same visibility rule as viewing the post (permitAll, missing/soft-deleted -> 404) since
    // downloading is just another way of reading a post someone can already see on-screen.
    @GetMapping("/post/download")
    public ResponseEntity<byte[]> downloadPost(@RequestParam("id") int id) {
        PostDto postDtoById = postService.getPostById(id);
        if (postDtoById == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] pdf = postPdfService.renderToPdf(postDtoById);
        String filename = (postDtoById.getTitle() != null ? postDtoById.getTitle() : "post")
                .replaceAll("[^a-zA-Z0-9 _-]", "")
                .trim()
                .replace(' ', '-') + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }

    //editPostByID(){}
    @GetMapping("/posts/edit")
    public String editPostByID(@RequestParam("id") int id, Model model, Authentication authentication){
        PostDto postDto = postService.getPostById(id);
        if (postDto == null) {
            return "redirect:/posts";
        }
        // Only the post's own author (or an ADMIN) may edit it - the UI already hides this link
        // for everyone else, but that's cosmetic; a direct request to this URL must be rejected
        // the same way isAuthorizedForComment already rejects a direct request to edit a comment.
        if (!PostAuthorization.isOwnerOrAdmin(authentication, postDto.getUser())) {
            return "redirect:/post/viewPost?id=" + id;
        }
        postDto.setId(id);
        model.addAttribute("post", postDto);
        return  "editByPostID";
    }

    //rePublishByID(){}
    @PostMapping("/post/republish")
    public String rePublishPostByID(@ModelAttribute("postDto") PostDto postDto, Authentication authentication){
        // Re-check ownership against the post as it exists in the DB right now - never trust the
        // submitted form for who owns the post, since that's exactly what an attacker controls.
        PostDto existing = postService.getPostById(postDto.getId());
        if (existing == null) {
            return "redirect:/posts";
        }
        if (!PostAuthorization.isOwnerOrAdmin(authentication, existing.getUser())) {
            return "redirect:/post/viewPost?id=" + postDto.getId();
        }
        postService.updatePostByID(postDto, postDto.getId());
        return "redirect:/posts";
    }

    //deletePostByID
    @PostMapping("/posts/delete")
    public String deletePost(@RequestParam("id") int id, RedirectAttributes redirectAttributes, Authentication authentication){
        PostDto existing = postService.getPostById(id);
        if (existing == null) {
            return "redirect:/posts";
        }
        if (!PostAuthorization.isOwnerOrAdmin(authentication, existing.getUser())) {
            return "redirect:/post/viewPost?id=" + id;
        }
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
        List<Integer> postIds = postPage.getContent().stream().map(Post::getId).toList();

        model.addAttribute("posts", postPage.getContent());
        model.addAttribute("viewCounts", postViewService.countViewsForPosts(postIds));
        // Just public counts here (no per-viewer "did I react" state, unlike the post page
        // itself) - the dashboard is a listing, not somewhere you react from.
        model.addAttribute("postReactions", postReactionService.getSummaries(postIds, null));
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
        // getPostById returns null for a missing OR soft-deleted post - reusing that contract
        // here blocks commenting on a "deleted" post the same way viewing it is already blocked.
        if (postService.getPostById(postId) == null) {
            return "redirect:/posts";
        }
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
        if (postCom == null || postCom.isDeleted()) {
            return "redirect:/posts";
        }

        if (!isAuthorizedForComment(authentication, com)) {
            return "redirect:/post/viewPost?id=" + postCom.getId();
        }

        softDeleteCommentWithReplies(com);

        return "redirect:/post/viewPost?id=" + postCom.getId();
    }

    // Soft delete, same reasoning as Post: this table is shared with another app, so physically
    // removing rows is risky. Recursively marks the whole subtree deleted rather than just this
    // comment, matching the previous hard-delete behavior of removing the entire reply chain
    // from view together.
    private void softDeleteCommentWithReplies(Comment comment) {
        if (comment.getReplies() != null) {
            for (Comment reply : comment.getReplies()) {
                softDeleteCommentWithReplies(reply);
            }
        }
        comment.setDeleted(true);
        commentRepo.save(comment);
    }

    @GetMapping("/posts/comments/edit")
    public String editCommentPage(@RequestParam("id") int commentId, Model model, Authentication authentication) {
        Comment comment = commentRepo.findById(commentId);
        if (comment == null || comment.getPost() == null || comment.getPost().isDeleted() || comment.isDeleted()) {
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
        if (comment == null || comment.getPost() == null || comment.getPost().isDeleted() || comment.isDeleted()) {
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

        // Blocks replying through a deleted post, and through an already-deleted comment
        // directly (same reasoning either way: the thing being replied to is supposed to be gone).
        if (parentComment == null || parentComment.getPost() == null
                || parentComment.getPost().isDeleted() || parentComment.isDeleted()) {
            return "redirect:/posts";
        }

        commentService.saveReply(commentId, content, authentication.getName());

        return "redirect:/post/viewPost?id=" + parentComment.getPost().getId();
    }

}
