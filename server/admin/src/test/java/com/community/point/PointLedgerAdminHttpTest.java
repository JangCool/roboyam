package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.permission.entity.Permission;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 관리자 포인트 원장·조정 후보·회원 상세 최근 내역(커머스 1-1 — commerce-stage1-contract 「1-1 화면 확정과 서버 세부」 API) — 원장 줄의 무상·유상 몫, 새 종류 거르기(사용 취소·소멸·환불),
 * 새 사유 거르기, 합계의 뜻(earned = + 줄 합, spent = − 줄 크기 합), 후보의 무상·유상 잔액. 실제 필터 체인·권한 인터셉터·PostgreSQL.
 */
class PointLedgerAdminHttpTest extends PointAdminHttpTestBase {

    private static final String LEDGER = "/api/admin/points/ledger";

    /**
     * 한 회원에 원장 줄 다섯: 400일 전 운영 지급 +40(지금은 지난 묶음) → 유상 충전 +1000 → 운영 지급 +300 → 지난 묶음 소멸 −40 → 사용 −500(무상 300 + 유상 200) → 사용 취소 +500
     */
    private long memberWithEveryKind() {
        long member = newMember();
        Instant now = now();
        long old = grant(member, 40, now.minus(Duration.ofDays(400)));
        chargePaid(member, 1000, now.minusSeconds(30));
        grant(member, 300, now.minusSeconds(20));
        assertThat(expire(old, now.minusSeconds(15))).isEqualTo(40);
        long useLine = use(member, 500, now.minusSeconds(10));
        cancel(useLine, now.minusSeconds(5));
        assertInvariants(member);
        return member;
    }

    @Test
    void 원장_줄에_무상_유상_몫이_있고_합은_delta_다() throws Exception {
        long member = memberWithEveryKind();
        Cookie reader = login(operatorWith(Permission.POINT_READ));

        mvc.perform(get(LEDGER).param("q", Long.toString(member)).param("kind", "USE").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].kind").value("USE"))
                .andExpect(jsonPath("$.data.items[0].reasonCode").value("POINT_SHOP_PURCHASE"))
                .andExpect(jsonPath("$.data.items[0].delta").value(-500))
                .andExpect(jsonPath("$.data.items[0].freeDelta").value(-300))
                .andExpect(jsonPath("$.data.items[0].paidDelta").value(-200));
        mvc.perform(get(LEDGER).param("q", Long.toString(member)).param("reasons", "CHARGE").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].kind").value("EARN"))
                .andExpect(jsonPath("$.data.items[0].freeDelta").value(0))
                .andExpect(jsonPath("$.data.items[0].paidDelta").value(1000));
        mvc.perform(get(LEDGER).param("q", Long.toString(member)).param("reasons", "ADMIN_ADJUST").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].freeDelta").value(300))
                .andExpect(jsonPath("$.data.items[0].paidDelta").value(0))
                .andExpect(jsonPath("$.data.items[0].actorNickname").value(operatorName(granter)));
    }

    @Test
    void 종류_거르기는_사용_취소_소멸_환불을_받고_모르는_종류는_400() throws Exception {
        long member = memberWithEveryKind();
        Cookie reader = login(operatorWith(Permission.POINT_READ));
        String q = Long.toString(member);

        mvc.perform(get(LEDGER).param("q", q).param("kind", "EXPIRE").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].kind").value("EXPIRE"))
                .andExpect(jsonPath("$.data.items[0].reasonCode").value("EXPIRE"))
                .andExpect(jsonPath("$.data.items[0].delta").value(-40))
                .andExpect(jsonPath("$.data.items[0].freeDelta").value(-40))
                .andExpect(jsonPath("$.data.items[0].paidDelta").value(0))
                .andExpect(jsonPath("$.data.items[0].actorNickname").isEmpty());
        mvc.perform(get(LEDGER).param("q", q).param("kind", "CANCEL_USE").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].reasonCode").value("POINT_SHOP_REFUND"))
                .andExpect(jsonPath("$.data.items[0].delta").value(500))
                .andExpect(jsonPath("$.data.items[0].freeDelta").value(300))
                .andExpect(jsonPath("$.data.items[0].paidDelta").value(200));
        // 충전 환불은 3단계 기능이라 줄이 없다 — 거르기 값으로는 받는다
        mvc.perform(get(LEDGER).param("q", q).param("kind", "REFUND").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get(LEDGER).param("q", q).param("kind", "EARN").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3));
        mvc.perform(get(LEDGER).param("q", q).param("kind", "SPEND").cookie(reader)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.fields.kind").exists());
    }

