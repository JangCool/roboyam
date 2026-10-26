package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

/**
 * DB 가 직접 막는 포인트 규칙(V85) — 원장·묶음 반영 수정 금지 트리거, 묶음의 정체 고정, 몫·부호·사유-종류 CHECK, 합계 칸은 계산 칸, 회원 삭제 연쇄 제거(17-2, 결정 5).
 */
class PointSchemaGuardTest extends PointTestBase {

    @Test
    void 원장은_새_칸까지_고칠_수_없다() {
        long member = kit.newMember();
        long line = kit.grantLine(member, 10);

        for (String change : new String[] {"free_delta = 5", "paid_delta = 1", "delta = 99", "reason = 'POST'", "kind = 'REVOKE'"}) {
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_ledger SET " + change + " WHERE id = ?", line)).as(change)
                    .isInstanceOf(DataAccessException.class);
        }
        assertThat(kit.jdbc.queryForObject("SELECT free_delta FROM point_ledger WHERE id = ?", Long.class, line)).isEqualTo(10L);
    }

    @Test
    void 원장_묶음_반영은_고칠_수_없다() {
        long member = kit.newMember();
        long line = kit.grantLine(member, 10);

        assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_ledger_lots SET amount = 5 WHERE ledger_id = ?", line)).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThat(kit.entryLots(line).values()).containsExactly(10L);
    }

    @Test
    void 묶음은_남은_양만_바뀌고_정체는_고정이다() {
        long member = kit.newMember();
        long lot = kit.lotOf(kit.grantLine(member, 10));

        for (String change : new String[] {"original_amount = 20", "kind = 'PAID', expires_at = NULL", "expires_at = expires_at + interval '1 day'",
                                           "user_id = user_id + 1", "source_reason = 'POST'"}) {
            assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_lots SET " + change + " WHERE id = ?", lot)).as(change).isInstanceOf(DataAccessException.class);
        }
        // 남은 양은 0 ~ 원래 양 사이에서만
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_lots SET remaining_amount = 11 WHERE id = ?", lot)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_lots SET remaining_amount = -1 WHERE id = ?", lot)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void 몫의_합과_부호와_사유_종류의_짝은_DB가_막는다() {
        long member = kit.newMember();
        String insert = "INSERT INTO point_ledger (id, user_id, kind, reason, delta, free_delta, paid_delta, balance_after, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, 0, now())";

        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), member, "EARN", "POST", 10, 5, 4)).as("합 ≠ delta").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), member, "REVOKE", "ADMIN_ADJUST", -10, 0, -10)).as("회수는 유상 몫 없음")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), member, "CANCEL_USE", "POINT_SHOP_REFUND", -10, -10, 0)).as("사용 취소는 +")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), member, "EARN", "POINT_SHOP_PURCHASE", 10, 10, 0)).as("구매 사유는 USE 만")
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), member, "EARN", "NOPE", 10, 10, 0)).as("모르는 사유").isInstanceOf(DataAccessException.class);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ?", member)).isZero();
    }

    @Test
    void 합계_칸은_DB가_계산한다() {
        long member = kit.newMember();
        kit.adjust(member, 30);
        kit.chargePaid(member, 12);

        assertThat(kit.balance(member)[3]).isEqualTo(42);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_balances SET balance = 1 WHERE user_id = ?", member)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_balances SET free_balance = -1 WHERE user_id = ?", member)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE point_balances SET revoke_debt = -1 WHERE user_id = ?", member)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void 잔액이나_원장이_있는_회원_행은_지울_수_없다() {
        long withPoints = kit.newMember();
        kit.adjust(withPoints, 10);
        long balanceOnly = kit.newMember();
        kit.jdbc.update("INSERT INTO point_balances (user_id, free_balance, updated_at) VALUES (?, 0, now())", balanceOnly);
        long none = kit.newMember();

        assertThatThrownBy(() -> kit.jdbc.update("DELETE FROM users WHERE id = ?", withPoints)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> kit.jdbc.update("DELETE FROM users WHERE id = ?", balanceOnly)).isInstanceOf(DataAccessException.class);
        assertThat(kit.jdbc.update("DELETE FROM users WHERE id = ?", none)).isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ?", withPoints)).isEqualTo(1);
    }

    @Test
    void 같은_대상은_미회수_기록도_하나뿐이다() {
        long member = kit.newMember();
        String insert = "INSERT INTO point_revoke_shortfalls (id, user_id, reason, ref_type, ref_id, requested, applied, created_at) VALUES (?, ?, 'POST_REVOKE', 'POST', ?, 20, 0, now())";
        long ref = kit.ids.nextId();
        kit.jdbc.update(insert, kit.ids.nextId(), member, ref);

        assertThatThrownBy(() -> kit.jdbc.update(insert, kit.ids.nextId(), member, ref)).isInstanceOf(DataAccessException.class);
        kit.jdbc.update("DELETE FROM point_revoke_shortfalls WHERE user_id = ?", member);
    }
}
