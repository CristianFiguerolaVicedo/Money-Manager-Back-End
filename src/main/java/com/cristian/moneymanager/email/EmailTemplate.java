package com.cristian.moneymanager.email;

import org.springframework.web.util.HtmlUtils;

import java.time.Year;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds every Money Manager email from one layout so they all look the same.
 *
 * Email clients (Gmail, Outlook, phones) ignore most modern CSS, so the layout uses
 * nested tables, inline styles, a 600px max width and a "bulletproof" button.
 * Every email also gets a plain-text version.
 *
 * <pre>
 * EmailContent email = EmailTemplate.builder("Activa tu cuenta")
 *         .preheader("Confirma tu email para empezar")
 *         .greeting("Cristian")
 *         .paragraph("Gracias por registrarte...")
 *         .button("Activar mi cuenta", link)
 *         .note("El enlace caduca en 24 horas.")
 *         .build();
 * </pre>
 */
public final class EmailTemplate {

    private static final String BRAND = "#047857";       // emerald, same accent as the web
    private static final String BRAND_DARK = "#111715";  // graphite, like the web side panel
    private static final String TEXT = "#1f2937";
    private static final String MUTED = "#6b7280";
    private static final String BACKGROUND = "#f4f6f5";
    private static final String FONT = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif";

    private final String subject;
    private String preheader = "";
    private String greetingName;
    private final List<String> paragraphs = new ArrayList<>();
    private String rawHtml;
    private String rawText;
    private String buttonText;
    private String buttonUrl;
    private final List<String> notes = new ArrayList<>();
    private String unsubscribeUrl;

    private EmailTemplate(String subject) {
        this.subject = subject;
    }

    public static EmailTemplate builder(String subject) {
        return new EmailTemplate(subject);
    }

    /** Short summary shown next to the subject in the inbox list (hidden in the body). */
    public EmailTemplate preheader(String preheader) {
        this.preheader = preheader;
        return this;
    }

    /** "Hola, {name}:" — omitted when the name is blank. */
    public EmailTemplate greeting(String name) {
        this.greetingName = name;
        return this;
    }

    /** Plain text paragraph (escaped). */
    public EmailTemplate paragraph(String text) {
        this.paragraphs.add(text);
        return this;
    }

    /** Trusted HTML block (e.g. a table) plus its plain-text equivalent. Escape any user data yourself. */
    public EmailTemplate rawBlock(String html, String text) {
        this.rawHtml = html;
        this.rawText = text;
        return this;
    }

    public EmailTemplate button(String text, String url) {
        this.buttonText = text;
        this.buttonUrl = url;
        return this;
    }

    /** Small grey text under the button (expiry, "ignore this email", ...). */
    public EmailTemplate note(String text) {
        this.notes.add(text);
        return this;
    }

    /** Optional emails only: adds a "darse de baja" link to the footer (and the List-Unsubscribe header). */
    public EmailTemplate unsubscribeLink(String url) {
        this.unsubscribeUrl = url;
        return this;
    }

    public EmailContent build() {
        return new EmailContent(subject, html(), text(), unsubscribeUrl);
    }

    // ------------------------------------------------------------------ HTML

