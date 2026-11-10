package com.cristian.moneymanager.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentServiceTest {

    private static byte[] withHeader(int... header) {
        byte[] data = new byte[32];
        for (int i = 0; i < header.length; i++) {
            data[i] = (byte) header[i];
        }
        return data;
    }

    @Test
    void detectsTypeFromTheBytes() {
        assertThat(AttachmentService.detectType(withHeader(0xFF, 0xD8, 0xFF, 0xE0))).isEqualTo("image/jpeg");
        assertThat(AttachmentService.detectType(withHeader(0x89, 'P', 'N', 'G', 0x0D, 0x0A))).isEqualTo("image/png");
        assertThat(AttachmentService.detectType(withHeader('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'))).isEqualTo("image/webp");
        assertThat(AttachmentService.detectType("%PDF-1.7 and more text".getBytes(StandardCharsets.US_ASCII))).isEqualTo("application/pdf");
        assertThat(AttachmentService.detectType("<html><script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII))).isNull();
        assertThat(AttachmentService.detectType(new byte[3])).isNull();
    }

    @Test
    void cleansFilenames() {
        assertThat(AttachmentService.cleanFilename("C:\\fotos\\ticket super.JPG", "image/jpeg")).isEqualTo("ticket super.jpg");
        assertThat(AttachmentService.cleanFilename("../../etc/passwd", "application/pdf")).isEqualTo("passwd.pdf");
        assertThat(AttachmentService.cleanFilename("factura.exe", "application/pdf")).isEqualTo("factura.pdf");
        assertThat(AttachmentService.cleanFilename(null, "image/png")).isEqualTo("adjunto.png");
        assertThat(AttachmentService.cleanFilename("a\"b<c>.png", "image/png")).isEqualTo("abc.png");
    }
}
