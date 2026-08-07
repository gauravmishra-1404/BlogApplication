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
import java.time.Instant;
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
            // The producer side (SqsNotificationPublisher) sends Instant.now().toString() - an
            // unambiguous UTC instant (trailing "Z", e.g. "2026-08-07T13:35:17.605223929Z"),
            // not a LocalDateTime/OffsetDateTime string. This used to be parsed with
            // OffsetDateTime.parse(), which requires an explicit zone offset in the string and
            // fails outright on a bare LocalDateTime.toString() (no offset at all) - the actual
            // cause of every in-app notification insert failing until this was caught. Instant
            // is the right type for a timestamp crossing a service boundary in the first place:
            // it has no timezone ambiguity to get wrong on either side.
            Instant createdAt = notification.getCreatedAt() != null
                    ? Instant.parse(notification.getCreatedAt())
                    : Instant.now();
            statement.setTimestamp(7, Timestamp.from(createdAt));
            statement.executeUpdate();
        }
    }
}
