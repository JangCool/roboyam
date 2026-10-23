package com.community.media.controller;

import com.community.auth.dto.AuthUser;
import com.community.media.dto.response.ImageUploadResponse;
import com.community.media.service.PostImageUploadService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 글 본문 이미지 올리기 — multipart {@code file} 한 장 → {@code 201 {id, url, width, height}}. 로그인한 회원만({@code SecurityConfig} 의 {@code /api/**} 인증 —
 * 비회원 401), CSRF 헤더 필요. 칸이 빠진 요청도 서비스가 칸 오류로 거절하도록 {@code required = false} 로 받는다.
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final PostImageUploadService service;

    public ImageController(PostImageUploadService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ImageUploadResponse>> upload(@RequestParam(value = "file", required = false) MultipartFile file,
                                                                  Authentication authentication) {
        Long userId = authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.upload(userId, file)));
    }
}
