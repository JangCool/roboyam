<template>
    <!--
        게시판 모음 본문 — 게시판 페이지(`/boards`)와 전체 화면 메뉴(`/menu`, mobile-nav-prefix-style-contract 4절)가 같이 쓴다(제목·앞뒤 덧붙임은 페이지가).
        최근 방문 알약 → (`#before-sections` — 메뉴 화면의 바로 가기 칸) → 메뉴 그룹별 게시판 줄(색 네모 아이콘·이름·말머리 요약).
        줄 오른쪽 `›` 화살표는 `arrows` 일 때만(게시판 페이지 — 메뉴 화면은 그리지 않는다).
    -->
    <!-- 로딩 -->
    <div v-if="state === 'loading'" class="mt-4" role="status" :aria-label="t('feed.hubLoading')">
        <div v-for="n in 8" :key="n" class="flex items-center gap-3 py-3">
            <div class="size-9 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
            <div class="flex-1 space-y-2">
                <div class="h-3.5 w-1/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div class="h-3 w-2/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
            </div>
        </div>
    </div>

    <!-- 오류 — 메뉴를 못 받았다(게시판 묶음·순서의 주인이 메뉴다) -->
    <ErrorState v-else-if="state === 'error'" :title="t('feed.hubError')" :description="t('feed.hubErrorText')" retry @retry="reload" />

    <template v-else>
        <!-- 최근 방문(이 기기에 5개까지) -->
        <section class="mt-3" :aria-labelledby="recentId">
            <h2 :id="recentId" class="font-normal text-muted-color" :class="compact ? 'text-[0.6875rem]' : 'text-xs'">{{ t('feed.recentTitle') }}</h2>
            <ul v-if="recentBoards.length" class="m-0 mt-1.5 flex list-none flex-wrap gap-1.5 p-0" data-testid="hub-recent">
                <li v-for="b in recentBoards" :key="b.id">
                    <NuxtLink
                        :to="`/boards/${b.slug}`"
                        class="inline-block rounded-full border border-surface bg-surface-0 px-3 py-0.5 hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:bg-surface-900 dark:hover:bg-surface-800"
                        :class="compact ? 'text-xs' : 'text-sm'"
                    >
                        {{ localizedName(b.name, b.nameEn) }}
                    </NuxtLink>
                </li>
            </ul>
            <p v-else class="mt-1 text-muted-color" :class="compact ? 'text-[0.6875rem]' : 'text-xs'">{{ t('feed.recentEmpty') }}</p>
        </section>

        <slot name="before-sections" />

        <!-- 메뉴 그룹별 게시판 — 그룹 이름·순서는 메뉴 구성이 정한다. 하위 그룹은 평평하게 펴지 않고 상위 그룹 아래 소제목으로 둔다.
             그룹 제목·게시판 이름의 글자 색·굵게·배지는 다른 메뉴와 같은 `AppMenuItemLabel`(기본이면 제목의 흐린 색 그대로). 왼쪽 글자 칸(●의 표시색)은 그대로다 -->
        <section v-for="section in sections" :key="section.key" class="mt-5" :aria-labelledby="section.title ? `${uid}-${section.key}` : undefined">
            <h2 v-if="section.title" :id="`${uid}-${section.key}`" class="font-normal text-muted-color" :class="compact ? 'text-[0.6875rem]' : 'text-xs'">
                <AppMenuItemLabel v-if="section.group" :node="section.group" />
                <template v-else>{{ localizedName(section.title, section.titleEn) }}</template>
            </h2>
            <template v-for="block in section.blocks" :key="block.key">
                <h3 v-if="block.group" class="mt-3 font-normal text-muted-color" :class="compact ? 'text-[0.6875rem]' : 'text-xs'"><AppMenuItemLabel :node="block.group" /></h3>
                <ul class="m-0 mt-1 list-none divide-y divide-surface-200 p-0 dark:divide-surface-800">
                    <li v-for="row in block.items" :key="row.key">
                        <NuxtLink
                            :to="row.node.path"
                            class="-mx-2 flex items-center gap-3 rounded-lg px-2 py-2.5 hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                            data-testid="hub-board"
                        >
                            <span
                                class="grid size-9 shrink-0 place-items-center rounded-lg font-semibold"
                                :class="[UtilBoard.tagStyle(row.color) ? '' : UtilBoard.tagClass('neutral'), compact ? 'text-[0.6875rem]' : 'text-xs']"
                                :style="UtilBoard.tagStyle(row.color)"
                                aria-hidden="true"
                            >
                                {{ UtilBoard.iconText(localizedName(row.node.label, row.node.nameEn)) }}
                            </span>
                            <span class="min-w-0 flex-1">
                                <span class="block font-medium" :class="compact ? 'text-sm' : ''"><AppMenuItemLabel :node="row.node" /></span>
                                <span class="block truncate text-muted-color" :class="compact ? 'text-[0.6875rem]' : 'text-xs'">{{ summary(row.node.board?.slug) }}</span>
                            </span>
                            <i v-if="arrows" class="pi pi-angle-right text-muted-color" aria-hidden="true" />
                        </NuxtLink>
                    </li>
                </ul>
            </template>
        </section>
        <p v-if="sections.length === 0" class="mt-5 text-sm text-muted-color">{{ t('menu.boardsEmpty') }}</p>
    </template>
