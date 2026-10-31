<template>
    <!--
        사업자 › 사업자 목록(`/businesses`) — 승인된 사업자 운영: 승인·정지·탈퇴·반려·전체 탭 + 서비스 필터, 서랍에서 정지 · 정지 해제 · 평문 열람 · 등록증 열람(04-ia-review 5절).
        화면 한 벌은 `BusinessAdminPage`(인증 심사와 같이 쓴다)이고 여기서는 갈래만 넘긴다. `?status=`·`?id=` 로 탭·서랍을 바로 연다.
    -->
    <BusinessAdminPage mode="directory" />
</template>
