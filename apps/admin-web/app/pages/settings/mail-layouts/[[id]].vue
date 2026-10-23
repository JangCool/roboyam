<template>
    <!-- 옛 주소(`/settings/mail-layouts[/{id}]` — 개정 3 에서 알림 템플릿 › 메일 레이아웃으로 옮겼다) — 화면이 없고 `/settings/notification-templates/layouts[/{id}]` 로 보낸다(쿼리는 그대로) -->
    <div />
</template>

<script setup lang="ts">
definePageMeta({
    middleware: (to) => {
        const id = typeof to.params.id === 'string' ? to.params.id : '';
        return navigateTo({ path: `/settings/notification-templates/layouts${id ? `/${encodeURIComponent(id)}` : ''}`, query: to.query }, { replace: true });
    },
});
</script>
