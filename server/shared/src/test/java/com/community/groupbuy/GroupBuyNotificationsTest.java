package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.lines;
import static org.assertj.core.api.Assertions.assertThat;

import com.community.groupbuy.dto.GroupBuyEvents;
import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.entity.QtyMovementKind;
import com.community.groupbuy.entity.RequestType;
import com.community.groupbuy.entity.WatchKind;
import com.community.groupbuy.service.GroupBuyBusinessDays;
import com.community.groupbuy.service.GroupBuyRequestService;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.entity.NotificationSeverity;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 커머스 2b-4 회원 알림·메일·운영자 알림(계약 결정 12·16·31·40·52·53·57·81)과 1시간 기한 점검(P35·결정 51·마감 임박) — G8·G21·G37 의 2b-4 몫, P27(메일 대기열).
 * 알림 행은 {@code member_notifications}(종류 GROUP_BUY · 덧붙임 kind), 메일은 {@code mail_outbox}, 운영자 알림은 시험 키트가 모은다.
 */
class GroupBuyNotificationsTest {

    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
        kit.jdbc.update("UPDATE commerce_business_info SET company_name = '시험상회', representative = '홍길동', business_no = '123-45-67891', mail_order_no = '2026-서울-0001', "
                + "mail_order_authority = '서울특별시 강남구', address = '서울특별시 강남구 1', phone = '02-000-0000', email = 'help@test.local' WHERE id = 1");
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    private int notices(long userId, String kind) {
        return kit.count("SELECT count(*) FROM member_notifications WHERE user_id = ? AND type = 'GROUP_BUY' AND payload ->> 'kind' = ?", userId, kind);
    }

    private int mails(long userId, String mailKind) {
        return kit.count("SELECT count(*) FROM mail_outbox WHERE user_id = ? AND mail_kind = ?", userId, mailKind);
    }

    private Map<String, Object> mail(long userId, String mailKind) {
        return kit.jdbc.queryForMap("SELECT dedupe_key, variables::text AS variables FROM mail_outbox WHERE user_id = ? AND mail_kind = ? ORDER BY created_at DESC LIMIT 1",
                userId, mailKind);
    }

    private void watch(long userId, long groupBuyId, WatchKind kind, Long skuId) {
        kit.jdbc.update("INSERT INTO group_buy_watches (id, group_buy_id, user_id, kind, sku_id, created_at) VALUES (?, ?, ?, ?, ?, now())", kit.ids.nextId(), groupBuyId,
                userId, kind.name(), skuId);
    }

    private void inTx(Runnable work) {
        kit.tx.executeWithoutResult(status -> work.run());
    }

    // ── 주문·환불 ───────────────────────────────────────────────────────────

