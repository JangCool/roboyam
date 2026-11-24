package com.community.jjal.controller;

import com.community.jjal.service.AdminJjalImageService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 짤 원본(관리자 앱) — 글 서랍·신고 서랍이 본문 짤을 그린다. 권한 {@code post.read} 또는 {@code report.read}. 계약은 docs/features/post-admin-contract.md ·
 * report-admin-contract.md 「본문 로보티콘·짤 그림」, my-jjal-contract.md.
 */
@RestController
@RequestMapping("/api/admin/jjals")
public class AdminJjalController {

    private final AdminJjalImageService images;

    public AdminJjalController(AdminJjalImageService images) {
        this.images = images;
    }

    /** 짤 원본(내린 짤도 — 운영자 전용, {@code private, no-store}). 내려간 짤이면 감사 기록 JJAL_ORIGINAL_VIEW. 파일이 없으면 404 JJAL_NOT_FOUND */
    @RequirePermission(anyOf = {Permission.POST_READ, Permission.REPORT_READ})
    @GetMapping("/{jjalId}/image")
    public ResponseEntity<byte[]> image(@PathVariable String jjalId, AdminActor actor) {
        AdminJjalImageService.JjalImage image = images.image(actor, jjalId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff").body(image.data());
    }
}
