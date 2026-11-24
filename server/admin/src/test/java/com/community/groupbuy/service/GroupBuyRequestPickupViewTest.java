package com.community.groupbuy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.groupbuy.dto.response.GroupBuyOrderAdminView;
import org.junit.jupiter.api.Test;

/** 요청 상세의 회수 송장 — 개인정보 파기(R__batch_47)로 송장 번호가 비어도 택배사는 남긴다(commerce.md 17-3, 2026-11-24) */
class GroupBuyRequestPickupViewTest {

    @Test
    void 회수_송장을_입력한_적이_없으면_null() {
        assertThat(GroupBuyRequestAdminFacade.pickupOf(null, null)).isNull();
    }

    @Test
    void 송장_번호가_파기되면_택배사만_남는다() {
        assertThat(GroupBuyRequestAdminFacade.pickupOf("CJ", null)).isEqualTo(new GroupBuyOrderAdminView.Pickup("CJ", null));
    }

    @Test
    void 택배사와_송장이_있으면_그대로() {
        assertThat(GroupBuyRequestAdminFacade.pickupOf("CJ", "123456789012")).isEqualTo(new GroupBuyOrderAdminView.Pickup("CJ", "123456789012"));
    }
}
