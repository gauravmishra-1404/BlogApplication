package com.BlogApplication.Blog.payloads;

public class UsernameAvailability {
    private boolean available;

    public UsernameAvailability() {
    }

    public UsernameAvailability(boolean available) {
        this.available = available;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
