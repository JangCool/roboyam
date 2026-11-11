<template>
    <!-- 상단 바 — 모바일: 로고(아이콘 포함) · 검색 · 야간 모드 · 알림 종 · 내 정보 아이콘(오른쪽 끝, 로그인 전엔 「로그인」 단추만), 데스크톱: 로고 · 검색 · 알림 종 · 쪽지 아이콘 · 로그인·가입 또는 계정 메뉴.
         모바일 오른쪽 끝(mobile-nav-prefix-style-contract 3절, 2026-11-11): 햄버거(☰)·서랍을 없애고 그 자리에 내 정보(`/me`) 아이콘 — 동그라미 안 사람 아이콘. 메뉴는 하단 탭 「메뉴」의 전체 화면(`/menu`)이 맡는다.
         알림 종은 모바일에도 있다(사용자 요청 2026-11-06). 쪽지 아이콘은 데스크톱에만 — 모바일은 내 정보의 "쪽지함" 줄로 들어간다 (design 01-shell 6). 데스크톱은 바뀌지 않았다.
         오른쪽 계정 부분은 테마 셸들이 같이 쓰는 `AppHeaderActions` 다. 모바일 상단 바는 테마와 무관하게 이것 하나다(`themes.md`) -->
    <header class="app-nav sticky top-0 z-30 border-b">
        <div class="mx-auto flex h-12 w-full max-w-[var(--content-max)] items-center gap-1 px-2 md:px-4">
            <NuxtLink to="/" class="site-brand flex min-w-0 items-center rounded px-1 text-base font-bold tracking-wider focus-visible:outline-2 focus-visible:outline-primary max-md:min-h-10 max-md:min-w-8 md:px-0" :aria-label="t('shell.homeLink', { site: siteLabel })"><SiteBrand /></NuxtLink>

            <!-- 로고 옆 자리 — 데스크톱 셸이 최상위 항목 링크 줄(`AppLogoLinks`)을 넣는다(기본 테마, 「로고 옆」일 때). 모바일 상단 바로만 쓰는 테마는 비워 둔다 -->
            <slot name="links" />

            <span class="flex-1" />

            <AppHeaderActions />

            <!-- 내 정보(모바일, 로그인한 사람만) — 다른 상단 아이콘(검색·야간 모드·알림 종)과 같은 맨 아이콘. 옅은 동그라미 바탕은 내 정보 화면에 있을 때만(선택 표시 — 사용자 결정 2026-11-12).
                 로그인 전에는 `AppHeaderActions` 의 「로그인」 단추만 -->
            <NuxtLink
                v-if="loaded && user"
                to="/me"
                class="flex size-10 shrink-0 items-center justify-center rounded-full hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary md:hidden dark:hover:bg-surface-800"
                :aria-label="t('shell.myInfo')"
                :aria-current="onMe ? 'page' : undefined"
                data-testid="header-me"
            >
                <span class="grid size-[1.625rem] place-items-center rounded-full" :class="onMe ? 'header-me-on' : ''" aria-hidden="true"><i class="pi pi-user text-lg" /></span>
            </NuxtLink>
        </div>
    </header>
</template>

<script setup lang="ts">
const { t } = useI18n();
const route = useRoute();
const { siteLabel } = useSiteName();
const { user, loaded } = useAuth();
/** 내 정보 묶음 화면에 있는가 */
const onMe = computed(() => route.path === '/me' || route.path.startsWith('/me/'));
</script>

<style scoped>
/*
 * 모바일: 오른쪽 단추(검색·야간 모드·알림 종 또는 로그인·내 정보)가 먼저 자리를 잡고 로고 그림은 남는 폭까지만 줄어든다(높이는 그대로, 비율은 `object-contain`).
 * 로고 부품(`SiteBrand`)이 정한 최대 폭이 남는 폭보다 커도 단추와 겹치지 않는다
 */
@media (max-width: 767.98px) {
    .site-brand :deep(img) {
        flex-shrink: 1;
        min-width: 0;
    }
}

/* 내 정보 선택 표시 — 내 정보 화면에서만 옅은 동그라미 바탕. 상단 바(`.app-nav`)가 주 색 바탕이라 바의 글자색(`--nav-fg` = 지금 글자색)으로 칠한다(주 색으로 칠하면 바탕에 묻힌다) */
.header-me-on {
    background: color-mix(in srgb, currentColor 18%, transparent);
}
</style>
