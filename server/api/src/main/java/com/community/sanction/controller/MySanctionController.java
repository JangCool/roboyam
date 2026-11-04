package com.community.sanction.controller;

import com.community.auth.dto.AuthUser;
import com.community.sanction.dto.request.AppealRequest;
import com.community.sanction.dto.response.AppealCreatedView;
import com.community.sanction.dto.response.MySanctionsView;
import com.community.sanction.service.MySanctionService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 제재·이의제기(공개 사이트) — 로그인 필요(없으면 401, {@code /api/me/**} 보안 규칙), 정지·영구 정지 중에도 · 이메일 인증 전에도 쓴다. 본인 것만(경로에 회원 ID 없음).
 * 쓰기는 CSRF, 응답은 {@code Cache-Control: no-store}. 계약 docs/features/member-sanction-contract.md.
 *
 * <ul>
 *   <li>{@code GET /api/me/sanctions} → {@code MySanctions}</li>
 *   <li>{@code POST /api/me/sanctions/{id}/acknowledge} → 204(멱등) · 404 {@code SANCTION_NOT_FOUND}</li>
 *   <li>{@code POST /api/me/board-sanctions/{id}/acknowledge} → 204(멱등) · 404 {@code BOARD_SANCTION_NOT_FOUND}</li>
 *   <li>{@code POST /api/me/appeals} {@code { sanctionId | boardSanctionId, reason }}(둘 중 하나 — 둘 다면 400 {@code fields.boardSanctionId}) → 201 {@code { id, no, status }} ·
 *       400 {@code fields.reason} · 404 {@code SANCTION_NOT_FOUND}·{@code BOARD_SANCTION_NOT_FOUND} · 409 {@code SANCTION_NOT_APPEALABLE}·{@code APPEAL_ALREADY}·{@code APPEAL_EXPIRED}</li>
 * </ul>
 */
@RestController
public class MySanctionController {

    private final MySanctionService service;

    public MySanctionController(MySanctionService service) {
        this.service = service;
    }

    @GetMapping("/api/me/sanctions")
    public ResponseEntity<ApiResponse<MySanctionsView>> mine(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(service.mine(principal(authentication).id())));
    }

    @PostMapping("/api/me/sanctions/{id}/acknowledge")
    public ResponseEntity<Void> acknowledge(@PathVariable String id, Authentication authentication) {
        service.acknowledge(principal(authentication).id(), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    /** 게시판 경고 확인(board-sanction-contract) → 204(멱등) · 404 {@code BOARD_SANCTION_NOT_FOUND} */
    @PostMapping("/api/me/board-sanctions/{id}/acknowledge")
    public ResponseEntity<Void> acknowledgeBoard(@PathVariable String id, Authentication authentication) {
        service.acknowledgeBoard(principal(authentication).id(), id);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/api/me/appeals")
    public ResponseEntity<ApiResponse<AppealCreatedView>> appeal(@RequestBody(required = false) AppealRequest body, Authentication authentication) {
        AppealCreatedView created = service.appeal(principal(authentication).id(), body);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(created));
    }

    private static AuthUser principal(Authentication authentication) {
        if (!(authentication != null && authentication.getPrincipal() instanceof AuthUser principal)) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return principal;
    }
}