    private String html() {
        StringBuilder body = new StringBuilder();

        if (greetingName != null && !greetingName.isBlank()) {
            body.append(p("Hola, " + esc(greetingName.trim()) + ":"));
        }
        for (String paragraph : paragraphs) {
            body.append(p(esc(paragraph)));
        }
        if (rawHtml != null) {
            body.append("<div style=\"margin:0 0 16px 0;\">").append(rawHtml).append("</div>");
        }
        if (buttonText != null && buttonUrl != null) {
            String url = esc(buttonUrl);
            body.append("<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:28px 0;\"><tr>")
                .append("<td align=\"center\" bgcolor=\"").append(BRAND).append("\" style=\"border-radius:8px;\">")
                .append("<a href=\"").append(url).append("\" target=\"_blank\" style=\"display:inline-block;padding:14px 28px;")
                .append("font-family:").append(FONT).append(";font-size:16px;font-weight:bold;color:#ffffff;text-decoration:none;border-radius:8px;\">")
                .append(esc(buttonText)).append("</a></td></tr></table>")
                .append("<p style=\"margin:0 0 16px 0;font-family:").append(FONT).append(";font-size:13px;line-height:20px;color:").append(MUTED).append(";\">")
                .append("Si el botón no funciona, copia este enlace en tu navegador:<br>")
                .append("<a href=\"").append(url).append("\" style=\"color:").append(BRAND).append(";word-break:break-all;\">").append(url).append("</a></p>");
        }
        for (String note : notes) {
            body.append("<p style=\"margin:0 0 8px 0;font-family:").append(FONT).append(";font-size:13px;line-height:20px;color:")
                .append(MUTED).append(";\">").append(esc(note)).append("</p>");
        }
        body.append("<p style=\"margin:24px 0 0 0;font-family:").append(FONT).append(";font-size:15px;line-height:24px;color:").append(TEXT)
            .append(";\">Un saludo,<br>El equipo de Money Manager</p>");

        return "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<meta name=\"color-scheme\" content=\"light\"><title>" + esc(subject) + "</title></head>"
                + "<body style=\"margin:0;padding:0;background-color:" + BACKGROUND + ";\">"
                // preheader: visible in the inbox preview, hidden in the email
                + "<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;\">" + esc(preheader) + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" bgcolor=\"" + BACKGROUND + "\">"
                + "<tr><td align=\"center\" style=\"padding:24px 12px;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"max-width:600px;\">"
                // header
                + "<tr><td bgcolor=\"" + BRAND_DARK + "\" style=\"padding:22px 32px;border-radius:12px 12px 0 0;\">"
                + "<span style=\"font-family:" + FONT + ";font-size:20px;font-weight:bold;color:#ffffff;\">Money Manager</span>"
                + "</td></tr>"
                // card
                + "<tr><td bgcolor=\"#ffffff\" style=\"padding:32px;border-radius:0 0 12px 12px;\">"
                + "<h1 style=\"margin:0 0 20px 0;font-family:" + FONT + ";font-size:22px;line-height:30px;color:" + TEXT + ";\">" + esc(subject) + "</h1>"
                + body
                + "</td></tr>"
                // footer
                + "<tr><td align=\"center\" style=\"padding:20px 32px;font-family:" + FONT + ";font-size:12px;line-height:18px;color:" + MUTED + ";\">"
                + (unsubscribeUrl == null
                    ? "Has recibido este email porque tienes una cuenta en Money Manager."
                    : "Recibes este email porque lo activaste en tu perfil de Money Manager.<br>"
                        + "<a href=\"" + esc(unsubscribeUrl) + "\" style=\"color:" + MUTED + ";text-decoration:underline;\">No quiero recibir más estos emails</a>")
                + "<br>&copy; " + Year.now() + " Money Manager"
                + "</td></tr>"
                + "</table></td></tr></table></body></html>";
    }

    private static String p(String html) {
        return "<p style=\"margin:0 0 16px 0;font-family:" + FONT + ";font-size:15px;line-height:24px;color:" + TEXT + ";\">" + html + "</p>";
    }

    // ------------------------------------------------------------------ text

    private String text() {
        StringBuilder text = new StringBuilder();
        text.append(subject).append("\n\n");
        if (greetingName != null && !greetingName.isBlank()) {
            text.append("Hola, ").append(greetingName.trim()).append(":\n\n");
        }
        for (String paragraph : paragraphs) {
            text.append(paragraph).append("\n\n");
        }
        if (rawText != null) {
            text.append(rawText).append("\n\n");
        }
        if (buttonText != null && buttonUrl != null) {
            text.append(buttonText).append(": ").append(buttonUrl).append("\n\n");
        }
        for (String note : notes) {
            text.append(note).append("\n");
        }
        text.append("\nUn saludo,\nEl equipo de Money Manager\n");
        if (unsubscribeUrl != null) {
            text.append("\nPara dejar de recibir estos emails: ").append(unsubscribeUrl).append("\n");
        }
        return text.toString();
    }

    private static String esc(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value);
    }
}
