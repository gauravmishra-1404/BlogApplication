package com.BlogApplication.Blog.services;

import com.BlogApplication.Blog.payloads.PostDto;

public interface PostPdfService {
    byte[] renderToPdf(PostDto post);
}
