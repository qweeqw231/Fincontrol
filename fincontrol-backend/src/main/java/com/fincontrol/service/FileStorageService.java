package com.fincontrol.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 截图本地文件存储。
 *
 * <p>Phase 1：落地到 {@code ./uploads/screenshots/{uuid}{ext}}（路径由 {@code fincontrol.upload.screenshot.path} 覆盖）。
 * 1b 阶段若需 HTTP 访问，需在 ScreenshotController 加 GET /api/screenshot/file/{fileId} 暴露静态文件，
 * 或迁移至 OSS/MinIO（Phase 5）。
 */
@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    @Value("${fincontrol.upload.screenshot.path:./uploads/screenshots/}")
    private String storagePath;

    /**
     * 保存 multipart 文件，返回 fileId 与磁盘绝对路径。
     *
     * @throws java.io.IOException 写盘失败
     */
    public StoredFile store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件为空");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        } else if (file.getContentType() != null) {
            // 用 mime 反推
            String ct = file.getContentType();
            if (ct.contains("jpeg") || ct.contains("jpg")) ext = ".jpg";
            else if (ct.contains("png")) ext = ".png";
            else if (ct.contains("webp")) ext = ".webp";
        }
        if (!ext.matches("\\.(jpg|jpeg|png|webp)")) {
            throw new IllegalArgumentException("仅支持 jpg/png/webp：当前 ext=" + ext);
        }

        String fileId = UUID.randomUUID().toString().replace("-", "");
        Path dir = Paths.get(storagePath);
        Files.createDirectories(dir);
        Path target = dir.resolve(fileId + ext);
        file.transferTo(target);
        log.info("Stored screenshot fileId={} -> {}", fileId, target.toAbsolutePath());

        return new StoredFile(fileId, target);
    }

    /**
     * 根据 fileId 推断磁盘路径。
     */
    public Path resolveByFileId(String fileId) {
        // 简化：不查 DB，磁盘上寻找匹配的扩展名
        for (String ext : new String[]{".png", ".jpg", ".jpeg", ".webp"}) {
            Path p = Paths.get(storagePath, fileId + ext);
            if (Files.exists(p)) return p;
        }
        return null;
    }

    public record StoredFile(String fileId, Path absolutePath) {
        public String fileUrl() {
            return absolutePath.toString();
        }
    }
}
