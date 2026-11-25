package com.community.groupbuy.service;

import com.community.audit.dto.AuditActions;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.ParcelTrackingStore;
import com.community.groupbuy.repository.master.ParcelTrackingStore.Claim;
import com.community.groupbuy.repository.master.ParcelTrackingStore.Claimed;
import com.community.groupbuy.repository.master.ParcelTrackingStore.SettingsRow;
import com.community.groupbuy.repository.master.ParcelTrackingStore.ShippedOrder;
import com.community.groupbuy.repository.master.ParcelTrackingStore.TrackingRow;
import com.community.groupbuy.repository.master.ParticipationStore;
import com.community.groupbuy.tracking.ParcelTrackingClient;
import com.community.groupbuy.tracking.ParcelTrackingClient.Outcome;
import com.community.groupbuy.tracking.ParcelTrackingClient.Tracking;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 배송 상태 자동 반영(계약 결정 90 · 고친 결정 7) — 스마트택배 조회로 발송 줄의 단계·이력을 적고, 배송 완료면 운영자 배송완료와 같은 처리({@link FulfillmentService#deliverAt}).
 * 한 바퀴({@link #runOnce}, 워커 키 {@value #WORKER_KEY})는 admin {@code GroupBuyTrackingScheduler} 가 배치를 켠 서버에서만 돈다.
 *
 * <ul>
 *   <li><b>대상</b>: 자동 조회가 켜져 있고 키가 있을 때, 배송완료 전 발송 줄 — 처음 발송(주문 SHIPPED)·교환 재발송(주문 DELIVERED), 택배사에 스마트택배 코드가 있고, 발송한 지
 *       {@value #WINDOW_DAYS}일 안, 다음 조회 시각이 된 것. 한 바퀴 {@value #BATCH}줄.</li>
 *   <li><b>겹치지 않게</b>: 줄마다 {@code SKIP LOCKED} 로 집으며 다음 조회를 {@link #LEASE} 미룬다(에스크로 대기열과 같은 임대). 조회 호출은 트랜잭션 밖.</li>
 *   <li><b>하루 제한</b>: 송장(발송 줄)당 KST 하루 {@value #DAILY_LIMIT}회(스마트택배 105) — 배치는 {@value #AUTO_DAILY_LIMIT}회까지 쓰고 남은 것은 [지금 조회] 몫. 다 쓰면 다음 조회는 내일 0시.</li>
 *   <li><b>간격</b>: 조회됨 → 설정 주기. 스캔 없음(단계 0)·104·106·응답 없음이 이어지면 실패 횟수를 올리고 {@link #backoff} 표로 늘린다(1~2번째 주기 그대로 · 3번째 ×2 · 4번째 ×4 ·
 *       5번째부터 ×8, 최대 24시간). 단계 1 이상이 오면 0으로.</li>
 *   <li><b>키 문제</b>(101·102·103): 그 회차를 멈추고 운영자 알림 {@code COMMERCE_TRACKING_KEY_ERROR} 한 번 — 같은 코드는 설정 행의 알림 상태가 막는다(조회가 다시 되거나 키를 바꾸면 지운다).</li>
 *   <li><b>완료</b>: 단계 6 또는 {@code complete} 면 택배사 완료 시각(완료 단계 이력 시각, 없으면 조회 시각)으로 배송완료 — 처음 발송은 주문 SHIPPED → DELIVERED(이미 DELIVERED 면 아무것도 안 함,
 *       이력 사유 {@code DELIVER_TRACKING}, 배치는 행위자 시스템), 교환 재발송은 그 발송 줄의 배송완료 시각만. 완료 시각이 발송 시각보다 이르거나 지금보다
 *       {@link #FUTURE_SKEW} 넘게 뒤면 자동 처리하지 않고 사유({@code BEFORE_SHIPPED}·{@code IN_FUTURE})를 남긴다(운영자 몫). 완료를 받은 줄은 더 자동 조회하지 않는다.</li>
 *   <li><b>발송 뒤 N일 자동 배송완료</b>(설정, 기본 꺼짐): 발송 + N일이 지난 SHIPPED 주문을 그 시각(발송 + N일)으로 — 같은 경로, 감사 {@code auto=AFTER_DAYS}. 키·자동 조회와 상관없다.</li>
 * </ul>
 * 키·송장 번호는 로그에 싣지 않는다(송장은 끝 4자리).
 */
@Service
public class ParcelTrackingService {

    private static final Logger log = LoggerFactory.getLogger(ParcelTrackingService.class);

    public static final String WORKER_KEY = "groupbuy-tracking";
    /** 송장당 KST 하루 조회 수(스마트택배 제한) · 배치가 쓰는 몫 */
    public static final int DAILY_LIMIT = 20;
    public static final int AUTO_DAILY_LIMIT = 18;
    /** 한 바퀴에 조회하는 줄 · N일 자동 완료하는 주문 */
    static final int BATCH = 50;
    static final int AUTO_DELIVER_BATCH = 200;
    /** 발송한 지 이만큼 지난 줄은 자동 조회하지 않는다(운영자 몫) */
    public static final int WINDOW_DAYS = 30;
    /** 집은 줄의 임대 — 조회·기록이 끝나면 진짜 다음 조회 시각으로 바꾼다 */
    static final Duration LEASE = Duration.ofMinutes(10);
    /** 완료 시각이 이만큼 넘게 미래면 자동 처리하지 않는다(시계 차이 여유) */
    static final Duration FUTURE_SKEW = Duration.ofMinutes(10);
    static final Duration MAX_INTERVAL = Duration.ofHours(24);
    /** 자동 완료를 하지 않은 사유 */
    public static final String HOLD_BEFORE_SHIPPED = "BEFORE_SHIPPED";
    public static final String HOLD_IN_FUTURE = "IN_FUTURE";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** 한 줄 결과 */
    public enum Step {
        /** 중간 단계를 적었다 */
        UPDATED,
        /** 배송완료로 바꿨다 */
        DELIVERED,
        /** 완료를 받았지만 이미 배송완료였다 */
        ALREADY_DELIVERED,
        /** 완료를 받았지만 시각이 이상해 자동 처리하지 않았다 */
        HELD,
        /** 스캔 없음 · 104 · 106 · 응답 없음 */
        FAILED,
        /** 하루 조회 수를 다 썼다(우리 셈 또는 105) */
        LIMIT,
        /** 키 문제(101·102·103) */
        KEY_ERROR,
        /** 다른 서버가 잡았거나 차례가 아니다 */
        SKIPPED
    }

    /** 한 바퀴 결과 */
    public record Round(int checked, int delivered, int autoDelivered, String keyError) {

        public int total() {
            return checked + autoDelivered;
        }
    }

    /** 저장된 추적 이력 한 줄(회원·관리자 화면) */
    public record Event(Instant time, String kind, String where, int level) {
    }

    private final ParcelTrackingStore store;
    private final ParticipationStore participations;
    private final ParcelTrackingSettingsService settings;
    private final ParcelTrackingClient client;
    private final FulfillmentService fulfillment;
    private final GroupBuyAudit audit;
    private final NotificationPublisher operators;
    private final TransactionTemplate tx;
    private final ObjectProvider<Clock> clocks;

    public ParcelTrackingService(ParcelTrackingStore store, ParticipationStore participations, ParcelTrackingSettingsService settings, ParcelTrackingClient client,
                                 FulfillmentService fulfillment, GroupBuyAudit audit, NotificationPublisher operators, PlatformTransactionManager transactions,
                                 ObjectProvider<Clock> clocks) {
        this.store = store;
        this.participations = participations;
        this.settings = settings;
        this.client = client;
        this.fulfillment = fulfillment;
        this.audit = audit;
        this.operators = operators;
        this.tx = new TransactionTemplate(transactions);
        this.clocks = clocks;
    }

    // ── 한 바퀴 ─────────────────────────────────────────────────────────────

    /** 한 바퀴 — 발송 뒤 N일 자동 배송완료(켜져 있으면) → 자동 조회(켜져 있고 키가 있으면) */
    public Round runOnce() {
        SettingsRow s = store.settings();
        int autoDelivered = s.autoDeliverEnabled() ? autoDeliverAfterDays(s.autoDeliverDays()) : 0;
        if (!s.enabled()) {
            return new Round(0, 0, autoDelivered, null);
        }
        Optional<String> key = settings.apiKey();
        if (key.isEmpty()) {
            return new Round(0, 0, autoDelivered, null);
        }
        Instant now = now();
        int checked = 0;
        int delivered = 0;
        for (long shipmentId : store.due(now, now.minus(Duration.ofDays(WINDOW_DAYS)), BATCH)) {
            Step step = trackOne(shipmentId, key.get(), s.intervalMinutes(), true, null, null);
            if (step == Step.SKIPPED) {
                continue;
            }
            if (step == Step.KEY_ERROR) {
                String code = store.find(shipmentId).map(TrackingRow::errorCode).orElse("KEY");
                return new Round(checked, delivered, autoDelivered, code);
            }
            if (step != Step.LIMIT) {
                checked++;
            }
            if (step == Step.DELIVERED) {
                delivered++;
            }
        }
        return new Round(checked, delivered, autoDelivered, null);
    }

    /** 발송 + N일이 지난 SHIPPED 주문을 (발송 + N일) 시각으로 배송완료 — 다룬 수 */
    int autoDeliverAfterDays(int days) {
        Instant now = now();
        int n = 0;
        for (ShippedOrder o : store.shippedBefore(now.minus(Duration.ofDays(days)), AUTO_DELIVER_BATCH)) {
            try {
                if (fulfillment.deliverAt(o.participationId(), o.shippedAt().plus(Duration.ofDays(days)), FulfillmentService.AUTO_AFTER_DAYS, null, null, Map.of("days", days))) {
                    n++;
                }
            } catch (RuntimeException e) {
                log.warn("발송 뒤 N일 자동 배송완료 실패 participationId={} 원인={}", o.participationId(), e.getClass().getSimpleName());
            }
        }
        return n;
    }

    // ── 지금 조회(관리자) ────────────────────────────────────────────────────

    /**
     * [지금 조회] — 그 주문의 배송완료 전 발송 줄을 바로 조회해 반영한다(하루 제한 안 — 자동 조회 켜기와 상관없이 키만 있으면). 결과는 부르는 쪽이 다시 읽는다.
     *
     * @throws GroupBuyException {@code COMMERCE_TRACKING_KEY_MISSING}(키 없음) · {@code GROUP_BUY_TRACKING_NOT_AVAILABLE}(조회할 발송 없음 — 배송완료·스마트택배 코드 없는 택배사) ·
     *                           {@code GROUP_BUY_TRACKING_DAILY_LIMIT}(모든 줄이 하루 조회 수를 다 씀) · {@code COMMERCE_TRACKING_KEY_ERROR}(키를 받지 않음 — {@code providerCode})
     */
    public List<Step> refresh(long participationId, long operatorId, String ip) {
        participations.find(participationId).orElseThrow(GroupBuyException::orderNotFound);
        String key = settings.apiKey().orElseThrow(GroupBuyException::trackingKeyMissing);
        List<Long> shipments = store.trackableOf(participationId);
        if (shipments.isEmpty()) {
            throw GroupBuyException.trackingNotAvailable();
        }
        int interval = store.settings().intervalMinutes();
        List<Step> steps = new ArrayList<>();
        for (long shipmentId : shipments) {
            Step step = trackOne(shipmentId, key, interval, false, operatorId, ip);
            if (step == Step.KEY_ERROR) {
                throw GroupBuyException.trackingKeyError(store.find(shipmentId).map(TrackingRow::errorCode).orElse(null));
            }
            steps.add(step);
        }
        if (steps.stream().allMatch(s -> s == Step.LIMIT)) {
            throw GroupBuyException.trackingDailyLimit();
        }
        return steps;
    }

    // ── 한 줄 ───────────────────────────────────────────────────────────────

    /** 한 줄 — 집고(짧은 트랜잭션) → 조회(밖) → 반영(짧은 트랜잭션). 부르는 쪽에 트랜잭션이 없어야 한다 */
    Step trackOne(long shipmentId, String key, int intervalMinutes, boolean due, Long actorId, String ip) {
        Instant now = now();
        LocalDate today = ServiceTime.dateOf(now);
        Instant tomorrow = today.plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant();
        Claimed claimed = tx.execute(status -> store.claim(shipmentId, due, now, today, tomorrow, now.plus(LEASE), due ? AUTO_DAILY_LIMIT : DAILY_LIMIT));
        if (claimed == null || claimed.claim() == Claim.BUSY) {
            return Step.SKIPPED;
        }
        if (claimed.claim() == Claim.LIMIT) {
            return Step.LIMIT;
        }
        TrackingRow row = claimed.row();
        Tracking result = client.track(key, row.sweetTrackerCode(), row.trackingNo());
        Step step = tx.execute(status -> apply(row, result, intervalMinutes, today, tomorrow, due, actorId, ip));
        if (step == Step.KEY_ERROR) {
            tx.executeWithoutResult(status -> {
                if (store.raiseAlert(result.errorCode(), now())) {
                    operators.publish(AdminNotificationEvent.COMMERCE_TRACKING_KEY_ERROR, NotificationContext.empty().args(result.errorCode()).dedupe(result.errorCode()));
                }
            });
        }
        return step;
    }

    /** 결과 반영(트랜잭션 안) — 주문 행을 먼저 잠근다(운영자 배송완료와 같은 잠금 차례) */
    private Step apply(TrackingRow row, Tracking result, int intervalMinutes, LocalDate today, Instant tomorrow, boolean due, Long actorId, String ip) {
        Instant now = now();
        participations.lock(row.participationId());
        Duration interval = Duration.ofMinutes(intervalMinutes);
        switch (result.outcome()) {
            case KEY_ERROR -> {
                store.release(row.shipmentId(), now.plus(interval), result.errorCode());
                return Step.KEY_ERROR;
            }
            case DAILY_LIMIT -> {
                store.recordDailyLimit(row.shipmentId(), now, today, DAILY_LIMIT, tomorrow);
                return Step.LIMIT;
            }
            case FAILED -> {
                int fails = row.failCount() + 1;
                store.recordFailure(row.shipmentId(), now, now.plus(backoff(intervalMinutes, fails)), fails, result.errorCode());
                return Step.FAILED;
            }
            case NO_SCAN -> {
                int fails = row.failCount() + 1;
                store.recordResult(row.shipmentId(), 0, false, eventsJson(result.events()), now, now.plus(backoff(intervalMinutes, fails)), fails, "NO_SCAN", null, null);
                store.clearAlert();
                return Step.FAILED;
            }
            default -> {
                // OK — 아래
            }
        }
        store.clearAlert();
        int level = result.level() == null ? 0 : result.level();
        if (!result.complete()) {
            store.recordResult(row.shipmentId(), level, false, eventsJson(result.events()), now, now.plus(interval), 0, null, null, null);
            return Step.UPDATED;
        }
        Instant completedAt = result.completedAt() != null ? result.completedAt().truncatedTo(ChronoUnit.MICROS) : now;
        String hold = completedAt.isBefore(row.shippedAt()) ? HOLD_BEFORE_SHIPPED : completedAt.isAfter(now.plus(FUTURE_SKEW)) ? HOLD_IN_FUTURE : null;
        store.recordResult(row.shipmentId(), 6, true, eventsJson(result.events()), now, null, 0, null, completedAt, hold);
        if (hold != null) {
            log.warn("스마트택배 완료 시각이 이상해 자동 배송완료하지 않았다 shipmentId={} 사유={}", row.shipmentId(), hold);
            return Step.HELD;
        }
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("shipmentId", Long.toString(row.shipmentId()));
        extra.put("via", due ? "BATCH" : "REFRESH");
        if ("EXCHANGE".equals(row.kind())) {
            if (!store.markExchangeDelivered(row.shipmentId(), completedAt, now)) {
                return Step.ALREADY_DELIVERED;
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("step", "EXCHANGE_DELIVER");
            detail.put("auto", FulfillmentService.AUTO_TRACKING);
            detail.put("deliveredAt", completedAt.toString());
            detail.putAll(extra);
            audit.record(actorId, AuditActions.GROUPBUY_FULFILLMENT, GroupBuyAudit.TARGET_ORDER, row.participationId(), ip, detail);
            return Step.DELIVERED;
        }
        return fulfillment.deliverAt(row.participationId(), completedAt, FulfillmentService.AUTO_TRACKING, actorId, ip, extra) ? Step.DELIVERED : Step.ALREADY_DELIVERED;
    }

    /** 이어진 실패 n번째 뒤 다음 조회까지 — 1~2번째 주기 그대로 · 3번째 ×2 · 4번째 ×4 · 5번째부터 ×8, 최대 24시간 */
    public static Duration backoff(int intervalMinutes, int failCount) {
        int factor = failCount <= 2 ? 1 : failCount == 3 ? 2 : failCount == 4 ? 4 : 8;
        Duration d = Duration.ofMinutes((long) intervalMinutes * factor);
        return d.compareTo(MAX_INTERVAL) > 0 ? MAX_INTERVAL : d;
    }

    // ── 이력 JSON ───────────────────────────────────────────────────────────

    static String eventsJson(List<ParcelTrackingClient.Event> events) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ParcelTrackingClient.Event e : events) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("time", e.time().toString());
            m.put("kind", e.kind());
            m.put("where", e.where());
            m.put("level", e.level());
            out.add(m);
        }
        return JSON.writeValueAsString(out);
    }

    /** 저장된 이력(jsonb) → 줄들(시각 차례). 비었거나 읽지 못하면 빈 목록 */
    public static List<Event> events(String json) {
        List<Event> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            for (JsonNode n : JSON.readTree(json)) {
                JsonNode time = n.get("time");
                if (time == null || time.isNull()) {
                    continue;
                }
                out.add(new Event(Instant.parse(time.asString()), text(n, "kind"), text(n, "where"), n.has("level") ? n.get("level").asInt() : 0));
            }
        } catch (RuntimeException e) {
            log.warn("배송 추적 이력을 읽지 못했다 원인={}", e.getClass().getSimpleName());
            return List.of();
        }
        out.sort(java.util.Comparator.comparing(Event::time));
        return out;
    }

    private static String text(JsonNode n, String name) {
        JsonNode v = n.get(name);
        return v == null || v.isNull() ? "" : v.asString();
    }

    private Instant now() {
        return clocks.getIfUnique(Clock::systemUTC).instant().truncatedTo(ChronoUnit.MICROS);
    }
}
