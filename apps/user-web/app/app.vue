<template>
    <InstallStatusBanner />
    <NuxtLayout>
        <NuxtPage />
    </NuxtLayout>
</template>

<script setup lang="ts">
import UtilSiteName from '~/utils/util-site-name';

// 페이지마다 제목을 정하고(useSeoMeta) 여기서 "제목 · 사이트 이름" 으로 붙인다. 이름을 아직 모르면(설치 전) 이름을 붙이지 않는다.
// 이름이 바뀌면 다시 계산하도록 getter 로 준다(titleTemplate 함수 안에서 읽으면 반응하지 않는다).
// ⚠ 단순 버전 — 원본(community)의 사이트 아이콘(탭 아이콘·iOS 홈 화면 아이콘) 교체 코드는 마법사에 필요 없어 뺐다.
const { siteName, siteLabel } = useSiteName();
const { tagline } = useSiteTagline();
useHead(() => {
    const name = siteName.value;
    const slogan = tagline.value;
    return {
        titleTemplate: (title?: string) => UtilSiteName.formatTitle(title, name, slogan),
        meta: [{ name: 'apple-mobile-web-app-title', content: siteLabel.value }],
    };
});
</script>
