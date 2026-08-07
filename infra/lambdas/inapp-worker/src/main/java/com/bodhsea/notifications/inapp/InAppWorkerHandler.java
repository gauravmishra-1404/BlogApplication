package com.bodhsea.notifications.inapp;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

// Consumes the in-app queue - "delivery" here just means the row exists in the SAME Postgres
// database the main Spring Boot app already reads from (notifications table, see the
// Notification JPA entity on the app side - column names are deliberately kept in sync by hand
// between the two, see that entity's own doc comment). The bell-icon UI reads this table
// directly; there's no separate "push it to the browser" step in this worker.
public class InAppWorkerHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DB_URL = System.getenv("DATABASE_URL");
    private static final String DB_USERNAME = System.getenv("DATABASE_USERNAME");
    private static final String DB_PASSWORD = System.getenv("DATABASE_PASSWORD");

    private static final String INSERT_SQL =
            "INSERT INTO notifications (recipient_user_id, type, actor_name, title, body, target_url, is_read, created_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, false, ?)";

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        if (DB_URL == null || DB_URL.isBlank()) {
            context.getLogger().log("DATABASE_URL is not configured - failing whole batch for retry");
            for (SQSEvent.SQSMessage message : event.getRecords()) {
                failures.add(SQSBatchResponse.BatchItemFailure.builder().withItemIdentifier(message.getMessageId()).build());
            }
            return SQSBatchResponse.builder().withBatchItemFailures(failures).build();
        }

        Properties props = new Properties();
        props.setProperty("user", DB_USERNAME);
        props.setProperty("password", DB_PASSWORD);
        // Render's managed Postgres requires TLS - without this, connecting from outside
        // Render's own network (which is exactly what this Lambda is doing) gets rejected.
        props.setProperty("sslmode", "require");

        try (Connection connection = DriverManager.getConnection(DB_URL, props)) {
            for (SQSEvent.SQSMessage message : event.getRecords()) {
                try {
                    insert(connection, message.getBody());
                } catch (Exception e) {
                    context.getLogger().log("In-app notification insert failed for message " + message.getMessageId() + ": " + e.getMessage());
                    failures.add(SQSBatchResponse.BatchItemFailure.builder()
                            .withItemIdentifier(message.getMessageId())
                            .build());
                }
            }
        } catch (Exception e) {
            // Couldn't even open a connection - every message in this batch failed together,
            // same reasoning push-worker's Firebase-init failure path uses.
            context.getLogger().log("Database connection failed: " + e.getMessage());
            for (SQSEvent.SQSMessage message : event.getRecords()) {
                failures.add(SQSBatchResponse.BatchItemFailure.builder().withItemIdentifier(message.getMessageId()).build());
            }
        }

        return SQSBatchResponse.builder().withBatchItemFailures(failures).build();
    }

    private void insert(Connection connection, String rawBody) throws Exception {
        NotificationMessage notification = MAPPER.readValue(rawBody, NotificationMessage.class);

        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setInt(1, notification.getRecipientUserId());
            statement.setString(2, notification.getType());
            statement.setString(3, notification.getActorName());
            statement.setString(4, notification.getTitle());
            statement.setString(5, notification.getBody());
            statement.setString(6, notification.getTargetUrl());
            OffsetDateTime createdAt = notification.getCreatedAt() != null
                    ? OffsetDateTime.parse(notification.getCreatedAt())
                    : OffsetDateTime.now();
            statement.setTimestamp(7, Timestamp.from(createdAt.toInstant()));
            statement.executeUpdate();
        }
    }
}
