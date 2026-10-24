package com.community.debate.controller;

import com.community.debate.dto.request.DebatePinRejectRequest;
import com.community.debate.dto.request.DebateSaveRequest;
import com.community.debate.dto.response.DebateItemView;
import com.community.debate.dto.response.DebateOpinionListView;
import com.community.debate.dto.response.DebateOpinionView;
import com.community.debate.dto.response.DebatePinRequestListView;
import com.community.debate.dto.response.DebatePinRequestView;
import com.community.debate.service.DebateAdminService;
import com.community.debate.service.DebateOpinionService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.util.Optional;
import org.springframework.http.CacheControl;
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
 * 글 찬반 토론 관리 API(관리자 앱, 글 번호 기준) — 읽기 {@code post.read}, 쓰기 {@code post.manage}(CSRF). 게시글 관리의 글 상세 서랍 토론 구획·하이라이트 목록의 의견 서랍이 쓴다
 * (docs/features/post-debate-contract.md). 규칙은 {@link DebateAdminService}·{@link DebateOpinionService}. 모든 응답은 {@code Cache-Control: no-store}.
 */
@RestController
@RequestMapping("/api/admin/debates")
public class DebateAdminController {

    private final DebateAdminService service;
    private final DebateOpinionService opinions;

    public DebateAdminController(DebateAdminService service, DebateOpinionService opinions) {
        this.service = service;
        this.opinions = opinions;
    }

    /** 그 글의 열린 토론 — 없으면 404 DEBATE_NOT_FOUND */
    @RequirePermission(Permission.POST_READ)
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<DebateItemView>> get(@PathVariable String postId) {
        return noStore(service.get(postId));
    }

    /** 열기·설정 바꾸기(200 줄) · 닫기(204) — 본문 {@code { enabled, neutralEnabled, stanceChangeAllowed }} */
    @RequirePermission(Permission.POST_MANAGE)
    @PutMapping("/{postId}")
    public ResponseEntity<ApiResponse<DebateItemView>> save(@PathVariable String postId, @RequestBody(required = false) DebateSaveRequest body,
                                                            AdminActor actor) {
        Optional<DebateItemView> item = service.save(actor, postId, body);
        return item.map(DebateAdminController::noStore).orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** 의견 목록 — {@code stance} PRO · CON · NEUTRAL(선택) · {@code page}(1부터) · {@code size}(기본 30, 최대 100) */
    @RequirePermission(Permission.POST_READ)
    @GetMapping("/{postId}/opinions")
    public ResponseEntity<ApiResponse<DebateOpinionListView>> opinions(@PathVariable String postId, @RequestParam(required = false) String stance,
                                                                      @RequestParam(required = false) String page,
                                                                      @RequestParam(required = false) String size) {
        return noStore(opinions.list(postId, stance, page, size));
    }

    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/{postId}/opinions/{commentId}/pin")
    public ResponseEntity<ApiResponse<DebateOpinionView>> pin(@PathVariable String postId, @PathVariable String commentId, AdminActor actor) {
        return noStore(opinions.pin(actor, postId, commentId, true));
    }

    @RequirePermission(Permission.POST_MANAGE)
    @DeleteMapping("/{postId}/opinions/{commentId}/pin")
    public ResponseEntity<ApiResponse<DebateOpinionView>> unpin(@PathVariable String postId, @PathVariable String commentId, AdminActor actor) {
        return noStore(opinions.pin(actor, postId, commentId, false));
    }

    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/{postId}/opinions/{commentId}/hide")
    public ResponseEntity<ApiResponse<DebateOpinionView>> hide(@PathVariable String postId, @PathVariable String commentId, AdminActor actor) {
        return noStore(opinions.hide(actor, postId, commentId, true));
    }

    @RequirePermission(Permission.POST_MANAGE)
    @DeleteMapping("/{postId}/opinions/{commentId}/hide")
    public ResponseEntity<ApiResponse<DebateOpinionView>> unhide(@PathVariable String postId, @PathVariable String commentId, AdminActor actor) {
        return noStore(opinions.hide(actor, postId, commentId, false));
    }

    /** 대기 중인 의견 고정 요청 — 읽기는 {@code post.read}(승인·거절은 {@code post.manage}) */
    @RequirePermission(Permission.POST_READ)
    @GetMapping("/{postId}/pin-requests")
    public ResponseEntity<ApiResponse<DebatePinRequestListView>> pinRequests(@PathVariable String postId) {
        return noStore(opinions.pinRequests(postId));
    }

    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/{postId}/pin-requests/{requestId}/approve")
    public ResponseEntity<ApiResponse<DebatePinRequestView>> approvePinRequest(@PathVariable String postId, @PathVariable String requestId,
                                                                              AdminActor actor) {
        return noStore(opinions.decidePinRequest(actor, postId, requestId, true, null));
    }

    /** 거절 — 본문 {@code { reason? }}(0~100자) */
    @RequirePermission(Permission.POST_MANAGE)
    @PostMapping("/{postId}/pin-requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<DebatePinRequestView>> rejectPinRequest(@PathVariable String postId, @PathVariable String requestId,
                                                                             @RequestBody(required = false) DebatePinRejectRequest body, AdminActor actor) {
        return noStore(opinions.decidePinRequest(actor, postId, requestId, false, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
