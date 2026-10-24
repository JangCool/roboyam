package com.community.comment.controller;

import com.community.auth.dto.AuthUser;
import com.community.board.dto.request.PasswordRequest;
import com.community.comment.dto.request.CommentUpdateRequest;
import com.community.comment.dto.request.CommentWriteRequest;
import com.community.comment.dto.response.CommentLikeResult;
import com.community.comment.dto.response.CommentListResponse;
import com.community.comment.dto.response.CommentView;
import com.community.comment.service.CommentService;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 댓글 — 모양은 user-web {@code useCommentApi.types.ts} 그대로. 목록·쓰기·고치기·지우기는 로그인 없이(비회원 댓글), 추천은 로그인 필요({@code SecurityConfig}).
 */
@RestController
public class CommentController {

    private final CommentService service;
    private final ClientIpResolver ipResolver;

    public CommentController(CommentService service, ClientIpResolver ipResolver) {
        this.service = service;
        this.ipResolver = ipResolver;
    }

    /** 원댓글 + 답글 + 베스트 + 보이는 수 + 토론 칸. {@code sort} OLDEST(기본) · LATEST, {@code kind} ALL(기본) · GENERAL · PRO · CON(하이라이트 토론 구분) */
    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentListResponse>> list(@PathVariable String postId, @RequestParam(required = false, defaultValue = "OLDEST") String sort,
                                                                 @RequestParam(required = false) String kind, Authentication authentication,
                                                                 HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.list(postId, sort, kind, userId(authentication), ipResolver.resolve(request))));
    }

    /** 쓰기(201) — 새 댓글 */
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentView>> create(@PathVariable String postId, @RequestBody CommentWriteRequest body, Authentication authentication,
                                                           HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(service.create(postId, body, userId(authentication), ipResolver.resolve(request))));
    }

    @PutMapping("/api/comments/{id}")
    public ApiResponse<CommentView> update(@PathVariable String id, @RequestBody CommentUpdateRequest body, Authentication authentication,
                                           HttpServletRequest request) {
        return ApiResponse.success(service.update(id, body, userId(authentication), ipResolver.resolve(request)));
    }

    /** 지우기(204) — 비회원 댓글은 본문에 {@code password} */
    @PostMapping("/api/comments/{id}/delete")
    public ResponseEntity<Void> delete(@PathVariable String id, @RequestBody(required = false) PasswordRequest body, Authentication authentication,
                                       HttpServletRequest request) {
        service.delete(id, body == null ? null : body.password(), userId(authentication), ipResolver.resolve(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/comments/{id}/like")
    public ApiResponse<CommentLikeResult> like(@PathVariable String id, Authentication authentication) {
        return ApiResponse.success(service.like(id, userId(authentication)));
    }

    static Long userId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }
}
