package com.example.chat.controller;

import com.example.chat.dto.media.MediaUploadResponse;
import com.example.chat.model.MediaAsset;
import com.example.chat.service.MediaStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@Tag(name = "Media", description = "Upload and download media stored as DB BLOBs")
public class MediaController {

    private final MediaStorageService mediaStorageService;

    public MediaController(MediaStorageService mediaStorageService) {
        this.mediaStorageService = mediaStorageService;
    }

    @Operation(summary = "Upload a media file (returns URL for chat messages)")
    @PostMapping(value = "/api/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MediaUploadResponse upload(@RequestParam("file") MultipartFile file) throws IOException {
        return MediaUploadResponse.from(mediaStorageService.store(file));
    }

    @Operation(summary = "Download media bytes by id")
    @GetMapping("/api/media/{id}")
    public ResponseEntity<byte[]> get(@PathVariable Long id) {
        return mediaStorageService.findById(id)
                .map(MediaController::toResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<byte[]> toResponse(MediaAsset asset) {
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(asset.getContentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(asset.getSizeBytes())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + sanitizeHeaderFilename(asset.getFileName()) + "\"")
                .body(asset.getData());
    }

    private static String sanitizeHeaderFilename(String name) {
        if (name == null) {
            return "file";
        }
        return name.replace("\"", "").replace("\r", "").replace("\n", "");
    }
}
