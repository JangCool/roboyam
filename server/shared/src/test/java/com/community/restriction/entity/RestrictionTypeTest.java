package com.community.restriction.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** commerce.md 12-3 의 「막는 것 / 그래도 되는 것」 표 그대로 */
class RestrictionTypeTest {

    private static Set<RestrictedFeature> blockedBy(RestrictionType type) {
        return Arrays.stream(RestrictedFeature.values()).filter(type::blocks).collect(Collectors.toCollection(() -> EnumSet.noneOf(RestrictedFeature.class)));
    }

    @Test
    void 막는_것_표() {
        assertThat(blockedBy(RestrictionType.POINT_FREEZE)).containsExactlyInAnyOrder(RestrictedFeature.POINT_EARN, RestrictedFeature.POINT_USE, RestrictedFeature.CHARGE);
        assertThat(blockedBy(RestrictionType.GROUP_BUY_BAN)).containsExactly(RestrictedFeature.GROUP_BUY_JOIN);
        assertThat(blockedBy(RestrictionType.CHARGE_BAN)).containsExactly(RestrictedFeature.CHARGE);
    }

    @Test
    void 충전_포인트_환불_신청은_어느_제한도_막지_않는다() {
        assertThat(Arrays.stream(RestrictionType.values()).noneMatch(type -> type.blocks(RestrictedFeature.CHARGE_REFUND_REQUEST))).isTrue();
    }

    @Test
    void 종류_이름은_DB_CHECK_와_같다() {
        assertThat(Arrays.stream(RestrictionType.values()).map(Enum::name)).containsExactly("POINT_FREEZE", "GROUP_BUY_BAN", "CHARGE_BAN");
        assertThat(Arrays.stream(RestrictionSource.values()).map(Enum::name)).containsExactly("AUTO", "OPERATOR");
    }
}
