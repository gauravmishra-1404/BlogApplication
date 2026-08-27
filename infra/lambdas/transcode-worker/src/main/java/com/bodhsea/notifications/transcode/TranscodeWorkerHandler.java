package com.bodhsea.notifications.transcode;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

// Triggered off the transcode-shorts SQS queue (infra/terraform/transcode.tf), which itself is
// fed by an S3 "ObjectCreated" event notification filtered to the shorts/ prefix - not a direct
// S3-to-Lambda trigger, so the event arrives as a raw JSON string in each SQS message body
// (S3EventNotification.parseJson() is the library's own official way to read that shape back
// out, same aws-lambda-java-events dependency the notification workers already use).
//
// One thing every other worker in this project didn't need: this is the first Lambda that
// actually reads/writes S3 object *bytes* itself (the notification workers only ever publish
// messages; S3MediaUploadService on the app side only ever presigns URLs, never touches bytes
// either) - hence the added software.amazon.awssdk:s3 dependency (see pom.xml).
//
// Writes its result straight to RDS via plain JDBC (same DATABASE_URL/USERNAME/PASSWORD env-var
// pattern as inapp-worker) rather than any callback/webhook - there's no inbound Lambda-to-app
// channel anywhere in this project, and inapp-worker's own "Lambda talks straight to Postgres"
// precedent already covers this need without inventing one.
public class TranscodeWorkerHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DB_URL = System.getenv("DATABASE_URL");
    private static final String DB_USERNAME = System.getenv("DATABASE_USERNAME");
    private static final String DB_PASSWORD = System.getenv("DATABASE_PASSWORD");
    private static final String MEDIA_BUCKET = System.getenv("MEDIA_BUCKET");
    private static final String MEDIA_CDN_DOMAIN = System.getenv("MEDIA_CDN_DOMAIN");

    // Same conventional path a Lambda Layer publishes its contents under - AWS extracts every
    // Lambda Layer to /opt at cold start, and this project's own IAM/Terraform comments already
    // assume that layout for the ffmpeg static binary (see transcode.tf's own comment on
    // var.ffmpeg_layer_arn).
    private static final String FFMPEG_BINARY = "/opt/bin/ffmpeg";

    private static final String UPDATE_SQL =
            "UPDATE shorts SET transcoded_video_url = ?, thumbnail_url = ?, processing_status = 'READY' WHERE video_url = ?";
    private static final String FAIL_SQL =
            "UPDATE shorts SET processing_status = 'FAILED' WHERE video_url = ?";

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();

        if (DB_URL == null || DB_URL.isBlank() || MEDIA_BUCKET == null || MEDIA_BUCKET.isBlank()) {
            context.getLogger().log("DATABASE_URL/MEDIA_BUCKET not configured - failing whole batch for retry");
            for (SQSEvent.SQSMessage message : event.getRecords()) {
                failures.add(SQSBatchResponse.BatchItemFailure.builder().withItemIdentifier(message.getMessageId()).build());
            }
            return SQSBatchResponse.builder().withBatchItemFailures(failures).build();
        }

        Properties dbProps = new Properties();
        dbProps.setProperty("user", DB_USERNAME);
        dbProps.setProperty("password", DB_PASSWORD);
        dbProps.setProperty("sslmode", "require");

        try (Connection connection = DriverManager.getConnection(DB_URL, dbProps);
             S3Client s3 = S3Client.create()) {
            for (SQSEvent.SQSMessage message : event.getRecords()) {
                try {
                    processMessage(connection, s3, message.getBody());
                } catch (Exception e) {
                    // A corrupt/unreadable input file is a data problem, not a delivery problem -
                    // same distinction the notification workers already draw between "bad
                    // recipient" (log, don't fail) and "connection down" (fail, let the DLQ
                    // retry). Here specifically: mark the row FAILED so it doesn't sit at PENDING
                    // forever, but don't add it to failures either - retrying the same corrupt
                    // file five times (maxReceiveCount, see transcode.tf) won't fix it.
                    context.getLogger().log("Transcode failed for message " + message.getMessageId() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            context.getLogger().log("Database/S3 client setup failed: " + e.getMessage());
            for (SQSEvent.SQSMessage message : event.getRecords()) {
                failures.add(SQSBatchResponse.BatchItemFailure.builder().withItemIdentifier(message.getMessageId()).build());
            }
        }

        return SQSBatchResponse.builder().withBatchItemFailures(failures).build();
    }

    // Plain Jackson tree-walking rather than binding into aws-lambda-java-events' own
    // S3EventNotification class - that class exists in this library version
    // (com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification) but doesn't
    // expose the static parseJson() helper some AWS docs/examples assume; walking the two fields
    // this Lambda actually needs (bucket name, object key) directly is simpler and doesn't depend
    // on that class's own Jackson-binding behavior matching the real S3 event JSON shape.
    private void processMessage(Connection connection, S3Client s3, String rawBody) throws Exception {
        JsonNode root = MAPPER.readTree(rawBody);
        JsonNode records = root.get("Records");
        if (records == null || !records.isArray()) {
            return; // not an S3 ObjectCreated notification (e.g. an SQS test event) - nothing to do
        }

        for (JsonNode record : records) {
            JsonNode s3Node = record.get("s3");
            if (s3Node == null) {
                continue;
            }
            String bucket = s3Node.path("bucket").path("name").asText(null);
            // S3 event keys are URL-encoded (e.g. spaces as "+") - decode before using as an
            // actual object key, or a key with any encoded character 404s on GetObject.
            String rawKey = s3Node.path("object").path("key").asText(null);
            if (bucket == null || rawKey == null) {
                continue;
            }
            String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
            transcodeOne(connection, s3, bucket, key);
        }
    }

    private void transcodeOne(Connection connection, S3Client s3, String bucket, String key) throws Exception {
        // key is "shorts/{ownerId}/{uuid}.{ext}" - see MediaUploadService.presignShortVideo's own
        // comment for this exact convention.
        String[] parts = key.split("/");
        if (parts.length != 3 || !"shorts".equals(parts[0])) {
            return; // not a Short upload - the bucket notification is already filtered to this
                     // prefix, but a defensive check costs nothing.
        }
        String ownerId = parts[1];
        String filename = parts[2];
        String extension = filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1) : "mp4";
        String uuid = UUID.randomUUID().toString();

        String rawUrl = "https://" + MEDIA_CDN_DOMAIN + "/" + key;

        Path input = Path.of("/tmp/input-" + uuid + "." + extension);
        Path output = Path.of("/tmp/output-" + uuid + ".mp4");
        Path thumb = Path.of("/tmp/thumb-" + uuid + ".jpg");

        try {
            s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(),
                    software.amazon.awssdk.core.sync.ResponseTransformer.toFile(input));

            // Normalize: H.264/AAC, width capped at 720 (height auto, kept even for yuv420p),
            // faststart so playback can begin before the whole file downloads - same reasoning
            // the app's own upload flow already documents for MP4 delivery.
            runFfmpeg(FFMPEG_BINARY, "-y", "-i", input.toString(),
                    "-vf", "scale='min(720,iw)':-2",
                    "-c:v", "libx264", "-preset", "fast", "-crf", "23",
                    "-c:a", "aac", "-b:a", "128k",
                    "-movflags", "+faststart",
                    output.toString());

            // One frame just under the half-second mark as the poster/thumbnail - early enough
            // to almost never land past a very short video's own end.
            runFfmpeg(FFMPEG_BINARY, "-y", "-i", input.toString(),
                    "-ss", "00:00:00.5", "-frames:v", "1",
                    thumb.toString());

            String transcodedKey = "shorts-transcoded/" + ownerId + "/" + uuid + ".mp4";
            String thumbnailKey = "shorts-thumbnails/" + ownerId + "/" + uuid + ".jpg";

            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(transcodedKey).contentType("video/mp4").build(),
                    RequestBody.fromFile(output));
            s3.putObject(PutObjectRequest.builder().bucket(bucket).key(thumbnailKey).contentType("image/jpeg").build(),
                    RequestBody.fromFile(thumb));

            String transcodedUrl = "https://" + MEDIA_CDN_DOMAIN + "/" + transcodedKey;
            String thumbnailUrl = "https://" + MEDIA_CDN_DOMAIN + "/" + thumbnailKey;

            try (PreparedStatement statement = connection.prepareStatement(UPDATE_SQL)) {
                statement.setString(1, transcodedUrl);
                statement.setString(2, thumbnailUrl);
                statement.setString(3, rawUrl);
                statement.executeUpdate();
            }
        } catch (IOException | InterruptedException | RuntimeException e) {
            // ffmpeg crashed on this specific file (corrupt upload, unsupported codec inside an
            // otherwise-allowed container, etc.) - a data problem, see handleRequest's own
            // comment. Mark the row FAILED so it's visible rather than silently stuck at PENDING
            // forever; the raw upload itself is untouched and still plays either way.
            try (PreparedStatement statement = connection.prepareStatement(FAIL_SQL)) {
                statement.setString(1, rawUrl);
                statement.executeUpdate();
            } catch (Exception ignored) {
                // best-effort - if even this fails, the row just stays PENDING, no worse than
                // before this catch block existed.
            }
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        } finally {
            deleteQuietly(input);
            deleteQuietly(output);
            deleteQuietly(thumb);
        }
    }

    private void runFfmpeg(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        // Drain stdout/stderr so the process can't block on a full pipe buffer for a long-running
        // encode - a real risk for anything beyond a trivially short clip.
        process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("ffmpeg exited with code " + exitCode + " for command: " + String.join(" ", command));
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // /tmp is wiped between cold starts anyway - a leftover file here just occupies
            // ephemeral storage until the next cold start, not a correctness problem.
        }
    }
}
