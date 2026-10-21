<template>
    <!-- 상단 가로 스크롤 탭(모바일) — 운영자가 메뉴 구성에서 "모바일 탭"으로 정한 항목들(기본: 홈 + 주제 게시판). 순서·이름은 서버가 준 것이고, 나머지는 ☰ 메뉴와 게시판 탭에서 들어간다.
         보여 드릴 탭이 없으면(빈 메뉴·탭으로 고른 항목 없음) 막대 자체를 그리지 않는다 — 로고와 하단 탭으로 이동한다 -->
    <nav v-if="visible" ref="scroller" :aria-label="t('menu.tabsLabel')" class="no-scrollbar relative flex overflow-x-auto whitespace-nowrap border-b border-surface bg-surface-0 px-2 text-sm dark:bg-surface-900">
        <template v-if="state === 'loading'">
            <span v-for="n in 5" :key="n" class="mx-2 my-3 h-3.5 w-12 animate-pulse rounded bg-surface-200 dark:bg-surface-800" aria-hidden="true" />
        </template>
        <template v-else>
            <AppMenuLink
                v-for="item in tabs"
                :key="item.id"
                :node="item"
                :data-active="item.id === activeId ? 'true' : undefined"
                :class="tabClass(item.id === activeId)"
                :aria-current="item.id === activeId ? 'page' : undefined"
            >
                {{ localizedName(item.label, item.nameEn) }}
            </AppMenuLink>
            <!-- 서버 메뉴도 게시판 목록도 못 받았다 -->
            <button v-if="state === 'error'" type="button" class="shrink-0 px-2 py-2.5 text-xs text-muted-color underline focus-visible:outline-2 focus-visible:outline-primary" @click="reload">
                {{ t('menu.tabsRetry') }}
            </button>
        </template>
    </nav>
</template>

<script setup lang="ts">
const { t } = useI18n();
const { tabs, state, activeId, reload } = useMenuItems();
const scroller = ref<HTMLElement | null>(null);
// 메뉴 이름은 운영자가 만든 동적 이름이다 — `label`(한국어)과 선택 영문명 `nameEn` 을 지금 언어로 고른다
const { localizedName } = useLocalizedName();

/** 막대를 그리나 — 받는 중이거나, 올릴 탭이 있거나, 못 받아 다시 시도를 보여야 할 때 */
const visible = computed(() => state.value === 'loading' || state.value === 'error' || tabs.value.length > 0);

function tabClass(active: boolean): string {
    return [
        // 눌리는 면을 가로 최소 32px 로(한 글자 탭 "홈" 은 글자 너비가 13px) — 탭 사이 간격은 가로 여백(px-2)이 맡아 글자 사이 거리는 그대로 16px 이다(D-14)
        'min-w-8 shrink-0 border-b-2 px-2 py-2.5 text-center focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary',
        active ? 'border-primary font-semibold text-color' : 'border-transparent text-muted-color',
    ].join(' ');
}

// 활성 탭이 화면 밖이면 가운데로 끌어온다(줄만 민다 — 상단 메뉴와 같은 방식, `useCenterActive`)
useCenterActive(scroller, [activeId, () => tabs.value.length, state]);
</script>
