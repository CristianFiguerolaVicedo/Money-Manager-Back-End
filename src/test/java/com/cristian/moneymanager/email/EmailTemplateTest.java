package com.cristian.moneymanager.email;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailTemplateTest {

    @Test
    void buildsHtmlAndTextVersions() {
        EmailContent email = EmailTemplate.builder("Activa tu cuenta")
                .preheader("Confirma tu email")
                .greeting("Ana")
                .paragraph("Gracias por registrarte.")
                .button("Activar mi cuenta", "https://example.com/activate?token=abc")
                .note("El enlace caduca en 24 horas.")
                .build();

        assertThat(email.subject()).isEqualTo("Activa tu cuenta");
        assertThat(email.html()).contains("Ana", "Gracias por registrarte.", "https://example.com/activate?token=abc");
        assertThat(email.text()).contains("Ana", "Gracias por registrarte.", "https://example.com/activate?token=abc");
    }

    @Test
    void escapesUserProvidedText() {
        EmailContent email = EmailTemplate.builder("Hola")
                .greeting("<script>alert(1)</script>")
                .paragraph("a < b & c")
                .build();

        assertThat(email.html()).doesNotContain("<script>").contains("&lt;script&gt;");
    }
}
