<template>
    <!--
        광고 › 광고 심사(`/ad-reviews`) — 사업자 광고 신청 건(AD_BANNER · AD_TEXT_LINE) 심사·결제 확인 큐(04-ia-review 1-2).
        조회 `ad.read`(메뉴 항목 — 셸이 거른다) · 처리 `ad.manage` · 환불 `deal-order.refund`. 화면 한 벌은 `BizAppReviewPage` 이고 여기서는 도메인만 넘긴다.
        관리자 알림 `AD_REVIEW_PENDING`·`AD_REFUND_REQUESTED` 의 링크가 `?id=` 로 들어온다.
    -->
    <BizAppReviewPage domain="AD" />
</template>
