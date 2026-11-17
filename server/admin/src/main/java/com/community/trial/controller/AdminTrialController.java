package com.community.trial.controller;

import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.trial.dto.TrialViews;
import com.community.trial.entity.TrialSettingEntity;
import com.community.trial.service.PurchaseProofService;
import com.community.trial.service.TrialService;
import com.community.trial.service.TrialSettingsService;
import com.community.zconfiguration.response.ApiResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 체험단 관리자 API(trial-group-contract 「구현 결정」 10) — 모집·뽑기·입고·발송·반납·반송·파손 신고는 {@code trial.manage}(또는 광고 처리 {@code ad.manage}), 파손 판정·구매 인증 확인은
 * {@code trial.review}, 설정 저장은 {@code trial.settings}(또는 {@code ad.settings}). 쓰기는 CSRF, 모든 본문 응답 {@code Cache-Control: no-store}.
 *
 * <ul>
 *   <li>{@code GET /api/admin/trials?status} · {@code POST /api/admin/trials} → 201 상세 · {@code GET·PUT /api/admin/trials/{id}}(PUT 본문 {@code action} 이 있으면 상태만 —
 *       PUBLISH·CLOSE_APPLY·CLOSE·CANCEL)</li>
 *   <li>{@code GET /api/admin/trials/{id}/applications} → 신청자(리뷰어 몫·일반 몫 인원) · {@code POST /api/admin/trials/{id}/select} {@code { applicationIds }} → 상세
 *       (인원 넘으면 400 {@code fields.applicationIds}) · {@code POST /api/admin/trials/applications/{id}/recommend} {@code { recommended }} → 204</li>
 *   <li>{@code POST /api/admin/trials/images}(multipart — 대표 그림) · {@code POST /api/admin/trials/{id}/photos?kind=STOCK|PRE_SHIP|RETURN|DAMAGE}(multipart) → 201</li>
 *   <li>입고·발송: {@code POST /api/admin/trials/{id}/stock} {@code { quantity, note, photoIds }} · {@code PUT /api/admin/trials/participants/{id}/shipment}
 *       {@code { carrier, trackingNo, photoIds }} → 204 · {@code POST …/participants/{id}/returned} {@code { photoIds }} → 204 · {@code POST /api/admin/trials/{id}/return-shipment}
 *       {@code { carrier, trackingNo }}</li>
 *   <li>파손: {@code POST /api/admin/trials/participants/{id}/damage-reports} {@code { description, photoIds }} → 201 · {@code POST /api/admin/trials/damage-reports/{id}/decide}
 *       {@code { verdict, note }} — 409 {@code TRIAL_DAMAGE_OBJECTION_OPEN}·{@code TRIAL_DAMAGE_DECIDED}</li>
 *   <li>구매 인증: {@code GET /api/admin/purchase-proofs/{id}/photo} · {@code POST /api/admin/purchase-proofs/{id}/approve|reject} {@code { reason }} → 204</li>
 *   <li>{@code GET·PUT /api/admin/trials/settings}</li>
 * </ul>
 */
@RestController
public class AdminTrialController {

    private final TrialService trials;
    private final TrialSettingsService settings;
    private final PurchaseProofService proofs;

