<template>
    <!--
        사업자 › 인증 심사(`/business-reviews`) — 사업자 신원 심사 큐: 심사 중(처음·재신청) · 재심사, 오래된 것이 위. 서랍에서 국세청 다시 조회 · 승인 · 반려 · 등록증 열람(04-ia-review 5절).
        화면 한 벌은 `BusinessAdminPage`(사업자 목록과 같이 쓴다)이고 여기서는 갈래만 넘긴다. 관리자 알림 `BUSINESS_APPLICATION_PENDING`·`BUSINESS_RE_REVIEW_PENDING` 의 링크가 `?id=` 로 들어온다.
    -->
    <BusinessAdminPage mode="review" />
</template>
