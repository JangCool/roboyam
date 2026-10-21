<template>
    <!-- 로그인 — 가운데 카드. 모바일 우선이고 데스크톱에서는 카드 폭만 제한한다. 토스트(로그아웃 알림)는 이 화면에서도 보인다 -->
    <div class="flex min-h-dvh flex-col bg-surface-50 dark:bg-surface-950">
        <div class="mx-auto flex w-full max-w-[26rem] flex-1 flex-col px-4 pb-6 pt-6 sm:justify-center sm:py-10">
            <header class="mb-5 flex justify-center text-xl">
                <!-- 로고는 상단 바와 같은 부품(사이트 이름). 로그인 화면은 이동할 곳이 없어 링크가 아니다 -->
                <SiteBrand />
            </header>

            <main class="rounded-2xl border border-(--line) bg-surface-0 p-5 dark:bg-surface-900 sm:p-6">
                <slot />
            </main>
        </div>

        <AppToaster />
    </div>
</template>
