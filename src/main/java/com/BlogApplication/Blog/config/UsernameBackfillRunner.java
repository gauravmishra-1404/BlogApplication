package com.BlogApplication.Blog.config;

import com.BlogApplication.Blog.models.User;
import com.BlogApplication.Blog.repositories.UserRepo;
import com.BlogApplication.Blog.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

// GlobalModelAttributes.currentUser() only backfills a username for whoever is currently logged
// in and loading a page - it does nothing for OTHER accounts whose posts/comments merely show up
// on someone else's screen (e.g. a commenter who hasn't visited since this feature shipped, or
// whose remember-me cookie has already expired). Those names render fine but their profile link
// stays hidden until they happen to visit again. This runs once at every startup and backfills
// every account still missing one, so existing users get a working profile link immediately
// instead of waiting on their next visit. Idempotent and cheap once caught up -
// findByUsernameIsNull() returns nothing on subsequent runs.
@Component
public class UsernameBackfillRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UsernameBackfillRunner.class);

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private UserService userService;

    @Override
    public void run(String... args) {
        List<User> missing = userRepo.findByUsernameIsNull();
        if (missing.isEmpty()) {
            return;
        }
        log.info("Backfilling username for {} existing account(s)", missing.size());
        for (User user : missing) {
            userService.ensureUsername(user);
        }
    }
}
