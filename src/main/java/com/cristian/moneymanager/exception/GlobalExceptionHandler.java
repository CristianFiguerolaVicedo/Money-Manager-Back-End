package com.cristian.moneymanager.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Turns exceptions into JSON bodies of the shape {"message": "..."}, which is what the
 * front-end reads (error.response.data.message) to show its toasts.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException e) {
        String message = e.getReason() != null ? e.getReason() : e.getStatusCode().toString();
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", message));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage(), "code", e.getCode()));
    }

    @ExceptionHandler(CategoryInUseException.class)
    public ResponseEntity<Map<String, Object>> handleCategoryInUse(CategoryInUseException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", e.getMessage(), "transactionCount", e.getTransactionCount()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "La petición no tiene un formato válido"));
    }

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(TypeMismatchException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "Valor no válido para '" + e.getPropertyName() + "'"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            // Spring's own exceptions (404 unknown route, 405 wrong method, ...) keep their status
            String detail = errorResponse.getBody().getDetail();
            return ResponseEntity.status(errorResponse.getStatusCode())
                    .body(Map.of("message", detail != null ? detail : errorResponse.getStatusCode().toString()));
        }
        log.error("Unhandled error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "Algo ha ido mal. Inténtalo de nuevo más tarde."));
    }
}
