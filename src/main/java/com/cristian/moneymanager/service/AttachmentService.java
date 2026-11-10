package com.cristian.moneymanager.service;

import com.cristian.moneymanager.dto.AttachmentDto;
import com.cristian.moneymanager.entity.AttachmentEntity;
import com.cristian.moneymanager.entity.ProfileEntity;
import com.cristian.moneymanager.repository.AttachmentRepository;
import com.cristian.moneymanager.repository.ExpenseRepository;
import com.cristian.moneymanager.repository.IncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Receipts/invoices attached to incomes and expenses (F15). Only their owner can list, download or delete them. */
@Service
@RequiredArgsConstructor
public class AttachmentService {

    static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    static final int MAX_PER_MOVEMENT = 5;
    static final long MAX_BYTES_PER_USER = 50L * 1024 * 1024;

    private final ProfileService profileService;
    private final AttachmentRepository attachmentRepository;
    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;

    public List<AttachmentDto> list(String type, Long movementId) {
        ProfileEntity profile = profileService.getCurrentProfile();
        checkMovement(type, movementId, profile.getId());
        return isIncome(type) ? attachmentRepository.listForIncome(movementId) : attachmentRepository.listForExpense(movementId);
    }

    @Transactional
    public AttachmentDto upload(String type, Long movementId, MultipartFile file) {
        ProfileEntity profile = profileService.getCurrentProfile();
        checkMovement(type, movementId, profile.getId());
        if (file == null || file.isEmpty()) {
            throw badRequest("Elige un archivo");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw badRequest("El archivo pasa de 2 MB");
        }
        long count = isIncome(type) ? attachmentRepository.countByIncomeId(movementId) : attachmentRepository.countByExpenseId(movementId);
        if (count >= MAX_PER_MOVEMENT) {
            throw badRequest("Cada movimiento puede tener como mucho " + MAX_PER_MOVEMENT + " archivos");
        }
        if (attachmentRepository.totalBytesForProfile(profile.getId()) + file.getSize() > MAX_BYTES_PER_USER) {
            throw badRequest("Has llegado al límite de 50 MB de archivos adjuntos");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw badRequest("No se ha podido leer el archivo");
        }
        // Trust the bytes, not the name or the browser's content type
        String contentType = detectType(data);
        if (contentType == null) {
            throw badRequest("Solo se pueden adjuntar imágenes (JPG, PNG, WEBP) o PDF");
        }

        AttachmentEntity saved = attachmentRepository.save(AttachmentEntity.builder()
                .profile(profile)
                .incomeId(isIncome(type) ? movementId : null)
                .expenseId(isIncome(type) ? null : movementId)
                .filename(cleanFilename(file.getOriginalFilename(), contentType))
                .contentType(contentType)
                .sizeBytes(data.length)
                .data(data)
                .build());
        return new AttachmentDto(saved.getId(), saved.getFilename(), saved.getContentType(), saved.getSizeBytes(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public AttachmentEntity download(Long id) {
        return find(id);
    }

    @Transactional
    public void delete(Long id) {
        attachmentRepository.delete(find(id));
    }

    private AttachmentEntity find(Long id) {
        Long profileId = profileService.getCurrentProfile().getId();
        return attachmentRepository.findByIdAndProfileId(id, profileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Archivo no encontrado"));
    }

    private void checkMovement(String type, Long movementId, Long profileId) {
        if (!"income".equalsIgnoreCase(type) && !"expense".equalsIgnoreCase(type)) {
            throw badRequest("Tipo no válido, debe ser 'income' o 'expense'");
        }
        if (movementId == null) {
            throw badRequest("Falta el movimiento");
        }
        boolean owned = isIncome(type)
                ? incomeRepository.findById(movementId).filter(i -> i.getProfile().getId().equals(profileId)).isPresent()
                : expenseRepository.findById(movementId).filter(e -> e.getProfile().getId().equals(profileId)).isPresent();
        if (!owned) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Movimiento no encontrado");
        }
    }

    private static boolean isIncome(String type) {
        return "income".equalsIgnoreCase(type);
    }

    /** Content type from the file's first bytes (magic numbers), or null when not allowed. */
    static String detectType(byte[] data) {
        if (data == null || data.length < 12) {
            return null;
        }
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G') {
            return "image/png";
        }
        if (ascii(data, 0, 4).equals("RIFF") && ascii(data, 8, 4).equals("WEBP")) {
            return "image/webp";
        }
        if (ascii(data, 0, 5).equals("%PDF-")) {
            return "application/pdf";
        }
        return null;
    }

    private static String ascii(byte[] data, int from, int length) {
        return new String(Arrays.copyOfRange(data, from, from + length), StandardCharsets.US_ASCII);
    }

    /** Keeps a safe display name (no paths or control characters) with an extension matching the real type. */
    static String cleanFilename(String original, String contentType) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|:*?]", "").trim();
        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".pdf";
        };
        String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        if (base.isBlank()) {
            base = "adjunto";
        }
        if (base.length() > 100) {
            base = base.substring(0, 100);
        }
        return base + extension;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
