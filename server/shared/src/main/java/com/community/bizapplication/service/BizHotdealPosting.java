package com.community.bizapplication.service;

/**
 * 사업자 핫딜 글 게시의 고리(biz-hotdeal-contract — 구현 {@link BizHotdealPublisher}). 빈이 없으면 {@link #NONE}(아무것도 하지 않음).
 * 신청 건 처리 트랜잭션 안(그 건 행을 잠근 뒤)에서 불린다(예외를 던지면 그 처리가 되돌아간다 — 구현은 외부 호출을 하지 않는다).
 */
public interface BizHotdealPosting {

    /** 사업자 핫딜 글(HOTDEAL_POST) 신청 건이 예약 확정됐다(결제 완료·입금 확인) — 시작이 이미 지났으면 바로 글을 만든다(아니면 배치 {@code biz-hotdeal-publish} 몫) */
    void onScheduled(long applicationId);

    /** 예약·진행 중이던 핫딜 글 신청 건이 취소·환불·내림됐다 — 글을 숨긴다(지우지 않는다) */
    void onClosed(long applicationId);

    /** 내림(긴급·정지)을 되돌렸다 — 이 흐름이 숨긴 글을 다시 보이고 딜 마감을 (연장된) 신청 기간 끝에 맞춘다 */
    default void onRestored(long applicationId) {
        onScheduled(applicationId);
    }

    BizHotdealPosting NONE = new BizHotdealPosting() {
        @Override
        public void onScheduled(long applicationId) {
        }

        @Override
        public void onClosed(long applicationId) {
        }
    };
}
