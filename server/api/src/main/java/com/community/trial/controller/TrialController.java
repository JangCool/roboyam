package com.community.trial.controller;

import com.community.auth.dto.AuthUser;
import com.community.trial.dto.TrialViews;
import com.community.trial.service.PurchaseProofService;
import com.community.trial.service.TrialMemberService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 체험단 공개·회원 API(trial-group-contract 「구현 결정」 9). 목록·상세는 누구나, 그 밖은 로그인(401). 모든 응답 {@code Cache-Control: no-store}.
 *
 * <ul>
 *   <li>{@code GET /api/trials?status=RECRUITING|RUNNING|CLOSED} → 카드 목록(모집 중은 뽑는 중 포함) · {@code GET /api/trials/{id}} → 상세(끝난 모집이면 {@code reviews[]},
 *       로그인했으면 {@code mine}) · 404 {@code TRIAL_NOT_FOUND}</li>
 *   <li>{@code POST /api/trials/{id}/applications} {@code { addressId, consents[] }} → 201 {@code mine} · 403 {@code IDENTITY_REQUIRED}·{@code MEMBER_RESTRICTED} ·
 *       409 {@code TRIAL_APPLICANT_EXCLUDED}·{@code TRIAL_NOT_RECRUITING}·{@code TRIAL_APPLY_NOT_OPEN}·{@code TRIAL_ALREADY_APPLIED} · 400 {@code fields.addressId|consents} ·
 *       503 {@code DATA_KEY_MISSING}</li>
 *   <li>{@code DELETE /api/trials/{id}/applications/mine} → 204 · 404 {@code TRIAL_APPLICATION_NOT_FOUND} · 409 {@code TRIAL_APPLICATION_CLOSED}</li>
 *   <li>{@code GET /api/me/trials} → 내 신청·뽑힘(받음 버튼·쓰러 가기·반납 안내·이의 제기) · {@code GET /api/me/trials/writable?board=} → 「뽑힌 체험단 사용기로 쓰기」 후보</li>
 *   <li>{@code POST /api/me/trials/participants/{id}/received} → 204 · 409 {@code TRIAL_NOT_SHIPPED}·{@code TRIAL_ALREADY_RECEIVED}</li>
 *   <li>{@code POST /api/me/trials/damage-reports/{id}/objection} {@code { text }} → 204 · 409 {@code TRIAL_OBJECTION_CLOSED}</li>
 *   <li>{@code POST /api/posts/{id}/purchase-proof}(multipart {@code file}) → 201 {@code { id, status }} · 409 {@code PURCHASE_PROOF_NOT_ALLOWED}·{@code PURCHASE_PROOF_EXISTS} ·
 *       400 {@code fields.file}</li>
 * </ul>
 */
@RestController
public class TrialController {

    private final TrialMemberService trials;
    private final PurchaseProofService proofs;

    public TrialController(TrialMemberService trials, PurchaseProofService proofs) {
        this.trials = trials;
        this.proofs = proofs;
    }

    @GetMapping("/api/trials")
    public ResponseEntity<ApiResponse<List<TrialViews.TrialCard>>> list(@RequestParam(required = false) String status) {
        return noStore(trials.list(status));
    }

    @GetMapping("/api/trials/{id}")
    public ResponseEntity<ApiResponse<TrialViews.TrialPublicDetail>> detail(@PathVariable String id, Authentication authentication) {
        return noStore(trials.detail(id, optionalUser(authentication)));
    }

    @PostMapping("/api/trials/{id}/applications")
    public ResponseEntity<ApiResponse<TrialViews.Mine>> apply(@PathVariable String id, @RequestBody(required = false) TrialViews.ApplyRequest body,
                                                              Authentication authentication) {
        TrialViews.Mine mine = trials.apply(userId(authentication), id, body);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(mine));
    }

    @DeleteMapping("/api/trials/{id}/applications/mine")
    public ResponseEntity<Void> cancel(@PathVariable String id, Authentication authentication) {
        trials.cancel(userId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/me/trials")
    public ResponseEntity<ApiResponse<List<TrialViews.MyTrial>>> mine(Authentication authentication) {
        return noStore(trials.mine(userId(authentication)));
    }

    @GetMapping("/api/me/trials/writable")
    public ResponseEntity<ApiResponse<List<TrialViews.Writable>>> writable(@RequestParam(required = false) String board, Authentication authentication) {
        return noStore(trials.writable(userId(authentication), board));
    }

    @PostMapping("/api/me/trials/participants/{id}/received")
    public ResponseEntity<Void> received(@PathVariable String id, Authentication authentication) {
        trials.received(userId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/me/trials/damage-reports/{id}/objection")
    public ResponseEntity<Void> objection(@PathVariable String id, @RequestBody(required = false) TrialViews.ObjectionRequest body, Authentication authentication) {
        trials.objection(userId(authentication), id, body);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/posts/{id}/purchase-proof")
    public ResponseEntity<ApiResponse<TrialViews.ProofResult>> purchaseProof(@PathVariable String id, @RequestParam(value = "file", required = false) MultipartFile file,
                                                                             Authentication authentication) {
        long userId = userId(authentication);
        byte[] data;
        try {
            data = file == null ? null : file.getBytes();
        } catch (IOException e) {
            data = null;
        }
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(proofs.submit(userId, id, data)));
    }

    static long userId(Authentication authentication) {
        Long id = optionalUser(authentication);
        if (id == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return id;
    }

    static Long optionalUser(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuthUser principal ? principal.id() : null;
    }

    static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
