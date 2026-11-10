package com.community.home.controller;

import com.community.home.dto.response.GadgetIconUploadView;
import com.community.home.service.GadgetIconUploadService;
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
 * 가젯 제목 아이콘 직접 올리기(gadget-page-contract 「가젯 제목 아이콘」) — multipart {@code file}(SVG · PNG · WEBP, 200KB). 홈·가젯 페이지·게시판 레이아웃이 같이 쓴다. site.manage.
 * 올린 파일은 가젯 저장의 {@code style.iconUpload.fileId} 로 쓰여야 남는다.
 */
@RestController
@RequestMapping("/api/admin/home-layouts/icons")
public class GadgetIconController {

    private final GadgetIconUploadService service;

    public GadgetIconController(GadgetIconUploadService service) {
        this.service = service;
    }

    @RequirePermission(Permission.SITE_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<GadgetIconUploadView>> upload(@RequestParam(value = "file", required = false) MultipartFile file, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.upload(actor, file)));
    }
}
