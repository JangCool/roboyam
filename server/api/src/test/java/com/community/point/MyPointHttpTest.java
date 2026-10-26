package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.auth.dto.AuthUser;
import com.community.commerce.CommerceApiTestBase;
import com.community.point.entity.PointKind;
import com.community.point.entity.PointLotKind;
import com.community.point.entity.PointReason;
import com.community.point.repository.master.PointLedgerWriter;
import com.community.point.service.PointPolicy;
import com.community.point.service.PointService;
import com.community.testsupport.PointInvariants;
import com.community.user.entity.Role;
import com.community.zconfiguration.time.ServiceTime;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 내 포인트 요약·내역(커머스 1-1 — commerce-stage1-contract 「1-1 화면 확정과 서버 세부」 API) — 요약의 무상·유상·충전 여부·소멸 예정(KST 날짜·그날 합, 지난 묶음 제외)·미회수,
 * 내역의 새 종류 거르기(사용 취소·소멸)·{@code restoredUntil}·글이 아닌 대상의 {@code target}. 공개 사이트 앱 문맥 + 실제 PostgreSQL + MockMvc(로그인 주체를 요청에 싣는다).
 * 시험 데이터는 실제 {@link PointService} 로 만들고(옮길 수 있는 시계 — 지난 묶음을 기다리지 않는다) 불변식(commerce.md 5-5)을 본다.
 */
class MyPointHttpTest extends CommerceApiTestBase {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private PointService points;
    @Autowired
    private PointLedgerWriter writer;
    @Autowired
    private PointPolicy policy;

