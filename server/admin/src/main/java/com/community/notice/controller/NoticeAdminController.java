package com.community.notice.controller;

import com.community.notice.dto.request.NoticeSaveRequest;
import com.community.notice.dto.response.NoticeContextView;
import com.community.notice.dto.response.NoticeCreatedView;
import com.community.notice.dto.response.NoticeDetailView;
import com.community.notice.dto.response.NoticeImageView;
import com.community.notice.dto.response.NoticeListView;
import com.community.notice.service.NoticeQueryService;
import com.community.notice.service.NoticeWriteService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 공지 관리 API(관리자 앱) — 공지 게시판(slug {@code notice})의 글. 읽기 {@code post.read}, 쓰기 · 고치기 · 고정 · 해제 · 삭제 · 본문 이미지 {@code post.manage}, 쓰기는 CSRF.
 * 본문이 있는 응답은 모두 {@code Cache-Control: no-store}. 규칙은 {@link NoticeQueryService} · {@link NoticeWriteService}, 계약은 docs/features/notice-admin-contract.md.
 */
@RestController
@RequestMapping("/api/admin/notices")
public class NoticeAdminController {

    private final NoticeQueryService queries;
    private final NoticeWriteService writes;

    public NoticeAdminController(NoticeQueryService queries, NoticeWriteService writes) {
        this.queries = queries;
        this.writes = writes;
    }

    /** 공지 게시판(없으면 null) · 내 운영자 회원 계정 · 공지 띠 요약 */
    @RequirePermission(Permission.POST_READ)
    @GetMapping("/context")
    public ResponseEntity<ApiResponse<NoticeContextView>> context(AdminActor actor) {
        return noStore(HttpStatus.OK, queries.context(actor));
    }

    /** 목록 — {@code q}(제목) · {@code state}(ALL · PUBLIC · HIDDEN · DELETED) · {@code prefix}(말머리 ID) · {@code page}(0부터) · {@code size}(기본 20, 최대 50). 고정 글 먼저 */
    @RequirePermission(Permission.POST_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<NoticeListView>> list(@RequestParam(required = false) String q, @RequestParam(required = false) String state,
                                                            @RequestParam(required = false) String prefix, @RequestParam(required = false) String page,
                                                            @RequestParam(required = false) String size) {
        return noStore(HttpStatus.OK, queries.list(q, state, prefix, page, size));
    }

    /** 공지 하나 + 본문. 공지 게시판 글이 아니면 404 POST_NOT_FOUND */
    @RequirePermission(Permission.POST_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<NoticeDetailView>> detail(@PathVariable String id) {
        return noStore(HttpStatus.OK, queries.detail(id));
    }

    /** 새 공지 — 201 {@code { id }} */
    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<NoticeCreatedView>> create(@RequestBody(required = false) NoticeSaveRequest body, AdminActor actor) {
        return noStore(HttpStatus.CREATED, writes.create(actor, body));
    }

    /** 공지 고치기 — 204, 작성자는 그대로 */
    @RequirePermission(Permission.POST_MANAGE)
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(@PathVariable String id, @RequestBody(required = false) NoticeSaveRequest body, AdminActor actor) {
        writes.update(actor, id, body);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/{id}/pin")
    public ResponseEntity<Void> pin(@PathVariable String id, AdminActor actor) {
        writes.pin(actor, id);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/{id}/unpin")
    public ResponseEntity<Void> unpin(@PathVariable String id, AdminActor actor) {
        writes.unpin(actor, id);
        return ResponseEntity.noContent().build();
    }

    /** 삭제 — 사유 · 작성자 알림 · 포인트 회수 없음(복구는 게시글 관리에서) */
    @RequirePermission(Permission.POST_MANAGE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, AdminActor actor) {
        writes.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    /** 본문 이미지 — multipart {@code file} 한 장 → 201 {@code { id, url, width, height }}. 칸이 빠진 요청도 서비스가 칸 오류로 거절하도록 {@code required = false} */
    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/images")
    public ResponseEntity<ApiResponse<NoticeImageView>> image(@RequestParam(value = "file", required = false) MultipartFile file, AdminActor actor) {
        return noStore(HttpStatus.CREATED, writes.uploadImage(actor, file));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
