package com.community.bizapplication.controller;

import com.community.auth.dto.AuthUser;
import com.community.bizapplication.dto.BizMemberView;
import com.community.bizapplication.dto.request.BizAdApplyRequest;
import com.community.bizapplication.dto.request.BizCreativeRequest;
import com.community.bizapplication.dto.request.BizDealApplyRequest;
import com.community.bizapplication.dto.request.BizReasonRequest;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.service.BizAdImageService;
import com.community.bizapplication.service.MyBizApplicationService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
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
 * 사업자 센터 — 신청 건(광고·핫딜)(user-web {@code useBusinessApi} 의 신청 건 부분, docs/features/biz-application-contract.md). 로그인 필요(401), 응답은 {@code Cache-Control: no-store}.
 * 자격 409 {@code BUSINESS_NOT_APPROVED}·{@code BUSINESS_RE_REVIEW}, 자리 409 {@code BIZ_SLOT_FULL}, 상태 409 {@code BIZ_APPLICATION_STATE_CHANGED}, 남의 건 404
 * {@code BIZ_APPLICATION_NOT_FOUND}. 신원(/me·/files)은 {@code MyBusinessController}.
 */
@RestController
@RequestMapping("/api/business")
public class MyBizApplicationController {

    private final MyBizApplicationService service;
    private final BizAdImageService images;

    public MyBizApplicationController(MyBizApplicationService service, BizAdImageService images) {
        this.service = service;
        this.images = images;
    }

    @GetMapping("/ad-products")
    public ResponseEntity<ApiResponse<List<BizMemberView.AdProduct>>> adProducts(Authentication authentication) {
        user(authentication);
        return noStore(service.adProducts());
    }

    @GetMapping("/deal-products")
    public ResponseEntity<ApiResponse<BizMemberView.DealProduct>> dealProducts(Authentication authentication) {
        user(authentication);
        return noStore(service.dealProduct());
    }

    /** 날짜별 남은 칸 — {@code slot} WIDE·STRIP·SQUARE·SMALL·TEXT_LINE·HOME_TOP·CATEGORY_TOP, 92일까지 */
    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<List<BizMemberView.AvailabilityDay>>> availability(@RequestParam(required = false) String slot,
                                                                                          @RequestParam(required = false) String from,
                                                                                          @RequestParam(required = false) String to, Authentication authentication) {
        user(authentication);
        return noStore(service.availability(slot, from, to));
    }

    @GetMapping("/deal-link-check")
    public ResponseEntity<ApiResponse<BizMemberView.DealLinkCheck>> dealLinkCheck(@RequestParam(required = false) String url, Authentication authentication) {
        user(authentication);
        return noStore(service.checkLink(url));
    }

    /** 광고 이미지(multipart {@code file}, JPG·PNG·GIF·WEBP 10MB) — 신청·소재 바꾸기가 {@code imageFileId} 로 붙인다 */
    @PostMapping("/ad-images")
    public ResponseEntity<ApiResponse<BizMemberView.File>> adImage(@RequestParam(value = "file", required = false) MultipartFile file, Authentication authentication) {
        return noStore(images.upload(user(authentication), file));
    }

    @PostMapping("/applications/ads")
    public ResponseEntity<ApiResponse<BizMemberView.Created>> submitAd(@RequestBody(required = false) BizAdApplyRequest body, Authentication authentication) {
        return created(service.submitAd(user(authentication), body));
    }

    @PostMapping("/applications/deals")
    public ResponseEntity<ApiResponse<BizMemberView.Created>> submitDeal(@RequestBody(required = false) BizDealApplyRequest body, Authentication authentication) {
        return created(service.submitDeal(user(authentication), body));
    }

    @GetMapping("/applications")
    public ResponseEntity<ApiResponse<BizMemberView.Page>> list(@RequestParam(required = false) String bucket, Authentication authentication) {
        return noStore(service.list(user(authentication), bucket));
    }

    @GetMapping("/applications/{id}")
    public ResponseEntity<ApiResponse<BizMemberView.Detail>> detail(@PathVariable String id, Authentication authentication) {
        return noStore(service.detail(user(authentication), parseId(id)));
    }

    /** 결제 전 취소(심사 대기·결제 대기·입금 대기 — 입금 기록 전) */
    @PostMapping("/applications/{id}/cancel")
    public ResponseEntity<ApiResponse<BizMemberView.Detail>> cancel(@PathVariable String id, Authentication authentication) {
        return noStore(service.cancel(user(authentication), parseId(id)));
    }

    /** 소재 바꾸기 — 승인 뒤 결제 전이면 다시 심사 대기 */
    @PutMapping("/applications/{id}/creative")
    public ResponseEntity<ApiResponse<BizMemberView.Detail>> creative(@PathVariable String id, @RequestBody(required = false) BizCreativeRequest body,
                                                                      Authentication authentication) {
        return noStore(service.changeCreative(user(authentication), parseId(id), body));
    }

    /** 온라인 결제 시작 — 결제창 정보(완료는 결제 모듈의 완료 API) */
    @PostMapping("/applications/{id}/payment")
    public ResponseEntity<ApiResponse<BizMemberView.PaymentStart>> payment(@PathVariable String id, Authentication authentication) {
        return noStore(service.startPayment(user(authentication), parseId(id)));
    }

    /** 취소·환불 요청(예약) — 시작 전 전액 자동, 진행 중 운영팀 심사 */
    @PostMapping("/applications/{id}/refund-request")
    public ResponseEntity<ApiResponse<BizMemberView.Detail>> refundRequest(@PathVariable String id, @RequestBody(required = false) BizReasonRequest body,
                                                                           Authentication authentication) {
        return noStore(service.requestRefund(user(authentication), parseId(id), body == null ? null : body.reason()));
    }

    private static long user(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser principal) {
            return principal.id();
        }
        throw new CommonException(ErrorCode.UNAUTHENTICATED);
    }

    private static long parseId(String text) {
        try {
            return Long.parseLong(text == null ? "" : text.strip());
        } catch (NumberFormatException e) {
            throw BizApplicationException.notFound();
        }
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }

    private static <T> ResponseEntity<ApiResponse<T>> created(T body) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
