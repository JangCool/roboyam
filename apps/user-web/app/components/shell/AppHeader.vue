<template>
    <!-- 상단 바 — 모바일: ☰ · 로고 · 검색 · 로그인/닉네임, 데스크톱: 로고 · 검색 · 알림 종 · 쪽지 아이콘 · 로그인·가입 또는 계정 메뉴.
         알림 종은 데스크톱에만 있다(모바일은 하단 탭의 "알림"이 같은 일을 한다). 쪽지 아이콘도 데스크톱에만 — 모바일은 메뉴·내 정보의 "쪽지함" 줄로 들어간다 (design 01-shell 6).
         오른쪽 계정 부분은 테마 셸들이 같이 쓰는 `AppHeaderActions` 다. 모바일 상단 바는 테마와 무관하게 이것 하나다(`themes.md`) -->
    <header class="app-nav sticky top-0 z-30 border-b">
        <div class="mx-auto flex h-12 w-full max-w-[var(--content-max)] items-center gap-1 px-2 md:px-4">
            <button
                type="button"
                class="flex size-10 items-center justify-center rounded-full hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary md:hidden dark:hover:bg-surface-800"
                :aria-label="t('shell.openMenu')"
                aria-haspopup="dialog"
                @click="$emit('open-menu')"
            >
                <i class="pi pi-bars text-lg" aria-hidden="true" />
            </button>

            <NuxtLink to="/" class="flex min-w-0 items-center rounded px-1 text-base font-bold tracking-wider focus-visible:outline-2 focus-visible:outline-primary max-md:min-h-10 max-md:min-w-8 md:px-0" :aria-label="t('shell.homeLink', { site: siteLabel })"><SiteBrand /></NuxtLink>

            <!-- 로고 옆 자리 — 데스크톱 셸이 최상위 항목 링크 줄(`AppLogoLinks`)을 넣는다(기본 테마, 「로고 옆」일 때). 모바일 상단 바로만 쓰는 테마는 비워 둔다 -->
            <slot name="links" />

            <span class="flex-1" />

            <AppHeaderActions />
        </div>
    </header>
</template>

<script setup lang="ts">
defineEmits<{ 'open-menu': [] }>();

const { t } = useI18n();
const { siteLabel } = useSiteName();
</script>
