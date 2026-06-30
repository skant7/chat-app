package com.example.chat.service;

import com.example.chat.domain.MessageType;
import com.example.chat.model.MediaAsset;
import com.example.chat.repository.MediaAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Stores media as BLOBs in PostgreSQL.
 * Validation isolated in private helpers for a single responsibility boundary.
 */
@Service
public class MediaStorageService {

    private static final long MAX_BYTES = 10 * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf", "text/plain",
            "audio/mpeg", "audio/ogg", "audio/wav",
            "video/mp4", "video/webm");

    private static final Map<String, String> EXTENSION_TO_TYPE = Map.ofEntries(
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".png", "image/png"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".pdf", "application/pdf"),
            Map.entry(".txt", "text/plain"),
            Map.entry(".mp3", "audio/mpeg"),
            Map.entry(".ogg", "audio/ogg"),
            Map.entry(".wav", "audio/wav"),
            Map.entry(".mp4", "video/mp4"),
            Map.entry(".webm", "video/webm"));

    private final MediaAssetRepository repository;

    public MediaStorageService(MediaAssetRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public StoredMedia store(MultipartFile file) throws IOException {
        byte[] bytes = readValidatedBytes(file);
        String contentType = normalizeContentType(file.getContentType(), file.getOriginalFilename());
        requireAllowedType(contentType);
        String original = sanitizeFileName(file.getOriginalFilename());
        MessageType messageType = MessageType.forContentType(contentType);

        MediaAsset asset = repository.save(new MediaAsset(original, contentType, messageType.name(), bytes));
        return new StoredMedia(asset.getId(), asset.publicUrl(), contentType, original, messageType.name());
    }

    @Transactional(readOnly = true)
    public Optional<MediaAsset> findById(Long id) {
        return repository.findById(id);
    }

    private static byte[] readValidatedBytes(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File exceeds 10 MB limit");
        }
        byte[] bytes = file.getBytes();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("File is empty");
        }
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("File exceeds 10 MB limit");
        }
        return bytes;
    }

    private static void requireAllowedType(String contentType) {
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }
    }

    private static String normalizeContentType(String contentType, String fileName) {
        if (contentType != null && !contentType.isBlank() && !"application/octet-stream".equals(contentType)) {
            return contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        }
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        for (var entry : EXTENSION_TO_TYPE.entrySet()) {
            if (lower.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return contentType == null ? "application/octet-stream" : contentType;
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        String base = Paths.get(name).getFileName().toString();
        base = base.replaceAll("[\\\\/\\x00-\\x1f]", "_");
        if (base.length() > 200) {
            base = base.substring(base.length() - 200);
        }
        return base.isBlank() ? "file" : base;
    }

    public record StoredMedia(Long id, String url, String contentType, String fileName, String messageType) {
    }
}
