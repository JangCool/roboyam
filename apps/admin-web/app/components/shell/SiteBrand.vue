<template>
    <!--
        로고 자리 — 평소 로고 묶음 4칸(한글·영문 × 밝은·어두운 바탕용, `me` 의 `siteLogos`)에서 화면 언어와 놓인 면의 바탕에 맞는 한 칸을 제 비율로 보인다(2026-11-19 — 예전엔 한글·밝은 칸 한 장을 20px 네모에 넣어 가로 로고가 점처럼 작아지고 강조색 바에서 묻혔다).
          · 바탕: `surface="bar"` 면 상단 네비(강조색 칠하기·밝은 강조색·테마로 정해진다 — `UtilLayoutPrefs.barIsDark`), 아니면 화면 테마(로그인 카드·서랍 머리 — 라이트 흰 면 · 다크 어두운 면).
          · 대체 순서·못 불러온 그림 건너뛰기는 `UtilSiteLogo.resolve` 한 곳(공개 사이트 `SiteBrand` 와 같은 규칙).
          · 이름 글자: 묶음이 「로고에 사이트 이름이 들어 있음」(`siteNameInLogo`)이고 그림이 보이면 붙이지 않고 그림의 `alt` 가 이름이다. 아니면 이름 글자를 붙이고 그림 `alt` 는 비운다("관리자" 뱃지는 없다 — 브라우저 탭 제목과 홈 링크의 이름표에는 "관리자"가 남는다).
          · 크기: 높이 28px, 가로는 비율대로 최대 180px. 글자 색은 바깥을 따른다.
        ⚠ 그림은 `<img src>` 하나로만 그린다 — SVG 로 올린 것도 서버가 data URI 로 바꿔 주므로 `v-html` 을 쓰지 않는다(올린 마크업의 스크립트가 도는 자리를 만들지 않는다).
    -->
    <span class="inline-flex min-w-0 items-center gap-2">
        <img v-if="pick" :src="pick.url" :alt="showName ? '' : siteLabel" :aria-hidden="showName ? 'true' : undefined" class="h-7 w-auto max-w-[180px] shrink-0 object-contain object-left" @error="markFailed(pick.url)" />
        <span v-if="showName" class="block min-w-0 truncate font-bold tracking-wider">{{ siteLabel }}</span>
    </span>
</template>

<script setup lang="ts">
import UtilLayoutPrefs from '~/utils/util-layout-prefs';
import UtilSiteLogo from '~/utils/util-site-logo';

interface Props {
    /** 놓인 면 — `bar` 상단 네비(강조색 바탕) · `plain` 화면 테마의 기본 면(로그인 카드·서랍 머리) */
    surface?: 'bar' | 'plain';
}

const props = withDefaults(defineProps<Props>(), { surface: 'plain' });

const { siteLabel, siteLogos, siteNameInLogo } = useAdminAuth();
const { language } = useLanguage();
const { prefs } = useLayoutPrefs();

/** 놓인 면의 바탕 */
const tone = computed(() => {
    const dark = props.surface === 'bar' ? UtilLayoutPrefs.barIsDark(prefs.value.primary, prefs.value.barTint, prefs.value.theme) : prefs.value.theme === 'dark';
    return dark ? 'dark' : 'light';
});

/** 못 불러온 그림 주소 — 대체 순서에서 건너뛴다. 로고가 바뀌면 다시 시도한다 */
const failed = ref<string[]>([]);
watch(siteLogos, () => (failed.value = []));

function markFailed(url: string): void {
    if (!failed.value.includes(url)) failed.value = [...failed.value, url];
}

/** 보일 칸 — 없으면(올린 로고 없음·모두 못 불러옴) 이름 글자만 */
const pick = computed(() => UtilSiteLogo.resolve(siteLogos.value, UtilSiteLogo.variantOf(language.value, tone.value), failed.value));
/** 이름 글자를 붙이는가 — 그림에 이름이 들어 있고 그림이 보이면 붙이지 않는다 */
const showName = computed(() => !(pick.value && siteNameInLogo.value));
</script>
