package com.fleetride.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MimeGuesserTest {

    @Test
    void pdf() {
        assertEquals("application/pdf", MimeGuesser.guess("receipt.pdf"));
        assertEquals("application/pdf", MimeGuesser.guess("SIGNED.PDF"));
    }

    @Test
    void png() {
        assertEquals("image/png", MimeGuesser.guess("photo.png"));
        assertEquals("image/png", MimeGuesser.guess("PHOTO.PNG"));
    }

    @Test
    void jpegDefaultForOtherImages() {
        assertEquals("image/jpeg", MimeGuesser.guess("photo.jpg"));
        assertEquals("image/jpeg", MimeGuesser.guess("photo.jpeg"));
    }

    @Test
    void unknownDefaultsToJpeg() {
        // Intentional: the upload picker allowlist is PDF/JPG/JPEG/PNG only, so any
        // filename that reaches this helper and isn't one of those is still treated
        // as JPEG rather than rejected here — the service layer enforces the allowlist.
        assertEquals("image/jpeg", MimeGuesser.guess("something.bin"));
    }

    @Test
    void nullReturnsOctetStream() {
        assertEquals("application/octet-stream", MimeGuesser.guess(null));
    }
}
