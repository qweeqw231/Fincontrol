package com.fincontrol.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 1a.7 补测：FileStorageService 业务测试（截图本地文件存储）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>A7-T21 store 写入文件 → 返回 fileId + 路径</li>
 *   <li>A7-T22 store 空文件 → IllegalArgumentException</li>
 *   <li>A7-T23 store 非法扩展名（.gif） → IllegalArgumentException</li>
 *   <li>A7-T24 store 从 contentType 推断扩展名（无原始文件名）</li>
 *   <li>A7-T25 resolveByFileId 找到文件 → 返 Path</li>
 *   <li>A7-T26 resolveByFileId 找不到 → 返 null</li>
 *   <li>A7-T27 StoredFile.fileUrl() → 返磁盘绝对路径</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileStorageTest {

    @TempDir Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService();
        // 注入 storagePath 到 @Value 字段
        ReflectionTestUtils.setField(service, "storagePath", tempDir.toString() + "/");
    }

    @Test
    @DisplayName("A7-T21: store(jpg) → 写盘 + 返 fileId(32 位无连字符) + Path")
    void store_jpg_writesFile() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "screenshot.jpg", "image/jpeg", "fake-jpg-content".getBytes()
        );

        FileStorageService.StoredFile result = service.store(file);

        assertThat(result.fileId()).hasSize(32);  // UUID without hyphens
        assertThat(result.absolutePath()).exists();
        assertThat(result.absolutePath().getFileName().toString()).endsWith(".jpg");
        // 磁盘上确实写了内容
        assertThat(Files.readString(result.absolutePath())).isEqualTo("fake-jpg-content");
    }

    @Test
    @DisplayName("A7-T22: store 空文件 → IllegalArgumentException")
    void store_emptyFile_throws() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]
        );

        assertThatThrownBy(() -> service.store(empty))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("为空");
    }

    @Test
    @DisplayName("A7-T23: store 非法扩展名(.gif) → IllegalArgumentException")
    void store_invalidExtension_throws() {
        MockMultipartFile gif = new MockMultipartFile(
                "file", "screenshot.gif", "image/gif", "fake".getBytes()
        );

        assertThatThrownBy(() -> service.store(gif))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持");
    }

    @Test
    @DisplayName("A7-T24: store 无原始文件名 → 从 contentType 推断扩展名(.png)")
    void store_noFilename_inferFromContentType() throws IOException {
        MockMultipartFile png = new MockMultipartFile(
                "file", null, "image/png", "fake-png".getBytes()
        );

        FileStorageService.StoredFile result = service.store(png);

        assertThat(result.absolutePath().getFileName().toString()).endsWith(".png");
    }

    @Test
    @DisplayName("A7-T25: resolveByFileId 找到存在的文件 → 返 Path")
    void resolveByFileId_exists_returnsPath() throws IOException {
        // 先存一个
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.png", "image/png", "data".getBytes()
        );
        FileStorageService.StoredFile stored = service.store(file);

        Path resolved = service.resolveByFileId(stored.fileId());

        assertThat(resolved).isNotNull();
        assertThat(resolved).exists();
        assertThat(resolved.getFileName().toString()).isEqualTo(stored.fileId() + ".png");
    }

    @Test
    @DisplayName("A7-T26: resolveByFileId 找不到 → 返 null")
    void resolveByFileId_notExists_returnsNull() {
        Path result = service.resolveByFileId("nonexistent-file-id-xxxxxxxxxxxxxxxxxxxxxxxx");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("A7-T27: StoredFile.fileUrl() 返磁盘绝对路径字符串")
    void storedFile_fileUrl_returnsAbsolutePathString() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", "x".getBytes()
        );

        FileStorageService.StoredFile stored = service.store(file);

        assertThat(stored.fileUrl()).isEqualTo(stored.absolutePath().toString());
        assertThat(stored.fileUrl()).startsWith(tempDir.toString());
    }

    @Test
    @DisplayName("A7-T28: store 大写 .JPG → 转小写")
    void store_uppercaseExtension_lowercased() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "TEST.JPG", "image/jpeg", "x".getBytes()
        );

        FileStorageService.StoredFile result = service.store(file);

        assertThat(result.absolutePath().getFileName().toString()).endsWith(".jpg");
    }
}