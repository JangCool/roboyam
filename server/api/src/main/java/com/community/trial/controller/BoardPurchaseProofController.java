package com.community.trial.controller;

import com.community.trial.dto.TrialViews;
import com.community.trial.service.PurchaseProofService;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
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
 * 게시판 매니저의 구매 인증 확인(trial-group-contract 「구현 결정」 7 — 회원 앱 스텝 화면 「구매 인증」 탭). 그 게시판의 매니저(스텝 아님)만, 자기 글은 처리하지 못한다. 로그인 필요(401).
 *
 * <ul>
 *   <li>{@code GET /api/boards/{slug}/staff/purchase-proofs?status=PENDING|DONE|ALL} → 목록 · 403 {@code PURCHASE_PROOF_FORBIDDEN}</li>
 *   <li>{@code GET …/{id}/photo} → 비공개 사진(JPEG, 열람 기록) · 410 {@code PURCHASE_PROOF_PURGED}</li>
 *   <li>{@code POST …/{id}/approve} → 204(보너스·알림) · {@code POST …/{id}/reject} {@code { reason }} → 204 · 403 {@code PURCHASE_PROOF_FORBIDDEN}·{@code PURCHASE_PROOF_SELF} ·
 *       404 {@code PURCHASE_PROOF_NOT_FOUND} · 409 {@code PURCHASE_PROOF_CLOSED}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/boards/{slug}/staff/purchase-proofs")
public class BoardPurchaseProofController {

    private final PurchaseProofService proofs;
    private final ClientIpResolver clientIps;

    public BoardPurchaseProofController(PurchaseProofService proofs, ClientIpResolver clientIps) {
        this.proofs = proofs;
        this.clientIps = clientIps;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TrialViews.Proof>>> list(@PathVariable String slug, @RequestParam(required = false) String status,
                                                                    Authentication authentication) {
        return TrialController.noStore(proofs.listForManager(slug, TrialController.userId(authentication), status));
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable String slug, @PathVariable String id, Authentication authentication, HttpServletRequest request) {
        long memberId = TrialController.userId(authentication);
        requireBoard(slug, memberId);
        PurchaseProofService.Photo photo = proofs.photo(PurchaseProofService.Reviewer.manager(memberId, clientIps.resolve(request)), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(photo.contentType())).body(photo.bytes());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable String slug, @PathVariable String id, Authentication authentication, HttpServletRequest request) {
        long memberId = TrialController.userId(authentication);
        requireBoard(slug, memberId);
        proofs.approve(PurchaseProofService.Reviewer.manager(memberId, clientIps.resolve(request)), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable String slug, @PathVariable String id, @RequestBody(required = false) TrialViews.ReasonRequest body,
                                       Authentication authentication, HttpServletRequest request) {
        long memberId = TrialController.userId(authentication);
        requireBoard(slug, memberId);
        proofs.reject(PurchaseProofService.Reviewer.manager(memberId, clientIps.resolve(request)), id, body == null ? null : body.reason());
        return ResponseEntity.noContent().build();
    }

    /** 주소의 게시판 매니저인가(인증 줄의 게시판은 서비스가 다시 본다) */
    private void requireBoard(String slug, long memberId) {
        proofs.managedBoard(slug, memberId);
    }
}
