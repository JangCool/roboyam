package com.community.groupbuy.service;

import com.community.groupbuy.dto.Availability;
import com.community.groupbuy.dto.OrderLine;
import com.community.groupbuy.dto.Quote;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.GroupBuyStore.SkuRow;
import com.community.groupbuy.repository.master.ShippingPolicyStore;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 견적과 상세의 옵션 축·단품·남은 수량(화면 표시용 — 주문 때 {@link GroupBuyJoinService} 가 같은 식으로 다시 계산한다, commerce.md 3절). 선점하지 않는다.
 * 모집 전(예정) 공구는 시작 때 복사될 배송비 정책 줄로 보여 준다(예정 공구 가격 미리 공개).
 */
@Service
public class GroupBuyQuoteService {

    /** 주문 한 번의 줄 수 상한 */
    public static final int MAX_LINES = 50;
    /** 줄 하나의 수량 상한 */
    public static final int MAX_QTY_PER_LINE = 9999;

    private final GroupBuyStore groups;
    private final ShippingPolicyStore policies;
    private final Clock clock;

    public GroupBuyQuoteService(GroupBuyStore groups, ShippingPolicyStore policies, ObjectProvider<Clock> clocks) {
        this.groups = groups;
        this.policies = policies;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 주문 줄의 금액(상세의 「선택한 상품」 합계) */
    public Quote quote(long groupBuyId, List<OrderLine> items) {
        GroupBuyRow group = groups.find(groupBuyId).filter(g -> g.publishedAt() != null).orElseThrow(GroupBuyException::notFound);
        return price(group, groups.skus(groupBuyId), items, shippingOf(group));
    }

    /** 상세 화면 — 옵션 축·값과 단품별 남은 수량·품절 구분(결정 17·84·88) */
    public Availability availability(long groupBuyId) {
        GroupBuyRow g = groups.find(groupBuyId).filter(row -> row.publishedAt() != null).orElseThrow(GroupBuyException::notFound);
        int groupRemaining = g.maxQty() - g.currentQty() - g.reservedQty();
        boolean soldOut = g.currentQty() >= g.maxQty();
        List<Availability.Sku> skus = new ArrayList<>();
        for (SkuRow s : groups.skus(groupBuyId)) {
            Integer skuRemaining = s.maxQty() == null ? null : s.maxQty() - s.currentQty() - s.reservedQty();
            Integer remaining;
            boolean skuSoldOut;
            if (s.main()) {
                remaining = Math.max(0, skuRemaining == null ? groupRemaining : Math.min(skuRemaining, groupRemaining));
                skuSoldOut = soldOut || (s.maxQty() != null && s.currentQty() >= s.maxQty());
            } else {
                remaining = skuRemaining == null ? null : Math.max(0, skuRemaining);
                skuSoldOut = s.maxQty() != null && s.currentQty() >= s.maxQty();
            }
            skus.add(new Availability.Sku(s.id(), s.kind().name(), s.name(), s.valueIds(), s.unitPrice(g.groupPrice()), remaining, skuSoldOut,
                    !skuSoldOut && remaining != null && remaining <= 0, s.onSale()));
        }
        List<Availability.Axis> axes = groups.axes(groupBuyId).stream().map(a -> new Availability.Axis(a.id(), a.name(),
                a.values().stream().map(v -> new Availability.Value(v.id(), v.value())).toList())).toList();
        return new Availability(g.id(), Math.max(0, groupRemaining), soldOut, !soldOut && groupRemaining <= 0,
                g.initialMaxQty() == null ? 0 : Math.max(0, g.maxQty() - g.initialMaxQty()), axes, skus);
    }

    /**
     * 줄을 검사하고 금액을 계산한다 — 줄마다 그 공구의 판매 중인 단품, 같은 단품 두 줄·수량 0 은 400, <b>MAIN 단품 줄이 하나 이상</b>(추가 구성상품 단독 구매 불가 — 결정 88).
     * 판매하지 않는 단품은 409 {@code GROUP_BUY_SKU_NOT_ON_SALE}.
     */
    static Quote price(GroupBuyRow group, List<SkuRow> skus, List<OrderLine> items, Shipping shipping) {
        if (items == null || items.isEmpty() || items.size() > MAX_LINES) {
            throw ValidationException.of("items", new MessageRef("groupbuy.order.lines.required"));
        }
        Map<Long, SkuRow> byId = skus.stream().collect(Collectors.toMap(SkuRow::id, Function.identity()));
        Set<Long> seen = new HashSet<>();
        List<Quote.Line> out = new ArrayList<>();
        long total = 0;
        boolean anyMain = false;
        for (OrderLine line : items) {
            if (line == null || line.qty() < 1 || line.qty() > MAX_QTY_PER_LINE) {
                throw ValidationException.of("items", new MessageRef("groupbuy.order.qty.invalid"));
            }
            SkuRow sku = byId.get(line.skuId());
            if (sku == null) {
                throw ValidationException.of("items", new MessageRef("groupbuy.order.sku.unknown"));
            }
            if (!seen.add(sku.id())) {
                throw ValidationException.of("items", new MessageRef("groupbuy.order.sku.duplicate"));
            }
            if (!sku.onSale()) {
                throw GroupBuyException.skuNotOnSale(sku.id());
            }
            anyMain |= sku.main();
            long unit = sku.unitPrice(group.groupPrice());
            long amount = Math.multiplyExact(unit, line.qty());
            total = Math.addExact(total, amount);
            out.add(new Quote.Line(sku.id(), sku.kind().name(), sku.name(), line.qty(), unit, amount));
        }
        if (!anyMain) {
            throw ValidationException.of("items", new MessageRef("groupbuy.order.main.required"));
        }
        long fee = GroupBuyPricing.shippingFee(total, shipping.baseFee(), shipping.freeShippingOver());
        return new Quote(group.id(), out, total, fee, total + fee, shipping.freeShippingOver());
    }

    /** 배송비 — 모집을 시작한 공구는 고정 사본, 예정 공구는 시작 때(또는 지금) 유효한 정책 줄 */
    Shipping shippingOf(GroupBuyRow group) {
        if (group.shippingFee() != null) {
            return new Shipping(group.shippingFee(), group.freeShippingOver());
        }
        Instant at = group.startAt().isAfter(clock.instant()) ? group.startAt() : clock.instant();
        Optional<ShippingPolicyStore.PolicyRow> policy = policies.effective(group.shippingPolicyCode(), at);
        return policy.map(p -> new Shipping(p.baseFee(), p.freeShippingOver())).orElseThrow(GroupBuyException::shippingPolicyNotFound);
    }

    /** 배송비 기준 */
    record Shipping(long baseFee, Long freeShippingOver) {
    }
}
