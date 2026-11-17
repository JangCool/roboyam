package com.community.title.controller;

import com.community.auth.dto.AuthUser;
import com.community.title.dto.request.TitleReviewRequest;
import com.community.title.dto.response.BoardTitleApplicationPageView;
import com.community.title.service.BoardTitleReviewService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시판 매니저의 칭호 신청 심사(member-title-contract 「구현 결정」 5) — 로그인 필요(401, {@code GET /api/boards/**} 는 보안 설정에서 열려 있어 여기서 본다).
 *
 * <ul>
 *   <li>{@code GET /api/boards/{slug}/staff/title-applications?status=PENDING|APPROVED|REJECTED|ALL} → 신청 목록(기본 대기) · 403 {@code TITLE_REVIEW_FORBIDDEN}(그 게시판 매니저가 아님)</li>
 *   <li>{@code POST …/{id}/approve} {@code { reason? }} → 204 · {@code POST …/{id}/reject} {@code { reason }} → 204 · 400 {@code fields.reason}(거절 사유 없음) ·
 *       403 {@code TITLE_REVIEW_FORBIDDEN}·{@code TITLE_REVIEW_SELF} · 404 {@code TITLE_APPLICATION_NOT_FOUND} · 409 {@code TITLE_APPLICATION_CLOSED}·{@code TITLE_ALREADY_GRANTED}·
 *       {@code TITLE_MEMBER_EXCLUDED}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/boards/{slug}/staff/title-applications")
public class BoardTitleReviewController {

    private final BoardTitleReviewService service;
    private final ClientIpResolver clientIps;

    public BoardTitleReviewController(BoardTitleReviewService service, ClientIpResolver clientIps) {
        this.service = service;
        this.clientIps = clientIps;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BoardTitleApplicationPageView>> list(@PathVariable String slug, @RequestParam(required = false) String status,
                                                                             Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.list(slug, userId(authentication), status)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable String slug, @PathVariable String id, @RequestBody(required = false) TitleReviewRequest body,
                                        Authentication authentication, HttpServletRequest request) {
        service.approve(slug, userId(authentication), id, body, clientIps.resolve(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable String slug, @PathVariable String id, @RequestBody(required = false) TitleReviewRequest body,
                                       Authentication authentication, HttpServletRequest request) {
        service.reject(slug, userId(authentication), id, body, clientIps.resolve(request));
        return ResponseEntity.noContent().build();
    }

    private static long userId(Authentication authentication) {
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return principal.id();
    }
}
