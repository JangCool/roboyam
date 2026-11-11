<template>
    <InstallStatusBanner />
    <NuxtLayout>
        <NuxtPage />
    </NuxtLayout>
</template>

<script setup lang="ts">
import UtilSiteInfo from '~/utils/util-site-info';
import UtilSiteName from '~/utils/util-site-name';

// 페이지마다 제목을 정하고(useSeoMeta) 여기서 "제목 · 사이트 이름" 으로 붙인다. 이름을 아직 모르면(설치 전·못 받음) 이름을 붙이지 않는다.
// 이름이 바뀌면 다시 계산하도록 getter 로 준다(titleTemplate 함수 안에서 읽으면 반응하지 않는다).
//
// ⚠ 운영에서는 서버가 초기 `index.html` 의 `<head>`(제목·소개·탭 아이콘·manifest·링크 미리보기)를 채운다 — 화면이 그것을 덮어쓰지 않게 `app.head` 에 기본 제목을 두지 않고(nuxt.config.ts),
//   여기서도 제목(쪽 제목이 있을 때만)과 iOS 홈 화면 이름만 넣는다. 아이콘·manifest `<link>` 는 운영에서 넣지 않는다.
const { siteName, siteLabel } = useSiteName();
const { tagline } = useSiteTagline();
const siteInfo = useSiteInfoState();
useHead(() => {
    const name = siteName.value;
    const slogan = tagline.value;
    return {
        titleTemplate: (title?: string) => UtilSiteName.formatTitle(title, name, slogan),
        meta: [{ name: 'apple-mobile-web-app-title', content: siteLabel.value }],
        // 개발 서버 전용 — Nuxt 개발 서버가 `index.html` 을 내려 주어 서버의 `<head>` 채우기가 보이지 않으므로 올린 아이콘·manifest 를 화면이 넣어 확인할 수 있게 한다.
        // 운영 빌드(`import.meta.dev` 가 false)에서는 이 줄이 없어지고 서버가 채운 링크가 그대로 남는다.
        link: import.meta.dev ? UtilSiteInfo.devHeadLinks(siteInfo.value) : [],
    };
});
</script>
