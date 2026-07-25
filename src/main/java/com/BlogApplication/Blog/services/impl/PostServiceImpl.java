package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.Tags;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.PostDto;
import com.BlogApplication.Blog.repositories.PostRepo;
import com.BlogApplication.Blog.repositories.PostViewRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.PostService;
import com.BlogApplication.Blog.services.TagService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PostServiceImpl implements PostService {
    @Autowired
    private PostRepo postRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PostViewRepo postViewRepo;

    @Autowired
    private TagService tagService;

    @Autowired
    private ModelMapper modelMapper;

    private Post dtoToPost(PostDto postDto){
        return this.modelMapper.map(postDto,Post.class);
    }

    @Override
    public void save(PostDto postDto, Principal principal) {
        Post post = this.dtoToPost(postDto);
        post.setTitle(post.getTitle());
        post.setAuthor(post.getAuthor());
        post.setUpdatedAt(LocalDateTime.now());
        post.setPublishedAt(LocalDateTime.now());
        Optional<User> userOptional = userRepo.findByEmail(principal.getName());

        if (userOptional.isEmpty()) {
            throw new UsernameNotFoundException("Could not found user !!");
        }
        User currentUser = userOptional.get();
        post.setUser(currentUser);

        // Excerpt Generation
        StringBuffer excerptString = new StringBuffer();
        String[] excerptContent = post.getContent().split(" ");
        for (int i = 0; i < 15 && i < excerptContent.length; i++) {
            excerptString.append(excerptContent[i]);
            excerptString.append(" ");
        }
        excerptString.append(".....");
        post.setExcerpt(excerptString.toString());

        post.setTagList(resolveTags(postDto.getTags()));
        currentUser.getPosts().add(post);
        postRepo.save(post);
    }

    public void updatePostByID(PostDto postDto, int id) {
        Post post = this.dtoToPost(postDto);
        post.setUpdatedAt(LocalDateTime.now());

        Post postByID = postRepo.findById(id).orElseThrow(() -> new RuntimeException("Post not found"));
        postByID.setUpdatedAt(post.getUpdatedAt());
        postByID.setContent(post.getContent());
        postByID.setTitle(post.getTitle());
        postByID.setAuthor(post.getAuthor());

        StringBuffer excerptString = new StringBuffer();
        String[] excerptContent = post.getContent().split(" ");
        for (int i = 0; i < 15 && i < excerptContent.length; i++) {
            excerptString.append(excerptContent[i]);
            excerptString.append(" ");
        }
        excerptString.append(".....");
        postByID.setExcerpt(excerptString.toString());

        postByID.setTagList(resolveTags(postDto.getTags()));
        postRepo.save(postByID);
    }

    private List<Tags> resolveTags(String tagsInput) {
        if (tagsInput == null || tagsInput.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(tagsInput.split(","))
                .map(tagName -> {
                    String upperTagName = tagName.trim().toUpperCase();
                    Optional<Tags> isTagPresent = tagService.findByName(upperTagName);
                    Tags tag;
                    if (isTagPresent.isEmpty()) {
                        tag = new Tags();
                        tag.setName(upperTagName);
                        tag.setCreated_at(LocalDateTime.now());
                        tag.setUpdated_at(LocalDateTime.now());
                        try {
                            synchronized (this) {
                                tagService.savePost(tag);
                            }
                        } catch (DataIntegrityViolationException e) {
                            tag = tagService.findByName(upperTagName)
                                    .orElseThrow(() -> new RuntimeException("Tag creation failed for: " + upperTagName));
                        }
                    } else {
                        tag = isTagPresent.get();
                    }
                    return tag;
                })
                .collect(Collectors.toList());
    }

    // Returns null (not a thrown exception) for both a genuinely missing id and a soft-deleted
    // one - PostController.viewPostByID checks for null and redirects to /posts, so a deleted
    // or nonexistent post lands there cleanly instead of a 500.
    @Override
    public PostDto getPostById(int id) {
        Optional<Post> postOptional = postRepo.findById(id);
        if (postOptional.isEmpty() || postOptional.get().isDeleted()) {
            return null;
        }
        Post postByID = postOptional.get();
        PostDto postDtoByID = new PostDto();
        postDtoByID.setAuthor(postByID.getAuthor());
        postDtoByID.setContent(postByID.getContent());
        postDtoByID.setUpdatedAt(postByID.getUpdatedAt());
        postDtoByID.setTitle(postByID.getTitle());
        postDtoByID.setId(postByID.getId());
        postDtoByID.setComments(postByID.getComments());
        postDtoByID.setUser(postByID.getUser());

        List<Tags> tagsList = postByID.getTagList();
        StringBuilder constructTagList = new StringBuilder();
        for(Tags tag : tagsList){
            constructTagList.append(tag.getName()).append(",");
        }
        postDtoByID.setTags(constructTagList.toString());
        postDtoByID.setViewCount(postViewRepo.countByPostId(id));
        return postDtoByID;
    }

    // "Delete" is a soft delete: the row (and its comments/tags) stay in the database, just
    // hidden from listing/search/direct view. A hard delete here used to crash - Post/Tags had
    // a bidirectional CascadeType.ALL that reached into every other post sharing a tag - and
    // even fixed, this table's FK graph (shared with another app's comment threads) makes
    // physical deletion risky enough that soft delete is the safer permanent choice.
    @Override
    public void isDeleted(int id) {
        Post post = postRepo.findById(id).orElseThrow(() -> new RuntimeException("Post not found"));
        post.setDeleted(true);
        postRepo.save(post);
    }

    @Override
    public List<String> getAllUniqueAuthor() {
        return this.postRepo.distinctAuthor();
    }

    @Override
    public Page<Post> searchPosts(String query, List<String> authors, List<String> tags, String order, int page, int size) {
        Specification<Post> spec = Specification.where((root, cq, cb) ->
                cb.or(cb.isNull(root.get("deleted")), cb.isFalse(root.get("deleted"))));

        if (query != null && !query.isBlank()) {
            String likePattern = "%" + query.trim().toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> {
                cq.distinct(true);
                Join<Post, Tags> tagJoin = root.join("tagList", JoinType.LEFT);
                return cb.or(
                        cb.like(cb.lower(root.get("title")), likePattern),
                        cb.like(cb.lower(root.get("author")), likePattern),
                        cb.like(cb.lower(root.get("content")), likePattern),
                        cb.like(cb.lower(tagJoin.get("name")), likePattern)
                );
            });
        }

        List<String> cleanAuthors = cleanValues(authors, String::toUpperCase);
        if (!cleanAuthors.isEmpty()) {
            spec = spec.and((root, cq, cb) -> cb.upper(root.get("author")).in(cleanAuthors));
        }

        List<String> cleanTags = cleanValues(tags, String::toUpperCase);
        if (!cleanTags.isEmpty()) {
            spec = spec.and((root, cq, cb) -> {
                cq.distinct(true);
                Join<Post, Tags> tagJoin = root.join("tagList", JoinType.INNER);
                return tagJoin.get("name").in(cleanTags);
            });
        }

        Sort sort = "increase".equals(order)
                ? Sort.by(Sort.Direction.ASC, "updatedAt")
                : Sort.by(Sort.Direction.DESC, "updatedAt");

        Pageable pageable = PageRequest.of(page, size, sort);
        return postRepo.findAll(spec, pageable);
    }

    private List<String> cleanValues(List<String> values, java.util.function.Function<String, String> transform) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(transform)
                .collect(Collectors.toList());
    }

    @Override
    public List<Comment> getComment(int postId) {
        Optional<Post> commentsPost = postRepo.findById(postId);
        if(commentsPost.isPresent()){
            return commentsPost.get().getComments();
        }
        return new ArrayList<>();
    }
}