    @Test
    void 결제가_확정되면_주문_확인_알림과_계약_내용_통지_메일이_대기열에_한_통_결정57_P27() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).price(25_000).perUser(3).option("블랙", 0, 10).open();
        long m = kit.newMember();
        JoinResult o = kit.paidOrder(m, gb, lines(kit.skuId(gb, "블랙"), 2));

        assertThat(notices(m, "PAID")).isEqualTo(1);
        assertThat(mails(m, "GROUP_BUY_ORDER_CONFIRMED")).as("메일 서버와 무관하게 결제 확정 트랜잭션에서 대기열에 남는다(P27)").isEqualTo(1);
        Map<String, Object> mail = mail(m, "GROUP_BUY_ORDER_CONFIRMED");
        assertThat(mail.get("dedupe_key")).isEqualTo("GB_ORDER_CONFIRMED:" + o.participationId());
        assertThat((String) mail.get("variables")).contains(o.orderNo()).contains("블랙 × 2").contains("50,000").contains("시험상회").contains("123-45-67891")
                .contains("1인 3개").contains("선착순").contains("영업일 안에 발송");

        // 같은 주문을 다시 처리해도 메일은 한 통(대기열 중복 열쇠)
        inTx(() -> kit.notifications.on(new GroupBuyEvents.OrderPaid(o.participationId(), gb, m)));
        assertThat(mails(m, "GROUP_BUY_ORDER_CONFIRMED")).isEqualTo(1);
        kit.assertInvariants(gb);
    }

    @Test
    void 환불이_끝나면_환불_완료_알림과_메일_결정16() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).price(25_000).open();
        long m = kit.newMember();
        JoinResult o = kit.paidOrder(m, gb, 1);
        kit.cancels.cancelByMember(m, o.participationId(), List.of());
        kit.settleCancels();

        assertThat(notices(m, "REFUNDED")).isEqualTo(1);
        assertThat(mails(m, "GROUP_BUY_REFUND_COMPLETED")).isEqualTo(1);
        assertThat((String) mail(m, "GROUP_BUY_REFUND_COMPLETED").get("variables")).contains("28,000").contains("결제 취소");
    }

    @Test
    void 불변식이_깨진_거절은_회원_안내와_운영자_주문_이상_도메인_거절은_회원_안내만_결정12_40() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).price(25_000).open();
        long m = kit.newMember();
        JoinResult o = kit.order(m, gb, 1);

        inTx(() -> kit.notifications.on(new GroupBuyEvents.OrderRejected(o.participationId(), gb, m, "GROUP_BUY_CLOSED", false)));
        assertThat(kit.operatorNotices).doesNotContain(AdminNotificationEvent.COMMERCE_ORDER_ANOMALY);
        inTx(() -> kit.notifications.on(new GroupBuyEvents.OrderRejected(o.participationId(), gb, m, "GROUP_BUY_QTY_ANOMALY", true)));
        assertThat(kit.operatorNotices).containsExactly(AdminNotificationEvent.COMMERCE_ORDER_ANOMALY);
        assertThat(kit.operatorContexts.getFirst().dedupeKey()).isEqualTo(Long.toString(o.participationId()));
        assertThat(notices(m, "REJECTED")).isEqualTo(2);
        assertThat(mails(m, "GROUP_BUY_ORDER_NOTICE")).as("같은 주문의 자동 취소 안내 메일은 한 통").isEqualTo(1);
    }

    // ── 수량 풀림·연장·차수·마감 임박 ─────────────────────────────────────────

    @Test
    void 수량_풀림은_공구와_단품이_둘_다_남을_때만_같은_신청은_10분에_한_번_결정81() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).qty(0, 2).option("A", 0, 5).option("B", 0, 5).open();
        long a = kit.skuId(gb, "A");
        long watcherSku = kit.newMember();
        long watcherGroup = kit.newMember();
        watch(watcherSku, gb, WatchKind.RESTOCK, a);
        watch(watcherGroup, gb, WatchKind.RESTOCK, null);
        long buyer = kit.newMember();
        kit.order(buyer, gb, lines(a, 2)); // 공구 최대 2 를 선점 — 공구 남은 0

        inTx(() -> kit.notifications.on(new GroupBuyEvents.QtyReleased(gb, a, QtyMovementKind.RELEASE)));
        assertThat(notices(watcherSku, "RESTOCK")).as("공구 남은 수량 0 이면 보내지 않는다").isZero();
        assertThat(notices(watcherGroup, "RESTOCK")).isZero();

        kit.advance(Duration.ofMinutes(6));
        kit.holds.expireOverdue(100); // 선점 만료 → RELEASE → 수량 풀림
        assertThat(notices(watcherSku, "RESTOCK")).isEqualTo(1);
        assertThat(notices(watcherGroup, "RESTOCK")).isEqualTo(1);

        inTx(() -> kit.notifications.on(new GroupBuyEvents.QtyReleased(gb, a, QtyMovementKind.CANCEL)));
        assertThat(notices(watcherSku, "RESTOCK")).as("10분 안에는 다시 보내지 않는다").isEqualTo(1);
        kit.advance(Duration.ofMinutes(11));
        inTx(() -> kit.notifications.on(new GroupBuyEvents.QtyReleased(gb, a, QtyMovementKind.CANCEL)));
        assertThat(notices(watcherSku, "RESTOCK")).isEqualTo(2);
        assertThat(notices(watcherGroup, "RESTOCK")).isEqualTo(2);
        kit.assertInvariants(gb);
    }

    @Test
    void 모집이_연장되면_마감_임박_수량_풀림_신청자에게_한_번씩() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).open();
        long both = kit.newMember();
        long closing = kit.newMember();
        watch(both, gb, WatchKind.CLOSING_SOON, null);
        watch(both, gb, WatchKind.RESTOCK, null);
        watch(closing, gb, WatchKind.CLOSING_SOON, null);

        inTx(() -> kit.notifications.on(new GroupBuyEvents.Extended(gb, kit.now().plus(Duration.ofMinutes(30)))));
        assertThat(notices(both, "EXTENDED")).as("같은 회원 한 번").isEqualTo(1);
        assertThat(notices(closing, "EXTENDED")).isEqualTo(1);
    }

    @Test
    void G8_추가_차수가_모집을_시작하면_루트의_이전_신청자에게_같은_회원_한_번() {
        long first = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).open();
        kit.admin.closeEarly(first, "1차 조기 마감", kit.operator, "ip");
        long nextRound = kit.newMember();
        long restock = kit.newMember();
        watch(nextRound, first, WatchKind.NEXT_ROUND, null);
        watch(nextRound, first, WatchKind.CLOSING_SOON, null);
        watch(restock, first, WatchKind.RESTOCK, null);

        var round = kit.admin.createRound(first, kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofDays(3)), null, kit.operator, "ip");
        kit.admin.publish(round.groupBuyId(), kit.group(round.groupBuyId()).version(), kit.operator, "ip");

        assertThat(notices(nextRound, "ROUND_STARTED")).isEqualTo(1);
        assertThat(notices(restock, "ROUND_STARTED")).isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM member_notifications WHERE type = 'GROUP_BUY' AND payload ->> 'kind' = 'ROUND_STARTED' AND payload ->> 'path' = ?",
                "/group-buys/" + round.groupBuyId())).as("새 차수로 가는 길").isEqualTo(2);
    }

    @Test
    void 마감_임박_알림은_마감_24시간_안_모집_중_공구의_신청자에게_한_번() {
        long soon = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).period(kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofHours(20))).open();
        long later = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).period(kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofDays(3))).open();
        long m = kit.newMember();
        watch(m, soon, WatchKind.CLOSING_SOON, null);
        watch(m, later, WatchKind.CLOSING_SOON, null);

        kit.deadlineWatch.runOnce();
        kit.deadlineWatch.runOnce();
        assertThat(notices(m, "CLOSING_SOON")).isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM member_notifications WHERE user_id = ? AND payload ->> 'groupBuyId' = ?", m, Long.toString(later))).isZero();
    }

    // ── 발송 지연·공급 불가·중단·요청 ────────────────────────────────────────

    @Test
    void 발송_지연_공지는_공지_전에_결제한_발송_전_주문_전원에게_앱과_메일_결정52() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).open();
        long a = kit.newMember();
        long b = kit.newMember();
        long shipped = kit.newMember();
        kit.paidOrder(a, gb, 1);
        kit.paidOrder(b, gb, 1);
        JoinResult s = kit.paidOrder(shipped, gb, 1);
        kit.ship(s.participationId());

        kit.admin.announceDelay(gb, ServiceTime.dateOf(kit.now()).plusDays(10), "원물 수급 지연", kit.operator, "ip");
        assertThat(notices(a, "DELAY")).isEqualTo(1);
        assertThat(notices(b, "DELAY")).isEqualTo(1);
        assertThat(notices(shipped, "DELAY")).as("이미 발송한 주문은 빼고").isZero();
        assertThat(mails(a, "GROUP_BUY_ORDER_NOTICE")).isEqualTo(1);
        assertThat((String) mail(a, "GROUP_BUY_ORDER_NOTICE").get("variables")).contains(ServiceTime.dateOf(kit.now()).plusDays(10).toString());
    }

    @Test
    void 공급_불가로_선택을_물으면_앱과_메일_결정53() {
        long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).option("A", 0, 10).option("B", 0, 10).open();
        long m = kit.newMember();
        kit.paidOrder(m, gb, lines(kit.skuId(gb, "A"), 1, kit.skuId(gb, "B"), 1));
        kit.supply.markFailure(gb, kit.skuId(gb, "A"), "공급처 단종", kit.operator, "ip");

        assertThat(notices(m, "SUPPLY_CHOICE")).isEqualTo(1);
        assertThat(mails(m, "GROUP_BUY_ORDER_NOTICE")).isEqualTo(1);
    }

    @Test
    void 중단은_진행_중_주문의_회원과_운영자에게_결정12() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).open();
        long m = kit.newMember();
        kit.paidOrder(m, gb, 1);
        kit.admin.cancel(gb, "공급처 사정으로 중단", kit.operator, "ip");

        assertThat(notices(m, "GROUP_CANCELED")).isEqualTo(1);
        assertThat(kit.operatorNotices).contains(AdminNotificationEvent.COMMERCE_GROUPBUY_CANCELED);
    }

    @Test
    void 요청이_접수되면_운영자_요청_알림_본인_철회는_회원_알림_없음() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).open();
        long m = kit.newMember();
        JoinResult o = kit.paidOrder(m, gb, 1);
        kit.fulfillment.prepare(List.of(o.participationId()), kit.operator, "ip");
        var created = kit.requests.create(m, o.participationId(), new GroupBuyRequestService.RequestInput(RequestType.CANCEL, null, null,
                List.of(new GroupBuyRequestService.LineInput(kit.mainSku(gb), 1, null)), "마음이 바뀌었어요", List.of(), null));

        assertThat(kit.operatorNotices).contains(AdminNotificationEvent.COMMERCE_REFUND_REQUESTED);
        assertThat(notices(m, "REQUEST_REQUESTED")).isEqualTo(1);
        kit.requests.withdraw(m, created.requestId());
        assertThat(kit.count("SELECT count(*) FROM member_notifications WHERE user_id = ? AND payload ->> 'kind' LIKE 'REQUEST_WITHDRAWN'", m)).isZero();
    }

    // ── 1시간 기한 점검 ──────────────────────────────────────────────────────

    @Test
    void G21_P35_휴일이_낀_요청_처리_기한은_2영업일_경고_3영업일_심각_단계마다_한_번() {
        LocalDate today = ServiceTime.dateOf(kit.now());
        LocalDate holiday = today.minusDays(1);
        while (holiday.getDayOfWeek() == DayOfWeek.SATURDAY || holiday.getDayOfWeek() == DayOfWeek.SUNDAY) {
            holiday = holiday.minusDays(1);
        }
        List<LocalDate> holidays = List.of(holiday);
        kit.jdbc.update("UPDATE group_buy_settings SET holidays = ARRAY[?::date] WHERE id = 1", holiday.toString());
        try {
            long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).open();
            long m = kit.newMember();
            JoinResult o = kit.paidOrder(m, gb, 1);
            kit.fulfillment.prepare(List.of(o.participationId()), kit.operator, "ip");
            var created = kit.requests.create(m, o.participationId(), new GroupBuyRequestService.RequestInput(RequestType.CANCEL, null, null,
                    List.of(new GroupBuyRequestService.LineInput(kit.mainSku(gb), 1, null)), "단순 변심", List.of(), null));
            // 휴일을 건너뛰고 2영업일이 지난 날(휴일이 없었다면 3영업일 — 심각이었을 날)
            LocalDate start = today.minusDays(1);
            while (GroupBuyBusinessDays.elapsed(start, today, holidays) < 2) {
                start = start.minusDays(1);
            }
            assertThat(GroupBuyBusinessDays.elapsed(start, today, List.of())).as("휴일이 없으면 하루 더 센다").isGreaterThan(2);
            backdate(created.requestId(), start);
            kit.operatorNotices.clear();
            kit.operatorContexts.clear();

            kit.deadlineWatch.runOnce();
            kit.deadlineWatch.runOnce();
            List<NotificationSeverity> levels = levels();
            assertThat(levels).as("2영업일 — 경고 한 번(다시 돌려도 한 번)").containsExactly(NotificationSeverity.WARNING);

            LocalDate earlier = start.minusDays(1);
            while (GroupBuyBusinessDays.elapsed(earlier, today, holidays) < 3) {
                earlier = earlier.minusDays(1);
            }
            backdate(created.requestId(), earlier);
            kit.deadlineWatch.runOnce();
            kit.deadlineWatch.runOnce();
            assertThat(levels()).as("3영업일 — 심각 한 번 더").containsExactly(NotificationSeverity.WARNING, NotificationSeverity.CRITICAL);
            assertThat(kit.jdbc.queryForObject("SELECT deadline_alert_level FROM group_buy_requests WHERE id = ?", String.class, created.requestId())).isEqualTo("CRITICAL");
        } finally {
            kit.jdbc.update("UPDATE group_buy_settings SET holidays = '{}' WHERE id = 1");
        }
    }

    private void backdate(long requestId, LocalDate day) {
        Instant at = day.atTime(12, 0).atZone(ServiceTime.ZONE).toInstant();
        kit.jdbc.update("UPDATE group_buy_requests SET created_at = ? WHERE id = ?", Timestamp.from(at), requestId);
    }

    private List<NotificationSeverity> levels() {
        List<NotificationSeverity> out = new ArrayList<>();
        for (int i = 0; i < kit.operatorNotices.size(); i++) {
            if (kit.operatorNotices.get(i) == AdminNotificationEvent.COMMERCE_REFUND_REQUESTED && kit.operatorContexts.get(i).dedupeKey().startsWith("deadline:")) {
                out.add(kit.operatorContexts.get(i).severity());
            }
        }
        return out;
    }

    @Test
    void G37_발송_기한_지난_발송_전_주문은_경고_발송_지연_공지의_새_예정일이_기한이_된다_결정51() {
        long gb = kit.spec(kit.policy(3_000, null, 3_000, "ONE_WAY")).open();
        long late = kit.newMember();
        long fresh = kit.newMember();
        JoinResult l = kit.paidOrder(late, gb, 1);
        JoinResult f = kit.paidOrder(fresh, gb, 1);
        kit.jdbc.update("UPDATE group_buy_participations SET confirmed_at = confirmed_at - interval '15 days', joined_at = joined_at - interval '15 days' WHERE id = ?",
                l.participationId());

        List<Long> overdue = kit.shipDeadlines.overdue(kit.now(), 1_000_000).stream().map(d -> d.participationId()).toList();
        assertThat(overdue).contains(l.participationId()).doesNotContain(f.participationId());
        kit.deadlineWatch.runOnce();
        int at = kit.operatorNotices.indexOf(AdminNotificationEvent.COMMERCE_SHIP_OVERDUE);
        assertThat(at).isGreaterThanOrEqualTo(0);
        assertThat(kit.operatorContexts.get(at).dedupeKey()).as("하루 한 건").isEqualTo(ServiceTime.dateOf(kit.now()).toString());

        // 발송 지연 공지 뒤에는 새 예정일 다음 날이 기한(그 전에 결제한 주문)
        kit.admin.announceDelay(gb, ServiceTime.dateOf(kit.now()).plusDays(5), "입고 지연", kit.operator, "ip");
        assertThat(kit.shipDeadlines.dueAt(l.participationId())).isAfter(kit.now());
    }
}
