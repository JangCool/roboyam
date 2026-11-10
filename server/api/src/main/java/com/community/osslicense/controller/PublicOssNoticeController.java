package com.community.osslicense.controller;

import com.community.osslicense.dto.response.OssNoticePublicResponse;
import com.community.osslicense.service.PublicOssNoticeService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 오픈소스 라이선스 고지(docs/features/oss-licenses-contract.md) — 로그인 없이 부른다. 안내 문구 + 숨기지 않은 직접 추가 항목.
 * 자동 수집 목록은 정적 파일 {@code /oss-licenses.json} 이다(이 API 에 없다). 캐시 규칙은 {@code /api/site/info} 와 같다 — 서버 사본({@link PublicOssNoticeService}) +
 * {@code Cache-Control: no-cache} + {@code ETag}(같으면 304).
 */
@RestController
@RequestMapping("/api/site")
public class PublicOssNoticeController {

    private final PublicOssNoticeService service;

    public PublicOssNoticeController(PublicOssNoticeService service) {
        this.service = service;
    }

    @GetMapping("/oss-licenses")
    public ResponseEntity<ApiResponse<OssNoticePublicResponse>> get() {
        PublicOssNoticeService.Snapshot snapshot = service.snapshot();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .eTag(snapshot.etag())
                .body(ApiResponse.success(snapshot.response()));
    }
}
