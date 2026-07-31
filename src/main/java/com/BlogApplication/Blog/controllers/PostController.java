package com.BlogApplication.Blog.controllers;

import com.BlogApplication.Blog.exceptions.InvalidPostException;
import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.PostDetail;
import com.BlogApplication.Blog.payloads.PostDto;
import com.BlogApplication.Blog.payloads.PostListing;
import com.BlogApplication.Blog.util.PostAuthorization;
import com.BlogApplication.Blog.repositories.CommentRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.CommentService;
import com.BlogApplication.Blog.services.PostPdfService;
import com.BlogApplication.Blog.services.PostService;
import com.BlogApplication.Blog.services.PostViewService;
import com.BlogApplication.Blog.services.TagService;
import com.BlogApplication.Blog.services.VisitorIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
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
    private PostPdfService postPdfService;

    @GetMapping("/home")
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
    public String publishPost(@ModelAttribute("postDto") PostDto postDto, Principal principal, Model model) {
        postDto.setCreatedAt(LocalDateTime.now());
        try {
            postService.save(postDto, principal);
        } catch (InvalidPostException ex) {
            userRepo.findByEmail(principal.getName()).ifPresent(u -> model.addAttribute("role", u.getRole()));
            model.addAttribute("postDto", postDto);
            model.addAttribute("error", ex.getMessage());
            return "newPost";
        }
        return "redirect:/home";
    }

    @GetMapping("/post/viewPost")
    public String viewPostByID(@RequestParam("id") int id, Model model, Authentication authentication,
                               HttpServletRequest request, HttpServletResponse response) {
        PostDetail detail = recordViewAndGetDetail(id, authentication, request, response);
        if (detail == null) {
            return "redirect:/home";
        }

        model.addAttribute("comment", new Comment());
        model.addAttribute("post", detail.getPost());
        model.addAttribute("postReaction", detail.getPostReaction());
        model.addAttribute("commentReactions", detail.getCommentReactions());
        // currentUser is populated globally for every page by GlobalModelAttributes now.

        return "viewPostByID";
    }

    // Dashboard's modal post view (js/postModal.js) - same content as the full page above, just
    // rendered as a fragment for injection into an overlay instead of a page navigation. Reuses
    // recordViewAndGetDetail so a view counts the same way regardless of which UI path was used.
    @GetMapping("/post/{id}/modal")
    public String postModal(@PathVariable int id, Model model, Authentication authentication,
                            HttpServletRequest request, HttpServletResponse response) {
        PostDetail detail = recordViewAndGetDetail(id, authentication, request, response);
        if (detail == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return "fragments/postModal :: notFound";
        }

        model.addAttribute("comment", new Comment());
        model.addAttribute("post", detail.getPost());
        model.addAttribute("postReaction", detail.getPostReaction());
        model.addAttribute("commentReactions", detail.getCommentReactions());

        return "fragments/postModal :: postModal";
    }

    // Anonymous visitors count too (both the full page and the modal are publicly viewable
    // without login), tracked via a long-lived cookie rather than an account - see
    // VisitorIdentityService. Reaction counts are public the same way; only reacting itself
    // requires login. userEmail is null for a logged-out viewer, which both reaction services
    // already treat as "no reaction of mine" rather than an error.
    private PostDetail recordViewAndGetDetail(int id, Authentication authentication,
                                              HttpServletRequest request, HttpServletResponse response) {
        String viewerToken = visitorIdentityService.resolveViewerToken(authentication, request, response);
        postViewService.recordView(id, viewerToken);

        boolean isLoggedIn = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);
        String userEmail = isLoggedIn ? authentication.getName() : null;

        return postService.getPostDetail(id, userEmail);
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
            return "redirect:/home";
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
    public String rePublishPostByID(@ModelAttribute("postDto") PostDto postDto, Authentication authentication, Model model){
        // Re-check ownership against the post as it exists in the DB right now - never trust the
        // submitted form for who owns the post, since that's exactly what an attacker controls.
        PostDto existing = postService.getPostById(postDto.getId());
        if (existing == null) {
            return "redirect:/home";
        }
        if (!PostAuthorization.isOwnerOrAdmin(authentication, existing.getUser())) {
            return "redirect:/post/viewPost?id=" + postDto.getId();
        }
        try {
            postService.updatePostByID(postDto, postDto.getId());
        } catch (InvalidPostException ex) {
            model.addAttribute("post", postDto);
            model.addAttribute("error", ex.getMessage());
            return "editByPostID";
        }
        return "redirect:/home";
    }

    //deletePostByID
    @PostMapping("/posts/delete")
    public String deletePost(@RequestParam("id") int id, RedirectAttributes redirectAttributes, Authentication authentication, HttpServletRequest request){
        PostDto existing = postService.getPostById(id);
        if (existing == null) {
            return "redirect:/home";
        }
        if (!PostAuthorization.isOwnerOrAdmin(authentication, existing.getUser())) {
            return "redirect:/post/viewPost?id=" + id;
        }
        postService.isDeleted(id);
        redirectAttributes.addFlashAttribute("message", "Post deleted successfully");
        return "redirect:" + profileRefererOrHome(request);
    }

    // The post itself is gone after a delete, so there's no page left to show it on - but a post
    // opened from a user's profile should still land back on that profile afterward rather than
    // always bouncing to /home. Only ever trusts a same-host /profile/* path out of the Referer
    // header (never an arbitrary external URL), so this can't become an open redirect.
    private String profileRefererOrHome(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                java.net.URI uri = new java.net.URI(referer);
                if (uri.getHost() != null && uri.getHost().equalsIgnoreCase(request.getServerName())
                        && uri.getPath() != null && uri.getPath().startsWith("/profile/")) {
                    return uri.getPath();
                }
            } catch (Exception ignored) {
            }
        }
        return "/home";
    }

    //sorting post
    @GetMapping("/home/sort")
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
    @GetMapping("/home/search")
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
    @GetMapping("/home/filter-author")
    public String filteredPostAuthor(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(required = false) String query,
                                     @RequestParam(required = false) List<String> author,
                                     @RequestParam(required = false) List<String> tag,
                                     @RequestParam(required = false) String order,
                                     Model model){
        return listPosts(query, author, tag, order, page, size, model);
    }

    @GetMapping("/home/filter-tag")
    public String filteredPostTag(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  @RequestParam(required = false) String query,
                                  @RequestParam(required = false) List<String> author,
                                  @RequestParam(required = false) List<String> tag,
                                  @RequestParam(required = false) String order,
                                  Model model){
        return listPosts(query, author, tag, order, page, size, model);
    }

    // Kept for the initial full-page load (and any bookmarked/shared filtered URL still linking
    // to page=N directly) - the dashboard itself no longer shows page links, js/infiniteScroll.js
    // drives further pages through postsFragment() below instead.
    private String listPosts(String query, List<String> author, List<String> tag, String order,
                             int page, int size, Model model) {
        addListingToModel(query, author, tag, order, page, size, model);

        model.addAttribute("authors", postService.getAllUniqueAuthor());
        model.addAttribute("tags", tagService.getAllUniqueTags());

        // Dashboard sidebar/right-rail widgets - real data, not placeholders.
        model.addAttribute("trendingTags", tagService.getTrendingTags(5));
        model.addAttribute("topAuthors", postService.getTopAuthors(3));

        return "postDashboard";
    }

    // Infinite scroll's per-batch fetch (js/infiniteScroll.js) - same search/filter/sort as the
    // full page, but renders only the repeated post-row markup (fragments/postRows.html) so the
    // client appends it to the existing list instead of re-rendering the whole dashboard.
    @GetMapping("/home/fragment")
    public String postsFragment(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "10") int size,
                                @RequestParam(required = false) String query,
                                @RequestParam(required = false) List<String> author,
                                @RequestParam(required = false) List<String> tag,
                                @RequestParam(required = false) String order,
                                Model model) {
        addListingToModel(query, author, tag, order, page, size, model);
        return "fragments/postRows :: postRows";
    }

    private void addListingToModel(String query, List<String> author, List<String> tag, String order,
                                   int page, int size, Model model) {
        PostListing listing = postService.getListing(query, author, tag, order, page, size);

        model.addAttribute("posts", listing.getPosts());
        model.addAttribute("viewCounts", listing.getViewCounts());
        model.addAttribute("postReactions", listing.getReactions());
        model.addAttribute("commentCounts", listing.getCommentCounts());
        model.addAttribute("currentPage", listing.getCurrentPage());
        model.addAttribute("totalPages", listing.getTotalPages());
        model.addAttribute("totalItems", listing.getTotalItems());
        model.addAttribute("pageSize", listing.getPageSize());
        model.addAttribute("hasNextPage", listing.isHasNextPage());
        model.addAttribute("activeQuery", listing.getActiveQuery());
        model.addAttribute("activeAuthors", listing.getActiveAuthors());
        model.addAttribute("activeTags", listing.getActiveTags());
        model.addAttribute("activeOrder", listing.getActiveOrder());
    }

    //    add comments on post by id
    @PostMapping("/posts/{id}/comments/add")
    public String addComment(@PathVariable("id") int postId,
                             @RequestParam String content,
                             Authentication authentication) {
        // getPostById returns null for a missing OR soft-deleted post - reusing that contract
        // here blocks commenting on a "deleted" post the same way viewing it is already blocked.
        if (postService.getPostById(postId) == null) {
            return "redirect:/home";
        }
        commentService.save(postId, content, authentication.getName());
        return "redirect:/post/viewPost?id=" + postId;
    }

    @PostMapping("/posts/comments/delete/{id}")
    public String deleteComment(@PathVariable("id") int commentId, Authentication authentication) {
        Comment com = commentRepo.findById(commentId);
        if (com == null) {
            return "redirect:/home";
        }

        Post postCom = com.getPost();
        if (postCom == null || postCom.isDeleted()) {
            return "redirect:/home";
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
            return "redirect:/home";
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
            return "redirect:/home";
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
            return "redirect:/home";
        }

        commentService.saveReply(commentId, content, authentication.getName());

        return "redirect:/post/viewPost?id=" + parentComment.getPost().getId();
    }

}
