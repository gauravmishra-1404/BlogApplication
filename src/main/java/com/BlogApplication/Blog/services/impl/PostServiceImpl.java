package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.exceptions.InvalidPostException;
import com.BlogApplication.Blog.models.Comment;
import com.BlogApplication.Blog.models.Post;
import com.BlogApplication.Blog.models.PostMedia;
import com.BlogApplication.Blog.models.Tags;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.FeedItem;
import com.BlogApplication.Blog.payloads.MediaAttachment;
import com.BlogApplication.Blog.payloads.PostDetail;
import com.BlogApplication.Blog.payloads.PostDto;
import com.BlogApplication.Blog.payloads.PostListing;
import com.BlogApplication.Blog.payloads.ReactionSummary;
import com.BlogApplication.Blog.repositories.AuthorPostCount;
import com.BlogApplication.Blog.repositories.PostRepo;
import com.BlogApplication.Blog.repositories.PostViewRepo;
import com.BlogApplication.Blog.repositories.RepostCount;
import com.BlogApplication.Blog.repositories.RepostRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.CommentReactionService;
import com.BlogApplication.Blog.services.CommentService;
import com.BlogApplication.Blog.services.PostReactionService;
import com.BlogApplication.Blog.services.PostService;
import com.BlogApplication.Blog.services.PostViewService;
import com.BlogApplication.Blog.services.TagService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private PostViewService postViewService;

    @Autowired
    private PostReactionService postReactionService;

    @Autowired
    private CommentReactionService commentReactionService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private TagService tagService;

    @Autowired
    private RepostRepo repostRepo;

    // Spring Boot's own auto-configured Jackson bean (JacksonAutoConfiguration) - reused here
    // rather than `new ObjectMapper()`, same instance the rest of the app's JSON (de)serializes
    // through, and it's genuinely thread-safe to share.
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ModelMapper modelMapper;

    private Post dtoToPost(PostDto postDto){
        return this.modelMapper.map(postDto,Post.class);
    }

    @Override
    public void save(PostDto postDto, Principal principal) {
        // Authoritative check - the "required" attributes on newPost.html/composeModal.html's
        // fields are UX, not enforcement (a direct POST to this endpoint bypasses them
        // entirely, and previously did: a missing content crashed with a NullPointerException
        // at the excerpt-generation line below, and a blank title or empty tags silently saved
        // a broken post). Images/video are intentionally not checked here - they're optional
        // per the actual feature, once PostMedia exists.
        //
        // A draft is deliberately exempt from all of this - the entire point of Save Draft is
        // to let someone stash unfinished work (no title yet, no tags yet) rather than forcing
        // the same "finish it or lose it" choice Publish makes. Only an actual Publish action
        // (postDto.getPublished() true) is held to the full requirement.
        if (postDto.getPublished()) {
            if (postDto.getTitle() == null || postDto.getTitle().isBlank()) {
                throw new InvalidPostException("Title is required.");
            }
            if (postDto.getContent() == null || postDto.getContent().isBlank()) {
                throw new InvalidPostException("Content is required.");
            }
            if (!hasAtLeastOneTag(postDto.getTags())) {
                throw new InvalidPostException("At least one tag is required.");
            }
        }

        Post post = this.dtoToPost(postDto);
        post.setTitle(post.getTitle());
        post.setAuthor(post.getAuthor());
        post.setUpdatedAt(LocalDateTime.now());
        if (postDto.getPublished()) {
            post.setPublished(true);
            post.setPublishedAt(LocalDateTime.now());
        } else {
            post.setPublished(false);
        }
        Optional<User> userOptional = userRepo.findByEmail(principal.getName());

        if (userOptional.isEmpty()) {
            throw new UsernameNotFoundException("Could not found user !!");
        }
        User currentUser = userOptional.get();
        post.setUser(currentUser);

        post.setExcerpt(buildExcerpt(post.getContent()));

        post.setTagList(resolveTags(postDto.getTags()));
        post.setMedia(resolveMedia(postDto.getMediaJson(), post));
        currentUser.getPosts().add(post);
        postRepo.save(post);
    }

    // A draft's content can genuinely be null/blank (Save Draft skips the "content required"
    // check above) - the old inline version of this crashed with a NullPointerException the
    // moment that became possible, since it called .split(" ") straight on post.getContent().
    private String buildExcerpt(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        StringBuilder excerptString = new StringBuilder();
        String[] excerptContent = content.split(" ");
        for (int i = 0; i < 15 && i < excerptContent.length; i++) {
            excerptString.append(excerptContent[i]);
            excerptString.append(" ");
        }
        excerptString.append(".....");
        return excerptString.toString();
    }

    public void updatePostByID(PostDto postDto, int id) {
        // Same authoritative check as save() - only enforced for an actual Publish action, not
        // a draft being saved again as a draft (see save()'s own comment for why).
        if (postDto.getPublished()) {
            if (postDto.getTitle() == null || postDto.getTitle().isBlank()) {
                throw new InvalidPostException("Title is required.");
            }
            if (postDto.getContent() == null || postDto.getContent().isBlank()) {
                throw new InvalidPostException("Content is required.");
            }
            if (!hasAtLeastOneTag(postDto.getTags())) {
                throw new InvalidPostException("At least one tag is required.");
            }
        }

        Post post = this.dtoToPost(postDto);
        post.setUpdatedAt(LocalDateTime.now());

        Post postByID = postRepo.findById(id).orElseThrow(() -> new RuntimeException("Post not found"));
        postByID.setUpdatedAt(post.getUpdatedAt());
        postByID.setContent(post.getContent());
        postByID.setTitle(post.getTitle());
        postByID.setAuthor(post.getAuthor());

        // Publish is a one-way door once it's actually happened - an already-published post
        // can never be flipped back to a draft through this path (only Delete removes a live
        // post from view), regardless of what's submitted. A still-unpublished draft can move
        // to published (stamping publishedAt for the first time) or stay a draft.
        if (postDto.getPublished() && !postByID.isPublished()) {
            postByID.setPublished(true);
            postByID.setPublishedAt(LocalDateTime.now());
        }

        postByID.setExcerpt(buildExcerpt(post.getContent()));

        postByID.setTagList(resolveTags(postDto.getTags()));
        // clear()+addAll() on the SAME managed collection instance, not
        // postByID.setMedia(newList) - Hibernate's orphanRemoval only tracks mutations to the
        // persistent collection object it already knows about; replacing the field with a
        // brand-new List reference doesn't reliably trigger the DELETE of whatever rows were
        // removed. This is what actually makes "remove this image, keep that one" work on edit.
        postByID.getMedia().clear();
        postByID.getMedia().addAll(resolveMedia(postDto.getMediaJson(), postByID));
        postRepo.save(postByID);
    }

    // Same comma-split/trim rule resolveTags itself uses below - a plain !isBlank() check on
    // the raw string would let "tags= , ," through as "non-empty" despite parsing to zero
    // real tags.
    private boolean hasAtLeastOneTag(String tagsInput) {
        if (tagsInput == null || tagsInput.isBlank()) {
            return false;
        }
        return Arrays.stream(tagsInput.split(",")).anyMatch(tag -> !tag.trim().isEmpty());
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

    // Parses PostDto.mediaJson (the compose form's hidden "media" field - a JSON array of
    // {url, type} pairs already uploaded to S3 via the presign flow, see composeModal.js) into
    // real PostMedia rows, in submission order. Deliberately tolerant of bad input the same way
    // resolveTags() is of a blank tags string - malformed JSON here can only come from a
    // direct/tampered POST (composeModal.js always emits well-formed output), and the right
    // response to that is "this post just has no media," not a 500.
    private List<PostMedia> resolveMedia(String mediaJson, Post post) {
        if (mediaJson == null || mediaJson.isBlank()) {
            return new ArrayList<>();
        }
        List<MediaAttachment> attachments;
        try {
            attachments = objectMapper.readValue(mediaJson, new com.fasterxml.jackson.core.type.TypeReference<List<MediaAttachment>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }

        List<PostMedia> media = new ArrayList<>();
        int position = 0;
        for (MediaAttachment attachment : attachments) {
            if (attachment.getUrl() == null || attachment.getUrl().isBlank()
                    || !(PostMedia.IMAGE.equals(attachment.getType()) || PostMedia.VIDEO.equals(attachment.getType()))) {
                continue;
            }
            media.add(PostMedia.builder()
                    .post(post)
                    .mediaUrl(attachment.getUrl())
                    .mediaType(attachment.getType())
                    .position(position++)
                    .createdAt(LocalDateTime.now())
                    .build());
        }
        return media;
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
        // isPublished()'s own null-safe getter (not the raw field) - a pre-draft-feature row
        // with a null column still correctly reads as published here, same as everywhere else.
        postDtoByID.setPublished(postByID.isPublished());
        postDtoByID.setPublishedAt(postByID.getPublishedAt());
        postDtoByID.setExcerpt(postByID.getExcerpt());

        List<Tags> tagsList = postByID.getTagList();
        StringBuilder constructTagList = new StringBuilder();
        for(Tags tag : tagsList){
            constructTagList.append(tag.getName()).append(",");
        }
        postDtoByID.setTags(constructTagList.toString());
        postDtoByID.setMediaList(postByID.getMedia());
        postDtoByID.setViewCount(postViewRepo.countByPostId(id));
        return postDtoByID;
    }

    @Override
    public PostDetail getPostDetail(int id, String userEmail) {
        PostDto postDtoById = getPostById(id);
        if (postDtoById == null) {
            return null;
        }
        // Re-read after the caller's already-recorded view (see PostService.getPostDetail's
        // javadoc) - getPostById's own count above was read before that, so it's stale by one.
        postDtoById.setViewCount(postViewService.countViews(id));

        ReactionSummary postReaction = postReactionService.getSummary(id, userEmail);

        List<Integer> commentIds = postDtoById.getComments() == null
                ? List.of()
                : postDtoById.getComments().stream().map(Comment::getId).toList();
        Map<Integer, ReactionSummary> commentReactions = commentReactionService.getSummaries(commentIds, userEmail);

        return PostDetail.builder()
                .post(postDtoById)
                .postReaction(postReaction)
                .commentReactions(commentReactions)
                .build();
    }

    // "Delete" is a soft delete: the row (and its comments/tags/media) stay in the database,
    // just hidden from listing/search/direct view. A hard delete here used to crash - Post/Tags
    // had a bidirectional CascadeType.ALL that reached into every other post sharing a tag - and
    // even fixed, this table's FK graph (shared with another app's comment threads) makes
    // physical deletion risky enough that soft delete is the safer permanent choice. This is
    // exactly why Post.media's own CascadeType.ALL/orphanRemoval is safe despite that history:
    // those only fire when the media LIST ITSELF changes (an item added/removed in Java), never
    // from flipping the deleted flag on an otherwise-untouched Post - this method never touches
    // post.getMedia() at all, so no PostMedia row (or its S3/CloudFront object) is ever deleted
    // by a post "deletion." Don't add a media.clear() here thinking it's cleanup - it isn't;
    // the whole point of soft delete is that nothing related gets touched.
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
    public List<AuthorPostCount> getTopAuthors(int limit) {
        return postRepo.topAuthorsByPostCount(PageRequest.of(0, limit));
    }

    @Override
    public Page<Post> searchPosts(String query, List<String> authors, List<String> tags, String order, int page, int size) {
        Specification<Post> spec = Specification.where((root, cq, cb) ->
                cb.or(cb.isNull(root.get("deleted")), cb.isFalse(root.get("deleted"))));

        // Drafts never appear in the main feed/search - same null-safe check as deleted (a row
        // predating the draft feature has isPublished == null, which reads as published).
        spec = spec.and((root, cq, cb) ->
                cb.or(cb.isNull(root.get("isPublished")), cb.isTrue(root.get("isPublished"))));

        if (query != null && !query.isBlank()) {
            String likePattern = "%" + query.trim().toLowerCase() 
            + "%";
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

    @Override
    public PostListing getListing(String query, List<String> authors, List<String> tags, String order, int page, int size) {
        // The main dashboard feed is deliberately NOT personalized by follow relationships at
        // all - the same global timeline renders for every viewer regardless of who they follow.
        // Reposts never interleave into it, on purpose: this feed has no notion of "whose
        // activity", so a repost showing up here would mean the reposting user sees their OWN
        // repost reflected back at themselves on their own /home visit - redundant with their own
        // profile's Reposts tab, which already exists exactly to show that. Reposts belong to a
        // follow relationship (see FollowingFeedController, which merges them in correctly - a
        // repost surfaces to the reposting user's OWN followers, never to the reposting user's
        // own view of this global feed).
        Page<Post> postPage = searchPosts(query, authors, tags, order, page, size);
        List<FeedItem> items = postPage.getContent().stream().map(FeedItem::of).toList();
        List<Integer> postIds = items.stream().map(item -> item.getPost().getId()).distinct().toList();

        return PostListing.builder()
                .posts(items)
                .viewCounts(postViewService.countViewsForPosts(postIds))
                // Just public counts here (no per-viewer "did I react" state, unlike the post
                // page itself) - the dashboard is a listing, not somewhere you react from.
                .reactions(postReactionService.getSummaries(postIds, null))
                .commentCounts(commentService.countCommentsForPosts(postIds))
                .repostCounts(repostRepo.countGroupedByPostIds(postIds).stream()
                        .collect(Collectors.toMap(RepostCount::getPostId, RepostCount::getCount)))
                .currentPage(page)
                .totalPages(postPage.getTotalPages())
                .totalItems(postPage.getTotalElements())
                .pageSize(size)
                .hasNextPage(page + 1 < postPage.getTotalPages())
                .activeQuery(query)
                .activeAuthors(authors == null ? List.of() : authors)
                .activeTags(tags == null ? List.of() : tags)
                .activeOrder(order)
                .build();
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
