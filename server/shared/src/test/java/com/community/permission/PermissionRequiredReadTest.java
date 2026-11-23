package com.community.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.permission.entity.Permission;
import com.community.permission.entity.PermissionUnit;
import org.junit.jupiter.api.Test;

/** 쓰기 권한의 짝 조회 권한(2026-11-16 「쓰기 고르면 읽기 자동 체크」) — admin-web {@code UtilPermissionCatalog.requiredRead} 와 같은 규칙 */
class PermissionRequiredReadTest {

    @Test
    void 코드_앞부분_조회가_짝이다() {
        assertThat(Permission.POPULAR_SETTINGS.requiredRead()).contains(Permission.POPULAR_READ);
        assertThat(Permission.SITE_SECRET.requiredRead()).contains(Permission.SITE_READ);
        assertThat(Permission.BOARD_STAFF_MANAGE.requiredRead()).contains(Permission.BOARD_READ);
        assertThat(Permission.POINT_ADJUST_LARGE.requiredRead()).contains(Permission.POINT_READ);
        assertThat(Permission.HOTDEAL_DOMAIN_MANAGE.requiredRead()).contains(Permission.HOTDEAL_READ);
        // 권한 분리(2026-11-23) — site.* 에서 뗀 권한은 자기 조회와 짝이다(site.read 가 아니다)
        assertThat(Permission.ANALYTICS_SETTINGS.requiredRead()).contains(Permission.ANALYTICS_READ);
        assertThat(Permission.POPUP_MANAGE.requiredRead()).contains(Permission.POPUP_READ);
        assertThat(Permission.TRIAL_MANAGE.requiredRead()).contains(Permission.TRIAL_READ);
        assertThat(Permission.TRIAL_SETTINGS.requiredRead()).contains(Permission.TRIAL_READ);
        assertThat(Permission.TRIAL_REVIEW.requiredRead()).contains(Permission.TRIAL_READ);
        assertThat(Permission.TRIAL_ADDRESS_REVEAL.requiredRead()).contains(Permission.TRIAL_READ);
        assertThat(Permission.AD_TAKEDOWN.requiredRead()).contains(Permission.AD_READ);
    }

    @Test
    void 이름이_다르면_같은_유형의_하나뿐인_조회가_짝이다() {
        assertThat(Permission.APPEAL_MANAGE.requiredRead()).contains(Permission.SANCTION_READ);
        assertThat(Permission.ADMIN_MENU_MANAGE.requiredRead()).contains(Permission.LAYOUT_READ);
        assertThat(Permission.COMMERCE_REFUND_APPROVE.requiredRead()).contains(Permission.COMMERCE_ORDER_READ);
        assertThat(Permission.COMMERCE_STOCK_ADJUST.requiredRead()).contains(Permission.COMMERCE_PRODUCT_READ);
    }

    @Test
    void 예외_표와_조회_자신() {
        assertThat(Permission.COMMERCE_RECONCILE_MANAGE.requiredRead()).contains(Permission.COMMERCE_PAYMENT_READ);
        assertThat(Permission.COMMERCE_EXPORT.requiredRead()).isEmpty();
        assertThat(Permission.COMMERCE_SETTINGS.requiredRead()).isEmpty();
        assertThat(Permission.SITE_READ.requiredRead()).isEmpty();
    }

    @Test
    void 짝은_늘_같은_유형의_조회다() {
        for (Permission permission : Permission.values()) {
            permission.requiredRead().ifPresent(read -> {
                assertThat(read.unit()).as(permission.code()).isEqualTo(PermissionUnit.READ);
                assertThat(read.type()).as(permission.code()).isEqualTo(permission.type());
            });
        }
    }
}
