package com.cristian.moneymanager.controller;

import com.cristian.moneymanager.service.ScheduledEmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Starts the scheduled email jobs. Called by a scheduled GitHub Actions workflow (.github/workflows/scheduled-emails.yml):
 * Render's free instance sleeps, so an in-app @Scheduled timer would not fire, while an HTTP request wakes it up.
 */
@RestController
@RequiredArgsConstructor
public class JobsController {

    private final ScheduledEmailService scheduledEmailService;

    @Value("${app.jobs.secret:}")
    private String jobsSecret;

    @PostMapping("/internal/jobs/{job}")
    public ResponseEntity<ScheduledEmailService.JobResult> runJob(@PathVariable String job,
                                                                  @RequestHeader(value = "X-Jobs-Secret", required = false) String secret) {
        if (jobsSecret == null || jobsSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Los envíos programados no están configurados");
        }
        if (secret == null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), jobsSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No autorizado");
        }
        if (!ScheduledEmailService.JOBS.contains(job)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tarea desconocida");
        }
        return ResponseEntity.ok(scheduledEmailService.run(job));
    }
}
