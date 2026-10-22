<template>
    <!--
        로고 자리 — 운영자가 올린 로고(관리자 설정 「일반」)가 있으면 로고 그림 + 사이트 이름 글자를 함께, 없으면 사이트 이름 글자만.
        ⚠ 그림은 `<img src>` 하나로만 그린다 — SVG 로 올린 것도 서버가 data URI 로 바꿔 주므로 `v-html` 을 쓰지 않는다. 못 불러오면 이름 글자만 남는다.
        이름 글자가 옆에 있으므로 그림의 대체 텍스트는 비운다(스크린 리더가 이름을 두 번 읽지 않게).
        로고 크기는 임의라서 높이를 고정(28px)하고 가로는 비율을 지키되 최대 9rem 까지만 — 헤더(높이 48px)·드로어·로그인·설치 레이아웃에서 넘치지 않는다. 이름 글자는 남는 폭에서 줄임표(…)로 줄어든다.
    -->
    <span class="inline-flex min-w-0 items-center gap-2">
        <img v-if="showLogo" :src="siteLogoUrl ?? undefined" alt="" class="block h-7 w-auto max-w-36 shrink-0 object-contain" data-testid="site-brand-logo" @error="failed = true" />
        <span class="min-w-0 truncate" data-testid="site-brand-name">{{ siteLabel }}</span>
    </span>
</template>

<script setup lang="ts">
/** 상단 로고 자리(헤더·모바일 메뉴·로그인·설치 레이아웃). 이름을 모르면 기본 이름(`UtilSiteName.DEFAULT`)이다. */
const { siteLabel, siteLogoUrl } = useSiteName();

/** 그림을 못 불러왔는가 — 깨진 그림 대신 이름 글자만 보인다. 로고가 바뀌면 다시 시도한다 */
const failed = ref(false);
watch(siteLogoUrl, () => (failed.value = false));

/** 로고 그림을 보이나 — 로고가 있고 아직 못 불러온 일이 없을 때 */
const showLogo = computed(() => Boolean(siteLogoUrl.value) && !failed.value);
</script>
