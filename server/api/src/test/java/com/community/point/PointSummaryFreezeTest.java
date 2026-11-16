package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.CommerceApiTestBase;
import com.community.point.dto.response.PointRestrictionView;
import com.community.point.service.MyPointService;
import com.community.restriction.dto.request.RestrictionGrant;
import com.community.restriction.entity.RestrictionType;
import com.community.restriction.service.MemberRestrictionService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 내 포인트 요약({@code GET /api/me/points})은 포인트 동결(POINT_FREEZE)을 {@code restriction} 으로 알린다 — 동결 중에는 적립이 조용히 건너뛰어지므로
 * 화면이 미리 안내한다(공개 QA P-5, commerce.md 12-3). 오류 본문 {@code restriction} 과 같은 모양(종류·끝·공개 사유)이고 운영 메모는 싣지 않는다.
 */
class PointSummaryFreezeTest extends CommerceApiTestBase {

    @Autowired
    private MyPointService myPoints;
    @Autowired
    private MemberRestrictionService restrictions;

    @Test
    void 동결이_없으면_restriction_은_null() {
        long member = newMember();
        assertThat(myPoints.summary(member).restriction()).isNull();
    }

    @Test
    void 동결_중이면_종류_끝_공개_사유를_싣고_운영_메모는_싣지_않는다() {
        long member = newMember();
        Instant until = clock.instant().plus(Duration.ofDays(3));
        restrictions.grant(member, new RestrictionGrant(RestrictionType.POINT_FREEZE, until, "포인트 동결 안내 시험", "운영 메모 — 회원에게 보이면 안 됨"), newOperator(),
                "127.0.0.1");

        PointRestrictionView restriction = myPoints.summary(member).restriction();

        assertThat(restriction).isNotNull();
        assertThat(restriction.type()).isEqualTo("POINT_FREEZE");
        assertThat(Instant.parse(restriction.until())).isEqualTo(until);
        assertThat(restriction.reason()).isEqualTo("포인트 동결 안내 시험");
        assertThat(restriction.toString()).doesNotContain("운영 메모");
    }

    @Test
    void 포인트를_막지_않는_제한은_싣지_않는다() {
        long member = newMember();
        long operator = newOperator();
        restrictions.grant(member, new RestrictionGrant(RestrictionType.GROUP_BUY_BAN, null, "공구 제한 시험", null), operator, "127.0.0.1");
        restrictions.grant(member, new RestrictionGrant(RestrictionType.CHARGE_BAN, null, "충전 제한 시험", null), operator, "127.0.0.1");

        assertThat(myPoints.summary(member).restriction()).isNull();
    }

    @Test
    void 동결이_끝나면_다시_null() {
        long member = newMember();
        restrictions.grant(member, new RestrictionGrant(RestrictionType.POINT_FREEZE, clock.instant().plus(Duration.ofHours(1)), "한 시간 동결 시험", null), newOperator(),
                "127.0.0.1");
        assertThat(myPoints.summary(member).restriction()).isNotNull();

        clock.advance(Duration.ofHours(1));
        assertThat(myPoints.summary(member).restriction()).isNull();
    }
}
