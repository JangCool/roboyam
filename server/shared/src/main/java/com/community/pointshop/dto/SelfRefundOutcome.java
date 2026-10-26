package com.community.pointshop.dto;

import com.community.pointshop.dto.response.MyItemRefundView;
import com.community.pointshop.dto.response.PointShopRefundRequestedView;

/**
 * 셀프 환불의 결과 — 바로 환불했거나({@code refunded}, 200) 정지 중이라 환불 신청을 만들었다({@code requested}, 202). 둘 중 하나만 있다.
 */
public record SelfRefundOutcome(MyItemRefundView refunded, PointShopRefundRequestedView requested) {

    public static SelfRefundOutcome refunded(MyItemRefundView view) {
        return new SelfRefundOutcome(view, null);
    }

    public static SelfRefundOutcome requested(PointShopRefundRequestedView view) {
        return new SelfRefundOutcome(null, view);
    }

    /** 신청으로 끝났나(202) */
    public boolean isRequest() {
        return requested != null;
    }

    /** 응답 본문({@code data}) */
    public Object body() {
        return requested != null ? requested : refunded;
    }
}
