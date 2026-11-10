package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.dto.AttachmentDto;
import com.cristian.moneymanager.entity.AttachmentEntity;
import com.cristian.moneymanager.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    /** ?type=income|expense&transactionId=… */
    @GetMapping
    public ResponseEntity<List<AttachmentDto>> list(@RequestParam String type, @RequestParam Long transactionId) {
        return ResponseEntity.ok(attachmentService.list(type, transactionId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentDto> upload(@RequestParam String type,
                                                @RequestParam Long transactionId,
                                                @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachmentService.upload(type, transactionId, file));
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        AttachmentEntity attachment = attachmentService.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(attachment.getFilename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(attachment.getData());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        attachmentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
