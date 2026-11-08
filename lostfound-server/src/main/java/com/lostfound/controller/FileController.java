package com.lostfound.controller;

import com.lostfound.auth.RequireLogin;
import com.lostfound.common.Result;
import com.lostfound.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传接口
 */
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @RequireLogin
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = fileService.upload(file);
        return Result.ok(Map.of("url", url));
    }
}
