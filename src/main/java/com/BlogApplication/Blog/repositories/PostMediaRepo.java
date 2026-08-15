package com.BlogApplication.Blog.repositories;

import com.BlogApplication.Blog.models.PostMedia;
import org.springframework.data.jpa.repository.JpaRepository;

// Reads/writes mostly happen through Post.media itself (cascade-managed, see Post.java's own
// comment on why that's safe) rather than this repo directly - kept anyway, same "every entity
// gets a repo" convention every other model in this project already follows, and a natural home
// for a future batched query (e.g. a cover-thumbnail-per-post lookup for N posts at once,
// mirroring PostReactionRepo/PostViewRepo's own batched-by-post-ids pattern) if one's ever needed.
public interface PostMediaRepo extends JpaRepository<PostMedia, Long> {
}
