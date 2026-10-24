package com.community.debate.controller;

import com.community.auth.dto.AuthUser;
import com.community.debate.dto.request.StanceRequest;
import com.community.debate.dto.response.CommenterListView;
import com.community.debate.dto.response.StanceView;
import com.community.debate.service.DebateService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 하이라이트 토론(찬성·반대 의견)의 공개 API — 입장 바꾸기·@ 지정 후보. 둘 다 로그인 회원만(입장 바꾸기는 SecurityConfig 가, 후보는 서비스가 401).
 * 댓글 목록의 구분·배지와 댓글 쓰기의 입장은 {@code CommentController} 에 있다.
 */
@RestController
public class DebateController {

    private final DebateService service;

    public DebateController(DebateService service) {
        this.service = service;
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

    private static Long userId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }
}
