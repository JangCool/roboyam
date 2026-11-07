package com.community.tips.controller;

import com.community.auth.dto.AuthUser;
import com.community.tips.dto.response.TipsOverviewView;
import com.community.tips.dto.response.TipsPostPageView;
import com.community.tips.service.TipsService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팁·강좌·사용기 묶음 화면(user-web {@code /tips}, tips-contract 「서버 구현 결과」) — 로그인 없이 부른다(로그인했으면 줄에 차단·메모, 19금 글은 성인에게만 최신 글에).
 * 보는 사람마다 다를 수 있어 {@code no-store}. 규칙은 {@link TipsService}.
 */
@RestController
@RequestMapping("/api/tips")
public class TipsController {

    private final TipsService service;

    public TipsController(TipsService service) {
        this.service = service;
    }

    /** 묶음 머리 몫 — {@code board} 는 묶음 게시판 slug({@code tip}·{@code review}), 비었거나 모르는 값이면 전체 */
    @GetMapping
    public ResponseEntity<ApiResponse<TipsOverviewView>> overview(@RequestParam(required = false) String board, Authentication authentication) {
        return noStore(service.overview(board, userId(authentication)));
    }

    /** 최신 글 한 쪽 — {@code page} 1부터(작으면 1), 한 쪽 20개 */
    @GetMapping("/posts")
    public ResponseEntity<ApiResponse<TipsPostPageView>> posts(@RequestParam(required = false) String board,
                                                              @RequestParam(required = false, defaultValue = "1") int page, Authentication authentication) {
        return noStore(service.latest(board, page, userId(authentication)));
    }

    private static Long userId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
