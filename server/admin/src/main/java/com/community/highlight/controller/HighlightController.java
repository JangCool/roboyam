package com.community.highlight.controller;

import com.community.highlight.dto.request.HighlightOrderRequest;
import com.community.highlight.dto.request.HighlightPinRejectRequest;
import com.community.highlight.dto.request.HighlightSaveRequest;
import com.community.highlight.dto.request.HighlightVisibleRequest;
import com.community.highlight.dto.response.HighlightCandidatesView;
import com.community.highlight.dto.response.HighlightItemView;
import com.community.highlight.dto.response.HighlightListView;
import com.community.highlight.dto.response.HighlightOpinionListView;
import com.community.highlight.dto.response.HighlightOpinionView;
import com.community.highlight.dto.response.HighlightPinRequestListView;
import com.community.highlight.dto.response.HighlightPinRequestView;
import com.community.highlight.service.HighlightAdminService;
import com.community.highlight.service.HighlightOpinionService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.post.dto.response.AdminPostRefView;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
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

/**
 * 하이라이트 관리 API(관리자 앱) — 목록·글 찾기·후보·의견 목록은 {@code highlight.read}, 나머지(만들기·고치기·순서·노출·지우기·의견 고정·숨김)는 {@code highlight.manage}(CSRF).
 * 규칙은 {@link HighlightAdminService}·{@link HighlightOpinionService}. 대표 이미지는 {@code POST /api/admin/files}(purpose HIGHLIGHT_IMAGE)로 먼저 올린다.
 * 모든 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/highlights")
public class HighlightController {

    private final HighlightAdminService service;
    private final HighlightOpinionService opinions;

    public HighlightController(HighlightAdminService service, HighlightOpinionService opinions) {
        this.service = service;
        this.opinions = opinions;
    }

    @RequirePermission(Permission.HIGHLIGHT_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<HighlightListView>> list() {
        return noStore(service.list());
    }

    /** 대상 글 찾기 — {@code q} 제목 일부(2자 이상) 또는 글 번호(숫자 15자리 이상). 최대 10개 */
    @RequirePermission(Permission.HIGHLIGHT_READ)
    @GetMapping("/post-search")
    public ResponseEntity<ApiResponse<List<AdminPostRefView>>> search(@RequestParam(required = false) String q) {
        return noStore(service.search(q));
    }

    /** 후보 — {@code days} 0~365(0 = 전체, 기본 7) · {@code minLikes} 1~1000(기본 3) · {@code boardId}(선택) */
    @RequirePermission(Permission.HIGHLIGHT_READ)
    @GetMapping("/candidates")
    public ResponseEntity<ApiResponse<HighlightCandidatesView>> candidates(@RequestParam(required = false) String days,
                                                                          @RequestParam(required = false) String minLikes,
                                                                          @RequestParam(required = false) String boardId) {
        return noStore(service.candidates(days, minLikes, boardId));
    }

    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @PostMapping
    public ResponseEntity<ApiResponse<HighlightItemView>> create(@RequestBody(required = false) HighlightSaveRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.create(actor, body)));
    }

    /** 순서 바꾸기(204) — 경로가 {@code /{id}} 보다 먼저 맞는다 */
    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @PutMapping("/order")
    public ResponseEntity<Void> order(@RequestBody(required = false) HighlightOrderRequest body, AdminActor actor) {
        service.order(actor, body);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<HighlightItemView>> update(@PathVariable String id, @RequestBody(required = false) HighlightSaveRequest body,
                                                                 AdminActor actor) {
        return noStore(service.update(actor, id, body));
    }

    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @PutMapping("/{id}/visible")
    public ResponseEntity<ApiResponse<HighlightItemView>> visible(@PathVariable String id, @RequestBody(required = false) HighlightVisibleRequest body,
                                                                  AdminActor actor) {
        return noStore(service.visible(actor, id, body));
    }

    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, AdminActor actor) {
        service.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    /** 의견 목록 — {@code stance} PRO · CON · NEUTRAL(선택) · {@code page}(1부터) · {@code size}(기본 30, 최대 100) */
    @RequirePermission(Permission.HIGHLIGHT_READ)
    @GetMapping("/{id}/opinions")
    public ResponseEntity<ApiResponse<HighlightOpinionListView>> opinions(@PathVariable String id, @RequestParam(required = false) String stance,
                                                                         @RequestParam(required = false) String page,
                                                                         @RequestParam(required = false) String size) {
        return noStore(opinions.list(id, stance, page, size));
    }

    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @PostMapping("/{id}/opinions/{commentId}/pin")
    public ResponseEntity<ApiResponse<HighlightOpinionView>> pin(@PathVariable String id, @PathVariable String commentId, AdminActor actor) {
        return noStore(opinions.pin(actor, id, commentId, true));
    }

    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @DeleteMapping("/{id}/opinions/{commentId}/pin")
    public ResponseEntity<ApiResponse<HighlightOpinionView>> unpin(@PathVariable String id, @PathVariable String commentId, AdminActor actor) {
        return noStore(opinions.pin(actor, id, commentId, false));
    }

    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @PostMapping("/{id}/opinions/{commentId}/hide")
    public ResponseEntity<ApiResponse<HighlightOpinionView>> hide(@PathVariable String id, @PathVariable String commentId, AdminActor actor) {
        return noStore(opinions.hide(actor, id, commentId, true));
    }

    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @DeleteMapping("/{id}/opinions/{commentId}/hide")
    public ResponseEntity<ApiResponse<HighlightOpinionView>> unhide(@PathVariable String id, @PathVariable String commentId, AdminActor actor) {
        return noStore(opinions.hide(actor, id, commentId, false));
    }

    /** 대기 중인 의견 고정 요청 — 읽기는 {@code highlight.read}(승인·거절은 {@code highlight.manage}) */
    @RequirePermission(Permission.HIGHLIGHT_READ)
    @GetMapping("/{id}/pin-requests")
    public ResponseEntity<ApiResponse<HighlightPinRequestListView>> pinRequests(@PathVariable String id) {
        return noStore(opinions.pinRequests(id));
    }

    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @PostMapping("/{id}/pin-requests/{requestId}/approve")
    public ResponseEntity<ApiResponse<HighlightPinRequestView>> approvePinRequest(@PathVariable String id, @PathVariable String requestId, AdminActor actor) {
        return noStore(opinions.decidePinRequest(actor, id, requestId, true, null));
    }

    /** 거절 — 본문 {@code { reason? }}(0~100자) */
    @RequirePermission(Permission.HIGHLIGHT_MANAGE)
    @PostMapping("/{id}/pin-requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<HighlightPinRequestView>> rejectPinRequest(@PathVariable String id, @PathVariable String requestId,
                                                                                @RequestBody(required = false) HighlightPinRejectRequest body, AdminActor actor) {
        return noStore(opinions.decidePinRequest(actor, id, requestId, false, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
