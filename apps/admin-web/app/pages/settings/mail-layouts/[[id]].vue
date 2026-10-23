<template>
    <!-- 옛 주소(`/settings/mail-layouts[/{id}]` — 개정 3 의 메일 레이아웃, 개정 4 의 메일 템플릿) — 화면이 없고 `/settings/notification-templates/mail[/{id}]` 로 바로 보낸다(쿼리는 그대로) -->
    <div />
</template>

<script setup lang="ts">
definePageMeta({
    middleware: (to) => {
        const id = typeof to.params.id === 'string' ? to.params.id : '';
        return navigateTo({ path: `/settings/notification-templates/mail${id ? `/${encodeURIComponent(id)}` : ''}`, query: to.query }, { replace: true });
    },
});
</script>
