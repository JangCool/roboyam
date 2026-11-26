package com.community.highlight.controller;

import com.community.auth.dto.AuthUser;
import com.community.highlight.dto.response.BoardRecommendView;
import com.community.highlight.service.BoardRecommendService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시판 목록 위 「추천 글」 카드(user-web {@code /boards/[slug]}, board-recommend-contract) — 로그인 없이 부른다({@code SecurityConfig} 의 {@code GET /api/boards/**}).
 * 성인 전용 게시판 판정이 보는 사람마다 달라 {@code no-store}(카드 자체는 서버가 게시판마다 짧게 캐시한다). 규칙은 {@link BoardRecommendService}.
 */
@RestController
@RequestMapping("/api/boards")
public class BoardRecommendController {

    private final BoardRecommendService service;

    public BoardRecommendController(BoardRecommendService service) {
        this.service = service;
    }

    /** 최대 3장 — 없는 게시판 404 {@code BOARD_NOT_FOUND}, 꺼진 게시판 403 {@code BOARD_INACTIVE}, 성인 전용 게시판은 게시판 글 목록과 같은 판정 */
    @GetMapping("/{slug}/recommended")
    public ResponseEntity<ApiResponse<BoardRecommendView>> recommended(@PathVariable String slug, Authentication authentication) {
        Long viewerId = authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.recommended(slug, viewerId)));
    }
}
