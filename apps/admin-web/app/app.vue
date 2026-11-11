<template>
    <NuxtLayout>
        <NuxtPage />
    </NuxtLayout>
</template>

<script setup lang="ts">
import UtilAdminSite from '~/utils/util-admin-site';

// 페이지마다 제목을 정하고(useSeoMeta) 여기서 "제목 · 이름 관리자" 로 붙인다. 이름이 바뀌면(서버에서 받으면) 다시 계산하도록 getter 로 준다
// (titleTemplate 함수 안에서 읽으면 반응하지 않는다). 언어가 바뀌어도 "관리자" 글자가 따라온다.
const { t } = useI18n();
const { siteLabel } = useAdminAuth();
useHead(() => {
    const site = siteLabel.value;
    const admin = t('shell.adminBadge');
    return {
        titleTemplate: (title?: string) => UtilAdminSite.formatTitle(title, site, admin),
    };
});
</script>
