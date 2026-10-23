<template>
    <!--
        옛 주소(`/settings/notification-templates/templates` — 받는 사람을 목록 안에서 고르던 하나의 템플릿 목록) — 화면이 없고 받는 사람 메뉴로 보낸다.
        `?audience=MEMBER` 면 회원 템플릿(`templates/member`), 그 밖(없음·OPERATOR)은 운영자 템플릿(`templates/operator`). 나머지 쿼리(`channel`·`target`·`layout` …)는 그대로 넘긴다.
        안의 링크는 모두 새 주소로 바로 가고, 옛 주소(`/settings/mail-templates`)·밖에서 남은 링크만 여기로 온다.
    -->
    <div />
</template>

<script setup lang="ts">
definePageMeta({
    middleware: (to) => {
        const { audience, ...query } = to.query;
        return navigateTo({ path: `/settings/notification-templates/templates/${audience === 'MEMBER' ? 'member' : 'operator'}`, query }, { replace: true });
    },
});
</script>
