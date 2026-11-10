package com.cristian.moneymanager.dto;

import java.time.LocalDateTime;

/** Attachment metadata (the file itself is downloaded from GET /attachments/{id}). */
public record AttachmentDto(Long id, String filename, String contentType, Integer sizeBytes, LocalDateTime createdAt) {
}
