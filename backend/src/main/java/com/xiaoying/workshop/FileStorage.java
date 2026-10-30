package com.xiaoying.workshop;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Iterator;
import java.util.UUID;

@Component
public class FileStorage {
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final long MAX_PIXELS = 24_000_000;
    private final Path root;
    public record StoredFile(String relativePath, String originalName, String mimeType, long sizeBytes) {}

    public FileStorage(@Value("${storage.directory}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root.resolve("uploads"));
            Files.createDirectories(root.resolve("results"));
        } catch (IOException e) { throw new IllegalStateException("Cannot initialize storage", e); }
    }
    public Path root() { return root; }

    public StoredFile save(MultipartFile upload) {
        if (upload.isEmpty()) throw new ApiException(400, "图片不能为空");
        if (upload.getSize() > MAX_BYTES) throw new ApiException(413, "单张图片不能超过 10 MB");
        String mime = upload.getContentType();
        if (!"image/png".equals(mime) && !"image/jpeg".equals(mime))
            throw new ApiException(400, "仅支持 PNG 和 JPEG 图片");
        try {
            byte[] bytes = upload.getBytes();
            if (bytes.length > MAX_BYTES) throw new ApiException(413, "单张图片不能超过 10 MB");
            boolean png = bytes.length >= 8 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                    && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a;
            boolean jpeg = bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff;
            if (!(png && "image/png".equals(mime)) && !(jpeg && "image/jpeg".equals(mime)))
                throw new ApiException(400, "图片内容与声明格式不一致");
            validateDecodedImage(bytes, png ? "png" : "JPEG");
            String path = "uploads/" + UUID.randomUUID() + (png ? ".png" : ".jpg");
            Files.write(resolve(path), bytes, StandardOpenOption.CREATE_NEW);
            String name = upload.getOriginalFilename() == null ? "image" : upload.getOriginalFilename().replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "");
            if (name.isBlank()) name = "image";
            if (name.length() > 255) name = name.substring(name.length() - 255);
            return new StoredFile(path, name, mime, bytes.length);
        } catch (IOException e) { throw new ApiException(400, "图片无法读取，请选择完整有效的 PNG 或 JPEG 文件"); }
    }

    private void validateDecodedImage(byte[] bytes, String expectedFormat) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ApiException(400, "图片内容无法解码");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!reader.getFormatName().equalsIgnoreCase(expectedFormat)) throw new ApiException(400, "图片格式不匹配");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS)
                    throw new ApiException(400, "图片像素不能超过 2400 万");
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw new ApiException(400, "图片内容无法解码");
                decoded.flush();
            } finally { reader.dispose(); }
        }
    }

    public void checkExists(String relativePath) {
        if (!Files.isRegularFile(resolve(relativePath))) throw new IllegalStateException("上传素材已丢失，请重新上传");
    }
    /** The demo's deterministic output is a copy of the first input, never an external generation service. */
    public String createResult(String taskId, String sourcePath) {
        String path = "results/" + taskId + (sourcePath.endsWith(".png") ? ".png" : ".jpg");
        try { Files.copy(resolve(sourcePath), resolve(path), StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException e) { throw new IllegalStateException("结果文件保存失败", e); }
        return "/media/" + path;
    }
    public void delete(String relativePath) {
        try { Files.deleteIfExists(resolve(relativePath)); }
        catch (IOException e) { throw new IllegalStateException("Cannot remove uncommitted upload", e); }
    }
    private Path resolve(String path) {
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("Invalid media path");
        return resolved;
    }
}
