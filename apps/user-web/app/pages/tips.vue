<template>
    <!--
        팁·강좌·사용기 묶음 `/tips?board=&page=`(매거진형 — 사용자가 시안 B 를 고름, 2026-11-07 화면 확정·서버 연결). 게시판 두 개(팁과강좌·사용기)를 한 화면에 묶는다.
        **레이아웃 편집**(사용자 2026-11-13 — tips-contract 「/tips 레이아웃 편집」): 카드 배치는 시스템 가젯 페이지 `tips-magazine`(`GET /api/pages/tips-magazine`)의 지금 테마 배치다 —
        관리자 `설정 › 테마 › 레이아웃 › 페이지` 에서 홈·가젯 페이지와 같은 편집기(실제 화면 iframe)로 옮기고 크기를 바꾼다. 가젯 페이지 `/p/tips` 는 따로 남아 있다.
          · 제목 카드(가젯이 아니라 이 화면이 그린다) — 페이지 이름·설명(관리자 「페이지 정보」) + 오른쪽 게시판 칩(`?board=` — 네 가젯 모두에 걸린다), 굵은 밑줄.
            보이는 첫 「이번 주 추천」(`TIPS_HEADLINE`)은 밑줄 아래 이 카드 안에 넣는다(제목을 따로 떼지 않는다 — 사용자 2026-11-07). 관리자 미리보기 편집 중에는 격자에 둔다(손잡이로 옮길 수 있게).
          · 그 아래 격자(지금 테마의 홈 템플릿 `GadgetGrid`) — 이번 주 추천 · 최신글(`TIPS_LATEST`, 쪽 번호) · 많이 본 글(`TIPS_POPULAR`) · 태그(`TIPS_TAGS` — ⚠ MOCK) + 운영자가 넣은 다른 가젯.
            처음 배치는 최신글 17칸 + 오른쪽 7칸에 많이 본 글·태그(본문 폭 1200 에서 지금까지와 같은 칸).
        네 가젯의 데이터는 이 화면이 한 번 받아 나눠 준다(`provideTipsShared` — `GET /api/tips`·`GET /api/tips/posts`). 시스템 페이지가 없으면(옛 설치) 화면에 박힌 처음 배치(`UtilTips.fallbackPage`)로 그린다.
        **관리자 미리보기**(`useAdminPreview`): 관리자가 보낸 저장 전 배치(`home`)·머리(`page`)를 덮어 그리고, 편집 중이면 격자에 손잡이가 얹힌다(가젯 페이지 `/p/{key}` 와 같다).
    -->
    <div class="flex flex-col gap-(--gadget-gap)" :aria-busy="status === 'loading'" data-cache-area="pages.layout" data-testid="tips-page">
        <p role="status" aria-live="polite" class="sr-only">{{ summary }}</p>

        <ErrorState v-if="status === 'error'" :title="t('tips.loadFailed')" :description="errorMessage" retry @retry="load" />

        <template v-else-if="!grid">
            <section class="gadget-card" data-headless>
                <PageTitleHead :title="head.name" :description="head.description" />
            </section>
            <GadgetSkeleton />
        </template>

        <component :is="template" v-else :layout="grid.layout" :gadgets="grid.gadgets">
            <template #lead>
                <!-- 제목 카드 — 페이지 제목·설명 + 오른쪽 게시판 칩(굵은 밑줄로 지면 머리처럼), 그 아래 「이번 주 추천」 -->
                <section class="gadget-card flex flex-col" data-headless data-testid="tips-head">
                    <!-- 제목 머리 — 묶음 메뉴 화면 공용(`PageTitleHead` — 커뮤니티·핫 로토픽·소모임과 같은 글자 크기·여백) -->
                    <PageTitleHead :title="head.name" :description="head.description" :divider="Boolean(headGadget)" title-testid="tips-title">
                        <template #actions>
                            <nav :aria-label="t('tips.boardTabs')" class="relative flex min-w-0 shrink-0 gap-1.5 overflow-x-auto py-1 [scrollbar-width:none]" data-testid="tips-board-tabs">
                                <template v-if="tips.boardsLoaded.value">
                                    <NuxtLink
                                        v-for="chip in tips.chips.value"
                                        :key="chip.slug || 'all'"
                                        :to="tips.to({ board: chip.slug, page: 1 })"
                                        :class="chipClass(chip.slug === tips.board.value)"
                                        :aria-current="chip.slug === tips.board.value ? 'page' : undefined"
                                        :data-testid="`tips-tab-${chip.slug || 'all'}`"
                                    >
                                        <span class="relative top-[-0.09em]">{{ chip.label }}</span>
                                    </NuxtLink>
                                </template>
                                <template v-else-if="tips.overviewStatus.value === 'loading'">
                                    <i v-for="n in 3" :key="n" class="h-7 w-14 shrink-0 animate-pulse rounded-full bg-surface-200 dark:bg-surface-800" aria-hidden="true" />
                                </template>
                            </nav>
                        </template>
                    </PageTitleHead>
                    <div v-if="headGadget" class="page-head-gadget mt-[0.9375rem]" data-testid="tips-head-gadget">
                        <GadgetRenderer :gadget="headGadget" />
                    </div>
                </section>
            </template>
        </component>

        <!-- 글쓰기 — 운영자가 「페이지 정보」에서 켰을 때 오른쪽 아래에 떠 있는 단추 -->
        <WriteFloatButton v-if="head.writeButton" :to="writeTarget" data-testid="tips-write" />
    </div>
