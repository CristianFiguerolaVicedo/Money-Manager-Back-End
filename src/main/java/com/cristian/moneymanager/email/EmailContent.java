package com.cristian.moneymanager.email;

/**
 * A ready-to-send email: subject plus HTML and plain-text bodies (clients that block HTML show the text).
 * {@code unsubscribeUrl} is set on optional (scheduled) emails and also sent as the List-Unsubscribe header.
 */
public record EmailContent(String subject, String html, String text, String unsubscribeUrl) {

    public EmailContent(String subject, String html, String text) {
        this(subject, html, text, null);
    }
}
