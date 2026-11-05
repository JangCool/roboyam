package com.community.emoticon.controller;

import com.community.emoticon.dto.request.EmoticonOfficialPackSaveRequest;
import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.dto.request.EmoticonSettingsSaveRequest;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.service.EmoticonPackAdminService;
import com.community.emoticon.service.EmoticonPayoutService;
import com.community.emoticon.service.EmoticonReviewService;
import com.community.emoticon.service.EmoticonSettingsService;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 관리자 로보티콘 API(docs/features/emoticon-contract.md S1 — admin-web {@code useEmoticonAdminApi.ts} 머리말의 심사·팩·운영 팩·설정·작가 요약). 회원·작가 정보가 실려 {@code no-store}.
 * 권한: 조회 {@code emoticon.read} · 승인(가격 단계)·반려·판매 중단·다시 판매·가격 바꾸기·운영 팩·그림 올리기 {@code emoticon.review} · 설정 저장 {@code emoticon.settings} ·
 * 작가 정산 계좌 원문 보기 {@code emoticon.payout.pii}(S2). 판매 내역({@code emoticon.sales})·정산({@code emoticon.settlement})은 S3·S4.
 */
@RestController
@RequestMapping("/api/admin/emoticons")
public class EmoticonAdminController {

    private final EmoticonReviewService reviews;
    private final EmoticonPackAdminService packs;
    private final EmoticonSettingsService settings;
    private final EmoticonPayoutService payouts;

    public EmoticonAdminController(EmoticonReviewService reviews, EmoticonPackAdminService packs, EmoticonSettingsService settings, EmoticonPayoutService payouts) {
        this.reviews = reviews;
        this.packs = packs;
        this.settings = settings;
        this.payouts = payouts;
    }

    /** 심사 목록 — {@code status=PENDING|UPDATE|REJECTED|ALL}(없거나 모르면 PENDING) + 탭 숫자 */
    @RequirePermission(Permission.EMOTICON_READ)
    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<EmoticonAdminView.ReviewList>> reviews(@RequestParam(required = false) String status) {
        return noStore(reviews.list(status));
    }

    @RequirePermission(Permission.EMOTICON_READ)
    @GetMapping("/reviews/{id}")
    public ResponseEntity<ApiResponse<EmoticonAdminView.ReviewDetail>> review(@PathVariable String id) {
        return noStore(reviews.detail(id));
    }

    /** 승인 {@code { price }} — 0(무료) 또는 설정의 가격 단계(유료 조건 — {@code 400 fields.price}) · 204 */
    @RequirePermission(Permission.EMOTICON_REVIEW)
    @PostMapping("/reviews/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable String id, @RequestBody(required = false) EmoticonReviewDecisionRequest body, AdminActor actor) {
        reviews.approve(actor.operatorId(), actor.ip(), id, body);
        return ResponseEntity.noContent().build();
    }

    /** 반려 {@code { reason }} — 사유는 작가에게 그대로 간다 · 204 */
    @RequirePermission(Permission.EMOTICON_REVIEW)
    @PostMapping("/reviews/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable String id, @RequestBody(required = false) EmoticonReviewDecisionRequest body, AdminActor actor) {
        reviews.reject(actor.operatorId(), actor.ip(), id, body);
        return ResponseEntity.noContent().build();
    }

    /** 팩 목록 — {@code source}·{@code status}·{@code q}(이름·작가 닉네임·태그)·{@code creator}(회원 번호) */
    @RequirePermission(Permission.EMOTICON_READ)
    @GetMapping("/packs")
    public ResponseEntity<ApiResponse<EmoticonAdminView.PackList>> packs(@RequestParam(required = false) String source, @RequestParam(required = false) String status,
                                                                         @RequestParam(required = false) String q, @RequestParam(required = false) String creator) {
        return noStore(packs.list(source, status, q, creator));
    }

    @RequirePermission(Permission.EMOTICON_READ)
    @GetMapping("/packs/{id}")
    public ResponseEntity<ApiResponse<EmoticonAdminView.PackDetail>> pack(@PathVariable String id) {
        return noStore(packs.detail(id));
    }

    @RequirePermission(Permission.EMOTICON_REVIEW)
    @PostMapping("/packs/{id}/suspend")
    public ResponseEntity<ApiResponse<EmoticonAdminView.PackDetail>> suspend(@PathVariable String id, @RequestBody(required = false) EmoticonReviewDecisionRequest body,
                                                                             AdminActor actor) {
        return noStore(packs.suspend(actor.operatorId(), actor.ip(), id, body == null ? null : body.reason()));
    }

