package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.exceptions.InvalidPostException;
import com.BlogApplication.Blog.models.ShortVideo;
import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.payloads.ReactionSummary;
import com.BlogApplication.Blog.payloads.ShortDetail;
import com.BlogApplication.Blog.payloads.ShortDto;
import com.BlogApplication.Blog.payloads.ShortListing;
import com.BlogApplication.Blog.repositories.ShortRepo;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.ShortCommentService;
import com.BlogApplication.Blog.services.ShortReactionService;
import com.BlogApplication.Blog.services.ShortService;
import com.BlogApplication.Blog.services.ShortViewService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Mirrors PostServiceImpl's shape, scoped down for Shorts - no tags/media-gallery/repost
// handling, a Short always has exactly one required video instead.
@Service
public class ShortServiceImpl implements ShortService {

    @Autowired
    private ShortRepo shortRepo;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private ShortViewService shortViewService;

    @Autowired
    private ShortReactionService shortReactionService;

    @Autowired
    private ShortCommentService shortCommentService;

    @Autowired
    private ModelMapper modelMapper;

    private ShortVideo dtoToShort(ShortDto shortDto) {
        return this.modelMapper.map(shortDto, ShortVideo.class);
    }

    @Override
    public void save(ShortDto shortDto, Principal principal) {
        // Same authoritative-check idea as PostServiceImpl.save() - a direct POST bypassing the
        // compose modal's own validation must still be held to the real rule: a Short being
        // actually published (or scheduled) must have a video, since the video IS the Short.
        // A draft is exempt, same reasoning as a Post draft.
        if (shortDto.isPublished() && (shortDto.getVideoUrl() == null || shortDto.getVideoUrl().isBlank())) {
            throw new InvalidPostException("A video is required to publish a Short.");
        }

        ShortVideo shortVideo = this.dtoToShort(shortDto);
        shortVideo.setUpdatedAt(LocalDateTime.now());
        if (shortDto.isPublished()) {
            shortVideo.setPublished(true);
            shortVideo.setPublishedAt(LocalDateTime.now());
        } else {
            shortVideo.setPublished(false);
        }

        User currentUser = userRepo.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("Could not found user !!"));
        shortVideo.setUser(currentUser);

        shortRepo.save(shortVideo);
    }

    @Override
    public void updateShortByID(ShortDto shortDto, int id) {
        if (shortDto.isPublished() && (shortDto.getVideoUrl() == null || shortDto.getVideoUrl().isBlank())) {
            throw new InvalidPostException("A video is required to publish a Short.");
        }

        ShortVideo shortById = shortRepo.findById(id).orElseThrow(() -> new RuntimeException("Short not found"));
        shortById.setUpdatedAt(LocalDateTime.now());
        shortById.setCaption(shortDto.getCaption());
        shortById.setVideoUrl(shortDto.getVideoUrl());
        shortById.setScheduledAt(shortDto.getScheduledAt());

        // Same one-way-door publish rule as PostServiceImpl.updatePostByID.
        if (shortDto.isPublished() && !shortById.isPublished()) {
            shortById.setPublished(true);
            shortById.setPublishedAt(LocalDateTime.now());
        }

        shortRepo.save(shortById);
    }

    // Returns null (not a thrown exception) for both a genuinely missing id and a soft-deleted
    // one, same contract as PostService.getPostById.
    @Override
    public ShortDto getShortById(int id) {
        Optional<ShortVideo> shortOptional = shortRepo.findById(id);
        if (shortOptional.isEmpty() || shortOptional.get().isDeleted()) {
            return null;
        }
        ShortDto dto = toDto(shortOptional.get());
        dto.setViewCount(shortViewService.countViews(id));
        return dto;
    }

    @Override
    public void isDeleted(int id) {
        ShortVideo shortVideo = shortRepo.findById(id).orElseThrow(() -> new RuntimeException("Short not found"));
        shortVideo.setDeleted(true);
        shortRepo.save(shortVideo);
    }

    @Override
    public ShortDetail getShortDetail(int id, String userEmail) {
        ShortDto shortDtoById = getShortById(id);
        if (shortDtoById == null) {
            return null;
        }
        // Re-read after the caller's already-recorded view, same reasoning as
        // PostService.getPostDetail.
        shortDtoById.setViewCount(shortViewService.countViews(id));

        ReactionSummary reaction = shortReactionService.getSummary(id, userEmail);

        return ShortDetail.builder()
                .shortVideo(shortDtoById)
                .reaction(reaction)
                .commentReactions(java.util.Map.of())
                .build();
    }

    @Override
    public ShortListing getShortsListing(int page, int size) {
        Page<ShortVideo> shortPage = shortRepo.findAllVisible(PageRequest.of(page, size));
        return buildListing(shortPage.getContent(), page, shortPage.getTotalPages(), shortPage.getTotalElements(), size);
    }

    @Override
    public ShortListing getShortsListingStartingAt(int startId, int size) {
        ShortVideo target = shortRepo.findById(startId).orElse(null);
        if (target == null || target.isDeleted() || !target.isPublished()) {
            return getShortsListing(0, size);
        }

        Page<ShortVideo> firstPage = shortRepo.findAllVisible(PageRequest.of(0, size));
        // Target first, then the normal shared feed order with that id filtered out (it would
        // otherwise likely appear a second time near the top, since it just got fetched as part
        // of "the most recent" page) - trimmed back down to one page's worth either way.
        List<ShortVideo> merged = new ArrayList<>();
        merged.add(target);
        for (ShortVideo s : firstPage.getContent()) {
            if (s.getId() != startId) {
                merged.add(s);
            }
        }
        if (merged.size() > size) {
            merged = merged.subList(0, size);
        }

        return buildListing(merged, 0, firstPage.getTotalPages(), firstPage.getTotalElements(), size);
    }

    private ShortListing buildListing(List<ShortVideo> shorts, int page, int totalPages, long totalItems, int size) {
        List<ShortDto> items = shorts.stream().map(this::toDto).toList();
        List<Integer> shortIds = items.stream().map(ShortDto::getId).distinct().toList();

        return ShortListing.builder()
                .shorts(items)
                .viewCounts(shortViewService.countViewsForShorts(shortIds))
                .reactions(shortReactionService.getSummaries(shortIds, null))
                .commentCounts(shortCommentService.countCommentsForShorts(shortIds))
                .currentPage(page)
                .totalPages(totalPages)
                .totalItems(totalItems)
                .pageSize(size)
                .hasNextPage(page + 1 < totalPages)
                .build();
    }

    private ShortDto toDto(ShortVideo s) {
        return ShortDto.builder()
                .id(s.getId())
                .caption(s.getCaption())
                .videoUrl(s.getVideoUrl())
                .transcodedVideoUrl(s.getTranscodedVideoUrl())
                .thumbnailUrl(s.getThumbnailUrl())
                .processingStatus(s.getProcessingStatus())
                .user(s.getUser())
                .published(s.isPublished())
                .publishedAt(s.getPublishedAt())
                .scheduledAt(s.getScheduledAt())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
