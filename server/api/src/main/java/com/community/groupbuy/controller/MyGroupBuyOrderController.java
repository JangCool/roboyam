package com.community.groupbuy.controller;

import static com.community.groupbuy.controller.GroupBuyController.noStore;
import static com.community.groupbuy.controller.GroupBuyController.user;

import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.support.MoneyRequestGuard;
import com.community.groupbuy.dto.request.GroupBuyMemberRequest;
import com.community.groupbuy.dto.response.GroupBuyMemberView;
import com.community.groupbuy.dto.response.MyGroupBuyOrderView;
import com.community.groupbuy.service.MyGroupBuyOrderService;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 내 공구 주문(user-web {@code useGroupBuyOrderApi}) — 로그인 필요(401), 남의 주문 번호는 404 {@code GROUP_BUY_ORDER_NOT_FOUND}. 응답은 {@code Cache-Control: no-store}.
 * 돈이 움직이는 본인 취소·지연 취소·취소/반품 요청은 {@link MoneyRequestGuard}(머리글 {@code Idempotency-Key} 필수 · 요청 제한 {@code REFUND_REQUEST})가 감싼다.
 */
@RestController
@RequestMapping("/api/me/orders")
public class MyGroupBuyOrderController {

    private final MyGroupBuyOrderService service;
    private final MoneyRequestGuard guard;

