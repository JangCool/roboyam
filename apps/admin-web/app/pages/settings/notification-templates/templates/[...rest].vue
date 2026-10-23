<template>
    <!--
        옛 주소(개정 3 의 `/settings/notification-templates/templates[/operator|/member|/{id}|/new]` — 이벤트·채널별 내용을 「템플릿」이라 부르던 화면) — 화면이 없고 「문구」 화면으로 보낸다(계약 「개정 4」).
        `…/templates`(목록) 는 `?audience=MEMBER` 면 회원 문구, 그 밖은 운영자 문구. `…/templates/operator|member` 는 그 받는 사람의 문구 목록, `…/templates/{id}`·`new` 는 문구 편집 `/settings/notification-messages/{id}`.
        쿼리: `channel=CHAT` → `SLACK`, `layout=` → `template=`(그 밖은 그대로 — `UtilNotificationTemplateMessages.legacyMessageQuery`).
    -->
    <div />
</template>

<script setup lang="ts">
import UtilNotificationTemplateMessages from '~/utils/util-notification-template-messages';

definePageMeta({
    middleware: (to) => {
        const rest = Array.isArray(to.params.rest) ? to.params.rest.filter(Boolean) : [];
        const { audience, ...others } = UtilNotificationTemplateMessages.legacyMessageQuery(to.query);
        const first = typeof rest[0] === 'string' ? rest[0] : '';
        if (!first) return navigateTo({ path: UtilNotificationTemplateMessages.messageListPath(audience === 'MEMBER' ? 'MEMBER' : 'OPERATOR'), query: others }, { replace: true });
        if (first === 'operator' || first === 'member') return navigateTo({ path: UtilNotificationTemplateMessages.messageListPath(first === 'member' ? 'MEMBER' : 'OPERATOR'), query: others }, { replace: true });
        return navigateTo({ path: UtilNotificationTemplateMessages.messagePath(first), query: audience ? { ...others, audience } : others }, { replace: true });
    },
});
</script>
