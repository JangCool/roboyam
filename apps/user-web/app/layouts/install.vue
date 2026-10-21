<template>
    <!-- 설치 마법사(`/install`) — 셸(헤더·하단 탭·메뉴) 없이 가운데 카드만. 모바일 375px 한 열이고 데스크톱에서는 카드 폭만 제한한다 -->
    <div class="flex min-h-dvh flex-col bg-surface-50 dark:bg-surface-950">
        <div class="mx-auto flex w-full max-w-[34rem] flex-1 flex-col px-4 pb-8 pt-6 sm:justify-center sm:py-10">
            <header class="mb-5 text-center">
                <!-- 상단 로고는 공개 헤더·모바일 메뉴와 같은 부품(아이콘 + 사이트 이름, 아이콘이 없으면 이름만) — docs/api-site-icon.md -->
                <p class="text-xl font-bold tracking-wider"><SiteBrand class="max-w-full" /></p>
                <!-- 슬로건은 운영자가 정한 선택 값이다 — 없으면 줄 자체를 그리지 않는다(설치 전에는 대개 없다) -->
                <p v-if="tagline" class="mt-0.5 text-xs text-muted-color" data-testid="site-tagline">{{ tagline }}</p>
            </header>

            <main id="main-content" class="rounded-2xl border border-surface bg-surface-0 p-5 dark:bg-surface-900 sm:p-6">
                <slot />
            </main>
        </div>

        <AppToaster />
    </div>
</template>

<script setup lang="ts">
const { tagline } = useSiteTagline();
</script>
