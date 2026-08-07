package com.bodhsea.notifications.push;

// The one message shape published to all 3 queues (email/push/inapp) - each worker only reads
// the fields it actually needs. Deliberately duplicated in each Lambda module rather than
// shared via a published library - these are 3 small, independently deployed jars, and a
// shared-dependency jar would be more ceremony than 3 copies of one small class is worth at
// this scale. If it ever needs to change, change it in all 3 places (email/push/inapp-worker)
// AND in the Spring Boot producer (NotificationService) that builds this same JSON shape.
public class NotificationMessage {
    private long notificationId;
    private int recipientUserId;
    private String recipientEmail;
    private String recipientDeviceToken;
    private String type;
    private String actorName;
    private String title;
    private String body;
    private String targetUrl;
    private String createdAt;

    public long getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(long notificationId) {
        this.notificationId = notificationId;
    }

    public int getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(int recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getRecipientDeviceToken() {
        return recipientDeviceToken;
    }

    public void setRecipientDeviceToken(String recipientDeviceToken) {
        this.recipientDeviceToken = recipientDeviceToken;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
