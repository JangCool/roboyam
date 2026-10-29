package com.community.home.controller;

import com.community.auth.dto.AuthUser;
import com.community.home.dto.response.BoardLayoutResponse;
import com.community.home.service.HomeGadgetService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 게시판 레이아웃 — 게시판 목록·글 보기 둘레의 가젯(board-layout-contract). 모양은 user-web {@code useBoardLayoutApi.types.ts}. 로그인 없이 읽는다
 * ({@code SecurityConfig} 의 {@code GET /api/boards/**}). 보는 사람 몫(차단·메모)이 섞여 캐시하지 않는다.
 */
@RestController
@RequestMapping("/api/boards")
public class BoardLayoutController {

    private final HomeGadgetService gadgets;

    public BoardLayoutController(HomeGadgetService gadgets) {
        this.gadgets = gadgets;
    }

    /**
     * 그 테마의 게시판 레이아웃을 이 게시판 기준으로 채운다. {@code theme} 은 관리자 미리보기만 싣는다 — 모르는 값은 무시하고 사이트 테마(홈 {@code GET /api/home/gadgets?theme=} 와 같은 규칙).
     * 없는 게시판 404 {@code BOARD_NOT_FOUND}, 꺼진 게시판 403 {@code BOARD_INACTIVE}(게시판 조회와 같다).
     */
    @GetMapping("/{slug}/layout")
    public ResponseEntity<ApiResponse<BoardLayoutResponse>> layout(@PathVariable String slug, @RequestParam(required = false) String theme,
                                                                   Authentication authentication) {
        Long viewerId = authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(gadgets.boardLayout(slug, theme, viewerId)));
    }
}
