<template>
    <!-- 게시판 기본 설정의 부모 주소(`/boards/defaults`) — 화면이 없고 첫 하위(일반)로 보낸다. 역할 관리(`settings/roles/[[kind]].vue`)의 부모 주소 처리와 같다(주소의 쿼리는 그대로 넘긴다) -->
    <div />
</template>

<script setup lang="ts">
definePageMeta({
    middleware: (to) => navigateTo({ path: '/boards/defaults/general', query: to.query }, { replace: true }),
});
</script>
