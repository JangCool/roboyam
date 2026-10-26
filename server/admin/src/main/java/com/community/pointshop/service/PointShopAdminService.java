package com.community.pointshop.service;

import com.community.permission.dto.AdminActor;
import com.community.pointshop.dto.request.PointShopAvatarPriceRequest;
import com.community.pointshop.dto.request.PointShopAvatarSaleRequest;
import com.community.pointshop.dto.request.PointShopAvatarScope;
import com.community.pointshop.dto.request.PointShopAvatarTarget;
import com.community.pointshop.dto.request.PointShopItemSaveRequest;
import com.community.pointshop.dto.request.PointShopPurchaseQuery;
import com.community.pointshop.dto.request.PointShopRefundRequest;
import com.community.pointshop.dto.request.PointShopSettingsSaveRequest;
import com.community.pointshop.dto.response.PointShopAdminItemListView;
import com.community.pointshop.dto.response.PointShopAdminItemView;
import com.community.pointshop.dto.response.PointShopAvatarBulkResultView;
import com.community.pointshop.dto.response.PointShopAvatarSummaryView;
import com.community.pointshop.dto.response.PointShopOperatorRefundView;
import com.community.pointshop.dto.response.PointShopPurchasePageView;
import com.community.pointshop.dto.response.PointShopRefundDecisionView;
import com.community.pointshop.dto.response.PointShopSettingsView;
import com.community.pointshop.entity.PointShopItemKind;
import com.community.pointshop.entity.PointShopPurchaseStatus;
import com.community.pointshop.entity.PointShopRefundRequestStatus;
import com.community.pointshop.exception.PointShopItemNotFoundException;
import com.community.pointshop.exception.PointShopPurchaseNotFoundException;
import com.community.pointshop.exception.PointShopRefundRequestNotFoundException;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * 관리자 포인트 상점 — 질의 글자·경로 ID·범위 이름을 읽어 shared 의 {@link PointShopCatalogService}·{@link PointShopPurchaseService}·{@link PointShopRefundService} 에 넘긴다
 * (업무 규칙·트랜잭션·감사는 그쪽). 감사의 행위자·IP 는 {@link AdminActor}.
 *
 * <ul>
 *   <li>모르는 {@code kind}·{@code excludeKind}·{@code status}·{@code refundRequest}·시각 형식은 {@code 400 VALIDATION fields.<조건>} — 쪽 번호·크기가 숫자가 아니면 {@code fields.page|size}.</li>
 *   <li>경로 ID 가 숫자가 아니면 없는 것과 같다({@code 404 POINT_SHOP_ITEM_NOT_FOUND}·{@code POINT_SHOP_PURCHASE_NOT_FOUND}).</li>
 *   <li>아바타 묶음의 모르는 범위 이름은 범위가 없는 것으로 넘겨 {@code fields.scope} 가 가격·판매 값 오류와 함께 나가게 한다.</li>
 *   <li>본문이 없으면 칸이 모두 빈 요청으로 본다(칸 오류가 모여 나간다).</li>
 * </ul>
 */
@Service
public class PointShopAdminService {

    /** 구매 내역 한 쪽 기본 크기(화면 첫 쪽과 같다) */
    public static final int DEFAULT_SIZE = 20;

    private final PointShopCatalogService catalog;
    private final PointShopPurchaseService purchases;
    private final PointShopRefundService refunds;

    public PointShopAdminService(PointShopCatalogService catalog, PointShopPurchaseService purchases, PointShopRefundService refunds) {
        this.catalog = catalog;
        this.purchases = purchases;
        this.refunds = refunds;
    }

    // ── 품목 ────────────────────────────────────────────────────────────────

    public PointShopAdminItemListView items(String kind, String excludeKind) {
        Map<String, MessageRef> bad = new LinkedHashMap<>();
        PointShopItemKind only = parse("kind", kind, PointShopItemKind::valueOf, "pointshop.items.kind.invalid", bad);
        PointShopItemKind except = parse("excludeKind", excludeKind, PointShopItemKind::valueOf, "pointshop.items.kind.invalid", bad);
        if (!bad.isEmpty()) {
            throw new ValidationException(bad);
        }
        return catalog.adminItems(only, except);
    }

    public PointShopAdminItemView item(String id) {
        return catalog.adminItem(itemId(id));
    }

    public PointShopAdminItemView create(AdminActor actor, PointShopItemSaveRequest body) {
        return catalog.create(body == null ? empty() : body, actor.operatorId(), actor.ip());
    }

    public PointShopAdminItemView update(AdminActor actor, String id, PointShopItemSaveRequest body) {
        return catalog.update(itemId(id), body == null ? empty() : body, actor.operatorId(), actor.ip());
    }

    // ── 아바타 ──────────────────────────────────────────────────────────────

    public PointShopAvatarSummaryView avatarSummary() {
        return catalog.avatarSummary();
    }

    public PointShopAvatarBulkResultView avatarPrice(AdminActor actor, PointShopAvatarPriceRequest body) {
        PointShopAvatarPriceRequest b = body == null ? new PointShopAvatarPriceRequest(null, null, null, null) : body;
        return catalog.avatarPrice(new PointShopAvatarTarget(scope(b.scope()), b.categories(), b.itemIds()), b.price(), actor.operatorId(), actor.ip());
    }

