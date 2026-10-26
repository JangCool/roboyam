package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.community.permission.entity.Permission;
import com.community.point.entity.PointLotKind;
import com.community.point.repository.master.PointLedgerWriter;
import com.community.zconfiguration.time.ServiceTime;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 관리자 회원 포인트 묶음 {@code GET /api/admin/members/{id}/point-lots}(커머스 1-1 — commerce-stage1-contract 「1-1」 API) — 잔액 나눔·소멸 예정·미회수, 묶음 정렬 둘(남은 것만 = 차감
 * 순서, 전체 = 최근 받은 순)·쪽, 지난 묶음 표시, 되돌린 새 묶음 표시, 최근 미회수 기록 5건, 404·400·403. 실제 필터 체인·권한 인터셉터·PostgreSQL.
 */
class PointMemberLotsHttpTest extends PointAdminHttpTestBase {

    private Cookie reader;
    private Instant now;

    /** 만든 묶음들 */
    private long expiredLot;
    private long restoredLot;
    private long soonLot;
    private long laterLot;
    private long paidFirst;
    private long paidSecond;

    private static String path(Object member) {
        return "/api/admin/members/" + member + "/point-lots";
    }

    @BeforeEach
    void readerUp() throws Exception {
        reader = login(operatorWith(Permission.POINT_READ));
        now = now();
    }

    private ResultActions lots(long member, String active, String page, String size) throws Exception {
        var request = get(path(member)).cookie(reader).header("Accept-Language", "ko");
        if (active != null) {
            request = request.param("active", active);
        }
        if (page != null) {
            request = request.param("page", page);
        }
        if (size != null) {
            request = request.param("size", size);
        }
        return mvc.perform(request);
    }

    /**
     * 400일 전 지급 100(지금은 지난 묶음) → 그때 30 사용 → 300일 전 지급 50 → 200일 전 지급 200 → 유상 500·700 → 지금 그 사용을 취소(원래 묶음이 지나 30일 새 묶음).
     * 무상 350(지난 묶음 70 은 아직 소멸 처리 전) · 유상 1200.
     */
    private long memberWithLots() {
        long member = newMember();
        expiredLot = grant(member, 100, now.minus(Duration.ofDays(400)));
        long useLine = use(member, 30, now.minus(Duration.ofDays(400)).plusSeconds(60));
        soonLot = grant(member, 50, now.minus(Duration.ofDays(300)));
        laterLot = grant(member, 200, now.minus(Duration.ofDays(200)));
        paidFirst = chargePaid(member, 500, now.minusSeconds(20));
        paidSecond = chargePaid(member, 700, now.minusSeconds(10));
        cancel(useLine, now);
        restoredLot = jdbc.queryForObject("SELECT id FROM point_lots WHERE user_id = ? AND restored_from_lot_id = ?", Long.class, member, expiredLot);
        assertInvariants(member);
        return member;
    }

    private static List<String> ids(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.lots.items[*].lotId");
    }

    private static String id(long value) {
        return Long.toString(value);
    }

    @Test
    void 잔액_나눔_소멸_예정_미회수와_no_store() throws Exception {
        long member = memberWithLots();
        long free = 350;
        // 가장 가까운 소멸 예정 = 30일 새 묶음(지난 묶음 70 은 「예정」이 아니다)
        String date = ServiceTime.dateOf(now.plus(Duration.ofDays(policy.lots().restoredLotValidDays()))).toString();

        lots(member, null, null, null).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.freeBalance").value(free))
                .andExpect(jsonPath("$.data.paidBalance").value(1200))
                .andExpect(jsonPath("$.data.revokeDebt").value(0))
                .andExpect(jsonPath("$.data.expiring.date").value(date))
                .andExpect(jsonPath("$.data.expiring.amount").value(30))
                .andExpect(jsonPath("$.data.shortfalls.length()").value(0));
    }

    @Test
    void 남은_것만은_차감_순서다_무상_소멸_임박순_다음_유상_만든_순_지난_묶음은_빠진다() throws Exception {
        long member = memberWithLots();

        ResultActions all = lots(member, "true", "0", "10").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lots.total").value(5))
                .andExpect(jsonPath("$.data.lots.items[0].kind").value("FREE"))
                .andExpect(jsonPath("$.data.lots.items[0].restored").value(true))
                .andExpect(jsonPath("$.data.lots.items[0].originalAmount").value(30))
                .andExpect(jsonPath("$.data.lots.items[0].remainingAmount").value(30))
                .andExpect(jsonPath("$.data.lots.items[0].sourceReasonCode").value("POINT_SHOP_REFUND"))
                .andExpect(jsonPath("$.data.lots.items[0].expired").value(false))
                .andExpect(jsonPath("$.data.lots.items[1].sourceReasonCode").value("ADMIN_ADJUST"))
                .andExpect(jsonPath("$.data.lots.items[1].sourceReason").value("운영 조정"))
                .andExpect(jsonPath("$.data.lots.items[1].restored").value(false))
                .andExpect(jsonPath("$.data.lots.items[3].kind").value("PAID"))
                .andExpect(jsonPath("$.data.lots.items[3].expiresAt").isEmpty())
                .andExpect(jsonPath("$.data.lots.items[3].sourceReasonCode").value("CHARGE"));
        assertThat(ids(all)).containsExactly(id(restoredLot), id(soonLot), id(laterLot), id(paidFirst), id(paidSecond));

        // 쪽: 2개씩 — 둘째 쪽은 [나중 무상, 첫 유상], 셋째 쪽은 [둘째 유상], 넷째 쪽은 비어 있다(전체 수는 같다)
        assertThat(ids(lots(member, "true", "1", "2").andExpect(jsonPath("$.data.lots.total").value(5)))).containsExactly(id(laterLot), id(paidFirst));
        assertThat(ids(lots(member, "true", "2", "2"))).containsExactly(id(paidSecond));
        assertThat(ids(lots(member, "true", "3", "2").andExpect(jsonPath("$.data.lots.total").value(5)))).isEmpty();
        // active 를 빼면 남은 것만이다
        assertThat(ids(lots(member, null, null, null))).containsExactly(id(restoredLot), id(soonLot), id(laterLot), id(paidFirst), id(paidSecond));
    }

