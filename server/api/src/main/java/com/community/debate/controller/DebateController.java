package com.community.debate.controller;

import com.community.auth.dto.AuthUser;
import com.community.debate.dto.request.PinRequestRejectRequest;
import com.community.debate.dto.request.StanceRequest;
import com.community.debate.dto.response.CommenterListView;
import com.community.debate.dto.response.PinRequestListView;
import com.community.debate.dto.response.PinRequestView;
import com.community.debate.dto.response.StanceView;
import com.community.debate.service.DebateService;
import com.community.debate.service.PinRequestService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 하이라이트 토론의 공개 API — 입장 고르기·바꾸기 · @ 지정 후보 · 의견 고정 요청. 모두 로그인 회원만(쓰기는 SecurityConfig 가, GET 은 서비스가 401).
 * 댓글 목록의 구분·배지와 댓글 쓰기의 입장은 {@code CommentController} 에 있다.
 */
@RestController
public class DebateController {

    private final DebateService service;
    private final PinRequestService pinRequests;

    public DebateController(DebateService service, PinRequestService pinRequests) {
        this.service = service;
        this.pinRequests = pinRequests;
    }

    @PutMapping("/api/posts/{postId}/stance")
    public ApiResponse<StanceView> changeStance(@PathVariable String postId, @RequestBody(required = false) StanceRequest body,
                                                Authentication authentication) {
        return ApiResponse.success(service.changeStance(postId, body, userId(authentication)));
    }

    @GetMapping("/api/posts/{postId}/commenters")
    public ResponseEntity<ApiResponse<CommenterListView>> commenters(@PathVariable String postId, @RequestParam(required = false) String q,
                                                                     Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.commenters(postId, q, userId(authentication))));
    }

    /** 내 의견 고정 요청 보내기(201) */
    @PostMapping("/api/comments/{commentId}/pin-request")
    public ResponseEntity<ApiResponse<PinRequestView>> requestPin(@PathVariable String commentId, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(pinRequests.request(commentId, userId(authentication))));
    }

    /** 내 대기 요청 취소(204 — 없어도) */
    @DeleteMapping("/api/comments/{commentId}/pin-request")
    public ResponseEntity<Void> cancelPin(@PathVariable String commentId, Authentication authentication) {
        pinRequests.cancel(commentId, userId(authentication));
        return ResponseEntity.noContent().build();
    }

    /** 내가 확인할 그 글의 대기 요청(확인 권한자만) */
    @GetMapping("/api/posts/{postId}/pin-requests")
    public ResponseEntity<ApiResponse<PinRequestListView>> pendingPins(@PathVariable String postId, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(pinRequests.pending(postId, userId(authentication))));
    }

    @PostMapping("/api/pin-requests/{id}/approve")
    public ApiResponse<PinRequestView> approvePin(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success(pinRequests.decide(id, true, null, userId(authentication)));
    }

    /** 거절 — 본문 {@code { reason? }}(0~100자) */
    @PostMapping("/api/pin-requests/{id}/reject")
    public ApiResponse<PinRequestView> rejectPin(@PathVariable String id, @RequestBody(required = false) PinRequestRejectRequest body,
                                                 Authentication authentication) {
        return ApiResponse.success(pinRequests.decide(id, false, body, userId(authentication)));
    }

    private static Long userId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }
}
