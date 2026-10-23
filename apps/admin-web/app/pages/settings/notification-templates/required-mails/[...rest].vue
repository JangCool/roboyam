<template>
    <!-- 옛 주소(개정 3 의 필수 안내 메일 `/settings/notification-templates/required-mails[/{kind}]`) — 화면이 없고 문구 › 필수 안내 메일 `/settings/notification-messages/required-mails[/{kind}]` 로 보낸다(계약 「개정 4」, 쿼리는 그대로) -->
    <div />
</template>

<script setup lang="ts">
definePageMeta({
    middleware: (to) => {
        const rest = Array.isArray(to.params.rest) ? to.params.rest.filter(Boolean) : [];
        const kind = typeof rest[0] === 'string' ? rest[0] : '';
        return navigateTo({ path: `/settings/notification-messages/required-mails${kind ? `/${encodeURIComponent(kind)}` : ''}`, query: to.query }, { replace: true });
    },
});
</script>