</template>

<script setup lang="ts">
import type { BoardView } from '~/composables/board/api/useBoardApi.types';
import UtilBoard from '~/utils/util-board';
import UtilMenu from '~/utils/util-menu';

/**
 * 그룹·순서·이름은 메뉴 구성(`GET /api/menu` — 앱이 열릴 때 받아 둔 공유 상태 `useMenu`)이 정한다(`UtilMenu.boardSections` — 게시판 항목이 있는 그룹만).
 * 줄 아래 작은 글씨(말머리 이름·투자 고지)와 최근 방문은 게시판 목록(공유 상태 `useBoards`)에서 읽는다. 최근 방문은 이 기기에만(`useRecentBoards` — 게시판 목록 화면이 기록한다).
 */
interface Props {
    /** 게시판 줄 오른쪽 `›` 화살표(게시판 페이지) — 메뉴 화면은 끈다 */
    arrows?: boolean;
    /** 글자를 한 단계씩 작게(메뉴 화면 — 사용자 결정 2026-11-12). 게시판 페이지는 지금 크기 */
    compact?: boolean;
}

withDefaults(defineProps<Props>(), { arrows: true, compact: false });

const { t } = useI18n();
const { nodes, state, reload, ensureLoaded } = useMenu();
const { bySlug, ensureLoaded: ensureBoards } = useBoards();
const { localizedName } = useLocalizedName();
const recent = useRecentBoards();
const uid = useId();
const recentId = useId();
void ensureLoaded();
void ensureBoards();

onMounted(() => recent.load());

const sections = computed(() => UtilMenu.boardSections(nodes.value));

/** 최근 방문 — 지금도 있는 게시판만 */
const recentBoards = computed(() => recent.slugs.value.map((s) => bySlug(s)).filter((b): b is BoardView => Boolean(b)));

/** 줄 아래 작은 글씨 — 그 게시판 고유 말머리(공통 말머리는 뺀다). 투자 고지 게시판은 표시가 붙는다. 게시판 목록을 못 받았으면 빈 글자 */
function summary(slug: string | undefined): string {
    const b = bySlug(slug);
    if (!b) return '';
    const own = b.prefixes.filter((p) => !p.common).map((p) => p.name);
    const text = (own.length ? own : b.prefixes.map((p) => p.name)).join('·');
    if (!b.investmentNotice) return text;
    return text ? `${text} · ${t('menu.investmentNotice')}` : t('menu.investmentNotice');
}
</script>