</template>

<script setup lang="ts">
import type { Gadget, GadgetsResponse } from '~/composables/gadget/api/useGadgetApi.types';
import type { GadgetPageHead } from '~/composables/page/api/useGadgetPageApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAdminPreview from '~/utils/util-admin-preview';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGadget from '~/utils/util-gadget';
import UtilTheme from '~/utils/util-theme';
import UtilTips from '~/utils/util-tips';

const { t } = useI18n();
const api = useGadgetPageApi();
const { user } = useAuth();
const { theme } = useTheme();
const { localizedName } = useLocalizedName();
const { ensureLoaded: ensureDisplay } = useBoardDisplay();
void ensureDisplay();

// 이 방문에서 가젯들이 나눠 쓰는 것 — 폭(lg)·지금 시각 · 팁 묶음 데이터(네 가젯)
const { wide } = provideGadgetShared();
const tips = provideTipsShared();

/** 지금 테마의 홈 템플릿(없으면 기본 테마 것) — 가젯 페이지와 같은 겉모양 */
const template = computed(() => UtilTheme.resolve(theme.value, 'home'));

const status = ref<'loading' | 'ready' | 'error'>('loading');
/** 받은 페이지 — 시스템 페이지가 없으면(404) 머리 없이 처음 배치 */
const raw = ref<{ res: GadgetsResponse; head: GadgetPageHead | null; extra: Gadget[] } | null>(null);
const errorMessage = ref('');

const preview = useAdminPreview();

/** 그릴 머리 — 관리자 미리보기면 관리자의 저장 전 값, 페이지가 없으면 화면 문구 */
const head = computed(() => {
    const p = preview.page.value;
    if (p) return { name: p.name, description: p.description, writeButton: p.writeButton };
    const h = raw.value?.head;
    return h ? { name: localizedName(h.name, h.nameEn), description: h.description, writeButton: h.writeButton } : { name: t('tips.title'), description: t('tips.subtitle'), writeButton: false };
});
const writeTarget = computed(() => {
    const board = raw.value?.head?.writeBoard ?? null;
    return board ? { path: '/write', query: { board } } : { path: '/write' };
});

/** 그릴 배치·가젯 — 관리자 미리보기면 관리자의 저장 전 구성을 덮는다 */
const data = computed(() => {
    const r = raw.value;
    if (!r) return null;
    return UtilGadget.sanitize(preview.home.value ? UtilAdminPreview.applyHome(r.res, preview.home.value, r.extra) : r.res);
});
const shown = useAdGadgets(data, 'PAGE');

