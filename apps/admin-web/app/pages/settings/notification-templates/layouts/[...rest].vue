<template>
    <!-- 옛 주소(개정 3 의 메일 레이아웃 `/settings/notification-templates/layouts[/{id}]`) — 화면이 없고 메일 템플릿 `/settings/notification-templates/mail[/{id}]` 로 보낸다(계약 「개정 4」 — 메일 레이아웃 = 메일 템플릿, 쿼리는 그대로) -->
    <div />
</template>

<script setup lang="ts">
definePageMeta({
    middleware: (to) => {
        const rest = Array.isArray(to.params.rest) ? to.params.rest.filter(Boolean) : [];
        const id = typeof rest[0] === 'string' ? rest[0] : '';
        return navigateTo({ path: `/settings/notification-templates/mail${id ? `/${encodeURIComponent(id)}` : ''}`, query: to.query }, { replace: true });
    },
});
</script>
