package com.cristian.moneymanager.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends emails through Brevo's HTTP API (https://api.brevo.com/v3/smtp/email).
 *
 * SMTP is not used on purpose: Render's free instances block outbound SMTP ports
 * (25/465/587), so connecting to smtp-relay.brevo.com:587 just times out there.
 */
@Slf4j
@Service
public class EmailService {

    private static final String BREVO_SEND_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final String apiKey;
    private final String senderEmail;
    private final String senderName;

    public EmailService(
            RestClient.Builder restClientBuilder,
            @Value("${brevo.api-key:}") String apiKey,
            @Value("${brevo.sender-email}") String senderEmail,
            @Value("${brevo.sender-name:Money Manager}") String senderName
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(20));

        this.restClient = restClientBuilder
                .baseUrl(BREVO_SEND_URL)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
    }

    public void sendEmail(String to, String subject, String body) {
        Map<String, Object> payload = basePayload(to, subject);
        payload.put("textContent", body);
        send(payload);
    }

    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        Map<String, Object> payload = basePayload(to, subject);
        payload.put("htmlContent", htmlBody);
        send(payload);
    }

    public void sendEmailWithAttachment(String to, String subject, String body, byte[] attachment, String filename) {
        Map<String, Object> payload = basePayload(to, subject);
        payload.put("textContent", body);
        payload.put("attachment", List.of(Map.of(
                "name", filename,
                "content", Base64.getEncoder().encodeToString(attachment)
        )));
        send(payload);
    }

    private Map<String, Object> basePayload(String to, String subject) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sender", Map.of("email", senderEmail, "name", senderName));
        payload.put("to", List.of(Map.of("email", to)));
        payload.put("subject", subject);
        return payload;
    }

    private void send(Map<String, Object> payload) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("BREVO_API_KEY is not configured, cannot send emails");
        }
        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("api-key", apiKey)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Brevo explains the problem in the body (invalid key, unverified sender, ...)
            log.error("Brevo rejected email '{}': {} {}", payload.get("subject"), e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Email provider rejected the message: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.error("Could not reach Brevo to send '{}': {}", payload.get("subject"), e.getMessage());
            throw new RuntimeException("Could not reach the email provider", e);
        }
    }
}
