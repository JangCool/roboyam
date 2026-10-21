<template>
    <!--
        로고 자리 — 운영자가 올린 로고(관리자 설정 「일반」)가 있으면 이름 앞에 작게(20px), 없으면 사이트 이름만.
        ⚠ 그림은 `<img src>` 하나로만 그린다 — SVG 로 올린 것도 서버가 data URI 로 바꿔 주므로 `v-html` 을 쓰지 않는다. 못 불러오면 이름만 남긴다.
    -->
    <span class="inline-flex min-w-0 items-center gap-2">
        <img v-if="siteLogoUrl && !failed" :src="siteLogoUrl" alt="" aria-hidden="true" class="size-5 shrink-0 rounded-[4px] object-contain" data-testid="site-brand-logo" @error="failed = true" />
        <span class="min-w-0 truncate" data-testid="site-brand-name">{{ siteLabel }}</span>
    </span>
</template>

<script setup lang="ts">
/** 상단 로고 자리(헤더·모바일 메뉴·로그인·설치 레이아웃). 이름을 모르면 기본 이름(`UtilSiteName.DEFAULT`)이다. */
const { siteLabel, siteLogoUrl } = useSiteName();

/** 그림을 못 불러왔는가 — 깨진 그림 대신 이름만 보인다. 로고가 바뀌면 다시 시도한다 */
const failed = ref(false);
watch(siteLogoUrl, () => (failed.value = false));
</script>
