package com.BlogApplication.Blog.services.impl;

import com.BlogApplication.Blog.services.SmsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// Dev/test stand-in for a real SMS provider - no such provider (Twilio, MSG91, etc.) is wired
// up yet, so this is the ONLY SmsService implementation that exists right now, active in every
// profile including production. Mobile-number verification is fully functional for local
// testing (the code is right here in the log), but won't actually reach a real phone until a
// real provider replaces this - same gap this project's own email flow had before SendGrid was
// added (ConsoleEmailService was once the only EmailService too).
@Service
public class ConsoleSmsService implements SmsService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleSmsService.class);

    @Override
    public void sendOtp(String mobileNumber, String code) {
        log.info("[DEV SMS STUB] OTP for {}: {} (no real SMS provider configured - this code is only visible here)", mobileNumber, code);
    }
}
