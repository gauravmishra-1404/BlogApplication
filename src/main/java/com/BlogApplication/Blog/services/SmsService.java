package com.BlogApplication.Blog.services;

public interface SmsService {
    void sendOtp(String mobileNumber, String code);
}
