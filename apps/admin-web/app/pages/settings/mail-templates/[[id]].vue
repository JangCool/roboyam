<template>
    <!--
        옛 주소(`/settings/mail-templates[/{id}]` — 개정 3 에서 알림 템플릿으로 옮겼다) — 화면이 없고 `/settings/notification-templates/templates[/{id}]` 로 보낸다.
        옛 거르기 `?kind=` 는 새 `?target=` 으로 바꾸고(필수 안내 메일·이벤트 코드가 같다), 이메일 템플릿만 있던 화면이라 `?channel=EMAIL` 을 붙인다. 나머지 쿼리는 그대로.
    -->
    <div />
</template>

<script setup lang="ts">
definePageMeta({
    middleware: (to) => {
        const id = typeof to.params.id === 'string' ? to.params.id : '';
        const { kind, ...rest } = to.query;
        const query = { ...rest, ...(kind ? { target: kind } : {}), ...(id && id !== 'new' ? {} : { channel: 'EMAIL' }) };
        return navigateTo({ path: `/settings/notification-templates/templates${id ? `/${encodeURIComponent(id)}` : ''}`, query }, { replace: true });
    },
});
</script>
