package com.community.media.controller;

import com.community.media.dto.response.FileUploadResponse;
import com.community.media.service.FileUploadService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 올리기 API — multipart {@code file} + {@code purpose}(지금은 {@code SITE_LOGO}). 올린 파일은 어디에 쓰겠다고 지정하기 전까지 쓰이지 않는다.
 * 입구 권한은 현재 용도(사이트 로고)에 맞춰 site.manage 이고, 용도별 권한은 서비스가 다시 확인한다. 칸이 빠진 요청도 서비스가 칸 오류로 거절하도록 {@code required = false} 로 받는다.
 */
@RestController
@RequestMapping("/api/admin/files")
public class FileController {

    private final FileUploadService service;

    public FileController(FileUploadService service) {
        this.service = service;
    }

    @RequirePermission(anyOf = {Permission.SITE_MANAGE})
    @PostMapping
    public ResponseEntity<ApiResponse<FileUploadResponse>> upload(@RequestParam(value = "file", required = false) MultipartFile file,
                                                                 @RequestParam(value = "purpose", required = false) String purpose, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.upload(actor, purpose, file)));
    }
}
