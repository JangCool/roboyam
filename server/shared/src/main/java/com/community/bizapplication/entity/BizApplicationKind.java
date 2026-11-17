package com.community.bizapplication.entity;

import com.community.business.entity.BusinessServiceKind;

/** 신청 건 종류(★-1) — 배너 광고 · 한 줄 광고 · 사업자 핫딜 글(+ 고른 노출) · 게시된 핫딜에 노출만. DB 값이라 이름을 바꾸지 않는다 */
public enum BizApplicationKind {
    AD_BANNER(BusinessServiceKind.AD),
    AD_TEXT_LINE(BusinessServiceKind.AD),
    HOTDEAL_POST(BusinessServiceKind.HOTDEAL),
    HOTDEAL_PLACEMENT(BusinessServiceKind.HOTDEAL),
    /**
     * 체험단 모집(shared V241 — trial-group-contract 「구현 결정」 1) — 운영자 등록 건. 금액은 뽑기를 마친 뒤 확정하고, 금액 &gt; 0 이면 광고 심사 큐에 승인 대기로 나와 승인·결제를 탄다
     * (V242 「유료 결제 연결」 — {@code BizApplicationStore.paidTrial}). {@link BizDomain#kinds()}(장부·노출 종류)에는 넣지 않는다.
     */
    TRIAL(BusinessServiceKind.AD);

    private final BusinessServiceKind service;

    BizApplicationKind(BusinessServiceKind service) {
        this.service = service;
    }

    /** 자격을 따지는 사업자 서비스(광고 → AD, 핫딜 → HOTDEAL) */
    public BusinessServiceKind service() {
        return service;
    }

    /** 광고 심사 큐·사업자 신청 흐름(승인·결제·예약)을 타는 종류인가 — 체험단은 체험단 화면이 따로 다룬다 */
    public boolean reviewFlow() {
        return this != TRIAL;
    }

    public boolean ad() {
        return service == BusinessServiceKind.AD;
    }

    /** 관리자 메뉴·권한·알림을 가르는 도메인(광고 → AD, 핫딜 → HOTDEAL) */
    public BizDomain domain() {
        return ad() ? BizDomain.AD : BizDomain.HOTDEAL;
    }
}