    @Test
    void 사유_거르기는_새_사유도_받고_모르는_사유는_400() throws Exception {
        long member = memberWithEveryKind();
        Cookie reader = login(operatorWith(Permission.POINT_READ));
        String q = Long.toString(member);

        mvc.perform(get(LEDGER).param("q", q).param("reasons", "EXPIRE,POINT_SHOP_REFUND").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
        mvc.perform(get(LEDGER).param("q", q).param("reasons", "REVOKE_DEBT_OFFSET", "WITHDRAW_FORFEIT", "GB_POINT_USE").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        // 묶음 출처 전용 코드(전환)는 원장 사유가 아니다
        mvc.perform(get(LEDGER).param("q", q).param("reasons", "MIGRATION").cookie(reader)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.reasons").exists());
    }

    @Test
    void 합계는_종류와_무관하게_플러스_줄_합과_마이너스_줄_크기_합이다() throws Exception {
        long member = memberWithEveryKind();
        Cookie reader = login(operatorWith(Permission.POINT_READ));

        // + 줄: 지급 40 · 충전 1000 · 지급 300 · 사용 취소 500 / − 줄: 소멸 40 · 사용 500
        mvc.perform(get(LEDGER).param("q", Long.toString(member)).param("size", "2").cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(6))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.summary.earned").value(1840))
                .andExpect(jsonPath("$.data.summary.spent").value(540))
                .andExpect(jsonPath("$.data.summary.adjustCount").value(2));
    }

    @Test
    void 조정_후보에_무상_유상_잔액과_합계가_있다() throws Exception {
        long member = newMember();
        chargePaid(member, 700, now().minusSeconds(10));
        grant(member, 250, now().minusSeconds(5));
        assertInvariants(member);
        Cookie adjuster = login(operatorWith(Permission.POINT_ADJUST));

        mvc.perform(get("/api/admin/points/member-candidates").param("q", Long.toString(member)).cookie(adjuster)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].memberId").value(Long.toString(member)))
                .andExpect(jsonPath("$.data[0].balance").value(950))
                .andExpect(jsonPath("$.data[0].freeBalance").value(250))
                .andExpect(jsonPath("$.data[0].paidBalance").value(700));
        // 잔액 행이 없는 회원은 모두 0
        long empty = newMember();
        mvc.perform(get("/api/admin/points/member-candidates").param("q", Long.toString(empty)).cookie(adjuster)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].balance").value(0))
                .andExpect(jsonPath("$.data[0].freeBalance").value(0))
                .andExpect(jsonPath("$.data[0].paidBalance").value(0));
    }

    @Test
    void 회원_상세의_최근_포인트_내역은_새_종류를_그대로_낸다() throws Exception {
        long member = memberWithEveryKind();
        Cookie reader = login(operatorWith(Permission.MEMBER_READ));

        mvc.perform(get("/api/admin/members/" + member).cookie(reader)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gradePoint.pointEntries[0].kind").value("CANCEL_USE"))
                .andExpect(jsonPath("$.data.gradePoint.pointEntries[1].kind").value("USE"))
                .andExpect(jsonPath("$.data.gradePoint.pointEntries[2].kind").value("EXPIRE"))
                .andExpect(jsonPath("$.data.gradePoint.pointEntries[2].delta").value(-40));
    }
}