    public AdminTrialController(TrialService trials, TrialSettingsService settings, PurchaseProofService proofs) {
        this.trials = trials;
        this.settings = settings;
        this.proofs = proofs;
    }

    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE})
    @GetMapping("/api/admin/trials")
    public ResponseEntity<ApiResponse<List<TrialViews.TrialRow>>> list(@RequestParam(required = false) String status) {
        return noStore(trials.list(status));
    }

    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE})
    @PostMapping("/api/admin/trials")
    public ResponseEntity<ApiResponse<TrialViews.TrialDetail>> create(@RequestBody(required = false) TrialViews.TrialForm body, AdminActor actor) {
        return created(trials.create(actor.operatorId(), actor.ip(), body));
    }

    @RequirePermission(anyOf = {Permission.TRIAL_SETTINGS, Permission.AD_SETTINGS, Permission.TRIAL_MANAGE})
    @GetMapping("/api/admin/trials/settings")
    public ResponseEntity<ApiResponse<TrialSettingEntity.Values>> settings() {
        return noStore(settings.current());
    }

    @RequirePermission(anyOf = {Permission.TRIAL_SETTINGS, Permission.AD_SETTINGS})
    @PutMapping("/api/admin/trials/settings")
    public ResponseEntity<ApiResponse<TrialSettingEntity.Values>> saveSettings(@RequestBody(required = false) TrialSettingsService.Request body, AdminActor actor) {
        return noStore(settings.update(actor.operatorId(), actor.ip(), body));
    }

    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE})
    @PostMapping("/api/admin/trials/images")
    public ResponseEntity<ApiResponse<TrialViews.Uploaded>> cover(@RequestParam(value = "file", required = false) MultipartFile file) {
        return created(trials.uploadCover(bytes(file)));
    }

    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE, Permission.TRIAL_REVIEW})
    @GetMapping("/api/admin/trials/{id}")
    public ResponseEntity<ApiResponse<TrialViews.TrialDetail>> detail(@PathVariable String id, AdminActor actor) {
        return noStore(trials.detail(id, TrialService.Actor.operator(actor.operatorId(), actor.ip())));
    }

    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE})
    @PutMapping("/api/admin/trials/{id}")
    public ResponseEntity<ApiResponse<TrialViews.TrialDetail>> update(@PathVariable String id, @RequestBody(required = false) TrialViews.TrialForm body, AdminActor actor) {
        return noStore(trials.update(actor.operatorId(), actor.ip(), id, body));
    }

    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE})
    @GetMapping("/api/admin/trials/{id}/applications")
    public ResponseEntity<ApiResponse<TrialViews.ApplicantPage>> applications(@PathVariable String id, AdminActor actor) {
        return noStore(trials.applicants(id, TrialService.Actor.operator(actor.operatorId(), actor.ip())));
    }

    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE})
    @PostMapping("/api/admin/trials/{id}/select")
    public ResponseEntity<ApiResponse<TrialViews.TrialDetail>> select(@PathVariable String id, @RequestBody(required = false) TrialViews.SelectRequest body,
                                                                      AdminActor actor) {
        return noStore(trials.select(actor.operatorId(), actor.ip(), id, body));
    }

    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE})
    @PostMapping("/api/admin/trials/applications/{id}/recommend")
    public ResponseEntity<Void> recommend(@PathVariable String id, @RequestBody(required = false) TrialViews.RecommendRequest body, AdminActor actor) {
        trials.recommend(TrialService.Actor.operator(actor.operatorId(), actor.ip()), id, body == null ? null : body.recommended());
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE})
    @PostMapping("/api/admin/trials/{id}/photos")
    public ResponseEntity<ApiResponse<TrialViews.Uploaded>> photo(@PathVariable String id, @RequestParam(required = false) String kind,
                                                                  @RequestParam(value = "file", required = false) MultipartFile file, AdminActor actor) {
        return created(trials.uploadPhoto(TrialService.Actor.operator(actor.operatorId(), actor.ip()), id, kind, bytes(file)));
    }

    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE})
    @PostMapping("/api/admin/trials/{id}/stock")
    public ResponseEntity<ApiResponse<TrialViews.TrialDetail>> stock(@PathVariable String id, @RequestBody(required = false) TrialViews.StockRequest body, AdminActor actor) {
        return noStore(trials.stock(actor.operatorId(), actor.ip(), id, body));
    }

    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE})
    @PutMapping("/api/admin/trials/participants/{id}/shipment")
    public ResponseEntity<Void> ship(@PathVariable String id, @RequestBody(required = false) TrialViews.ShipmentRequest body, AdminActor actor) {
        trials.ship(TrialService.Actor.operator(actor.operatorId(), actor.ip()), id, body);
        return ResponseEntity.noContent().build();
    }

    /** 뽑힌 줄 운영자 취소(발송 전에만 — V242 D9) {@code { reason }} → 모집 상세(결제 전이면 금액 다시 셈) */
    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE})
    @PostMapping("/api/admin/trials/participants/{id}/cancel")
    public ResponseEntity<ApiResponse<TrialViews.TrialDetail>> cancelParticipant(@PathVariable String id, @RequestBody(required = false) TrialViews.ReasonRequest body,
                                                                                 AdminActor actor) {
        return noStore(trials.cancelParticipant(actor.operatorId(), actor.ip(), id, body));
    }

    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE, Permission.TRIAL_REVIEW})
    @PostMapping("/api/admin/trials/participants/{id}/returned")
    public ResponseEntity<Void> returned(@PathVariable String id, @RequestBody(required = false) TrialViews.PhotosRequest body, AdminActor actor) {
        trials.confirmReturn(TrialService.Actor.operator(actor.operatorId(), actor.ip()), id, body);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE})
    @PostMapping("/api/admin/trials/{id}/return-shipment")
    public ResponseEntity<ApiResponse<TrialViews.TrialDetail>> returnShipment(@PathVariable String id, @RequestBody(required = false) TrialViews.ShipmentRequest body,
                                                                              AdminActor actor) {
        return noStore(trials.returnShipment(actor.operatorId(), actor.ip(), id, body));
    }

    @RequirePermission(anyOf = {Permission.TRIAL_MANAGE, Permission.AD_MANAGE})
    @PostMapping("/api/admin/trials/participants/{id}/damage-reports")
    public ResponseEntity<ApiResponse<TrialViews.DamageReport>> damage(@PathVariable String id, @RequestBody(required = false) TrialViews.DamageRequest body,
                                                                       AdminActor actor) {
        return created(trials.reportDamage(TrialService.Actor.operator(actor.operatorId(), actor.ip()), id, body));
    }

    @RequirePermission(Permission.TRIAL_REVIEW)
    @PostMapping("/api/admin/trials/damage-reports/{id}/decide")
    public ResponseEntity<ApiResponse<TrialViews.DamageReport>> decide(@PathVariable String id, @RequestBody(required = false) TrialViews.DecideRequest body,
                                                                       AdminActor actor) {
        return noStore(trials.decideDamage(actor.operatorId(), actor.ip(), id, body));
    }

    @RequirePermission(Permission.TRIAL_REVIEW)
    @GetMapping("/api/admin/purchase-proofs/{id}/photo")
    public ResponseEntity<byte[]> proofPhoto(@PathVariable String id, AdminActor actor) {
        PurchaseProofService.Photo photo = proofs.photo(PurchaseProofService.Reviewer.operator(actor.operatorId(), actor.ip()), id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(photo.contentType())).body(photo.bytes());
    }

    @RequirePermission(Permission.TRIAL_REVIEW)
    @PostMapping("/api/admin/purchase-proofs/{id}/approve")
    public ResponseEntity<Void> approveProof(@PathVariable String id, AdminActor actor) {
        proofs.approve(PurchaseProofService.Reviewer.operator(actor.operatorId(), actor.ip()), id);
        return ResponseEntity.noContent().build();
    }

    @RequirePermission(Permission.TRIAL_REVIEW)
    @PostMapping("/api/admin/purchase-proofs/{id}/reject")
    public ResponseEntity<Void> rejectProof(@PathVariable String id, @RequestBody(required = false) TrialViews.ReasonRequest body, AdminActor actor) {
        proofs.reject(PurchaseProofService.Reviewer.operator(actor.operatorId(), actor.ip()), id, body == null ? null : body.reason());
        return ResponseEntity.noContent().build();
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file == null ? null : file.getBytes();
        } catch (IOException e) {
            return null;
        }
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> created(T body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
