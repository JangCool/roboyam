package com.community.anonymousauthor.controller;

import com.community.anonymousauthor.dto.request.AnonymousAuthorRevealRequest;
import com.community.anonymousauthor.dto.response.AnonymousAuthorRevealView;
import com.community.anonymousauthor.service.AnonymousAuthorRevealService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 익명 글·댓글 작성자 열람 API(봉인 열람 — anonymous-board-contract 정책 10) — 전용 권한 {@code anonymous-author.reveal} + 사유 + 메모 + 감사 + 다른 운영자 알림.
 * 응답은 {@code Cache-Control: no-store}(열린 신원이 실린다).
 */
@RestController
@RequestMapping("/api/admin/anonymous-authors")
public class AnonymousAuthorController {

    private final AnonymousAuthorRevealService service;

    public AnonymousAuthorController(AnonymousAuthorRevealService service) {
        this.service = service;
    }

    @RequirePermission(Permission.ANONYMOUS_AUTHOR_REVEAL)
    @PostMapping("/reveal")
    public ResponseEntity<ApiResponse<AnonymousAuthorRevealView>> reveal(@RequestBody(required = false) AnonymousAuthorRevealRequest body, AdminActor actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.reveal(actor, body)));
    }
}
