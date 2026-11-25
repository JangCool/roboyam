package com.community.groupbuy;

import com.community.groupbuy.tracking.ParcelTrackingClient;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 가짜 송장 조회(계약 결정 90) — 송장 번호마다 결과를 차례로 정해 둔다(다 쓰면 마지막 것을 되풀이, 정하지 않은 송장은 스캔 없음). 받은 키·택배사 코드·송장을 적어 둔다.
 */
final class FakeParcelTracking implements ParcelTrackingClient {

    record Call(String apiKey, String carrierCode, String invoiceNo) {
    }

    private final Map<String, Deque<Tracking>> plans = new ConcurrentHashMap<>();
    final List<Call> calls = new CopyOnWriteArrayList<>();
    volatile Usage usage = new Usage(Outcome.OK, null, "2026-01-01", "2026-12-31", 10_000L, 9_000L);

    void reset() {
        plans.clear();
        calls.clear();
        usage = new Usage(Outcome.OK, null, "2026-01-01", "2026-12-31", 10_000L, 9_000L);
    }

    /** 그 송장의 다음 결과들 */
    void plan(String invoiceNo, Tracking... results) {
        plans.put(invoiceNo, new ArrayDeque<>(List.of(results)));
    }

    long callsFor(String invoiceNo) {
        return calls.stream().filter(c -> c.invoiceNo().equals(invoiceNo)).count();
    }

    @Override
    public synchronized Tracking track(String apiKey, String carrierCode, String invoiceNo) {
        calls.add(new Call(apiKey, carrierCode, invoiceNo));
        Deque<Tracking> plan = plans.get(invoiceNo);
        if (plan == null || plan.isEmpty()) {
            return new Tracking(Outcome.NO_SCAN, "NO_SCAN", 0, false, List.of(), null);
        }
        return plan.size() > 1 ? plan.poll() : plan.peek();
    }

    @Override
    public Usage usage(String apiKey) {
        return usage;
    }

    // ── 결과 만들기 ──

    static Tracking inTransit(int level, Instant at) {
        return new Tracking(Outcome.OK, null, level, false, List.of(new Event(at.minusSeconds(3600), "집화처리", "서울 강남", 2), new Event(at, "배송중", "대전 HUB", level)),
                null);
    }

    static Tracking delivered(Instant shippedAt, Instant completedAt) {
        return new Tracking(Outcome.OK, null, 6, true, List.of(new Event(shippedAt.plusSeconds(600), "집화처리", "서울 강남", 2),
                new Event(completedAt.minusSeconds(3600), "배송출발", "부산 해운대", 5), new Event(completedAt, "배달완료", "부산 해운대", 6)), completedAt);
    }

    static Tracking error(String code) {
        Outcome outcome = switch (code) {
            case "101", "102", "103" -> Outcome.KEY_ERROR;
            case "105" -> Outcome.DAILY_LIMIT;
            default -> Outcome.FAILED;
        };
        return Tracking.failed(outcome, code);
    }
}
