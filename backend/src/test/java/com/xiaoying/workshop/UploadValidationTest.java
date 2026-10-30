package com.xiaoying.workshop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;

class UploadValidationTest {
    @TempDir Path directory;

    @Test void rejectsTextDisguisedAsPng() {
        assertThatThrownBy(() -> new FileStorage(directory.toString()).save(
                new MockMultipartFile("file", "safe.png", "image/png", "not an image".getBytes())))
                .isInstanceOf(ApiException.class);
    }

    @Test void rejectsWrongContentTypeAndEmptyFile() {
        FileStorage storage = new FileStorage(directory.toString());
        assertThatThrownBy(() -> storage.save(new MockMultipartFile("file", "image.gif", "image/gif", new byte[]{1})))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> storage.save(new MockMultipartFile("file", "image.png", "image/png", new byte[0])))
                .isInstanceOf(ApiException.class);
    }

    @Test void savesRealPngUnderGeneratedNameAndCopiesResult() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        FileStorage storage = new FileStorage(directory.toString());
        FileStorage.StoredFile file = storage.save(new MockMultipartFile("file", "../../photo.png", "image/png", bytes.toByteArray()));
        assertThat(file.relativePath()).startsWith("uploads/").doesNotContain("..");
        assertThat(file.originalName()).isEqualTo("photo.png");
        String result = storage.createResult("task-1", file.relativePath());
        assertThat(result).startsWith("/media/results/");
        assertThat(directory.resolve(result.substring("/media/".length()))).hasBinaryContent(bytes.toByteArray());
    }

    @Test void rejectsOversizedUploadBeforeReadingItsBytes() {
        var upload = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        org.mockito.Mockito.when(upload.getSize()).thenReturn(FileStorage.MAX_BYTES + 1);
        assertThatThrownBy(() -> new FileStorage(directory.toString()).save(upload))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(413));
    }

    @Test void acceptsJpegAndRejectsMimeMismatchOrTruncatedPng() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpg", bytes);
        FileStorage storage = new FileStorage(directory.toString());
        assertThat(storage.save(new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes.toByteArray())).mimeType()).isEqualTo("image/jpeg");
        assertThatThrownBy(() -> storage.save(new MockMultipartFile("file", "photo.png", "image/png", bytes.toByteArray())))
                .isInstanceOf(ApiException.class);
        byte[] truncated = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        assertThatThrownBy(() -> storage.save(new MockMultipartFile("file", "broken.png", "image/png", truncated)))
                .isInstanceOf(ApiException.class);
    }
}