    private MockMvc mvc;
    private long operator;
    private Instant now;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        operator = newOperator();
        now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        clock.set(now);
    }

    private static MockHttpServletRequestBuilder as(long memberId, MockHttpServletRequestBuilder request) {
        AuthUser user = new AuthUser(memberId, Role.USER);
        return request.principal(new UsernamePasswordAuthenticationToken(user, null, user.authorities())).header("Accept-Language", "ko");
    }

    // ── 데이터 만들기(각각 한 트랜잭션, 시계를 그 시각으로 옮겼다가 되돌린다) ─────────────

    /** {@code at} 에 운영 지급(+) — 그 지급이 만든 무상 묶음 ID */
    private long grant(long member, int amount, Instant at) {
        clock.set(at);
        try {
            tx.executeWithoutResult(s -> points.adjust(member, amount, "시험 지급", operator));
            Long line = jdbc.queryForObject("SELECT max(id) FROM point_ledger WHERE user_id = ? AND reason = 'ADMIN_ADJUST' AND kind = 'EARN'", Long.class, member);
            return jdbc.queryForObject("SELECT id FROM point_lots WHERE source_ledger_id = ?", Long.class, line);
        } finally {
            clock.set(now);
        }
    }

    private long use(long member, long amount, Instant at) {
        clock.set(at);
        try {
            long purchase = ids.nextId();
            return tx.execute(s -> points.use(member, amount, PointReason.POINT_SHOP_PURCHASE, PointService.REF_POINT_SHOP_PURCHASE, purchase));
        } finally {
            clock.set(now);
        }
    }

    private long cancel(long useLine, Instant at) {
        clock.set(at);
        try {
            long purchase = jdbc.queryForObject("SELECT ref_id FROM point_ledger WHERE id = ?", Long.class, useLine);
            return tx.execute(s -> points.cancelUse(useLine, PointReason.POINT_SHOP_REFUND, PointService.REF_POINT_SHOP_PURCHASE, purchase));
        } finally {
            clock.set(now);
        }
    }

    /** 유상 충전을 흉내 낸다(3단계 기능이 아직 없다) — 서비스와 같은 순서로 잔액 행을 잠그고 원장 줄 + 유상 묶음 + 반영 */
    private void chargePaid(long member, long amount, Instant at) {
        tx.executeWithoutResult(s -> {
            PointLedgerWriter.Balance balance = writer.lockBalance(member, at);
            long line = ids.nextId();
            writer.insertEntry(line, member, PointKind.EARN, PointReason.CHARGE.name(), (int) amount, 0, amount, balance.free() + balance.paid() + amount, null, null,
                    null, null, at);
            long lot = ids.nextId();
            writer.insertLot(new PointLedgerWriter.NewLot(lot, member, PointLotKind.PAID, amount, null, PointReason.CHARGE.name(), null, null, line, null, at));
            writer.insertEntryLot(line, lot, amount);
            writer.saveBalance(member, balance.free(), balance.paid() + amount, balance.debt(), at);
        });
    }

    /** 그 KST 날짜의 그 시각(한국 시간)에 소멸하도록 — 무상 묶음 유효기간만큼 앞에서 지급할 시각 */
    private Instant grantedToExpireAt(LocalDate date, int hour) {
        Instant expiresAt = date.atTime(hour, 0).atZone(ServiceTime.ZONE).toInstant();
        return expiresAt.minus(Duration.ofDays(policy.lots().freeValidDays()));
    }

    private void assertInvariants(long... members) {
        assertThat(PointInvariants.violations(jdbc, members)).as("포인트 불변식(commerce.md 5-5)").isEmpty();
    }

    private ResultActions summary(long member) throws Exception {
        return mvc.perform(as(member, get("/api/me/points"))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    }

    private ResultActions ledger(long member, String kind) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/me/points/ledger");
        if (kind != null) {
            request = request.param("kind", kind);
        }
        return mvc.perform(as(member, request));
    }

    // ── 요약 ────────────────────────────────────────────────────────────────

    @Test
    void 요약은_무상_유상_합계와_충전_꺼짐_소멸_예정은_가장_가까운_KST_날짜의_합() throws Exception {
        long member = newMember();
        LocalDate day = ServiceTime.dateOf(now).plusDays(60);
        // 같은 KST 날짜(01시·23시)에 소멸할 묶음 둘 + 다음 날 02시 묶음 + 이미 지난 묶음(아직 소멸 처리 전)
        grant(member, 100, grantedToExpireAt(day, 1));
        grant(member, 50, grantedToExpireAt(day, 23));
        grant(member, 7, grantedToExpireAt(day.plusDays(1), 2));
        grant(member, 30, now.minus(Duration.ofDays(policy.lots().freeValidDays() + 5L)));
        chargePaid(member, 400, now.minusSeconds(5));
        assertInvariants(member);

        summary(member)
                .andExpect(jsonPath("$.data.balance").value(587))
                .andExpect(jsonPath("$.data.freeBalance").value(187))
                .andExpect(jsonPath("$.data.paidBalance").value(400))
                .andExpect(jsonPath("$.data.chargeAvailable").value(false))
                .andExpect(jsonPath("$.data.expiring.date").value(day.toString()))
                .andExpect(jsonPath("$.data.expiring.amount").value(150))
                .andExpect(jsonPath("$.data.revokeDebt").value(0))
                .andExpect(jsonPath("$.data.ticketCount").value(0))
                .andExpect(jsonPath("$.data.today.date").exists())
                .andExpect(jsonPath("$.data.rules").exists());
    }

    @Test
    void 소멸_예정에서_지난_묶음과_다_쓴_묶음은_빠지고_없으면_null() throws Exception {
        long member = newMember();
        summary(member).andExpect(jsonPath("$.data.balance").value(0)).andExpect(jsonPath("$.data.freeBalance").value(0))
                .andExpect(jsonPath("$.data.expiring").isEmpty());

        // 지난 묶음만 남은 회원 — 잔액에는 있지만(소멸 배치 전) 「예정」은 아니다
        grant(member, 30, now.minus(Duration.ofDays(policy.lots().freeValidDays() + 1L)));
        summary(member).andExpect(jsonPath("$.data.freeBalance").value(30)).andExpect(jsonPath("$.data.expiring").isEmpty());

        // 가까운 묶음을 다 쓰면 그다음 날짜가 예정이 된다(사용이 지난 묶음을 먼저 소멸 처리한다)
        LocalDate first = ServiceTime.dateOf(now).plusDays(10);
        LocalDate second = ServiceTime.dateOf(now).plusDays(20);
        grant(member, 40, grantedToExpireAt(first, 12));
        grant(member, 60, grantedToExpireAt(second, 12));
        summary(member).andExpect(jsonPath("$.data.expiring.date").value(first.toString())).andExpect(jsonPath("$.data.expiring.amount").value(40));
        use(member, 40, now);
        summary(member).andExpect(jsonPath("$.data.freeBalance").value(60))
                .andExpect(jsonPath("$.data.expiring.date").value(second.toString())).andExpect(jsonPath("$.data.expiring.amount").value(60));
        assertInvariants(member);
    }

    @Test
    void 미회수는_잔액_행의_빚이다() throws Exception {
        long member = newMember();
        long post = ids.nextId();
        Long earned = tx.execute(s -> points.earn(member, PointReason.POST, post));
        assertThat(earned).as("글 적립(시험 DB 정책)").isPositive();
        tx.executeWithoutResult(s -> points.adjust(member, -earned.intValue(), "시험 차감", operator));
        Long revoked = tx.execute(s -> points.revokeContent(member, PointService.REF_POST, post));
        assertThat(revoked).isZero();
        assertInvariants(member);

        summary(member).andExpect(jsonPath("$.data.revokeDebt").value(earned.intValue())).andExpect(jsonPath("$.data.balance").value(0))
                .andExpect(jsonPath("$.data.expiring").isEmpty());
    }

    // ── 내역 ────────────────────────────────────────────────────────────────

    /**
     * 400일 전 지급 100 → 그때 30 사용 → 지금 그 사용을 취소(원래 묶음이 지나 30일 새 묶음) → 지난 묶음 소멸 −70 → 지금 지급 20 → 5 사용 → 그 사용 취소(원래 묶음, 새 묶음 없음).
     *
     * @return {restoredCancelLine, plainCancelLine, restoredLot}
     */
    private long[] memberWithRestoredCancel(long member) {
        long old = grant(member, 100, now.minus(Duration.ofDays(400)));
        long oldUse = use(member, 30, now.minus(Duration.ofDays(400)).plusSeconds(60));
        long restoredCancel = cancel(oldUse, now.minusSeconds(50));
        clock.set(now.minusSeconds(40));
        assertThat(points.expireLot(old)).isEqualTo(70);
        clock.set(now);
        grant(member, 20, now.minusSeconds(30));
        long plainUse = use(member, 5, now.minusSeconds(20));
        long plainCancel = cancel(plainUse, now.minusSeconds(10));
        long restoredLot = jdbc.queryForObject("SELECT id FROM point_lots WHERE restored_from_lot_id = ?", Long.class, old);
        // 5 사용은 소멸이 더 가까운 되돌림 묶음에서 빠졌고, 그 취소는 원래 묶음(= 되돌림 묶음, 아직 살아 있음)으로 돌아간다 — 새 묶음을 만들지 않았다
        assertThat(jdbc.queryForList("SELECT lot_id FROM point_ledger_lots WHERE ledger_id = ?", Long.class, plainCancel)).containsExactly(restoredLot);
        assertInvariants(member);
        return new long[] {restoredCancel, plainCancel, restoredLot};
    }

    @Test
    void 내역_거르기는_사용_취소와_소멸을_받고_소멸_줄은_글_대상이_없다() throws Exception {
        long member = newMember();
        memberWithRestoredCancel(member);

        ledger(member, "EXPIRE").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].kind").value("EXPIRE"))
                .andExpect(jsonPath("$.data.items[0].reasonCode").value("EXPIRE"))
                .andExpect(jsonPath("$.data.items[0].reason").value("유효기간 만료"))
                .andExpect(jsonPath("$.data.items[0].delta").value(-70))
                .andExpect(jsonPath("$.data.items[0].target").isEmpty())
                .andExpect(jsonPath("$.data.items[0].restoredUntil").isEmpty());
        ledger(member, "CANCEL_USE").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].kind").value("CANCEL_USE"))
                .andExpect(jsonPath("$.data.items[1].kind").value("CANCEL_USE"))
                .andExpect(jsonPath("$.data.items[0].target").isEmpty());
        ledger(member, "USE").andExpect(jsonPath("$.data.items.length()").value(2)).andExpect(jsonPath("$.data.items[0].target").isEmpty());
        ledger(member, "EARN").andExpect(jsonPath("$.data.items.length()").value(2));
        ledger(member, null).andExpect(jsonPath("$.data.items.length()").value(7));

        for (String bad : List.of("REFUND", "earn", "SPEND")) {
            ledger(member, bad).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.error.fields.kind").exists());
        }
    }

    @Test
    void 만료된_묶음_대신_새_묶음으로_돌려준_줄에만_restoredUntil_이_있다() throws Exception {
        long member = newMember();
        long[] made = memberWithRestoredCancel(member);
        Instant restoredExpiry = jdbc.queryForObject("SELECT expires_at FROM point_lots WHERE id = ?", java.sql.Timestamp.class, made[2]).toInstant();
        assertThat(restoredExpiry).isEqualTo(now.minusSeconds(50).plus(Duration.ofDays(policy.lots().restoredLotValidDays())));

        String body = ledger(member, null).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> entryIds = JsonPath.read(body, "$.data.items[*].entryId");
        List<Object> restoredUntil = JsonPath.read(body, "$.data.items[*].restoredUntil");
        for (int i = 0; i < entryIds.size(); i++) {
            if (entryIds.get(i).equals(Long.toString(made[0]))) {
                assertThat(Instant.parse((String) restoredUntil.get(i))).as("만료 묶음을 되돌린 사용 취소").isEqualTo(restoredExpiry);
            } else {
                assertThat(restoredUntil.get(i)).as("줄 " + entryIds.get(i)).isNull();
            }
        }
        assertThat(entryIds).contains(Long.toString(made[0]), Long.toString(made[1]));
    }
}
