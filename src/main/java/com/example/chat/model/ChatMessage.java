package com.example.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_user", nullable = false, length = 100)
    private String fromUser;

    /** Peer for DMs; null when this is a group message. */
    @Column(name = "to_user", length = 100)
    private String toUser;

    /** Group for group messages; null when this is a DM. */
    @Column(name = "group_id")
    private Long groupId;

    /** Caption for media, or the full body for text messages (empty when media-only). */
    @Column(nullable = false, length = 2000)
    private String text = "";

    /** TEXT, IMAGE, or FILE */
    @Column(name = "message_type", nullable = false, length = 20)
    private String messageType = "TEXT";

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Column(name = "media_content_type", length = 120)
    private String mediaContentType;

    @Column(name = "media_file_name", length = 255)
    private String mediaFileName;

    @Column(nullable = false)
    private long timestamp;

    /** SENT or DELIVERED (recipient client acknowledged). */
    @Column(nullable = false, length = 20)
    private String status = "SENT";

    /** Null until recipient ACKs delivery. */
    @Column(name = "delivered_at")
    private Long deliveredAt;

    public ChatMessage() {
    }

    /** Factory for new messages (timestamp = now). */
    public static ChatMessage create(
            String fromUser,
            String toUser,
            String text,
            String messageType,
            String mediaUrl,
            String mediaContentType,
            String mediaFileName) {
        ChatMessage m = new ChatMessage();
        m.fromUser = fromUser;
        m.toUser = toUser;
        m.text = text == null ? "" : text;
        m.messageType = messageType != null ? messageType : "TEXT";
        m.mediaUrl = mediaUrl;
        m.mediaContentType = mediaContentType;
        m.mediaFileName = mediaFileName;
        m.timestamp = System.currentTimeMillis();
        m.status = "SENT";
        m.deliveredAt = null;
        m.groupId = null;
        return m;
    }

    /** Factory for group messages. */
    public static ChatMessage createGroup(
            String fromUser,
            Long groupId,
            String text,
            String messageType,
            String mediaUrl,
            String mediaContentType,
            String mediaFileName) {
        ChatMessage m = create(fromUser, null, text, messageType, mediaUrl, mediaContentType, mediaFileName);
        m.groupId = groupId;
        return m;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFromUser() {
        return fromUser;
    }

    public void setFromUser(String fromUser) {
        this.fromUser = fromUser;
    }

    public String getToUser() {
        return toUser;
    }

    public void setToUser(String toUser) {
        this.toUser = toUser;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getMediaContentType() {
        return mediaContentType;
    }

    public void setMediaContentType(String mediaContentType) {
        this.mediaContentType = mediaContentType;
    }

    public String getMediaFileName() {
        return mediaFileName;
    }

    public void setMediaFileName(String mediaFileName) {
        this.mediaFileName = mediaFileName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Long deliveredAt) {
        this.deliveredAt = deliveredAt;
    }
}
