package com.community.point.service;

import com.community.point.entity.PointLotKind;
import com.community.point.repository.master.PointLedgerWriter.Lot;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * 포인트 차감 순서 — docs/domain/commerce.md 4절·5-4 「차감 순서는 PointDeductionPolicy 로 분리」.
 *
 * <ol>
 *   <li><b>무상 먼저</b>: 소멸일이 가까운 순 → 같으면 먼저 만든 순(만든 시각 → ID).</li>
 *   <li><b>그다음 유상</b>: 먼저 만든 순(ID — Snowflake 라 시각순, FIFO).</li>
 * </ol>
 * 회수·운영 차감·미회수 상계는 무상 묶음만 넘겨 같은 순서를 쓰고, 「이 묶음 먼저」(적립한 그 묶음·방금 만든 묶음)가 있으면 그 묶음을 맨 앞에 둔다.
 * 소멸일이 지난 묶음은 넘기지 않는다(부르는 쪽이 먼저 소멸 처리한다).
 */
public final class PointDeductionPolicy {

    /** 차감 순서 */
    public static final Comparator<Lot> ORDER = Comparator.<Lot, Integer>comparing(lot -> lot.kind() == PointLotKind.FREE ? 0 : 1)
            .thenComparing((a, b) -> a.kind() == PointLotKind.FREE ? compareFree(a, b) : Long.compare(a.id(), b.id()));

    /** 묶음 하나에서 뺄 양 */
    public record Take(Lot lot, long amount) {
    }

    private PointDeductionPolicy() {
    }

    /** 차감 순서대로 늘어놓는다 */
    public static List<Lot> order(Collection<Lot> lots) {
        return lots.stream().sorted(ORDER).toList();
    }

    /**
     * {@code amount} 를 차감 순서대로 묶음에 나눈다.
     *
     * @param firstLotId 맨 앞에 둘 묶음(없으면 null) — 그 묶음이 목록에 없으면 무시한다
     * @throws IllegalStateException 묶음의 남은 양이 모자라면(부르는 쪽이 잔액을 먼저 확인한다 — 여기서 걸리면 잔액과 묶음이 어긋난 것이다)
     */
    public static List<Take> allocate(Collection<Lot> lots, long amount, Long firstLotId) {
        if (amount <= 0) {
            throw new IllegalArgumentException("나눌 양은 양수여야 한다: " + amount);
        }
        List<Lot> ordered = new ArrayList<>(order(lots));
        if (firstLotId != null) {
            ordered.stream().filter(lot -> lot.id() == firstLotId).findFirst().ifPresent(first -> {
                ordered.remove(first);
                ordered.addFirst(first);
            });
        }
        List<Take> takes = new ArrayList<>();
        long left = amount;
        for (Lot lot : ordered) {
            if (left == 0) {
                break;
            }
            long take = Math.min(left, lot.remaining());
            if (take > 0) {
                takes.add(new Take(lot, take));
                left -= take;
            }
        }
        if (left > 0) {
            throw new IllegalStateException("묶음의 남은 양이 잔액보다 적다 — 모자란 양 " + left);
        }
        return takes;
    }

    private static int compareFree(Lot a, Lot b) {
        int byExpiry = a.expiresAt().compareTo(b.expiresAt());
        if (byExpiry != 0) {
            return byExpiry;
        }
        int byCreated = a.createdAt().compareTo(b.createdAt());
        return byCreated != 0 ? byCreated : Long.compare(a.id(), b.id());
    }
}