    public PointShopAvatarBulkResultView avatarSale(AdminActor actor, PointShopAvatarSaleRequest body) {
        PointShopAvatarSaleRequest b = body == null ? new PointShopAvatarSaleRequest(null, null, null, null) : body;
        return catalog.avatarSale(new PointShopAvatarTarget(scope(b.scope()), b.categories(), b.itemIds()), b.active(), actor.operatorId(), actor.ip());
    }

    // ── 구매 내역·예외 환불 ─────────────────────────────────────────────────

    public PointShopPurchasePageView purchases(String from, String to, String status, String kind, String refundRequest, String q, String page, String size) {
        Map<String, MessageRef> bad = new LinkedHashMap<>();
        Instant fromAt = parse("from", from, Instant::parse, "pointshop.purchases.time.invalid", bad);
        Instant toAt = parse("to", to, Instant::parse, "pointshop.purchases.time.invalid", bad);
        PointShopPurchaseStatus statusValue = parse("status", status, PointShopPurchaseStatus::valueOf, "pointshop.purchases.status.invalid", bad);
        PointShopItemKind kindValue = parse("kind", kind, PointShopItemKind::valueOf, "pointshop.purchases.kind.invalid", bad);
        PointShopRefundRequestStatus requestValue = parse("refundRequest", refundRequest, PointShopRefundRequestStatus::valueOf, "pointshop.purchases.refundRequest.invalid", bad);
        Integer pageValue = parse("page", page, Integer::valueOf, "pointshop.purchases.page.min", bad);
        Integer sizeValue = parse("size", size, Integer::valueOf, null, bad);
        if (bad.containsKey("size")) {
            bad.put("size", new MessageRef("pointshop.purchases.size.range", 1, PointShopPurchaseQuery.SIZE_MAX));
        }
        if (!bad.isEmpty()) {
            throw new ValidationException(bad);
        }
        String keyword = q == null || q.isBlank() ? null : q.strip();
        return purchases.adminPurchases(new PointShopPurchaseQuery(fromAt, toAt, statusValue, kindValue, keyword, pageValue == null ? 0 : pageValue,
                sizeValue == null ? DEFAULT_SIZE : sizeValue, requestValue));
    }

    public PointShopOperatorRefundView refund(AdminActor actor, String purchaseId, PointShopRefundRequest body) {
        long id = parseId(purchaseId);
        if (id < 0) {
            throw new PointShopPurchaseNotFoundException();
        }
        return refunds.refundByOperator(id, body == null ? null : body.reason(), actor.operatorId(), actor.ip());
    }

    /** 환불 신청 수락 — 형식이 틀린 ID 는 404 {@code POINT_SHOP_REFUND_REQUEST_NOT_FOUND} */
    public PointShopRefundDecisionView approveRefundRequest(AdminActor actor, String requestId) {
        return refunds.approveRequest(requestIdOf(requestId), actor.operatorId(), actor.ip());
    }

    /** 환불 신청 거절 — 사유 5~200자({@code fields.reason}) */
    public PointShopRefundDecisionView rejectRefundRequest(AdminActor actor, String requestId, PointShopRefundRequest body) {
        return refunds.rejectRequest(requestIdOf(requestId), body == null ? null : body.reason(), actor.operatorId(), actor.ip());
    }

    private static long requestIdOf(String raw) {
        long id = parseId(raw);
        if (id < 0) {
            throw new PointShopRefundRequestNotFoundException();
        }
        return id;
    }

    // ── 설정 ────────────────────────────────────────────────────────────────

    public PointShopSettingsView settings() {
        return catalog.settings();
    }

    public PointShopSettingsView saveSettings(AdminActor actor, PointShopSettingsSaveRequest body) {
        return catalog.updateSettings(body == null ? null : body.refundDays(), actor.operatorId(), actor.ip());
    }

    // ── 읽기 도우미 ─────────────────────────────────────────────────────────

    private static PointShopItemSaveRequest empty() {
        return new PointShopItemSaveRequest(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static long itemId(String raw) {
        long id = parseId(raw);
        if (id < 0) {
            throw new PointShopItemNotFoundException();
        }
        return id;
    }

    /** 숫자 ID — 못 읽으면 -1 */
    private static long parseId(String raw) {
        if (raw == null || !raw.matches("\\d{1,19}")) {
            return -1;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 모르는 범위 이름은 null(서비스가 {@code fields.scope}) */
    private static PointShopAvatarScope scope(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return PointShopAvatarScope.valueOf(raw.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 질의 글자 하나를 읽는다 — 비었으면 null, 못 읽으면 {@code bad} 에 담고 null.
     *
     * @param messageKey 못 읽었을 때 문구 키(null 이면 「「칸」 값을 읽을 수 없어요」)
     */
    private static <T> T parse(String field, String raw, Function<String, T> reader, String messageKey, Map<String, MessageRef> bad) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return reader.apply(raw.strip());
        } catch (IllegalArgumentException | DateTimeParseException e) {
            bad.put(field, messageKey == null ? new MessageRef("error.INVALID_PARAMETER.named", field) : new MessageRef(messageKey));
            return null;
        }
    }
}
