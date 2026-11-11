<template>
    <!-- 하단 탭(모바일) — 홈·인기글·최신글·커뮤니티·소모임·메뉴 6개 고정(mobile-nav-prefix-style-contract 3절 — 알림은 상단 바의 알림 종, 내 정보는 상단 바 오른쪽 끝 아이콘. 하단 탭에 있는 항목은 모바일 상단 메뉴 줄에서 뺀다 — 목록의 원천은 `useBottomTabs`).
         소모임 탭은 `/groups` 로 시작하는 모든 주소(목록·내 소모임·상세·관리)에서 밝다. 커뮤니티 탭은 메뉴의 「커뮤니티」 묶음(그 폴더의 「누르면 가는 곳」 = 가젯 페이지 `/p/community`)으로 가고, 그 페이지와 묶음 안 게시판에서 밝다.
         메뉴 탭은 전체 화면 메뉴(`/menu`)로 간다.
         6칸이라 글자는 조금 작게(11px), 길면 말줄임 — 360px 폭에서 칸마다 60px -->
    <nav :aria-label="t('shell.bottomTabs')" class="sticky bottom-0 z-30 border-t border-surface bg-surface-0 pb-[env(safe-area-inset-bottom)] dark:bg-surface-900">
        <ul class="m-0 flex list-none justify-around p-0">
            <li v-for="tab in tabs" :key="tab.key" class="min-w-0 flex-1">
                <NuxtLink
                    :to="tab.to"
                    class="flex flex-col items-center gap-0.5 px-0.5 py-2 text-[0.6875rem] focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary"
                    :class="tab.active ? 'font-semibold text-color' : 'text-muted-color'"
                    :aria-current="tab.active ? 'page' : undefined"
                    :data-testid="`tab-${tab.key}`"
                >
                    <!-- 인기글 불꽃(Tabler — 가젯 제목 아이콘과 같은 세트). 크기는 PrimeIcons 글자(`text-lg`)와 같은 1.125rem -->
                    <TablerIcon v-if="tab.icon === 'flame'" name="flame" class="block size-[1.125rem]" />
                    <i v-else :class="[tab.icon, 'text-lg']" aria-hidden="true" />
                    <span class="w-full truncate text-center">{{ tab.label }}</span>
                </NuxtLink>
            </li>
        </ul>
    </nav>
</template>


<script setup lang="ts">
const { t } = useI18n();
/** 탭 목록·밝기는 `useBottomTabs` 하나가 정한다 — 모바일 상단 메뉴 줄도 같은 목록을 보고 겹치는 항목을 뺀다 */
const { tabs } = useBottomTabs();
</script>
