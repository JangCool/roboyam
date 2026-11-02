package com.community.bizapplication.controller;

import com.community.auth.dto.AuthUser;
import com.community.ad.dto.AdStatsView;
import com.community.bizapplication.dto.BizAdStatsView;
import com.community.bizapplication.dto.BizMemberView;
import com.community.bizapplication.dto.request.BizAdApplyRequest;
import com.community.bizapplication.dto.request.BizCreativeRequest;
import com.community.bizapplication.dto.request.BizDealApplyRequest;
import com.community.bizapplication.dto.request.BizReasonRequest;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.service.BizAdImageService;
import com.community.bizapplication.service.MyBizAdStatsService;
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
    private final MyBizAdStatsService stats;

    public MyBizApplicationController(MyBizApplicationService service, BizAdImageService images, MyBizAdStatsService stats) {
        this.service = service;
        this.images = images;
        this.stats = stats;
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

    /** 날짜별 남은 칸 — {@code slot} WIDE·STRIP·SQUARE·SMALL·WING·TEXT_LINE·HOME_TOP·CATEGORY_TOP, 92일까지 */
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

    /**
     * 한 건의 광고 성과(ad-performance-contract 규칙 2~7) — {@code from}·{@code to} KST 날짜(끝 포함, 빠지면 시작 날짜 ~ min(끝 날짜, 오늘)), 형식이 틀리거나 {@code from > to} 면
     * 400 {@code fields.from}. 남의 건·없는 건 404, 노출 자리 줄이 없는 본인 건은 빈 결과
     */
    @GetMapping("/applications/{id}/stats")
    public ResponseEntity<ApiResponse<AdStatsView.Application>> stats(@PathVariable String id, @RequestParam(required = false) String from,
                                                                      @RequestParam(required = false) String to, Authentication authentication) {
        return noStore(stats.application(user(authentication), parseId(id), from, to));
    }

    /** 사업자 센터 「진행 중 광고」 — 진행 중·정지 중·내려진(긴급·사업자 정지) 건 최대 5, 최근 7일(규칙 8) */
    @GetMapping("/ads/stats-summary")
    public ResponseEntity<ApiResponse<BizAdStatsView.Summary>> statsSummary(Authentication authentication) {
        return noStore(stats.summary(user(authentication)));
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

    /** 취소·환불 요청 — 시작 전 전액 자동(수수료 없음), 진행 중·정지 중은 중단과 같은 처리(바로 내림 + 일할 − 수수료 심사, V127) */
    @PostMapping("/applications/{id}/refund-request")
    public ResponseEntity<ApiResponse<BizMemberView.Detail>> refundRequest(@PathVariable String id, @RequestBody(required = false) BizReasonRequest body,
                                                                           Authentication authentication) {
        return noStore(service.requestRefund(user(authentication), parseId(id), body == null ? null : body.reason()));
    }

    /** 일시 정지(진행 중 — 바로 멈춤 · 자리 유지 · 한 건에 3번, 넘으면 409 {@code BIZ_PAUSE_LIMIT}) → 상세 */
    @PostMapping("/applications/{id}/pause")
    public ResponseEntity<ApiResponse<BizMemberView.Detail>> pause(@PathVariable String id, Authentication authentication) {
        return noStore(service.pause(user(authentication), parseId(id)));
    }

    /** 다시 켜기(정지 중 · 끝 전 — 승인 없이 바로, 끝 시각 그대로) → 상세 */
    @PostMapping("/applications/{id}/resume")
    public ResponseEntity<ApiResponse<BizMemberView.Detail>> resume(@PathVariable String id, Authentication authentication) {
        return noStore(service.resume(user(authentication), parseId(id)));
    }

    /** 중단(진행 중·정지 중 — 바로 내림 · 되돌리기 없음 · 일할 − 수수료 환불 심사) {@code { reason 1~300 }} → 상세 */
    @PostMapping("/applications/{id}/stop")
    public ResponseEntity<ApiResponse<BizMemberView.Detail>> stop(@PathVariable String id, @RequestBody(required = false) BizReasonRequest body,
                                                                  Authentication authentication) {
        return noStore(service.stop(user(authentication), parseId(id), body == null ? null : body.reason()));
    }

    /** 지금 환불 규정 버전 + 중도 해지 수수료율(신청 화면 동의 문구) */
    @GetMapping("/refund-policy")
    public ResponseEntity<ApiResponse<BizMemberView.RefundPolicy>> refundPolicy(Authentication authentication) {
        user(authentication);
        return noStore(service.refundPolicy());
    }

    /** 사업자 종료([품절로 종료] — 환불 없음 · 노출도 멈춤) → 새 「게시된 글」. 이미 끝남 409 {@code DEAL_ENDED}, 글 없음 409 {@code BIZ_APPLICATION_STATE_CHANGED} */
    @PostMapping("/applications/{id}/deal/end")
    public ResponseEntity<ApiResponse<com.community.bizapplication.dto.response.BizDealPostView>> endDeal(@PathVariable String id, Authentication authentication) {
        return noStore(service.endDeal(user(authentication), parseId(id)));
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
