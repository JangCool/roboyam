package com.community.groupbuy.controller;

import com.community.auth.dto.AuthUser;
import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.support.MoneyRequestGuard;
import com.community.groupbuy.dto.request.GroupBuyMemberRequest;
import com.community.groupbuy.dto.response.GroupBuyMemberView;
import com.community.groupbuy.service.GroupBuyMemberService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공동구매(공개 사이트, user-web {@code useGroupBuyApi}) — 목록·카테고리·상세·견적은 로그인 없이, 주문·관심 신청은 로그인(401). 응답은 {@code Cache-Control: no-store}(남은 수량·내 주문이 실린다).
 * 주문은 {@link MoneyRequestGuard}(머리글 {@code Idempotency-Key} 필수 · 요청 제한 {@code GROUPBUY_JOIN} · 같은 키·다른 본문 409 {@code IDEMPOTENCY_KEY_REUSED} — P26)가 감싼다.
 */
@RestController
@RequestMapping("/api/group-buys")
public class GroupBuyController {

    private final GroupBuyMemberService service;
    private final MoneyRequestGuard guard;

    public GroupBuyController(GroupBuyMemberService service, MoneyRequestGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    /** 탭 {@code phase} UPCOMING·ONGOING(기본)·ENDED, 카테고리(위 단이면 아래 단 포함), 쪽 1부터 */
    @GetMapping
    public ResponseEntity<ApiResponse<GroupBuyMemberView.ListPage>> list(@RequestParam(required = false) String phase, @RequestParam(required = false) String category,
                                                                         @RequestParam(defaultValue = "1") int page) {
        return noStore(service.list(phase, category, page));
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<GroupBuyMemberView.CategoryNode>>> categories() {
        return noStore(service.categories());
    }

    /** 상세 — 없거나 공개 전이면 404 {@code GROUP_BUY_NOT_FOUND} */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GroupBuyMemberView.Detail>> detail(@PathVariable String id, Authentication authentication) {
        return noStore(service.detail(id, optionalUser(authentication)));
    }

    /** 견적(화면은 계산하지 않는다 — 주문 때 다시 계산) */
    @PostMapping("/{id}/quote")
    public ResponseEntity<ApiResponse<GroupBuyMemberView.Quote>> quote(@PathVariable String id, @RequestBody(required = false) GroupBuyMemberRequest.Quote body) {
        return noStore(service.quote(id, body));
    }

    /** 주문(PENDING_PAYMENT + 선점) → 결제창 대상. 409 {@code GROUP_BUY_AMOUNT_CHANGED}·{@code _SOLD_OUT}·{@code _LIMIT_EXCEEDED}·…, 403 {@code GROUP_BUY_SALES_CLOSED} */
    @PostMapping("/{id}/orders")
    public ResponseEntity<ApiResponse<Object>> order(@PathVariable String id, @RequestHeader(value = MoneyRequestGuard.HEADER, required = false) String idempotencyKey,
                                                     @RequestBody(required = false) GroupBuyMemberRequest.Order body, Authentication authentication) {
        long userId = user(authentication);
        GroupBuyMemberRequest.Order request = body == null ? new GroupBuyMemberRequest.Order(null, null, null, null) : body;
        return guard.handle(userId, idempotencyKey, RequestAction.GROUPBUY_JOIN, new Keyed(id, request), () -> service.order(userId, id, request));
    }

    /** 같은 키·다른 공구도 「다른 본문」 — 해시에 공구 ID 를 넣는다 */
    private record Keyed(String groupBuyId, GroupBuyMemberRequest.Order order) {
    }

    @PutMapping("/{id}/watches")
    public ResponseEntity<Void> watch(@PathVariable String id, @RequestBody(required = false) GroupBuyMemberRequest.Watch body, Authentication authentication) {
        service.watch(user(authentication), id, body, true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/watches")
    public ResponseEntity<Void> unwatch(@PathVariable String id, @RequestParam(required = false) String kind, @RequestParam(required = false) String skuId,
                                        Authentication authentication) {
        service.watch(user(authentication), id, new GroupBuyMemberRequest.Watch(kind, skuId), false);
        return ResponseEntity.noContent().build();
    }

    static long user(Authentication authentication) {
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
