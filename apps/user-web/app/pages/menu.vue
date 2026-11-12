<template>
    <!--
        전체 화면 메뉴 — `/menu`(mobile-nav-prefix-style-contract 4절). 모바일 하단 탭 「메뉴」는 이제 서랍(`AppMenuDrawer`)으로 열고(사용자 2026-11-12), 이 화면은 주소로 직접 들어올 때만 쓴다.
        본문은 서랍과 같은 `AppMenuPanel`.
    -->
    <div class="content-card" data-testid="menu-page">
        <header>
            <h1 class="text-base font-bold">{{ t('shell.menu') }}</h1>
        </header>
        <AppMenuPanel />
    </div>
</template>

<script setup lang="ts">
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('shell.menu'), description: () => t('menu.pageSeoDescription', { site: siteLabel.value }) });
</script>
