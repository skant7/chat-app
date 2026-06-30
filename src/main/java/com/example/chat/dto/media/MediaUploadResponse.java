package com.example.chat.dto.media;

import com.example.chat.service.MediaStorageService.StoredMedia;

public record MediaUploadResponse(
        Long id,
        String url,
        String contentType,
        String fileName,
        String messageType) {

    public static MediaUploadResponse from(StoredMedia stored) {
        return new MediaUploadResponse(
                stored.id(),
                stored.url(),
                stored.contentType(),
                stored.fileName(),
                stored.messageType());
    }
}