    /** 판매 중 작가 팩 가격 바꾸기 {@code { price, reason }}(유료↔무료 · 단계 — 사유 5~200자, 작가 알림) */
    @RequirePermission(Permission.EMOTICON_REVIEW)
    @PostMapping("/packs/{id}/price")
    public ResponseEntity<ApiResponse<EmoticonAdminView.PackDetail>> price(@PathVariable String id, @RequestBody(required = false) EmoticonReviewDecisionRequest body,
                                                                           AdminActor actor) {
        return noStore(packs.changePrice(actor.operatorId(), actor.ip(), id, body));
    }

    @RequirePermission(Permission.EMOTICON_REVIEW)
    @PostMapping("/packs/{id}/resume")
    public ResponseEntity<ApiResponse<EmoticonAdminView.PackDetail>> resume(@PathVariable String id, AdminActor actor) {
        return noStore(packs.resume(actor.operatorId(), actor.ip(), id));
    }

    /** 운영 팩 만들기 — 심사 없이 판매 중 · 201 */
    @RequirePermission(Permission.EMOTICON_REVIEW)
    @PostMapping("/packs")
    public ResponseEntity<ApiResponse<EmoticonAdminView.PackDetail>> createOfficial(@RequestBody(required = false) EmoticonOfficialPackSaveRequest body, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(packs.createOfficial(actor.operatorId(), actor.ip(), body)));
    }

    /** 운영 팩 고치기 — 새 판이 바로 상점 판(작가 팩은 409 EMOTICON_PACK_NOT_OFFICIAL) */
    @RequirePermission(Permission.EMOTICON_REVIEW)
    @PutMapping("/packs/{id}")
    public ResponseEntity<ApiResponse<EmoticonAdminView.PackDetail>> updateOfficial(@PathVariable String id, @RequestBody(required = false) EmoticonOfficialPackSaveRequest body,
                                                                                    AdminActor actor) {
        return noStore(packs.updateOfficial(actor.operatorId(), actor.ip(), id, body));
    }

    /** 운영 팩 로보티콘 그림 올리기(multipart {@code file}) — 201 · {@code 400 fields.file}(이유 코드) */
    @RequirePermission(Permission.EMOTICON_REVIEW)
    @PostMapping("/uploads")
    public ResponseEntity<ApiResponse<EmoticonAdminView.ItemView>> upload(@RequestParam(value = "file", required = false) MultipartFile file, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(packs.uploadItem(actor.operatorId(), file)));
    }

    /** 운영 팩 대표 이미지 올리기(multipart {@code file}) — 201 · {@code 400 fields.cover}(이유 코드) */
    @RequirePermission(Permission.EMOTICON_REVIEW)
    @PostMapping("/uploads/cover")
    public ResponseEntity<ApiResponse<EmoticonAdminView.CoverView>> uploadCover(@RequestParam(value = "file", required = false) MultipartFile file, AdminActor actor) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(packs.uploadCover(actor.operatorId(), file)));
    }

    @RequirePermission(Permission.EMOTICON_READ)
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<EmoticonAdminView.SettingsView>> settings() {
        return noStore(settings.adminView());
    }

    @RequirePermission(Permission.EMOTICON_SETTINGS)
    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<EmoticonAdminView.SettingsView>> saveSettings(@RequestBody(required = false) EmoticonSettingsSaveRequest body, AdminActor actor) {
        return noStore(settings.save(actor.operatorId(), actor.ip(), body));
    }

    /** 회원 상세의 작가 한 줄 — 작가가 아니면 데이터 없음({@code data} 칸이 빠진다 = 화면의 null) */
    @RequirePermission(Permission.EMOTICON_READ)
    @GetMapping("/creators/{memberNo}")
    public ResponseEntity<ApiResponse<EmoticonAdminView.CreatorSummary>> creator(@PathVariable String memberNo) {
        return noStore(packs.creator(memberNo));
    }

    /** 작가 정산 계좌 원문 보기 {@code { reason }} — 사유 5~200자 · 감사 뒤에만 값 · 등록 전이면 404 {@code EMOTICON_PAYOUT_ACCOUNT_NOT_FOUND} */
    @RequirePermission(Permission.EMOTICON_PAYOUT_PII)
    @PostMapping("/creators/{memberNo}/payout-account/reveal")
    public ResponseEntity<ApiResponse<EmoticonAdminView.PayoutAccountReveal>> revealPayout(@PathVariable String memberNo,
                                                                                           @RequestBody(required = false) EmoticonReviewDecisionRequest body, AdminActor actor) {
        return noStore(payouts.reveal(actor.operatorId(), actor.ip(), memberNo, body == null ? null : body.reason()));
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
