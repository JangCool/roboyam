<template>
    <!--
        옛 주소(`/settings/mail-templates[/{id}]` — 개정 전 메일 템플릿 화면, 개정 4 의 「문구」) — 화면이 없고 문구 화면으로 바로 보낸다.
        목록은 이메일 문구만 있던 화면이라 `?channel=EMAIL` 을 붙여 운영자 문구(회원 이메일 인증 `?kind=MEMBER_EMAIL_VERIFY`·`?audience=MEMBER` 면 회원 문구)로, 하나는 `/settings/notification-messages/{id}`.
        쿼리: `kind=` → `target=`, `layout=` → `template=`(그 밖은 그대로 — `UtilNotificationTemplateMessages.legacyMessageQuery`).
    -->
    <div />
</template>

<script setup lang="ts">
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';

definePageMeta({
    middleware: (to) => {
        const id = typeof to.params.id === 'string' ? to.params.id : '';
        const { audience, ...query } = UtilNotificationTemplateMessages.legacyMessageQuery(to.query);
        if (id) return navigateTo({ path: UtilNotificationTemplateMessages.messagePath(id), query: id === 'new' ? { ...query, channel: 'EMAIL', ...(audience ? { audience } : {}) } : query }, { replace: true });
        const member = audience === 'MEMBER' || query.target === 'MEMBER_EMAIL_VERIFY';
        return navigateTo({ path: UtilNotificationTemplateMessages.messageListPath(member ? 'MEMBER' : 'OPERATOR'), query: { ...query, channel: 'EMAIL' } }, { replace: true });
    },
});
</script>
