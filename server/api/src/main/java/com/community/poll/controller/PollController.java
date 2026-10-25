package com.community.poll.controller;

import com.community.auth.dto.AuthUser;
import com.community.poll.dto.request.PollVoteRequest;
import com.community.poll.dto.response.PollView;
import com.community.poll.service.PollService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 글 투표 — 읽기는 비회원도(SecurityConfig {@code GET /api/posts/**}), 투표하기·지우기는 로그인 회원만(쓰기 요청은 SecurityConfig 가 401, CSRF 필요).
 * 붙이기·고치기는 글쓰기·고치기의 {@code poll} 칸. 규칙은 {@link PollService}, 계약은 docs/features/post-poll-contract.md.
 */
@RestController
public class PollController {

    private final PollService service;

    public PollController(PollService service) {
        this.service = service;
    }

    /** 투표만 읽기 — 글 상세의 {@code poll} 과 같은 모양, 조회수를 올리지 않는다. 투표가 없으면 404 POLL_NOT_FOUND */
    @GetMapping("/api/posts/{postId}/poll")
    public ResponseEntity<ApiResponse<PollView>> read(@PathVariable String postId, Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.read(postId, userId(authentication))));
    }

    /** 투표하기 — 본문 {@code { optionIds }}, 응답은 투표 뒤의 투표 */
    @PostMapping("/api/posts/{postId}/poll/votes")
    public ApiResponse<PollView> vote(@PathVariable String postId, @RequestBody(required = false) PollVoteRequest body, Authentication authentication) {
        return ApiResponse.success(service.vote(postId, body, userId(authentication)));
    }

    /** 글쓴이의 투표 지우기(204) — 첫 표 전만(409 POLL_LOCKED), 다른 사람 403 */
    @DeleteMapping("/api/posts/{postId}/poll")
    public ResponseEntity<Void> delete(@PathVariable String postId, Authentication authentication) {
        service.delete(postId, userId(authentication));
        return ResponseEntity.noContent().build();
    }

    private static Long userId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }
}
