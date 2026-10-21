<template>
    <!-- 상단 메뉴(데스크톱 전용 — 보기 설정의 메뉴 위치 「상단」) — 헤더 바로 아래 가로 한 줄: 최상위 항목(홈·공동구매)과 최상위 그룹별 "그룹 이름 + 직속 항목". 넘치면 옆으로 밀고 활성 항목을 가운데로 끌어온다.
         그룹 안의 하위 그룹은 눌렀을 때 드롭다운으로 펼친다(`AppTopMenuDropdown`). 접기 설정은 가로라 무시한다.
         메뉴 구성(그룹·순서·활성 판정·로딩/오류)은 왼쪽·오른쪽 메뉴·햄버거와 같은 `useMenuItems` 를 쓴다. 모바일은 `AppTopTabs`·하단 탭·☰ 가 그대로 맡는다(이 막대는 md 미만에서 숨는다) -->
    <nav :aria-label="t('menu.navLabel')" class="sticky top-12 z-20 border-b border-surface bg-surface-0 max-md:hidden dark:bg-surface-900" data-testid="top-menu">
        <div class="mx-auto flex w-full max-w-[var(--content-max)] items-center px-4">
            <div ref="scroller" class="no-scrollbar relative flex min-w-0 flex-1 items-center overflow-x-auto whitespace-nowrap text-sm">
                <!-- 아직 못 받았다 — 막대 높이(2.5rem)를 그대로 두고 줄 모양 스켈레톤 -->
                <div v-if="state === 'loading'" class="flex h-10 shrink-0 items-center gap-3" role="status" :aria-label="t('menu.loadingLabel')">
                    <span v-for="n in 6" :key="n" class="h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-14' : 'w-10'" />
                </div>

                <!-- 보여 드릴 메뉴가 없다 -->
                <div v-else-if="state === 'empty'" class="flex h-10 shrink-0 items-center gap-2 text-xs text-muted-color" data-testid="menu-empty">
                    <span>{{ t('menu.emptyTitle') }}</span>
                    <Button type="button" :label="t('menu.emptyHome')" size="small" variant="outlined" severity="secondary" @click="goHome" />
                </div>

                <template v-else>
                    <!-- 서버 메뉴를 못 받았다 — 기본 메뉴 앞에 안내 한 줄(게시판 목록도 못 받았으면 오류 문구) + 다시 시도 -->
                    <div
                        v-if="state === 'fallback' || state === 'error'"
                        class="mr-2 flex shrink-0 items-center gap-2 border-r border-surface pr-3 text-xs text-muted-color"
                        :role="state === 'error' ? 'alert' : 'status'"
                        data-testid="menu-fallback-notice"
                    >
                        <span>{{ state === 'error' ? t('menu.errorTitle') : t('menu.fallbackNotice') }}</span>
                        <Button type="button" :label="t('common.retry')" size="small" variant="outlined" severity="secondary" :loading="loading" @click="reload" />
                    </div>

                    <!-- 구역들 — 제목 없는 구역(연속된 최상위 항목)과 최상위 그룹(제목 + 직속 칸). 첫 구역 말고는 왼쪽에 구분선 -->
                    <section
                        v-for="(section, index) in bar"
                        :key="section.key"
                        :aria-labelledby="section.title ? `${uid}-${section.key}` : undefined"
                        class="flex shrink-0 items-center"
                        :class="index > 0 ? 'ml-2 border-l border-surface pl-3' : ''"
                    >
                        <h2 v-if="section.title" :id="`${uid}-${section.key}`" class="m-0 pr-1 text-xs font-normal text-muted-color">{{ localizedName(section.title, section.titleEn) }}</h2>
                        <ul class="m-0 flex list-none items-center p-0">
                            <li v-for="entry in section.entries" :key="entry.key">
                                <AppMenuLink
                                    v-if="entry.kind === 'item'"
                                    :node="entry.row.node"
                                    :data-active="entry.row.active ? 'true' : undefined"
                                    :class="UtilMenu.barItemClass(entry.row.active)"
                                    :aria-current="entry.row.active ? 'page' : undefined"
                                >
                                    <i v-if="entry.row.color" class="inline-block size-2 shrink-0 rounded-full" :style="UtilBoard.dotStyle(entry.row.color)" aria-hidden="true" data-testid="menu-dot-color" />
                                    <span>{{ localizedName(entry.row.node.label, entry.row.node.nameEn) }}</span>
                                    <small v-if="entry.row.notice" class="shrink-0 text-xs font-normal text-muted-color">{{ t('menu.investmentNotice') }}</small>
                                </AppMenuLink>
                                <AppTopMenuDropdown v-else :entry="entry" />
                            </li>
                        </ul>
                    </section>
                </template>
            </div>
        </div>
    </nav>
</template>

<script setup lang="ts">
import UtilBoard from '~/utils/util-board';
import UtilMenu from '~/utils/util-menu';

const { t } = useI18n();
const { bar, state, activeId, loading, reload } = useMenuItems();
const uid = useId();
// 메뉴 이름은 운영자가 만든 동적 이름이다 — `label`(한국어)과 선택 영문명 `nameEn` 을 지금 언어로 고른다
const { localizedName } = useLocalizedName();
const scroller = ref<HTMLElement | null>(null);

/** 메뉴 자리가 비었을 때 — 로고를 누르는 것과 같이 홈으로 간다 */
async function goHome() {
    await navigateTo('/');
}

// 활성 항목이 화면 밖이면 가운데로 끌어온다 — 상단 탭(모바일)과 같은 방식. 막대가 새로 떠도(메뉴 위치를 바꿀 때) 한 번 한다
useCenterActive(scroller, [activeId, state], { onMount: true });
</script>