    public MyGroupBuyOrderController(MyGroupBuyOrderService service, MoneyRequestGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MyGroupBuyOrderView.Page>> list(@RequestParam(defaultValue = "0") int page, Authentication authentication) {
        return noStore(service.list(user(authentication), page));
    }

    @GetMapping("/{orderNo}")
    public ResponseEntity<ApiResponse<MyGroupBuyOrderView.Detail>> detail(@PathVariable String orderNo, Authentication authentication) {
        return noStore(service.detail(user(authentication), orderNo));
    }

    /** 결제 다시 열기 — 선점은 늘지 않는다(결정 41), 남은 선점이 1분 미만이면 409 {@code GROUP_BUY_HOLD_EXPIRED}. 결제창 실패 뒤면 {@code afterFailure: true} */
    @PostMapping("/{orderNo}/payment")
    public ResponseEntity<ApiResponse<GroupBuyMemberView.PaymentStart>> payment(@PathVariable String orderNo, @RequestBody(required = false) Reopen body,
                                                                               Authentication authentication) {
        return noStore(service.reopen(user(authentication), orderNo, body != null && Boolean.TRUE.equals(body.afterFailure())));
    }

    public record Reopen(Boolean afterFailure) {
    }

    @GetMapping("/{orderNo}/address")
    public ResponseEntity<ApiResponse<MyGroupBuyOrderView.Address>> address(@PathVariable String orderNo, Authentication authentication) {
        return noStore(service.fullAddress(user(authentication), orderNo));
    }

    /** 배송지 수정 — 기한이 지나면 409 {@code GROUP_BUY_ADDRESS_LOCKED}(결정 30) */
    @PutMapping("/{orderNo}/address")
    public ResponseEntity<ApiResponse<MyGroupBuyOrderView.Detail>> updateAddress(@PathVariable String orderNo, @RequestBody(required = false) GroupBuyMemberRequest.Address body,
                                                                                Authentication authentication) {
        return noStore(service.updateAddress(user(authentication), orderNo, body));
    }

    @PostMapping("/{orderNo}/cancel/preview")
    public ResponseEntity<ApiResponse<MyGroupBuyOrderView.CancelPreview>> cancelPreview(@PathVariable String orderNo,
                                                                                       @RequestBody(required = false) GroupBuyMemberRequest.Cancel body,
                                                                                       Authentication authentication) {
        return noStore(service.cancelPreview(user(authentication), orderNo, body));
    }

    /** 본인 즉시 취소 — 줄이 비면 주문 전체. 본 상품을 모두 빼고 추가상품만 남기면 409 {@code GROUP_BUY_ADDON_ONLY} */
    @PostMapping("/{orderNo}/cancel")
    public ResponseEntity<ApiResponse<Object>> cancel(@PathVariable String orderNo, @RequestHeader(value = MoneyRequestGuard.HEADER, required = false) String key,
                                                      @RequestBody(required = false) GroupBuyMemberRequest.Cancel body, Authentication authentication) {
        long userId = user(authentication);
        GroupBuyMemberRequest.Cancel request = body == null ? new GroupBuyMemberRequest.Cancel(null) : body;
        return guard.handle(userId, key, RequestAction.REFUND_REQUEST, new Keyed("cancel:" + orderNo, request), () -> service.cancel(userId, orderNo, request));
    }

    /** 발송 지연 공지 뒤 즉시 전액 취소(결정 52) */
    @PostMapping("/{orderNo}/delay-cancel")
    public ResponseEntity<ApiResponse<Object>> delayCancel(@PathVariable String orderNo, @RequestHeader(value = MoneyRequestGuard.HEADER, required = false) String key,
                                                           Authentication authentication) {
        long userId = user(authentication);
        return guard.handle(userId, key, RequestAction.REFUND_REQUEST, new Keyed("delay-cancel:" + orderNo, null), () -> service.delayCancel(userId, orderNo));
    }

    @PostMapping("/{orderNo}/requests/preview")
    public ResponseEntity<ApiResponse<MyGroupBuyOrderView.ReturnPreview>> requestPreview(@PathVariable String orderNo,
                                                                                        @RequestBody(required = false) GroupBuyMemberRequest.ReturnPreview body,
                                                                                        Authentication authentication) {
        return noStore(service.returnPreview(user(authentication), orderNo, body));
    }

    /** 취소·반품 요청 — 반품 결과가 마이너스면 부족분 결제창 대상이 함께 온다(결정 68). 409 {@code GROUP_BUY_REQUEST_OPEN}·{@code _RETURN_WINDOW_CLOSED}·{@code _AMOUNT_CHANGED} */
    @PostMapping("/{orderNo}/requests")
    public ResponseEntity<ApiResponse<Object>> createRequest(@PathVariable String orderNo, @RequestHeader(value = MoneyRequestGuard.HEADER, required = false) String key,
                                                             @RequestBody(required = false) GroupBuyMemberRequest.CreateRequest body, Authentication authentication) {
        long userId = user(authentication);
        return guard.handle(userId, key, RequestAction.REFUND_REQUEST, new Keyed("request:" + orderNo, body), () -> service.createRequest(userId, orderNo, body));
    }

    @PostMapping("/{orderNo}/requests/{requestId}/payment")
    public ResponseEntity<ApiResponse<GroupBuyMemberView.PaymentStart>> requestPayment(@PathVariable String orderNo, @PathVariable String requestId,
                                                                                      Authentication authentication) {
        return noStore(service.reopenFee(user(authentication), orderNo, requestId));
    }

    @PostMapping("/{orderNo}/requests/{requestId}/withdraw")
    public ResponseEntity<ApiResponse<MyGroupBuyOrderView.Detail>> withdraw(@PathVariable String orderNo, @PathVariable String requestId, Authentication authentication) {
        return noStore(service.withdraw(user(authentication), orderNo, requestId));
    }

    /** 단품 공급 불가 「나머지만 받기 / 전체 취소」(결정 53) */
    @PostMapping("/{orderNo}/supply-choice")
    public ResponseEntity<ApiResponse<MyGroupBuyOrderView.Detail>> supplyChoice(@PathVariable String orderNo,
                                                                               @RequestBody(required = false) GroupBuyMemberRequest.SupplyChoice body,
                                                                               Authentication authentication) {
        return noStore(service.supplyChoice(user(authentication), orderNo, body));
    }

    /** 하자·오배송 사진(multipart {@code file}, JPEG·PNG·WebP 5MB) — 요청에 붙이기 전 먼저 올린다 */
    @PostMapping("/{orderNo}/photos")
    public ResponseEntity<ApiResponse<MyGroupBuyOrderView.Photo>> photo(@PathVariable String orderNo, @RequestParam(value = "file", required = false) MultipartFile file,
                                                                       Authentication authentication) {
        return noStore(service.photo(user(authentication), orderNo, file));
    }

    /** 멱등 해시 — 같은 키를 다른 주문·다른 일에 쓰면 「다른 본문」 */
    private record Keyed(String target, Object body) {
    }
}
