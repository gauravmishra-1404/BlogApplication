package com.BlogApplication.Blog.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Timestamp display used by posts and comments: recent activity reads as relative
 * time ("2 hours ago"), older activity falls back to a structured absolute date -
 * the same pattern X and YouTube use for post/comment timestamps.
 */
public class TimeFormatter {

    private static final DateTimeFormatter ABSOLUTE = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a");

    private TimeFormatter() {
    }

    public static String relative(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }

        Duration duration = Duration.between(dateTime, LocalDateTime.now());
        long seconds = duration.getSeconds();

        if (seconds < 0) {
            return absolute(dateTime);
        }
        if (seconds < 60) {
            return "just now";
        }

        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute ago" : " minutes ago");
        }

        long hours = minutes / 60;
        if (hours < 24) {
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        }

        long days = hours / 24;
        if (days < 7) {
            return days + (days == 1 ? " day ago" : " days ago");
        }

        return absolute(dateTime);
    }

    public static String absolute(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(ABSOLUTE);
    }
}
