<template>
    <!-- 메뉴 관리의 부모 주소(`/menus`) — 화면이 없고 공개 사이트 메뉴로 보낸다(`boards/defaults/index.vue` 와 같은 방식, 쿼리는 그대로 넘긴다) -->
    <div />
</template>

<script setup lang="ts">
definePageMeta({
    middleware: (to) => navigateTo({ path: '/menus/public', query: to.query }, { replace: true }),
});
</script>
