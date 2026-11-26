package com.community.counsel.controller;

import com.community.auth.dto.AuthUser;
import com.community.counsel.dto.request.CounselPickRequest;
import com.community.counsel.dto.response.CounselRankingView;
import com.community.counsel.dto.response.PostCounselView;
import com.community.counsel.service.CounselService;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 연애상담소(ai-love-counsel-contract) — 모양은 user-web {@code useCounselApi.types.ts} 그대로. 접근 규칙은 {@code SecurityConfig}: 읽기 둘({@code /api/posts/**}·{@code /api/boards/**}
 * GET)은 로그인 없이, 조언 고르기는 로그인 필요(없으면 401) + CSRF. 응답은 {@code Cache-Control: no-store}(보는 사람마다 다른 칸 — {@code canPick}·내 추천).
 */
@RestController
public class CounselController {

    private final CounselService counsel;
    private final ClientIpResolver ipResolver;

    public CounselController(CounselService counsel, ClientIpResolver ipResolver) {
        this.counsel = counsel;
        this.ipResolver = ipResolver;
    }

    /** 상담 칸 — 404 {@code POST_NOT_FOUND} · {@code COUNSEL_NOT_FOUND}(상담 글 아님), 403 {@code POST_HIDDEN} 등 글 보기와 같은 오류 */
    @GetMapping("/api/posts/{id}/counsel")
    public ResponseEntity<ApiResponse<PostCounselView>> view(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(counsel.view(id, userId(authentication), ipResolver.resolve(request))));
    }

    /**
     * 「가장 도움 된 조언」 고르기 — 403 {@code COUNSEL_PICK_FORBIDDEN} · 409 {@code COUNSEL_PICK_UNAVAILABLE}·{@code COUNSEL_ALREADY_PICKED}·{@code COUNSEL_PICK_OWN} ·
     * 404 {@code COMMENT_NOT_FOUND}·{@code COUNSEL_NOT_FOUND}
     */
    @PutMapping("/api/posts/{id}/counsel/pick")
    public ResponseEntity<ApiResponse<PostCounselView>> pick(@PathVariable String id, @RequestBody(required = false) CounselPickRequest body, Authentication authentication,
                                                             HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(counsel.pick(id, body == null ? null : body.commentId(), userId(authentication), ipResolver.resolve(request))));
    }

    /** 이번 주 상담 순위 — 404 {@code BOARD_NOT_FOUND} · {@code COUNSEL_NOT_FOUND}(상담소가 없는 게시판), 403 {@code BOARD_INACTIVE} */
    @GetMapping("/api/boards/{slug}/counsel/ranking")
    public ResponseEntity<ApiResponse<CounselRankingView>> ranking(@PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(counsel.ranking(slug)));
    }

    private static Long userId(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }
}
