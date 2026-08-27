package com.BlogApplication.Blog.scheduler;

import com.BlogApplication.Blog.repositories.PostRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// The first resident of the scheduler package - the home for every scheduled job in this app
// going forward (see SchedulingConfig's own comment for the convention). Deliberately small and
// self-contained: no shared "job" interface/registry - that's a real abstraction to reach for
// once a second and third job reveal what they'd actually have in common, not before.
//
// Publishes any Post/Short whose scheduledAt has arrived - PostRepo.publishDueScheduledPosts()
// is a single atomic UPDATE (not select-then-save), so this stays correct even if the app ever
// runs more than one instance: two instances ticking at the same moment can't double-publish the
// same row, no distributed lock needed.
//
// @Slf4j (Lombok, already a real dependency here) generates the `log` field - no manual
// `LoggerFactory.getLogger(...)` boilerplate needed. First use of it in this codebase; a
// reasonable convention for every scheduled job that follows to pick up too.
@Slf4j
@Component
public class ScheduledPostPublisher {

    @Autowired
    private PostRepo postRepo;

    // Own property, not a shared/global one - the next scheduled job gets its own
    // scheduler.<job-name>.fixed-rate-ms entry too, so each job's cadence is independently
    // tunable without touching any other job's code or config.
    @Scheduled(fixedRateString = "${scheduler.post-publish.fixed-rate-ms:60000}")
    public void publishDuePosts() {
        int updated = postRepo.publishDueScheduledPosts();
        if (updated > 0) {
            log.info("Published {} scheduled post(s)", updated);
        }
    }
}
