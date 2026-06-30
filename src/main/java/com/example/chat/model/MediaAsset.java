package com.example.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Media bytes stored in PostgreSQL (bytea). Messages reference assets via URL /api/media/{id}.
 */
@Entity
@Table(name = "media_assets")
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** IMAGE or FILE — denormalized for message typing without reading bytes. */
    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType;

    /** PostgreSQL bytea (not OID LOB) so a simple GET API can stream bytes. */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] data;

    @Column(nullable = false)
    private long createdAt;

    public MediaAsset() {
    }

    public MediaAsset(String fileName, String contentType, String messageType, byte[] data) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.messageType = messageType;
        this.data = data;
        this.sizeBytes = data != null ? data.length : 0;
        this.createdAt = System.currentTimeMillis();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
        this.sizeBytes = data != null ? data.length : 0;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /** Public API path clients use in img/src and links. */
    public String publicUrl() {
        return "/api/media/" + id;
    }
}
