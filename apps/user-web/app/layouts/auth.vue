<template>
    <!-- 로그인·가입·인증·재설정·온보딩 — 가운데 카드. 모바일 우선이고 데스크톱에서는 카드 폭만 제한한다 -->
    <div class="flex min-h-dvh flex-col bg-surface-50 dark:bg-surface-950">
        <div class="mx-auto flex w-full max-w-[26rem] flex-1 flex-col px-4 pb-6 pt-6 sm:justify-center sm:py-10">
            <header class="mb-5 text-center">
                <!-- 상단 로고는 공개 헤더·모바일 메뉴와 같은 부품(아이콘 + 사이트 이름, 아이콘이 없으면 이름만) — docs/api-site-icon.md -->
                <NuxtLink to="/" class="inline-flex max-w-full items-center text-xl font-bold tracking-wider max-md:-my-1 max-md:py-1" :aria-label="t('login.homeLabel', { site: siteLabel })"><SiteBrand /></NuxtLink>
                <!-- 슬로건은 운영자가 정한 선택 값이다 — 없으면 줄 자체를 그리지 않는다(기본 문구를 두지 않는다) -->
                <p v-if="tagline" class="mt-0.5 text-xs text-muted-color" data-testid="site-tagline">{{ tagline }}</p>
            </header>

            <main class="rounded-2xl border border-surface bg-surface-0 p-5 dark:bg-surface-900 sm:p-6">
                <slot />
            </main>

            <!-- 약관 본문 링크 — 관리자 옵션(약관 요약의 `showLinks`)이 켜졌을 때만, 불러오기 전·실패 시 숨긴다. 라우터 밖 링크라 새 탭에서 열린다 -->
            <footer v-if="showTermsLinks" class="mt-5 flex justify-center gap-4 text-xs text-muted-color">
                <a href="/terms" target="_blank" rel="noopener" class="hover:underline max-md:-my-2 max-md:py-2">{{ t('common.terms') }}</a>
                <a href="/privacy" target="_blank" rel="noopener" class="font-semibold hover:underline max-md:-my-2 max-md:py-2">{{ t('common.privacy') }}</a>
            </footer>
        </div>

        <!-- 소셜 가입 마무리가 로그인 화면으로 돌려보낼 때의 안내(서버 문구) 등 — 셸이 없는 화면도 토스트를 보인다 -->
        <AppToaster />
    </div>
</template>

<script setup lang="ts">
const { t } = useI18n();
const { siteLabel } = useSiteName();
const { tagline } = useSiteTagline();
const { showLinks: showTermsLinks, ensureLoaded: ensureTermsSummary } = useTermsSummary();
void ensureTermsSummary();
</script>
