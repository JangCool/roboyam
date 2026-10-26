package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.CommerceApiTestBase;
import com.community.point.entity.PointReason;
import com.community.point.service.PointService;
import com.community.restriction.dto.request.RestrictionGrant;
import com.community.restriction.entity.RestrictionType;
import com.community.restriction.service.MemberRestrictionService;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 지금 있는 포인트 적립은 포인트 동결(POINT_FREEZE) 중이면 <b>조용히 건너뛴다</b> — 글쓰기 트랜잭션은 그대로 성공한다(commerce.md 12-3, 계약 「0-4」 데이터 규칙).
 */
class PointFreezeEarnTest extends CommerceApiTestBase {

    @Autowired
    private PointService points;
    @Autowired
    private MemberRestrictionService restrictions;

    private long ledgerRows(long member) {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM point_ledger WHERE user_id = ?", Integer.class, member);
        return count == null ? 0 : count;
    }

    /** 글쓰기 트랜잭션을 흉내 낸다 — 업무 행(여기서는 회원 갱신) + 적립 */
    private long writePostAndEarn(long member) {
        Long earned = tx.execute(status -> {
            jdbc.update("UPDATE users SET updated_at = now() WHERE id = ?", member);
            clock.advance(java.time.Duration.ofMinutes(5)); // 작성 간격(1-3)에 걸리지 않게
            return points.earnPost(new PointService.PostEarn(member, ids.nextId(), "가".repeat(200), true, true));
        });
        return earned == null ? 0 : earned;
    }

    @Test
    void 동결이_아니면_글_적립이_된다() {
        long member = newMember();
        assertThat(writePostAndEarn(member)).isPositive();
        assertThat(ledgerRows(member)).isEqualTo(1);
    }

    @Test
    void 동결_중이면_적립을_건너뛰고_글쓰기_트랜잭션은_성공한다() {
        long member = newMember();
        restrictions.grant(member, new RestrictionGrant(RestrictionType.POINT_FREEZE, clock.instant().plus(Duration.ofDays(1)), "포인트 동결 시험", null), newOperator(),
                "127.0.0.1");

        assertThat(writePostAndEarn(member)).isZero();
        assertThat(ledgerRows(member)).isZero();
        assertThat(jdbc.queryForObject("SELECT updated_at > created_at FROM users WHERE id = ?", Boolean.class, member)).isTrue(); // 업무 쪽 변경은 커밋됐다
    }

    @Test
    void 다른_종류의_제한은_적립을_막지_않는다() {
        long member = newMember();
        long operator = newOperator();
        restrictions.grant(member, new RestrictionGrant(RestrictionType.CHARGE_BAN, null, "충전 제한 시험", null), operator, "127.0.0.1");
        restrictions.grant(member, new RestrictionGrant(RestrictionType.GROUP_BUY_BAN, null, "공구 제한 시험", null), operator, "127.0.0.1");

        assertThat(writePostAndEarn(member)).isPositive();
    }

    @Test
    void 동결이_끝나면_다시_적립된다() {
        long member = newMember();
        restrictions.grant(member, new RestrictionGrant(RestrictionType.POINT_FREEZE, clock.instant().plus(Duration.ofHours(1)), "한 시간 동결 시험", null),
                newOperator(), "127.0.0.1");
        assertThat(writePostAndEarn(member)).isZero();

        clock.advance(Duration.ofHours(1));
        assertThat(writePostAndEarn(member)).isPositive();
    }
}
