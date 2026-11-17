package com.community.trial.controller;

import com.community.trial.dto.TrialViews;
import com.community.trial.service.TrialBusinessService;
import com.community.zconfiguration.response.ApiResponse;
import com.community.zconfiguration.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
 * 광고주 화면 체험단 API(사업자 센터 {@code /business/trials} — trial-group-contract 「구현 결정」 8). 로그인 필요(401), 광고주가 아니면 403 {@code TRIAL_NOT_ADVERTISER},
 * 남의 모집·초안은 404 {@code TRIAL_NOT_FOUND} — 다만 목록·상세는 결제 대상 선결제 초안(견적 &gt; 0 · 심사·결제·입금 대기 · 결제 끝 · 만료 · 반려)을 {@code status} DRAFT(공개 전)로 싣는다. 회원 주소·연락처는 동의 범위(광고주가 보내는 모집의 뽑힌 줄)만 싣고, 실리면 개인정보 제공 감사 기록.
 *
 * <ul>
 *   <li>{@code GET /api/business/trials} → 내 모집 목록 · {@code GET /api/business/trials/{id}} → {@code { trial, applicants }}</li>
 *   <li>{@code POST /api/business/trials/applications/{id}/recommend} {@code { recommended }} → 204(후보 추천 — 최종 선정은 운영자)</li>
 *   <li>{@code POST /api/business/trials/{id}/photos?kind=PRE_SHIP|RETURN|DAMAGE}(multipart {@code file}) → 201 {@code { id, kind, key, url }}</li>
 *   <li>{@code PUT /api/business/trials/participants/{id}/shipment} {@code { carrier, trackingNo, photoIds }} → 204(발송 전 사진 필수 — 400 {@code fields.photoIds})</li>
 *   <li>{@code POST /api/business/trials/participants/{id}/returned} {@code { photoIds }} → 204(반납 사진 필수)</li>
 *   <li>{@code POST /api/business/trials/participants/{id}/damage-reports} {@code { description, photoIds }} → 201 파손 신고</li>
 * </ul>
 * 보내는 쪽이 운영자인 모집의 발송·반납·파손은 409 {@code TRIAL_WRONG_SHIPPER}. 사진은 비공개(V243) — {@code GET /api/business/trials/photos/{id}} 바이트(올리기 응답 {@code url} 이 이 주소, {@code key} null).
 * 사업자 승인·광고 서비스 승인·광고주 보관 아님이 아니면 403 {@code TRIAL_ADVERTISER_BLOCKED}(점검 D5).
 */
@RestController
@RequestMapping("/api/business/trials")
public class BusinessTrialController {

    private final TrialBusinessService business;
    private final ClientIpResolver clientIps;

    public BusinessTrialController(TrialBusinessService business, ClientIpResolver clientIps) {
        this.business = business;
        this.clientIps = clientIps;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TrialViews.TrialRow>>> list(Authentication authentication) {
        return TrialController.noStore(business.list(TrialController.userId(authentication)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TrialViews.BusinessTrialDetail>> detail(@PathVariable String id, Authentication authentication, HttpServletRequest request) {
        return TrialController.noStore(business.detail(TrialController.userId(authentication), clientIps.resolve(request), id));
    }

    @PostMapping("/applications/{id}/recommend")
    public ResponseEntity<Void> recommend(@PathVariable String id, @RequestBody(required = false) TrialViews.RecommendRequest body, Authentication authentication) {
        business.recommend(TrialController.userId(authentication), id, body == null ? null : body.recommended());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/photos")
    public ResponseEntity<ApiResponse<TrialViews.Uploaded>> photo(@PathVariable String id, @RequestParam(required = false) String kind,
                                                                  @RequestParam(value = "file", required = false) MultipartFile file, Authentication authentication) {
        long userId = TrialController.userId(authentication);
        byte[] data;
        try {
            data = file == null ? null : file.getBytes();
        } catch (IOException e) {
            data = null;
        }
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(business.uploadPhoto(userId, id, kind, data)));
    }

    /** 사진 보기(V243 — 비공개) → 바이트 · {@code no-store} — 자기 모집의 붙은 사진 · 자기가 올린 붙기 전 사진(그 밖 404 {@code TRIAL_PHOTO_NOT_FOUND}) · 410 {@code TRIAL_PHOTO_PURGED} */
    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> photoBytes(@PathVariable String id, Authentication authentication) {
        return TrialController.photoResponse(business.photo(TrialController.userId(authentication), id));
    }

    @PutMapping("/participants/{id}/shipment")
    public ResponseEntity<Void> ship(@PathVariable String id, @RequestBody(required = false) TrialViews.ShipmentRequest body, Authentication authentication,
                                     HttpServletRequest request) {
        business.ship(TrialController.userId(authentication), clientIps.resolve(request), id, body);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/participants/{id}/returned")
    public ResponseEntity<Void> returned(@PathVariable String id, @RequestBody(required = false) TrialViews.PhotosRequest body, Authentication authentication,
                                         HttpServletRequest request) {
        business.confirmReturn(TrialController.userId(authentication), clientIps.resolve(request), id, body);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/participants/{id}/damage-reports")
    public ResponseEntity<ApiResponse<TrialViews.DamageReport>> damage(@PathVariable String id, @RequestBody(required = false) TrialViews.DamageRequest body,
                                                                       Authentication authentication, HttpServletRequest request) {
        TrialViews.DamageReport created = business.reportDamage(TrialController.userId(authentication), clientIps.resolve(request), id, body);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(created));
    }
}
