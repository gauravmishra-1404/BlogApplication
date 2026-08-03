package com.BlogApplication.Blog.payloads;

// Response shape for the bookmark-toggle endpoint.
public class BookmarkSummary {
    private boolean bookmarked;

    public BookmarkSummary() {
    }

    public BookmarkSummary(boolean bookmarked) {
        this.bookmarked = bookmarked;
    }

    public boolean isBookmarked() {
        return bookmarked;
    }

    public void setBookmarked(boolean bookmarked) {
        this.bookmarked = bookmarked;
    }
}
