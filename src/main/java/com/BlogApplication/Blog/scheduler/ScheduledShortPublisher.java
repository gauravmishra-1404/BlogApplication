package com.BlogApplication.Blog.scheduler;

import com.BlogApplication.Blog.repositories.ShortRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Mirrors ScheduledPostPublisher exactly, against Shorts instead of Posts - one more small,
// self-contained job in the already-modular scheduler package (see SchedulingConfig's own
// comment). Its own scheduler.short-publish.fixed-rate-ms property, independently tunable from
// scheduler.post-publish.fixed-rate-ms.
@Slf4j
@Component
public class ScheduledShortPublisher {

    @Autowired
    private ShortRepo shortRepo;

    @Scheduled(fixedRateString = "${scheduler.short-publish.fixed-rate-ms:60000}")
    public void publishDueShorts() {
        int updated = shortRepo.publishDueScheduledShorts();
        if (updated > 0) {
            log.info("Published {} scheduled short(s)", updated);
        }
    }
}
