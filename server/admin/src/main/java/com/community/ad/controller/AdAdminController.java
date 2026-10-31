package com.community.ad.controller;

import com.community.ad.dto.AdAdminRequest;
import com.community.ad.dto.AdAdminView;
import com.community.ad.service.AdAdminService;
import com.community.ad.service.AdAdvertiserService;
import com.community.bizapplication.dto.BizAdminView;
import com.community.permission.annotation.RequirePermission;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 광고 일정표(admin-web {@code useAdApi} — {@code /ads}, docs/features/ad-contract.md). 조회 {@code ad.read}(핫딜 노출 자리 탭 포함) · 운영자 등록·광고주·계약서·이미지 {@code ad.manage} ·
 * 긴급 내림 {@code ad.takedown} · 되돌리기 {@code ad.restore} · 광고주 연락처 평문 {@code ad.contact.reveal}. 운영자 등록 건의 심사·결제(승인 → 계약서 사본 + 입금 확인 → 확정)는
 * 신청 건 API({@code /api/admin/biz-applications/**})가 한다. 응답은 {@code Cache-Control: no-store}. 자리가 없으면 409 {@code BIZ_SLOT_FULL}, 상태가 이미 바뀌었으면 409
 * {@code BIZ_APPLICATION_STATE_CHANGED}.
 */
@RestController
@RequestMapping("/api/admin/ads")
public class AdAdminController {

    private final AdAdminService service;

    public AdAdminController(AdAdminService service) {
        this.service = service;
    }

    /** 그 달(YYYY-MM) 날짜별 남은 칸 + 유형 요약 + 그 달에 걸친 장부 건 */
    @RequirePermission(Permission.AD_READ)
    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<AdAdminView.Calendar>> calendar(@RequestParam(required = false) String type, @RequestParam(required = false) String month) {
        return ok(service.calendar(type, month));
    }

    @RequirePermission(Permission.AD_READ)
    @GetMapping
    public ResponseEntity<ApiResponse<AdAdminView.Page>> list(@RequestParam(required = false) String type, @RequestParam(required = false) String status,
                                                              @RequestParam(required = false) String q, @RequestParam(required = false) Integer page,
                                                              @RequestParam(required = false) Integer size) {
        return ok(service.list(type, status, q, page, size));
    }

    /** 기간 판정(새 등록) — 남은 칸 · 꽉 찬 날 · 요금표 계산 */
    @RequirePermission(Permission.AD_READ)
    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<AdAdminView.Availability>> availability(@RequestParam(required = false) String type, @RequestParam(required = false) String startAt,
                                                                              @RequestParam(required = false) String endAt) {
        return ok(service.availability(type, startAt, endAt));
    }

    /** 광고주 + 광고주와 연결되지 않은 승인 사업자 검색 */
    @RequirePermission(Permission.AD_MANAGE)
    @GetMapping("/advertisers")
    public ResponseEntity<ApiResponse<List<AdAdvertiserService.Option>>> advertisers(@RequestParam(required = false) String q) {
        return ok(service.advertisers(q));
    }

    @RequirePermission(Permission.AD_MANAGE)
    @PostMapping("/advertisers")
    public ResponseEntity<ApiResponse<AdAdvertiserService.Option>> createAdvertiser(@RequestBody(required = false) AdAdminRequest.AdvertiserCreate body, AdminActor actor) {
        return ok(service.createAdvertiser(actor, body));
    }

    /** 광고주 연락처 평문 — 사유 5~200자 · 열람 기록 · 화면은 {@code hideAt} 뒤 다시 가린다 */
    @RequirePermission(Permission.AD_CONTACT_REVEAL)
    @PostMapping("/advertisers/{id}/reveal")
    public ResponseEntity<ApiResponse<AdAdvertiserService.Revealed>> reveal(@PathVariable String id, @RequestBody(required = false) AdAdminRequest.Reveal body,
                                                                            AdminActor actor) {
        return ok(service.reveal(actor, id, body));
    }

    /** 계약서 사본 미리 올리기(운영자 등록 전) — multipart {@code file} 한 장(PDF·JPG·PNG 10MB) */
    @RequirePermission(Permission.AD_MANAGE)
    @PostMapping("/contract-files")
    public ResponseEntity<ApiResponse<BizAdminView.Contract>> contractFile(@RequestParam(value = "file", required = false) MultipartFile file, AdminActor actor) {
        return ok(service.uploadContract(actor, file));
    }

    /** 배너 이미지 올리기 — multipart {@code file}(PNG·JPG·GIF·WEBP 2MB) */
    @RequirePermission(Permission.AD_MANAGE)
    @PostMapping("/images")
    public ResponseEntity<ApiResponse<AdAdminView.Image>> image(@RequestParam(value = "file", required = false) MultipartFile file, AdminActor actor) {
        return ok(service.uploadImage(actor, file));
    }

    /** 운영자 등록 → 심사 대기 신청 건(광고 심사 큐) */
    @RequirePermission(Permission.AD_MANAGE)
    @PostMapping("/manual")
    public ResponseEntity<ApiResponse<AdAdminView.Detail>> manual(@RequestBody(required = false) AdAdminRequest.Manual body, AdminActor actor) {
        return ok(service.createManual(actor, body));
    }

    @RequirePermission(Permission.AD_READ)
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdAdminView.Detail>> detail(@PathVariable String id, AdminActor actor) {
        return ok(service.detail(actor, id));
    }

    @RequirePermission(Permission.AD_TAKEDOWN)
    @PostMapping("/{id}/takedown")
    public ResponseEntity<ApiResponse<AdAdminView.Detail>> takedown(@PathVariable String id, @RequestBody(required = false) AdAdminRequest.Takedown body,
                                                                    AdminActor actor) {
        return ok(service.takedown(actor, id, body));
    }

    @RequirePermission(Permission.AD_RESTORE)
    @PostMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<AdAdminView.Detail>> restore(@PathVariable String id, @RequestBody(required = false) AdAdminRequest.Restore body, AdminActor actor) {
        return ok(service.restore(actor, id, body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
