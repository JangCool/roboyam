<template>
    <!--
        로고 자리 — 운영자가 올린 로고 4칸(관리자 설정 「일반」: 한글·영문 × 밝은·어두운 바탕용)에서 화면 언어와 놓인 면의 바탕에 맞는 하나를 보인다. 쓸 그림이 없으면 사이트 이름 글자만.
          · 언어: 화면 언어(`useLanguage`) — `en` 이면 영문 칸, 아니면 한글 칸.
          · 바탕: 그림을 둘(밝은 바탕용·어두운 바탕용) 함께 두고 CSS 가 하나만 보인다(`main.css` 의 `.site-logo-light`·`.site-logo-dark` — 놓인 면의 역할 변수 `--brand-logo-light`·`--brand-logo-dark`).
            면의 바탕은 테마 하나로 정해지지 않는다(야간 모드 · 주 색상 바 · 흰 모바일 바 · 색상 테마의 로고 줄 색 · 모바일/데스크톱) — 그 색을 정하는 CSS 자리에서 짝 값을 같이 채운다.
            두 칸이 같은 그림으로 대체되면 하나만 둔다.
          · 대체 순서·못 불러온 그림 건너뛰기는 `UtilSiteLogo.resolve` 한 곳.
          · 이름 글자: 「로고에 사이트 이름이 들어 있음」(`nameInLogo`)이 켜져 있으면 붙이지 않고 그림의 `alt` 가 이름이다. 꺼져 있거나 쓸 그림이 없으면 글자를 붙이고 그림의 `alt` 는 비운다(스크린 리더가 이름을 두 번 읽지 않게).
          · 모바일도 데스크톱처럼 로고 전체(아이콘 포함)를 보인다(사용자 결정 2026-11-06 — 메뉴 단추가 상단 바 오른쪽 끝으로 가면서 「글자만(#wordmark)」을 그만둠).
        ⚠ 그림은 `<img src>` 로만 그린다 — SVG 로 올린 것도 서버가 data URI 로 바꿔 주므로 `v-html` 을 쓰지 않는다.
        크기: 가로로 긴 그림 로고(약 200×36)가 잘리지 않게 높이 28px(데스크톱 32px), 가로는 비율대로 최대 220px(모바일은 화면 폭의 45% 까지) — 헤더(48·56·60px)·드로어·로그인·설치 레이아웃에서 넘치지 않는다.
    -->
    <span class="inline-flex min-w-0 items-center gap-2">
        <img v-if="lightSrc && lightSrc === darkSrc" :src="lightSrc" :alt="imgAlt" :class="imgClass" data-testid="site-brand-logo" @error="markFailed(lightSrc)" />
        <template v-else>
            <img v-if="lightSrc" :src="lightSrc" :alt="imgAlt" :class="[imgClass, 'site-logo-light']" data-testid="site-brand-logo" @error="markFailed(lightSrc)" />
            <img v-if="darkSrc" :src="darkSrc" :alt="imgAlt" :class="[imgClass, 'site-logo-dark']" data-testid="site-brand-logo-dark" @error="markFailed(darkSrc)" />
        </template>
        <span v-if="showName" class="min-w-0 truncate" data-testid="site-brand-name">{{ siteLabel }}</span>
    </span>
</template>

<script setup lang="ts">
import UtilSiteLogo from '~/utils/util-site-logo';

/** 상단 로고 자리(헤더·모바일 메뉴·로그인·설치 레이아웃). 이름을 모르면 기본 이름(`UtilSiteName.DEFAULT`)이다. */
const { siteLabel, siteLogos, nameInLogo } = useSiteName();
const { language } = useLanguage();

/** 못 불러온 그림 주소 — 대체 순서에서 건너뛴다. 로고 값이 바뀌면 다시 시도한다 */
const failed = ref<string[]>([]);
watch(siteLogos, () => (failed.value = []));

function markFailed(url: string): void {
    if (!failed.value.includes(url)) failed.value = [...failed.value, url];
}

/** 밝은 면·어두운 면에 둘 그림(지금 언어 기준) — 없으면 null */
const lightSrc = computed(() => UtilSiteLogo.resolve(siteLogos.value, UtilSiteLogo.variantOf(language.value, 'light'), failed.value)?.url ?? null);
const darkSrc = computed(() => UtilSiteLogo.resolve(siteLogos.value, UtilSiteLogo.variantOf(language.value, 'dark'), failed.value)?.url ?? null);

/** 이름 글자를 붙이나 — 「이름 포함」이 꺼져 있거나, 어느 바탕이든 쓸 그림이 없을 때 */
const showName = computed(() => !nameInLogo.value || !lightSrc.value || !darkSrc.value);
/** 그림의 대체 텍스트 — 이름 글자가 옆에 없을 때만 이름 */
const imgAlt = computed(() => (showName.value ? '' : siteLabel.value));

const imgClass = 'block h-7 w-auto max-w-[min(13.75rem,45vw)] shrink-0 object-contain md:h-8 md:max-w-[13.75rem]';
</script>
