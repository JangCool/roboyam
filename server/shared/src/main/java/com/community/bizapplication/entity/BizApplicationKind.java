package com.community.bizapplication.entity;

import com.community.business.entity.BusinessServiceKind;

/** 신청 건 종류(★-1) — 배너 광고 · 한 줄 광고 · 사업자 핫딜 글(+ 고른 노출) · 게시된 핫딜에 노출만. DB 값이라 이름을 바꾸지 않는다 */
public enum BizApplicationKind {
    AD_BANNER(BusinessServiceKind.AD),
    AD_TEXT_LINE(BusinessServiceKind.AD),
    HOTDEAL_POST(BusinessServiceKind.HOTDEAL),
    HOTDEAL_PLACEMENT(BusinessServiceKind.HOTDEAL);

    private final BusinessServiceKind service;

    BizApplicationKind(BusinessServiceKind service) {
        this.service = service;
    }

    /** 자격을 따지는 사업자 서비스(광고 → AD, 핫딜 → HOTDEAL) */
    public BusinessServiceKind service() {
        return service;
    }

    public boolean ad() {
        return service == BusinessServiceKind.AD;
    }
}
