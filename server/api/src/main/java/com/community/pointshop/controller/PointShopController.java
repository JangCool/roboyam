package com.community.pointshop.controller;

import com.community.auth.dto.AuthUser;
import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.support.MoneyRequestGuard;
import com.community.pointshop.dto.request.PointShopPurchaseRequest;
import com.community.pointshop.dto.response.PointShopItemListView;
import com.community.pointshop.dto.response.PointShopItemView;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.service.MemberPointShopService;
import com.community.pointshop.service.PointShopCatalogService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.response.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포인트 상점(공개 사이트) — 로그인 필요(없으면 401), 쓰기는 CSRF, 내 잔액·보유가 실려 {@code Cache-Control: no-store}. docs/features/commerce-stage1-contract.md 「B」·「1-2 화면 목업」.
 * 구매는 {@link MoneyRequestGuard}(헤더 {@code Idempotency-Key} 필수 · 요청 제한 {@code POINT_SHOP_PURCHASE} · 같은 키 다시 보내기는 처음 응답 그대로)가 감싼다.
 * 회원 권한({@code pointshop.use})은 구매 서비스가 회원 역할로 판정한다(없으면 403 {@code PERMISSION_DENIED} — 사업자 전용 계정 포함).
 */
@RestController
@RequestMapping("/api/point-shop")
public class PointShopController {

    private final PointShopCatalogService catalog;
    private final MemberPointShopService service;
    private final MoneyRequestGuard guard;

    public PointShopController(PointShopCatalogService catalog, MemberPointShopService service, MoneyRequestGuard guard) {
        this.catalog = catalog;
        this.service = service;
        this.guard = guard;
    }

    /** 품목 목록 + 내 포인트 + 환불 날 수 — {@code kind} 가 없으면 아바타를 뺀 전부, 판매 끝난 품목은 빠진다. 모르는 {@code kind} 는 400 {@code INVALID_PARAMETER} */
    @GetMapping("/items")
    public ResponseEntity<ApiResponse<PointShopItemListView>> items(@RequestParam(required = false) PointShopItemKind kind, Authentication authentication) {
        return noStore(catalog.memberList(userId(authentication), kind));
    }

    /** 품목 하나 — 판매가 끝나도 열린다. 없으면 404 {@code POINT_SHOP_ITEM_NOT_FOUND} */
    @GetMapping("/items/{id}")
    public ResponseEntity<ApiResponse<PointShopItemView>> item(@PathVariable String id, Authentication authentication) {
        return noStore(service.item(userId(authentication), id));
    }

    /** 산다 — 응답 {@code { purchaseId, userItemId, balance, item }}(200, 같은 키 다시 보내기는 머리글 {@code Idempotent-Replayed: true}) */
    @PostMapping("/purchases")
    public ResponseEntity<ApiResponse<Object>> purchase(@RequestHeader(value = MoneyRequestGuard.HEADER, required = false) String idempotencyKey,
                                                        @RequestBody(required = false) PointShopPurchaseRequest body, Authentication authentication) {
        long userId = userId(authentication);
        PointShopPurchaseRequest request = body == null ? new PointShopPurchaseRequest(null, null) : body;
        return guard.handle(userId, idempotencyKey, RequestAction.POINT_SHOP_PURCHASE, request, () -> service.purchase(userId, request));
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