/**
 * 제목 카드에 넣을 가젯 — 이 폭에서 보이는 첫 「이번 주 추천」(읽는 순서 y·x), 없으면 ⚙ 「페이지 제목 카드에 넣기」를 켠 첫 가젯.
 * 관리자 미리보기 **편집 중**에는 넣지 않는다(격자 손잡이로 옮기고 크기를 바꿀 수 있게 격자에 그대로 둔다).
 */
const headGadget = computed(() => {
    const s = shown.value;
    if (!s || preview.editing.value) return null;
    const byId = new Map(s.gadgets.map((g) => [g.id, g]));
    const order = [...s.layout.items].sort((a, b) => a.y - b.y || a.x - b.x);
    const visible = order.map((i) => byId.get(i.gadgetId)).filter((g): g is Gadget => Boolean(g) && UtilGadget.isVisible(g!, wide.value));
    return visible.find((g) => g.type === 'TIPS_HEADLINE') ?? visible.find((g) => UtilGadget.pageHeadOn(g.type, g.config)) ?? null;
});
/** 격자에 그릴 배치·가젯 — 제목 카드에 넣은 가젯은 뺀다(자리는 아래 가젯이 당겨 메운다) */
const grid = computed(() => {
    const s = shown.value;
    const h = headGadget.value;
    if (!s || !h) return s;
    return { ...s, layout: { ...s.layout, items: s.layout.items.filter((i) => i.gadgetId !== h.id) }, gadgets: s.gadgets.filter((g) => g.id !== h.id) };
});

const summary = computed(() => {
    const listStatus = tips.listStatus.value;
    if (listStatus === 'loading') return t('tips.loading');
    if (listStatus === 'error') return t('tips.loadFailed');
    return t('tips.summary', { count: tips.list.value?.items.length ?? 0 });
});

function chipClass(active: boolean): string {
    return [
        'inline-flex min-h-7 shrink-0 items-center justify-center rounded-full border px-3 text-xs font-normal leading-none whitespace-nowrap focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary',
        active ? 'border-(--p-text-color) bg-(--p-text-color) font-semibold text-(--p-content-background)' : 'border-hairline-strong text-color hover:border-(--p-text-color)',
    ].join(' ');
}

let seq = 0;
let abort: AbortController | null = null;

async function load(): Promise<void> {
    const mine = ++seq;
    abort?.abort();
    const controller = new AbortController();
    abort = controller;
    status.value = 'loading';
    try {
        const { res, extra } = await api.page(UtilTips.PAGE_KEY, { signal: controller.signal, preview: preview.active.value });
        if (mine !== seq) return;
        const { page, ...rest } = res;
        if (!UtilGadget.sanitize(rest)) throw new Error('bad page response');
        raw.value = { res: rest, head: page, extra };
        status.value = 'ready';
    } catch (e) {
        if (mine !== seq) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        // 시스템 페이지가 없다(옛 설치 구조) — 화면에 박힌 처음 배치로 그린다
        if (e instanceof ApiError && e.status === 404) {
            const fallback = UtilGadget.sanitize(UtilTips.fallbackPage(t));
            if (fallback) {
                raw.value = { res: fallback, head: null, extra: [] };
                status.value = 'ready';
                return;
            }
        }
        errorMessage.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}

// 들어올 때 · 보는 사람이 바뀔 때(로그인·로그아웃) — 다시 받는 동안은 앞 화면을 그대로 둔다. 게시판 칩·쪽은 가젯 데이터만 바뀐다(`provideTipsShared`)
watch(() => user.value?.id ?? null, () => void load(), { immediate: true });
// 관리자 미리보기 — 테마를 바꿔 보내면 다시 받는다(배치·머리는 받은 값 위에 덮어 그린다)
watch(theme, () => {
    if (preview.active.value) void load();
});
onBeforeUnmount(() => abort?.abort());

const { siteLabel } = useSiteName();
useSeoMeta({ title: () => head.value.name, description: () => t('tips.seoDescription', { site: siteLabel.value }) });
</script>
