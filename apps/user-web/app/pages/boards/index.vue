<template>
    <div>
        <h1 class="text-lg font-bold">{{ t('feed.hubTitle') }}</h1>

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

        <!-- 오류 — 메뉴도 게시판 목록도 못 받았다 -->
        <ErrorState v-else-if="state === 'error'" :title="t('feed.hubError')" :description="t('feed.hubErrorText')" retry @retry="reload" />

        <template v-else>
            <!-- 메뉴 설정을 못 받아 기본 구성으로 그렸다 — 메뉴와 같은 안내 한 줄 -->
            <p v-if="state === 'fallback'" class="mt-3 text-xs text-muted-color" role="status" data-testid="hub-fallback-notice">
                {{ t('menu.fallbackNotice') }}
                <button type="button" class="ml-1 underline focus-visible:outline-2 focus-visible:outline-primary" @click="reload">{{ t('common.retry') }}</button>
            </p>

            <!-- 최근 방문(이 기기에 5개까지) -->
            <section class="mt-3" :aria-labelledby="recentId">
                <h2 :id="recentId" class="text-xs font-normal text-muted-color">{{ t('feed.recentTitle') }}</h2>
                <ul v-if="recentBoards.length" class="m-0 mt-1.5 flex list-none flex-wrap gap-1.5 p-0" data-testid="hub-recent">
                    <li v-for="b in recentBoards" :key="b.id">
                        <NuxtLink
                            :to="`/boards/${b.slug}`"
                            class="inline-block rounded-full border border-surface bg-surface-0 px-3 py-0.5 text-sm hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:bg-surface-900 dark:hover:bg-surface-800"
                        >
                            {{ localizedName(b.name, b.nameEn) }}
                        </NuxtLink>
                    </li>
                </ul>
                <p v-else class="mt-1 text-xs text-muted-color">{{ t('feed.recentEmpty') }}</p>
            </section>

            <!-- 메뉴 그룹별 게시판 — 그룹 이름·순서는 메뉴 구성이 정한다. 하위 그룹은 평평하게 펴지 않고 상위 그룹 아래 소제목으로 둔다 -->
            <section v-for="section in sections" :key="section.key" class="mt-5" :aria-labelledby="section.title ? `${uid}-${section.key}` : undefined">
                <h2 v-if="section.title" :id="`${uid}-${section.key}`" class="text-xs font-normal text-muted-color">{{ localizedName(section.title, section.titleEn) }}</h2>
                <template v-for="block in section.blocks" :key="block.key">
                    <h3 v-if="block.group" class="mt-3 text-xs font-normal text-muted-color">{{ localizedName(block.group.label, block.group.nameEn) }}</h3>
                    <ul class="m-0 mt-1 list-none divide-y divide-surface-200 p-0 dark:divide-surface-800">
                        <li v-for="row in block.items" :key="row.key">
                            <NuxtLink
                                :to="row.node.path"
                                class="-mx-2 flex items-center gap-3 rounded-lg px-2 py-2.5 hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                                data-testid="hub-board"
                            >
                                <span
                                    class="grid size-9 shrink-0 place-items-center rounded-lg text-xs font-semibold"
                                    :class="UtilBoard.tagStyle(row.color) ? '' : UtilBoard.tagClass('neutral')"
                                    :style="UtilBoard.tagStyle(row.color)"
                                    aria-hidden="true"
                                >
                                    {{ UtilBoard.iconText(localizedName(row.node.label, row.node.nameEn)) }}
                                </span>
                                <span class="min-w-0 flex-1">
                                    <span class="block font-medium">{{ localizedName(row.node.label, row.node.nameEn) }}</span>
                                    <span class="block truncate text-xs text-muted-color">{{ summary(row.node.board?.slug) }}</span>
                                </span>
                                <i class="pi pi-angle-right text-muted-color" aria-hidden="true" />
                            </NuxtLink>
                        </li>
                    </ul>
                </template>
            </section>
            <p v-if="sections.length === 0" class="mt-5 text-sm text-muted-color">{{ t('menu.boardsEmpty') }}</p>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { BoardView } from '~/composables/board/api/useBoardApi.types';
import UtilBoard from '~/utils/util-board';
import UtilMenu from '~/utils/util-menu';

/**
 * 게시판 모음 — `/boards`(community `pages/boards/index.vue`). 메뉴 BUILTIN `BOARDS_HUB` 가 이곳이다.
 * 그룹·순서·이름은 메뉴 구성(`GET /api/menu` — 실제)이 정한다(`UtilMenu.boardSections` — 게시판 항목이 있는 그룹만). 줄 아래 작은 글씨(말머리 이름·투자 고지)와 최근 방문은 게시판 목록(`GET /api/boards` — 실제)에서 읽는다.
 * 최근 방문은 이 기기에만(`useRecentBoards` — 게시판 목록 화면이 기록한다). 메뉴를 못 받으면 기본 메뉴(게시판 한 묶음)로 그리고 안내 한 줄.
 */
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

const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('feed.hubTitle'), description: () => t('feed.hubSeoDescription', { site: siteLabel.value }) });
</script>
