package com.BlogApplication.Blog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// The one and only place @EnableScheduling appears in the app. Every individual scheduled job
// (ScheduledPostPublisher today, more to come) is just a plain @Component with a @Scheduled
// method living in the scheduler package - this class doesn't need to know they exist, and they
// don't need to repeat this annotation. Keeps adding job #2, #3, ... a one-file change: drop a
// new @Component in scheduler/, give it its own scheduler.<job-name>.* property in
// application.properties (see ScheduledPostPublisher for the pattern), done.
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
