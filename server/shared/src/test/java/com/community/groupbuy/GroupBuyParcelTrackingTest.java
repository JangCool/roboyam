package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.lines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.audit.entity.AuditActorType;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.entity.CollectionMethod;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.RequestReason;
import com.community.groupbuy.entity.RequestType;
import com.community.groupbuy.entity.Resolution;
import com.community.groupbuy.entity.ReturnDisposition;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.repository.master.FulfillmentStore;
import com.community.groupbuy.repository.master.ParcelTrackingStore;
import com.community.groupbuy.service.FulfillmentService;
import com.community.groupbuy.service.GroupBuyRequestService;
import com.community.groupbuy.service.ParcelTrackingService;
import com.community.groupbuy.service.ParcelTrackingSettingsService;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 배송 상태 자동 반영 — 스마트택배 조회(계약 결정 90 · 고친 결정 7). 가짜 조회({@link FakeParcelTracking}) + 실제 PostgreSQL.
 * 설정은 한 행이라 시험마다 정하고 끝나면 끈다. 앞 시험이 남긴 배송완료 전 발송 줄은 시험마다 「추적 완료」로 덮어 이 시험의 줄만 조회되게 한다.
 */
class GroupBuyParcelTrackingTest {

    private static final String KEY = "test-SWEET-key-0001";
    private static GroupBuyTestKit kit;
    private static ParcelTrackingService tracking;
    private static ParcelTrackingSettingsService settings;
    private static ParcelTrackingStore store;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
        tracking = kit.spring.getBean(ParcelTrackingService.class);
        settings = kit.spring.getBean(ParcelTrackingSettingsService.class);
        store = kit.spring.getBean(ParcelTrackingStore.class);
    }

    @BeforeEach
    void reset() {
        kit.reset();
        kit.jdbc.update("UPDATE group_buy_shipments SET tracking_complete = true WHERE delivered_at IS NULL AND tracking_complete = false");
        kit.jdbc.update("UPDATE commerce_tracking_settings SET alert_code = NULL, alert_at = NULL WHERE id = 1");
        settings.saveKey(KEY, kit.operator, "127.0.0.1");
        settings.save(true, 180, false, 10, kit.operator, "127.0.0.1");
        kit.audits.clear();
    }

    @AfterEach
    void off() {
        settings.save(false, 180, false, 10, kit.operator, "127.0.0.1");
        // 이 시험이 남긴 SHIPPED 주문이 다른 시험의 N일 자동 완료·조회에 걸리지 않게
        kit.jdbc.update("UPDATE group_buy_shipments SET tracking_complete = true WHERE delivered_at IS NULL AND tracking_complete = false");
    }

    private String policy() {
        return kit.policy(3_000, 50_000L, 3_000, "ONE_WAY");
    }

    /** 발송까지(CJ — 스마트택배 04) */
    private JoinResult shipped() {
        long gb = kit.spec(policy()).open();
        JoinResult paid = kit.paidOrder(kit.newMember(), gb, 1);
        kit.ship(paid.participationId());
        return paid;
    }

    private FulfillmentStore.ShipmentRow original(JoinResult joined) {
        return kit.spring.getBean(FulfillmentStore.class).original(joined.participationId()).orElseThrow();
    }

    private ParcelTrackingStore.TrackingRow row(long shipmentId) {
        return store.find(shipmentId).orElseThrow();
    }

    private long keyNotices() {
        return kit.operatorNotices.stream().filter(e -> e == AdminNotificationEvent.COMMERCE_TRACKING_KEY_ERROR).count();
    }

    @Test
    void 결정90_중간_단계는_단계와_이력만_적고_다음_조회는_주기_뒤() {
        JoinResult paid = shipped();
        var s = original(paid);
        kit.parcelTracking.plan(s.trackingNo(), FakeParcelTracking.inTransit(3, kit.now()));

        tracking.runOnce();

        assertThat(kit.parcelTracking.calls).anySatisfy(c -> {
            assertThat(c.invoiceNo()).isEqualTo(s.trackingNo());
            assertThat(c.carrierCode()).as("CJ → 스마트택배 04").isEqualTo("04");
            assertThat(c.apiKey()).as("저장한 키를 풀어 쓴다").isEqualTo(KEY);
        });
        var t = row(s.id());
        assertThat(t.level()).isEqualTo(3);
        assertThat(t.complete()).isFalse();
        assertThat(t.checkedAt()).isEqualTo(kit.now());
        assertThat(t.nextAt()).as("설정 주기 180분").isEqualTo(kit.now().plus(Duration.ofMinutes(180)));
        assertThat(t.dayCount()).isEqualTo(1);
        List<ParcelTrackingService.Event> events = ParcelTrackingService.events(t.eventsJson());
        assertThat(events).extracting(ParcelTrackingService.Event::kind).containsExactly("집화처리", "배송중");
        assertThat(events).extracting(ParcelTrackingService.Event::where).containsExactly("서울 강남", "대전 HUB");
        assertThat(t.eventsJson()).doesNotContain("telno").doesNotContain("manName");
        assertThat(kit.participation(paid.participationId()).status()).isEqualTo(ParticipationStatus.SHIPPED);

        tracking.runOnce();
        assertThat(kit.parcelTracking.callsFor(s.trackingNo())).as("다음 조회 시각 전에는 다시 부르지 않는다").isEqualTo(1);
    }

    @Test
    void 결정90_배송_완료면_택배사_완료_시각으로_DELIVERED_이력_시스템_감사_AUTO_구매_확정도_그_시각_기준() {
        JoinResult paid = shipped();
        var s = original(paid);
        Instant completedAt = s.shippedAt().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS);
        kit.parcelTracking.plan(s.trackingNo(), FakeParcelTracking.delivered(s.shippedAt(), completedAt));
        kit.advance(Duration.ofDays(2));

        tracking.runOnce();

        var p = kit.participation(paid.participationId());
        assertThat(p.status()).isEqualTo(ParticipationStatus.DELIVERED);
        assertThat(p.deliveredAt()).as("지금이 아니라 택배사 완료 시각").isEqualTo(completedAt);
        assertThat(original(paid).deliveredAt()).isEqualTo(completedAt);
        var t = row(s.id());
        assertThat(t.level()).isEqualTo(6);
        assertThat(t.complete()).isTrue();
        assertThat(t.completedAt()).isEqualTo(completedAt);
        assertThat(t.holdReason()).isNull();
        Map<String, Object> history = kit.jdbc.queryForMap("SELECT cause, actor_id FROM participation_status_history WHERE participation_id = ? AND to_status = 'DELIVERED'",
                paid.participationId());
        assertThat(history.get("cause")).isEqualTo(FulfillmentService.CAUSE_DELIVER_TRACKING);
        assertThat(history.get("actor_id")).as("행위자 시스템").isNull();
        assertThat(kit.audits).anySatisfy(e -> {
            assertThat(e.action()).isEqualTo(AuditActions.GROUPBUY_FULFILLMENT);
            assertThat(e.actorType()).isEqualTo(AuditActorType.SYSTEM);
            assertThat(e.targetId()).isEqualTo(Long.toString(paid.participationId()));
            assertThat(e.detail()).containsEntry("step", "DELIVER").containsEntry("auto", "TRACKING").containsEntry("via", "BATCH");
        });

        // 결정 14 — 구매 확정 7일은 택배사 완료 시각부터(지금은 완료 + 1일): 5일 더 가면 완료 + 6일(아직), 6일 더 가면 완료 + 7일 넘음
        kit.escrowWorker.runOnce(); // 배송 정보 등록
        kit.advance(Duration.ofDays(5));
        kit.escrowWorker.runOnce();
        assertThat(original(paid).escrowCompletedAt()).isNull();
        kit.advance(Duration.ofDays(1).plusHours(1));
        kit.escrowWorker.runOnce();
        assertThat(original(paid).escrowCompletedAt()).as("운영자 처리였다면(지금 기준) 아직 6일").isNotNull();

        tracking.runOnce();
        assertThat(kit.parcelTracking.callsFor(s.trackingNo())).as("배송완료된 줄은 더 조회하지 않는다").isEqualTo(1);
    }

    @Test
    void 결정90_이미_운영자가_배송완료한_주문은_자동이_아무것도_바꾸지_않는다() {
        JoinResult paid = shipped();
        var s = original(paid);
        kit.deliver(paid.participationId());
        Instant operatorAt = kit.participation(paid.participationId()).deliveredAt();
        int historyBefore = kit.count("SELECT count(*) FROM participation_status_history WHERE participation_id = ?", paid.participationId());
        kit.parcelTracking.plan(s.trackingNo(), FakeParcelTracking.delivered(s.shippedAt(), s.shippedAt().plusSeconds(3600)));
        kit.advance(Duration.ofDays(1));
        kit.audits.clear();

        tracking.runOnce();
        assertThat(kit.parcelTracking.callsFor(s.trackingNo())).as("배송완료된 발송은 대상이 아니다").isZero();
        boolean changed = kit.fulfillment.deliverAt(paid.participationId(), s.shippedAt().plusSeconds(3600), FulfillmentService.AUTO_TRACKING, null, null, null);
        assertThat(changed).isFalse();
        assertThat(kit.participation(paid.participationId()).deliveredAt()).isEqualTo(operatorAt);
        assertThat(kit.count("SELECT count(*) FROM participation_status_history WHERE participation_id = ?", paid.participationId())).isEqualTo(historyBefore);
        assertThat(kit.audits).isEmpty();
        assertThatThrownBy(() -> tracking.refresh(paid.participationId(), kit.operator, "ip")).isInstanceOfSatisfying(GroupBuyException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.TRACKING_NOT_AVAILABLE));
    }

    @Test
    void 결정90_완료_시각이_발송보다_이르거나_미래면_자동_처리하지_않고_사유를_남긴다() {
        JoinResult early = shipped();
        JoinResult future = shipped();
        var se = original(early);
        var sf = original(future);
        kit.parcelTracking.plan(se.trackingNo(), FakeParcelTracking.delivered(se.shippedAt().minus(Duration.ofDays(2)), se.shippedAt().minus(Duration.ofHours(1))));
        kit.parcelTracking.plan(sf.trackingNo(), FakeParcelTracking.delivered(sf.shippedAt(), kit.now().plus(Duration.ofDays(1))));

        tracking.runOnce();

        assertThat(kit.participation(early.participationId()).status()).isEqualTo(ParticipationStatus.SHIPPED);
        assertThat(kit.participation(future.participationId()).status()).isEqualTo(ParticipationStatus.SHIPPED);
        assertThat(row(se.id()).holdReason()).isEqualTo(ParcelTrackingService.HOLD_BEFORE_SHIPPED);
        assertThat(row(sf.id()).holdReason()).isEqualTo(ParcelTrackingService.HOLD_IN_FUTURE);
        assertThat(row(se.id()).complete()).isTrue();
        assertThat(original(early).deliveredAt()).isNull();
        kit.advance(Duration.ofHours(4));
        tracking.runOnce();
        assertThat(kit.parcelTracking.callsFor(se.trackingNo())).as("완료를 받은 줄은 더 자동 조회하지 않는다(운영자 몫)").isEqualTo(1);
    }

    @Test
    void 결정90_송장당_하루_20회를_넘지_않는다_배치는_18회까지() {
        JoinResult paid = shipped();
        var s = original(paid);
        for (int i = 0; i < ParcelTrackingService.DAILY_LIMIT; i++) {
            tracking.refresh(paid.participationId(), kit.operator, "ip");
        }
        assertThatThrownBy(() -> tracking.refresh(paid.participationId(), kit.operator, "ip")).isInstanceOfSatisfying(GroupBuyException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.TRACKING_DAILY_LIMIT));
        assertThat(kit.parcelTracking.callsFor(s.trackingNo())).isEqualTo(ParcelTrackingService.DAILY_LIMIT);

        // 배치는 18회째에서 멈추고 다음 조회를 내일(KST 0시)로
        JoinResult other = shipped();
        var so = original(other);
        kit.jdbc.update("UPDATE group_buy_shipments SET tracking_day = ?, tracking_day_count = ? WHERE id = ?", java.sql.Date.valueOf(ServiceTime.dateOf(kit.now())),
                ParcelTrackingService.AUTO_DAILY_LIMIT, so.id());
        tracking.runOnce();
        assertThat(kit.parcelTracking.callsFor(so.trackingNo())).isZero();
        assertThat(row(so.id()).nextAt()).isEqualTo(ServiceTime.dateOf(kit.now()).plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant());

        // 스마트택배가 105(하루 제한)를 주면 오늘 몫을 다 쓴 것으로
        JoinResult third = shipped();
        var st = original(third);
        kit.parcelTracking.plan(st.trackingNo(), FakeParcelTracking.error("105"));
        tracking.runOnce();
        assertThat(row(st.id()).dayCount()).isEqualTo(ParcelTrackingService.DAILY_LIMIT);
        assertThat(row(st.id()).errorCode()).isEqualTo("105");
    }

    @Test
    void 결정90_104가_이어지면_간격을_늘리고_실패_횟수와_사유를_남긴다() {
        JoinResult paid = shipped();
        var s = original(paid);
        kit.parcelTracking.plan(s.trackingNo(), FakeParcelTracking.error("104"));
        List<Long> gaps = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tracking.runOnce();
            var t = row(s.id());
            assertThat(t.failCount()).isEqualTo(i + 1);
            assertThat(t.errorCode()).isEqualTo("104");
            gaps.add(Duration.between(kit.now(), t.nextAt()).toMinutes());
            kit.advance(Duration.between(kit.now(), t.nextAt()).plusMinutes(1));
        }
        assertThat(gaps).as("1~2번째 주기 · 3번째 ×2 · 4번째 ×4 · 5번째 ×8(최대 24시간)").containsExactly(180L, 180L, 360L, 720L, 1440L);
        assertThat(ParcelTrackingService.backoff(180, 9)).isEqualTo(Duration.ofHours(24));
        assertThat(ParcelTrackingService.backoff(60, 5)).isEqualTo(Duration.ofHours(8));

        kit.parcelTracking.plan(s.trackingNo(), FakeParcelTracking.inTransit(2, kit.now()));
        tracking.runOnce();
        assertThat(row(s.id()).failCount()).as("단계가 오면 0으로").isZero();
        assertThat(row(s.id()).errorCode()).isNull();

        // 스캔 없음(단계 0)도 이어지면 실패로 센다
        JoinResult blank = shipped();
        var sb = original(blank);
        tracking.runOnce();
        assertThat(row(sb.id()).level()).isZero();
        assertThat(row(sb.id()).failCount()).isEqualTo(1);
        assertThat(row(sb.id()).errorCode()).isEqualTo("NO_SCAN");
    }

    @Test
    void 결정90_키_오류는_그_회차를_멈추고_운영자_알림은_한_번() {
        JoinResult a = shipped();
        JoinResult b = shipped();
        var sa = original(a);
        var sb = original(b);
        kit.parcelTracking.plan(sa.trackingNo(), FakeParcelTracking.error("102"));
        kit.parcelTracking.plan(sb.trackingNo(), FakeParcelTracking.error("102"));

        ParcelTrackingService.Round round = tracking.runOnce();

        assertThat(round.keyError()).isEqualTo("102");
        assertThat(kit.parcelTracking.callsFor(sa.trackingNo()) + kit.parcelTracking.callsFor(sb.trackingNo())).as("첫 줄에서 멈춘다").isEqualTo(1);
        assertThat(keyNotices()).isEqualTo(1);
        assertThat(settings.view().keyAlertCode()).isEqualTo("102");

        kit.advance(Duration.ofHours(4));
        tracking.runOnce();
        assertThat(keyNotices()).as("같은 오류는 다시 알리지 않는다").isEqualTo(1);
        assertThatThrownBy(() -> tracking.refresh(b.participationId(), kit.operator, "ip")).isInstanceOfSatisfying(GroupBuyException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.TRACKING_KEY_ERROR));
        assertThat(keyNotices()).isEqualTo(1);

        // 조회가 다시 되면 알림 상태를 지운다 — 다음 키 오류는 다시 알린다
        kit.parcelTracking.plan(sa.trackingNo(), FakeParcelTracking.inTransit(3, kit.now()));
        tracking.refresh(a.participationId(), kit.operator, "ip");
        assertThat(settings.view().keyAlertCode()).isNull();
        kit.parcelTracking.plan(sb.trackingNo(), FakeParcelTracking.error("103"));
        assertThatThrownBy(() -> tracking.refresh(b.participationId(), kit.operator, "ip")).isInstanceOf(GroupBuyException.class);
        assertThat(keyNotices()).isEqualTo(2);
    }

    @Test
    void 결정90_자동_조회_꺼짐_키_없음_코드_없는_택배사는_대상이_아니다() {
        JoinResult paid = shipped();
        var s = original(paid);
        settings.save(false, 180, false, 10, kit.operator, "ip");
        tracking.runOnce();
        assertThat(kit.parcelTracking.callsFor(s.trackingNo())).as("자동 조회 꺼짐").isZero();

        settings.save(true, 180, false, 10, kit.operator, "ip");
        settings.deleteKey(kit.operator, "ip");
        tracking.runOnce();
        assertThat(kit.parcelTracking.callsFor(s.trackingNo())).as("키 없음").isZero();
        assertThatThrownBy(() -> tracking.refresh(paid.participationId(), kit.operator, "ip")).isInstanceOfSatisfying(GroupBuyException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.TRACKING_KEY_MISSING));
        settings.saveKey(KEY, kit.operator, "ip");

        // 스마트택배 코드가 없는 택배사
        kit.jdbc.update("INSERT INTO commerce_carriers (code, name, tracking_url, enabled, sort_order) VALUES ('TRKNONE', '코드 없는 택배', 'https://x.example/{no}', true, 99) "
                + "ON CONFLICT (code) DO NOTHING");
        long gb = kit.spec(policy()).open();
        JoinResult noCode = kit.paidOrder(kit.newMember(), gb, 1);
        assertThat(kit.fulfillment.ship(List.of(new FulfillmentService.ShipLine(noCode.participationId(), "TRKNONE", "8" + Long.toString(kit.ids.nextId()).substring(8))),
                kit.operator, "ip").succeeded()).isEqualTo(1);
        var sn = original(noCode);
        tracking.runOnce();
        assertThat(kit.parcelTracking.callsFor(s.trackingNo())).as("켜고 키가 있으면 조회").isEqualTo(1);
        assertThat(kit.parcelTracking.callsFor(sn.trackingNo())).as("코드 없는 택배사").isZero();
        assertThatThrownBy(() -> tracking.refresh(noCode.participationId(), kit.operator, "ip")).isInstanceOfSatisfying(GroupBuyException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.TRACKING_NOT_AVAILABLE));
    }

    @Test
    void 결정90_발송_뒤_N일_자동_배송완료는_켰을_때만_발송_더하기_N일_시각으로() {
        settings.save(false, 180, false, 10, kit.operator, "ip"); // 자동 조회와 상관없다
        JoinResult paid = shipped();
        Instant shippedAt = original(paid).shippedAt();
        kit.advance(Duration.ofDays(11));
        tracking.runOnce();
        assertThat(kit.participation(paid.participationId()).status()).as("꺼짐").isEqualTo(ParticipationStatus.SHIPPED);

        settings.save(false, 180, true, 10, kit.operator, "ip");
        kit.audits.clear();
        tracking.runOnce();
        var p = kit.participation(paid.participationId());
        assertThat(p.status()).isEqualTo(ParticipationStatus.DELIVERED);
        assertThat(p.deliveredAt()).isEqualTo(shippedAt.plus(Duration.ofDays(10)));
        assertThat(kit.jdbc.queryForObject("SELECT cause FROM participation_status_history WHERE participation_id = ? AND to_status = 'DELIVERED'", String.class,
                paid.participationId())).isEqualTo(FulfillmentService.CAUSE_DELIVER_AFTER_DAYS);
        assertThat(kit.audits).anySatisfy(e -> {
            assertThat(e.targetId()).isEqualTo(Long.toString(paid.participationId()));
            assertThat(e.actorType()).isEqualTo(AuditActorType.SYSTEM);
            assertThat(e.detail()).containsEntry("auto", "AFTER_DAYS").containsEntry("days", 10);
        });

        JoinResult young = shipped();
        kit.advance(Duration.ofDays(9));
        tracking.runOnce();
        assertThat(kit.participation(young.participationId()).status()).as("N일 전").isEqualTo(ParticipationStatus.SHIPPED);
    }

    @Test
    void 결정90_API_키는_암호문으로_저장하고_화면_감사에_값이_없다_설정은_범위와_바뀐_칸만_감사() {
        String secret = "plain-SECRET-abc123";
        var view = settings.saveKey("  " + secret + "  ", kit.operator, "ip");
        assertThat(view.apiKeyConfigured()).isTrue();
        assertThat(view.toString()).doesNotContain(secret);
        String cipher = kit.jdbc.queryForObject("SELECT api_key_cipher FROM commerce_tracking_settings WHERE id = 1", String.class);
        assertThat(cipher).startsWith("v1:").doesNotContain(secret);
        assertThat(settings.apiKey()).contains(secret);
        assertThat(kit.audits).anySatisfy(e -> {
            assertThat(e.action()).isEqualTo(AuditActions.COMMERCE_TRACKING_KEY_UPDATE);
            assertThat(e.toString()).doesNotContain(secret).doesNotContain(cipher);
        });
        assertThatThrownBy(() -> settings.saveKey("  ", kit.operator, "ip")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> settings.saveKey("a b", kit.operator, "ip")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> settings.saveKey("k".repeat(201), kit.operator, "ip")).isInstanceOf(ValidationException.class);

        var usage = settings.usage();
        assertThat(usage.outcome()).isEqualTo("OK");
        assertThat(usage.leftAmount()).isEqualTo(9_000L);

        kit.audits.clear();
        settings.deleteKey(kit.operator, "ip");
        assertThat(settings.view().apiKeyConfigured()).isFalse();
        assertThat(kit.audits).extracting(e -> e.action()).containsExactly(AuditActions.COMMERCE_TRACKING_KEY_DELETE);
        assertThatThrownBy(settings::usage).isInstanceOfSatisfying(GroupBuyException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.TRACKING_KEY_MISSING));

        kit.audits.clear();
        assertThatThrownBy(() -> settings.save(true, 59, false, 10, kit.operator, "ip")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> settings.save(true, 180, true, 31, kit.operator, "ip")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> settings.save(null, 180, true, 10, kit.operator, "ip")).isInstanceOf(ValidationException.class);
        settings.save(true, 240, false, 10, kit.operator, "ip");
        assertThat(kit.audits).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo(AuditActions.COMMERCE_TRACKING_SETTINGS_UPDATE);
            assertThat(e.detail().get("after")).isEqualTo(Map.of("intervalMinutes", 240));
        });
        assertThat(ParcelTrackingSettingsService.defaults()).isEqualTo(new ParcelTrackingSettingsService.Values(false, 180, false, 10));
    }

    @Test
    void 결정90_지금_조회는_바로_반영하고_운영자가_행위자_교환_재발송은_그_발송_줄만() {
        JoinResult paid = shipped();
        var s = original(paid);
        kit.parcelTracking.plan(s.trackingNo(), FakeParcelTracking.inTransit(5, kit.now()));
        settings.save(false, 180, false, 10, kit.operator, "ip"); // 자동 조회가 꺼져도 [지금 조회]는 된다
        assertThat(tracking.refresh(paid.participationId(), kit.operator, "ip")).containsExactly(ParcelTrackingService.Step.UPDATED);
        assertThat(row(s.id()).level()).isEqualTo(5);

        kit.advance(Duration.ofHours(2));
        Instant completedAt = kit.now().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.SECONDS);
        kit.parcelTracking.plan(s.trackingNo(), FakeParcelTracking.delivered(s.shippedAt(), completedAt));
        assertThat(tracking.refresh(paid.participationId(), kit.operator, "ip")).containsExactly(ParcelTrackingService.Step.DELIVERED);
        assertThat(kit.participation(paid.participationId()).deliveredAt()).isEqualTo(completedAt);
        assertThat(kit.jdbc.queryForObject("SELECT actor_id FROM participation_status_history WHERE participation_id = ? AND to_status = 'DELIVERED'", Long.class,
                paid.participationId())).isEqualTo(kit.operator);

        // 교환 재발송 — 주문은 DELIVERED 그대로, 그 발송 줄의 배송완료 시각만
        settings.save(true, 180, false, 10, kit.operator, "ip");
        long gb = kit.spec(policy()).open();
        long sku = kit.mainSku(gb);
        long member = kit.newMember();
        JoinResult order = kit.delivered(member, gb, lines(sku, 1));
        var created = kit.requests.create(member, order.participationId(), new GroupBuyRequestService.RequestInput(RequestType.RETURN, CollectionMethod.COMPANY_PICKUP,
                Resolution.EXCHANGE, List.of(new GroupBuyRequestService.LineInput(sku, 1, RequestReason.DEFECT)), "켜지지 않아요", null, null));
        kit.requests.approve(created.requestId(), null, kit.operator, "ip");
        kit.requests.recordPickup(created.requestId(), "CJ", "777766665555", kit.operator);
        kit.requests.receive(created.requestId(), kit.operator);
        String exchangeNo = "5" + Long.toString(kit.ids.nextId()).substring(8);
        kit.requests.exchangeShip(created.requestId(), "CJ", exchangeNo, List.of(new GroupBuyRequestService.DispositionInput(sku, ReturnDisposition.RETURN_TO_SUPPLIER, 1)),
                0, false, kit.operator, "ip");
        Instant orderDeliveredAt = kit.participation(order.participationId()).deliveredAt();
        var exchange = kit.spring.getBean(FulfillmentStore.class).shipmentsOf(order.participationId()).stream().filter(x -> x.kind().equals("EXCHANGE")).findFirst()
                .orElseThrow();
        Instant exchangeDone = exchange.shippedAt().plus(Duration.ofHours(20)).truncatedTo(ChronoUnit.SECONDS);
        kit.parcelTracking.plan(exchangeNo, FakeParcelTracking.delivered(exchange.shippedAt(), exchangeDone));
        kit.advance(Duration.ofDays(1));
        tracking.runOnce();
        assertThat(kit.spring.getBean(FulfillmentStore.class).shipment(exchange.id()).orElseThrow().deliveredAt()).isEqualTo(exchangeDone);
        assertThat(kit.participation(order.participationId()).status()).isEqualTo(ParticipationStatus.DELIVERED);
        assertThat(kit.participation(order.participationId()).deliveredAt()).as("주문의 배송완료 시각은 처음 것 그대로").isEqualTo(orderDeliveredAt);
        assertThat(kit.audits).anySatisfy(e -> assertThat(e.detail()).containsEntry("step", "EXCHANGE_DELIVER").containsEntry("auto", "TRACKING"));
    }

    @Test
    void 결정90_택배사_스마트택배_코드는_목록_통째_저장에_함께_숫자_영문_1에서_10자() {
        List<FulfillmentStore.CarrierRow> before = kit.fulfillment.carriers();
        assertThat(before).filteredOn(c -> List.of("CJ", "HANJIN", "LOTTE", "EPOST", "LOGEN", "KDEXP").contains(c.code()))
                .extracting(FulfillmentStore.CarrierRow::code, FulfillmentStore.CarrierRow::sweetTrackerCode)
                .contains(org.assertj.core.groups.Tuple.tuple("CJ", "04"), org.assertj.core.groups.Tuple.tuple("HANJIN", "05"), org.assertj.core.groups.Tuple.tuple("LOTTE", "08"),
                        org.assertj.core.groups.Tuple.tuple("EPOST", "01"), org.assertj.core.groups.Tuple.tuple("LOGEN", "06"), org.assertj.core.groups.Tuple.tuple("KDEXP", "23"));
        List<FulfillmentStore.CarrierRow> bad = new ArrayList<>(before);
        bad.add(new FulfillmentStore.CarrierRow("TRKBAD", "잘못", "https://x.example/{no}", true, 98, "0-4"));
        assertThatThrownBy(() -> kit.fulfillment.saveCarriers(bad, kit.operator, "ip")).isInstanceOf(ValidationException.class);
        List<FulfillmentStore.CarrierRow> next = new ArrayList<>(before.stream().filter(c -> !c.code().equals("TRKNEW")).toList());
        next.add(new FulfillmentStore.CarrierRow("TRKNEW", "새 택배", "https://x.example/{no}", true, 97, "99"));
        kit.fulfillment.saveCarriers(next, kit.operator, "ip");
        assertThat(kit.fulfillment.carriers()).anySatisfy(c -> {
            assertThat(c.code()).isEqualTo("TRKNEW");
            assertThat(c.sweetTrackerCode()).isEqualTo("99");
        });
        kit.fulfillment.saveCarriers(before.stream().filter(c -> !c.code().equals("TRKNEW")).toList(), kit.operator, "ip");
    }
}
