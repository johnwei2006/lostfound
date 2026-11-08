package com.lostfound.service;

import com.lostfound.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

/**
 * 文件上传服务：本地磁盘存储 + 图片压缩
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    @Value("${lostfound.upload.dir}")
    private String uploadDir;

    @Value("${lostfound.upload.base-url}")
    private String baseUrl;

    @Value("${lostfound.upload.max-edge}")
    private int maxEdge;

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        String original = file.getOriginalFilename();
        String ext = original == null || !original.contains(".")
                ? "jpg" : original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持图片格式：jpg / png / webp / gif");
        }

        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path dir = Paths.get(uploadDir, month);
        Path target = dir.resolve(name);
        try {
            Files.createDirectories(dir);
            // 图片压缩（gif 逐帧压缩易失真，直接存储）
            if ("gif".equals(ext)) {
                file.transferTo(target.toAbsolutePath());
            } else {
                Thumbnails.of(file.getInputStream())
                        .size(maxEdge, maxEdge)
                        .outputQuality(0.82)
                        .toFile(target.toFile());
            }
        } catch (IOException e) {
            log.error("文件保存失败", e);
            throw new BusinessException("文件保存失败，请重试");
        }
        String url = baseUrl + "/" + month + "/" + name;
        log.info("文件上传成功: {}", url);
        return url;
    }
}
