package com.community.bizapplication.service;

import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.exception.BizApplicationException;
import com.community.bizapplication.repository.master.BizPriceRuleStore;
import com.community.bizapplication.repository.master.BizPriceRuleStore.PriceRule;
import com.community.bizapplication.repository.master.BizSlotStore;
import com.community.bizapplication.repository.master.BizSlotStore.Occupied;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * 자리(한도) 판정(설계 3절) — 같은 상품의 「자리를 차지하는 상태」 줄 중 {@code [s, e)} 와 겹치는 줄들의 <b>최대 동시 겹침 수</b>(쓸기) {@literal <} 한도면 자리가 있다. 자기 자신은 뺀다.
 * 기간은 반열림이라 앞 건의 끝 = 뒷 건의 시작이면 겹치지 않는다. 승인 때는 상품마다 어드바이저리 잠금 아래 다시 판정한다({@link #lockAndRequire}).
 * 트랜잭션은 부르는 쪽이 연다.
 */
@Service
public class BizSlotService {

    /** 날짜 하루의 남은 칸 */
    public record DayRoom(LocalDate date, int remaining, int capacity) {
    }

    /** 기간의 남은 칸 — 한도 없는 상품(핫딜 게시)은 {@code limited = false} */
    public record Room(boolean limited, int remaining, int capacity) {
    }

    private final BizSlotStore slots;
    private final BizPriceRuleStore rules;

    public BizSlotService(BizSlotStore slots, BizPriceRuleStore rules) {
        this.slots = slots;
        this.rules = rules;
    }

    /** 최대 동시 겹침 수 — 끝과 시작이 같은 시각이면 끝을 먼저 센다(반열림) */
    public static int maxOverlap(List<Occupied> rows, Instant from, Instant to) {
        record Edge(Instant at, int delta) {
        }
        List<Edge> edges = new ArrayList<>();
        for (Occupied r : rows) {
            Instant s = r.startAt().isAfter(from) ? r.startAt() : from;
            Instant e = r.endAt().isBefore(to) ? r.endAt() : to;
            if (s.isBefore(e)) {
                edges.add(new Edge(s, 1));
                edges.add(new Edge(e, -1));
            }
        }
        edges.sort((a, b) -> a.at().equals(b.at()) ? Integer.compare(a.delta(), b.delta()) : a.at().compareTo(b.at()));
        int now = 0;
        int max = 0;
        for (Edge edge : edges) {
            now += edge.delta();
            max = Math.max(max, now);
        }
        return max;
    }

    /** 그 기간의 남은 칸(잠그지 않는다 — 안내·신청 때 판정) */
    public Room room(BizProduct product, Instant start, Instant end, Long exceptApplicationId) {
        PriceRule rule = rules.find(product).orElseThrow();
        if (rule.capacity() == null) {
            return new Room(false, 1, 1);
        }
        int used = maxOverlap(slots.overlapping(product, start, end, exceptApplicationId), start, end);
        return new Room(true, Math.max(0, rule.capacity() - used), rule.capacity());
    }

    /** 자리가 없으면 409 {@code BIZ_SLOT_FULL}(잠그지 않음) */
    public void require(BizProduct product, Instant start, Instant end, Long exceptApplicationId) {
        Room room = room(product, start, end, exceptApplicationId);
        if (room.limited() && room.remaining() <= 0) {
            throw BizApplicationException.slotFull();
        }
    }

    /**
     * 승인·연장 — 상품마다(이름 차례로) 어드바이저리 잠금을 걸고 다시 판정한다. 트랜잭션 안에서만 의미가 있다(잠금은 끝날 때 풀린다).
     *
     * @return 모두 자리가 있으면 true
     */
    public boolean lockAndCheck(Collection<BizProduct> products, Instant start, Instant end, long applicationId) {
        TreeSet<BizProduct> ordered = new TreeSet<>(products);
        for (BizProduct product : ordered) {
            if (rules.find(product).map(PriceRule::capacity).isPresent()) {
                slots.lock(product);
            }
        }
        for (BizProduct product : ordered) {
            Room room = room(product, start, end, applicationId);
            if (room.limited() && room.remaining() <= 0) {
                return false;
            }
        }
        return true;
    }

    /** {@link #lockAndCheck} — 자리가 없으면 409 {@code BIZ_SLOT_FULL} */
    public void lockAndRequire(Collection<BizProduct> products, Instant start, Instant end, long applicationId) {
        if (!lockAndCheck(products, start, end, applicationId)) {
            throw BizApplicationException.slotFull();
        }
    }

    /** 날짜별 남은 칸 — 그날 한국 [00:00, 24:00) 안의 최대 동시 겹침 기준 */
    public List<DayRoom> daily(BizProduct product, LocalDate from, LocalDate to) {
        PriceRule rule = rules.find(product).orElseThrow();
        int capacity = rule.capacity() == null ? 1 : rule.capacity();
        List<Occupied> rows = rule.capacity() == null ? List.of() : slots.overlapping(product, BizPeriods.startOf(from), BizPeriods.startOf(to.plusDays(1)), null);
        List<DayRoom> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            int used = maxOverlap(rows, BizPeriods.startOf(d), BizPeriods.startOf(d.plusDays(1)));
            out.add(new DayRoom(d, Math.max(0, capacity - used), capacity));
        }
        return out;
    }
}
