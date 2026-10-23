<template>
    <!--
        옛 주소(`/settings/mail-kinds[/{kind}]` — 개정 3 에서 없앤 「메일 종류」 화면) — 화면이 없고 새 화면으로 바로 보낸다(개정 4 주소).
        계정·보안 메일 종류는 문구 › 필수 안내 메일 상세, 알림 메일 종류(관리자 알림 이벤트 코드)는 운영자 이벤트 상세, 종류가 없으면 필수 안내 메일 목록.
    -->
    <div />
</template>

<script setup lang="ts">
/** 옛 「메일 종류」의 계정·보안 메일 — 개정 3 의 필수 안내 메일(코드가 같다) */
const REQUIRED_MAIL_CODES = ['MEMBER_EMAIL_VERIFY', 'OPERATOR_INVITE', 'OPERATOR_CREDENTIALS', 'OPERATOR_RESET_CODE', 'MAIL_TEST'];

definePageMeta({
    middleware: (to) => {
        const kind = typeof to.params.kind === 'string' ? to.params.kind : '';
        if (!kind) return navigateTo('/settings/notification-messages/required-mails', { replace: true });
        if (REQUIRED_MAIL_CODES.includes(kind)) return navigateTo(`/settings/notification-messages/required-mails/${encodeURIComponent(kind)}`, { replace: true });
        return navigateTo(`/settings/notifications/operator/${encodeURIComponent(kind)}`, { replace: true });
    },
});
</script>
