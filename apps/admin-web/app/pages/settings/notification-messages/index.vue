<template>
    <!-- 문구의 부모 주소(`/settings/notification-messages`) — 화면이 없고 첫 하위(운영자 문구 `…/operator`)로 보낸다. 템플릿·역할 관리·이벤트의 부모 주소 처리와 같다(주소의 쿼리는 그대로 넘긴다) -->
    <div />
</template>

<script setup lang="ts">
definePageMeta({
    middleware: (to) => navigateTo({ path: '/settings/notification-messages/operator', query: to.query }, { replace: true }),
});
</script>
