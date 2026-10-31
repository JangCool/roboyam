<template>
    <!--
        핫딜 › 핫딜 심사(`/hotdeal-reviews`) — 사업자 핫딜 신청 건(HOTDEAL_POST · HOTDEAL_PLACEMENT) 심사·결제 확인 큐(04-ia-review 1-2·4절).
        조회 `hotdeal.read`(메뉴 항목 — 셸이 거른다) · 처리 `hotdeal.manage` · 환불 `deal-order.refund`. 화면 한 벌은 `BizAppReviewPage` 이고 여기서는 도메인만 넘긴다.
        관리자 알림 `HOTDEAL_REVIEW_PENDING`·`HOTDEAL_REFUND_REQUESTED` 의 링크가 `?id=` 로 들어온다.
    -->
    <BizAppReviewPage domain="HOTDEAL" />
</template>
