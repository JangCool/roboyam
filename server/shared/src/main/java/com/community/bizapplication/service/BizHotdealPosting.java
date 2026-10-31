package com.community.bizapplication.service;

/**
 * 사업자 핫딜 글 게시의 고리 — <b>핫딜 게시 세션에서 구현</b>한다(이 세션은 고리만 둔다). 빈이 없으면 {@link #NONE}(아무것도 하지 않음).
 * 신청 건 처리 트랜잭션 안에서 불린다(예외를 던지면 그 처리가 되돌아간다 — 구현은 외부 호출을 하지 않는다).
 */
public interface BizHotdealPosting {

    /** 사업자 핫딜 글(HOTDEAL_POST) 신청 건이 예약 확정됐다(결제 완료·입금 확인) — 글을 만든다(시작 시각부터 공개) */
    void onScheduled(long applicationId);

    /** 예약·진행 중이던 핫딜 글 신청 건이 취소·환불·내림됐다 — 글을 내린다 */
    void onClosed(long applicationId);

    BizHotdealPosting NONE = new BizHotdealPosting() {
        @Override
        public void onScheduled(long applicationId) {
        }

        @Override
        public void onClosed(long applicationId) {
        }
    };
}
