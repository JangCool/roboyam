<template>
    <!-- 알림 템플릿의 부모 주소(`/settings/notification-templates`) — 화면이 없고 첫 하위(템플릿)로 보낸다. 역할 관리·게시판 기본 설정의 부모 주소 처리와 같다(주소의 쿼리는 그대로 넘긴다) -->
    <div />
</template>

<script setup lang="ts">
definePageMeta({
    middleware: (to) => navigateTo({ path: '/settings/notification-templates/templates', query: to.query }, { replace: true }),
});
</script>