    @Test
    void 전체는_최근_받은_순이고_지난_묶음은_expired_로_보인다() throws Exception {
        long member = memberWithLots();

        ResultActions all = lots(member, "false", "0", "10").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lots.total").value(6))
                .andExpect(jsonPath("$.data.lots.items[5].lotId").value(id(expiredLot)))
                .andExpect(jsonPath("$.data.lots.items[5].expired").value(true))
                .andExpect(jsonPath("$.data.lots.items[5].originalAmount").value(100))
                .andExpect(jsonPath("$.data.lots.items[5].remainingAmount").value(70));
        assertThat(ids(all)).containsExactly(id(restoredLot), id(paidSecond), id(paidFirst), id(laterLot), id(soonLot), id(expiredLot));
        assertThat(ids(lots(member, "false", "1", "4"))).containsExactly(id(soonLot), id(expiredLot));

        // 소멸 처리하면 남은 양 0 — 남은 것만에는 원래부터 없고, 전체에는 그대로 있다
        assertThat(expire(expiredLot, now)).isEqualTo(70);
        lots(member, "false", "1", "4").andExpect(jsonPath("$.data.lots.items[1].remainingAmount").value(0));
        lots(member, "true", null, null).andExpect(jsonPath("$.data.lots.total").value(5)).andExpect(jsonPath("$.data.freeBalance").value(280));
        assertInvariants(member);
    }

    @Test
    void 전환_묶음의_출처_이름은_요청_언어다() throws Exception {
        long member = newMember();
        // 전환 묶음은 마이그레이션만 만든다(원장 줄 없음) — 읽기 모양만 본다
        long lot = ids.nextId();
        tx.executeWithoutResult(s -> writer.insertLot(new PointLedgerWriter.NewLot(lot, member, PointLotKind.FREE, 80, now.plus(Duration.ofDays(100)), "MIGRATION", null,
                null, null, null, now.minusSeconds(1))));

        lots(member, "false", null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lots.items[0].sourceReasonCode").value("MIGRATION"))
                .andExpect(jsonPath("$.data.lots.items[0].sourceReason").value("기존 포인트 전환"));
        mvc.perform(get(path(member)).param("active", "false").cookie(reader).header("Accept-Language", "en")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lots.items[0].sourceReason").value("Converted existing points"));
    }

    @Test
    void 미회수_기록은_최근_5건이_먼저_오고_미회수_잔액은_잔액_행이다() throws Exception {
        long member = newMember();
        for (int i = 0; i < 6; i++) {
            jdbc.update("INSERT INTO point_revoke_shortfalls (id, user_id, reason, ref_type, ref_id, requested, applied, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    ids.nextId(), member, i % 2 == 0 ? "POST_REVOKE" : "COMMENT_REVOKE", i % 2 == 0 ? "POST" : "COMMENT", 9000L + i, 20, i,
                    OffsetDateTime.ofInstant(now.minus(Duration.ofHours(6 - i)), ZoneOffset.UTC));
        }
        jdbc.update("INSERT INTO point_balances (user_id, free_balance, paid_balance, revoke_debt, updated_at) VALUES (?, 0, 0, 105, now())", member);

        lots(member, null, null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revokeDebt").value(105))
                .andExpect(jsonPath("$.data.expiring").isEmpty())
                .andExpect(jsonPath("$.data.lots.total").value(0))
                .andExpect(jsonPath("$.data.shortfalls.length()").value(5))
                .andExpect(jsonPath("$.data.shortfalls[0].refType").value("COMMENT"))
                .andExpect(jsonPath("$.data.shortfalls[0].refId").value("9005"))
                .andExpect(jsonPath("$.data.shortfalls[0].requested").value(20))
                .andExpect(jsonPath("$.data.shortfalls[0].applied").value(5))
                .andExpect(jsonPath("$.data.shortfalls[0].id").isString())
                .andExpect(jsonPath("$.data.shortfalls[4].refId").value("9001"));
    }

    @Test
    void 없는_회원_탈퇴_회원_형식이_틀린_ID_는_404() throws Exception {
        mvc.perform(get(path(ids.nextId())).cookie(reader)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
        mvc.perform(get(path(withdrawnMember(Instant.now()))).cookie(reader)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
        mvc.perform(get(path("abc")).cookie(reader)).andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("MEMBER_NOT_FOUND"));
    }

    @Test
    void 조건이_틀리면_400_과_칸() throws Exception {
        long member = newMember();
        for (String[] bad : new String[][] {{"size", "0"}, {"size", "51"}, {"size", "abc"}, {"page", "-1"}, {"page", "x"}, {"active", "yes"}}) {
            mvc.perform(get(path(member)).param(bad[0], bad[1]).cookie(reader)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                    .andExpect(jsonPath("$.error.fields." + bad[0]).exists());
        }
        lots(member, "false", "0", "50").andExpect(status().isOk());
        lots(member, "true", "0", "1").andExpect(status().isOk());
    }

    @Test
    void point_read_가_없으면_403() throws Exception {
        long member = newMember();
        Cookie adjuster = login(operatorWith(Permission.POINT_ADJUST, Permission.MEMBER_READ));
        mvc.perform(get(path(member)).cookie(adjuster)).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("PERMISSION_DENIED"));
    }
}
