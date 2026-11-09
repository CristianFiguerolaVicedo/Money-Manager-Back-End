package com.cristian.moneymanager.email;

/** A ready-to-send email: subject plus HTML and plain-text bodies (clients that block HTML show the text). */
public record EmailContent(String subject, String html, String text) {
}
