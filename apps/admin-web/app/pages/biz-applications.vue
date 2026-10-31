<template>
    <!-- 옛 주소 — 통합 「신청 심사」는 없앴다(04-ia-review Q5). 남아 있는 옛 알림·즐겨찾기 링크를 광고 심사로 보낸다(`?id=`·`?queue=` 유지). 핫딜 건이면 그 화면이 권한 없음(403)을 보인다 -->
    <div />
</template>

<script setup lang="ts">
definePageMeta({ redirect: (to) => ({ path: '/ad-reviews', query: to.query }) });
</script>
