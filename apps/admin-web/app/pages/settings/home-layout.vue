<template>
    <!-- 옛 주소(`/settings/home-layout`) — 테마 화면의 탭으로 옮겼다(사용자 결정 2026-10-28). 쿼리(`?theme=`)는 그대로 넘긴다 -->
    <div />
</template>

<script setup lang="ts">
definePageMeta({
    middleware: (to) => navigateTo({ path: '/settings/theme/layout', query: to.query }, { replace: true }),
});
</script>
