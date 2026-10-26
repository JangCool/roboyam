package com.community.pointshop.controller;

import com.community.auth.dto.AuthUser;
import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.support.MoneyRequestGuard;
import com.community.pointshop.dto.SelfRefundOutcome;
import com.community.pointshop.dto.request.EquipRequest;
import com.community.pointshop.dto.response.MyEquippedView;
import com.community.pointshop.dto.response.MyItemListView;
import com.community.pointshop.entity.EquipSlot;
import com.community.pointshop.service.MemberPointShopService;
import com.community.pointshop.service.UserItemService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 아이템(공개 사이트) — 목록·장착·해제·셀프 환불. 로그인 필요(없으면 401), 본인 것만(경로의 ID 가 남의 것이면 404), 쓰기는 CSRF, {@code Cache-Control: no-store}.
 * 셀프 환불은 {@link MoneyRequestGuard}(헤더 {@code Idempotency-Key} 필수 · 요청 제한 {@code REFUND_REQUEST} · 같은 키 다시 보내기는 처음 응답 그대로)가 감싼다.
 * 모르는 {@code slot} 은 400 {@code INVALID_PARAMETER}. docs/features/commerce-stage1-contract.md 「1-2 화면 목업」.
 */
@RestController
@RequestMapping("/api/me")
public class MyItemController {

    private final UserItemService userItems;
    private final MemberPointShopService service;
    private final MoneyRequestGuard guard;

    public MyItemController(UserItemService userItems, MemberPointShopService service, MoneyRequestGuard guard) {
        this.userItems = userItems;
        this.service = service;
        this.guard = guard;
    }

    /** 내 아이템 전부 — 한 줄 = 보유 한 줄(최근에 산 줄이 위, 회수된 줄도 보인다) */
    @GetMapping("/items")
    public ResponseEntity<ApiResponse<MyItemListView>> items(Authentication authentication) {
        return noStore(userItems.myItems(userId(authentication)));
    }

    /** 그 자리에 장착 — 응답은 두 자리의 지금 장착 */
    @PutMapping("/equipped/{slot}")
    public ResponseEntity<ApiResponse<MyEquippedView>> equip(@PathVariable EquipSlot slot, @RequestBody(required = false) EquipRequest body,
                                                             Authentication authentication) {
        return noStore(service.equip(userId(authentication), slot, body));
    }

    /** 그 자리를 비운다(비어 있어도 200) */
    @DeleteMapping("/equipped/{slot}")
    public ResponseEntity<ApiResponse<MyEquippedView>> unequip(@PathVariable EquipSlot slot, Authentication authentication) {
        return noStore(userItems.unequip(userId(authentication), slot));
    }

    /**
     * 셀프 환불 — 응답 200 {@code { refundedPoints, balance }}. <b>정지·영구 정지 중</b>이면 환불 신청을 만들고 202 {@code { requestId, status: PENDING, requestedAt }}
     * (같은 키 다시 보내기는 처음 응답 그대로). 썼거나 기한이 지났으면 409 {@code POINT_SHOP_REFUND_NOT_ALLOWED}(최상위 {@code blockedBy}), 신청 대기 중이면
     * 409 {@code POINT_SHOP_REFUND_PENDING}, 정지 중인데 거절된 적 있으면 409 {@code POINT_SHOP_REFUND_REJECTED}
     */
    @PostMapping("/items/{id}/refund")
    public ResponseEntity<ApiResponse<Object>> refund(@PathVariable String id, @RequestHeader(value = MoneyRequestGuard.HEADER, required = false) String idempotencyKey,
                                                      Authentication authentication) {
        long userId = userId(authentication);
        return guard.handleWithStatus(userId, idempotencyKey, RequestAction.REFUND_REQUEST, Map.of("userItemId", id), () -> {
            SelfRefundOutcome outcome = service.refund(userId, id);
            return new MoneyRequestGuard.Result(outcome.isRequest() ? 202 : 200, outcome.body());
        });
    }

    private static long userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AuthUser principal) {
            return principal.id();
        }
        throw new CommonException(ErrorCode.UNAUTHENTICATED);
    }

    private static <T> ResponseEntity<ApiResponse<T>> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }
}
