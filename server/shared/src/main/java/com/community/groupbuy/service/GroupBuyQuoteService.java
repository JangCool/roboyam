package com.community.groupbuy.service;

import com.community.groupbuy.dto.Availability;
import com.community.groupbuy.dto.OrderLine;
import com.community.groupbuy.dto.Quote;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.GroupBuyStore;
import com.community.groupbuy.repository.master.GroupBuyStore.GroupBuyRow;
import com.community.groupbuy.repository.master.GroupBuyStore.OptionRow;
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
 * 견적과 남은 수량(화면 표시용 — 주문 때 {@link GroupBuyJoinService} 가 같은 식으로 다시 계산한다, commerce.md 3절). 선점하지 않는다.
 * 모집 전(예정) 공구는 시작 때 복사될 배송비 정책 줄로 보여 준다(예정 공구 가격 미리 공개).
 */
@Service
public class GroupBuyQuoteService {

    /** 주문 한 번의 줄 수 상한(옵션 수와 같은 규모 — 터무니없는 요청을 막는다) */
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

    /** 주문 줄의 금액 */
    public Quote quote(long groupBuyId, List<OrderLine> lines) {
        GroupBuyRow group = groups.find(groupBuyId).filter(g -> g.publishedAt() != null).orElseThrow(GroupBuyException::notFound);
        return price(group, groups.options(groupBuyId), lines, shippingOf(group));
    }

    /** 공구·옵션별 남은 수량과 품절 구분(결정 17·84) */
    public Availability availability(long groupBuyId) {
        GroupBuyRow g = groups.find(groupBuyId).filter(row -> row.publishedAt() != null).orElseThrow(GroupBuyException::notFound);
        int groupRemaining = g.maxQty() - g.currentQty() - g.reservedQty();
        boolean soldOut = g.currentQty() >= g.maxQty();
        List<Availability.OptionAvailability> options = new ArrayList<>();
        for (OptionRow o : groups.options(groupBuyId)) {
            int optionRemaining = o.maxQty() == null ? groupRemaining : Math.min(o.maxQty() - o.currentQty() - o.reservedQty(), groupRemaining);
            boolean optionSoldOut = soldOut || (o.maxQty() != null && o.currentQty() >= o.maxQty());
            options.add(new Availability.OptionAvailability(o.id(), o.name(), Math.max(0, optionRemaining), optionSoldOut, !optionSoldOut && optionRemaining <= 0));
        }
        return new Availability(g.id(), Math.max(0, groupRemaining), soldOut, !soldOut && groupRemaining <= 0,
                g.initialMaxQty() == null ? 0 : Math.max(0, g.maxQty() - g.initialMaxQty()), options);
    }

    /**
     * 줄을 검사하고 금액을 계산한다 — 옵션이 있는 공구는 줄마다 그 공구의 옵션, 없는 공구는 옵션 없는 줄 하나. 같은 옵션 두 줄·수량 0 은 400.
     */
    static Quote price(GroupBuyRow group, List<OptionRow> options, List<OrderLine> lines, Shipping shipping) {
        if (lines == null || lines.isEmpty() || lines.size() > MAX_LINES) {
            throw ValidationException.of("lines", new MessageRef("groupbuy.order.lines.required"));
        }
        Map<Long, OptionRow> byId = options.stream().collect(Collectors.toMap(OptionRow::id, Function.identity()));
        Set<Long> seen = new HashSet<>();
        List<Quote.Line> out = new ArrayList<>();
        long items = 0;
        for (OrderLine line : lines) {
            if (line == null || line.qty() < 1 || line.qty() > MAX_QTY_PER_LINE) {
                throw ValidationException.of("lines", new MessageRef("groupbuy.order.qty.invalid"));
            }
            OptionRow option = null;
            if (options.isEmpty()) {
                if (line.optionId() != null || lines.size() != 1) {
                    throw ValidationException.of("lines", new MessageRef("groupbuy.order.option.invalid"));
                }
            } else {
                option = line.optionId() == null ? null : byId.get(line.optionId());
                if (option == null) {
                    throw ValidationException.of("lines", new MessageRef("groupbuy.order.option.required"));
                }
            }
            if (!seen.add(line.optionId() == null ? -1L : line.optionId())) {
                throw ValidationException.of("lines", new MessageRef("groupbuy.order.option.duplicate"));
            }
            long unit = GroupBuyPricing.unitPrice(group.groupPrice(), option == null ? 0 : option.extraPrice());
            long amount = Math.multiplyExact(unit, line.qty());
            items = Math.addExact(items, amount);
            out.add(new Quote.Line(line.optionId(), option == null ? null : option.name(), line.qty(), unit, amount));
        }
        long fee = GroupBuyPricing.shippingFee(items, shipping.baseFee(), shipping.freeShippingOver());
        return new Quote(group.id(), out, items, fee, items + fee, shipping.freeShippingOver());
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
