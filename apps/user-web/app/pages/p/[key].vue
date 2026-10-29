<template>
    <!--
        가젯 페이지 `/p/{key}`(사용자 결정 2026-10-29 — 홈처럼 가젯 격자로 꾸미는 페이지. 첫 페이지 「커뮤니티」 `/p/community`, 다음 「주제」).
        `GET /api/pages/{key}`가 준 머리(이름·설명·글쓰기 단추) + 지금 테마의 그 페이지 배치·가젯을 그린다:
          · 위: 고정 머리 `GadgetPageHeader` — 배치와 상관없이 늘 붙는다.
          · 아래: 지금 테마의 홈 템플릿(`UtilTheme.resolve(theme, 'home')` — 겉모양만)이 공통 격자 `HomeGadgetGrid` 로 그린다. 가젯 부품·읽기(`UtilHomeGadget.sanitize`)는 홈과 같다.
        메뉴 폴더의 「누르면 가는 곳」이 이 페이지를 가리킬 수 있다(관리자 메뉴 관리, 연결 유형 `GADGET_PAGE`) — 메뉴의 「지금 있는 곳」은 공개 메뉴 응답의 `path` 로 맞춘다.
        없는 페이지는 「찾을 수 없어요」. 응답에 보는 사람 몫이 섞여 캐시하지 않고, 로그인·로그아웃하면 다시 받는다. 투자 고지 게시판 글이 보이면 맨 아래에 고지를 붙인다.
        **관리자 미리보기**(관리자 테마 화면 「레이아웃 › 페이지」의 iframe, `useAdminPreview`): 관리자가 보낸 저장 전 배치(`home`)·머리(`page`)를 덮어 그리고, 편집 중이면 격자에 손잡이가 얹힌다(홈과 같다).
    -->
    <div>
        <EmptyState v-if="status === 'missing'" :title="t('gadgetPage.notFoundTitle')" :description="t('gadgetPage.notFoundText')" icon="pi pi-compass" data-testid="gadget-page-missing">
            <Button type="button" :label="t('gadgetPage.goHome')" severity="secondary" @click="navigateTo('/')" />
        </EmptyState>

        <ErrorState v-else-if="status === 'error'" :title="t('gadgetPage.loadFailed')" :description="errorMessage" retry @retry="load" />

        <div v-else class="flex flex-col gap-4" :aria-busy="status === 'loading'">
            <GadgetPageHeader v-if="head" :name="head.name" :description="head.description" :write-button="head.writeButton" :write-board="writeBoard" />
            <div v-else class="h-11 w-48 animate-pulse rounded bg-surface-200 dark:bg-surface-800" role="status" :aria-label="t('gadgetPage.loading')" />

            <HomeGadgetSkeleton v-if="!data" />
            <component :is="template" v-else :layout="data.layout" :gadgets="data.gadgets" />

            <BoardInvestmentNotice v-if="hasInvestment" variant="home" data-testid="gadget-page-investment" />
        </div>
    </div>
</template>

<script setup lang="ts">
import type { HomeGadget, HomeGadgetsResponse } from '~/composables/home/api/useHomeGadgetApi.types';
import type { GadgetPageHead } from '~/composables/page/api/useGadgetPageApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAdminPreview from '~/utils/util-admin-preview';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilHomeGadget from '~/utils/util-home-gadget';
import UtilTheme from '~/utils/util-theme';

const { t } = useI18n();
const route = useRoute();
const api = useGadgetPageApi();
const { user } = useAuth();
const { theme } = useTheme();
const { localizedName } = useLocalizedName();
const { bySlug, ensureLoaded: ensureBoards } = useBoards();
const { ensureLoaded: ensureDisplay } = useBoardDisplay();
void ensureBoards();
void ensureDisplay();

// 이 방문에서 가젯들이 나눠 쓰는 것 — 폭(lg)·지금 시각
provideHomeGadgetShared();

const key = computed(() => String(route.params.key ?? ''));
/** 지금 테마의 홈 템플릿(없으면 기본 테마 것) — 가젯 페이지도 홈과 같은 겉모양 */
const template = computed(() => UtilTheme.resolve(theme.value, 'home'));

const status = ref<'loading' | 'ready' | 'error' | 'missing'>('loading');
const raw = ref<{ res: HomeGadgetsResponse; head: GadgetPageHead; extra: HomeGadget[] } | null>(null);
const errorMessage = ref('');

const preview = useAdminPreview();

/** 그릴 머리 — 관리자 미리보기면 관리자의 저장 전 값 */
const head = computed(() => {
    const h = raw.value?.head;
    if (!h) return null;
    const p = preview.page.value;
    return p ? { name: p.name, description: p.description, writeButton: p.writeButton } : { name: localizedName(h.name, h.nameEn), description: h.description, writeButton: h.writeButton };
});
const writeBoard = computed(() => raw.value?.head.writeBoard ?? null);

/** 그릴 배치·가젯 — 관리자 미리보기면 관리자의 저장 전 구성을 덮는다 */
const data = computed(() => {
    const r = raw.value;
    if (!r) return null;
    return UtilHomeGadget.sanitize(preview.home.value ? UtilAdminPreview.applyHome(r.res, preview.home.value, r.extra) : r.res);
});

/** 투자 고지 — 투자 고지 게시판(게시판 목록의 `investmentNotice`)의 글이 보이면 */
const hasInvestment = computed(() => (data.value ? UtilHomeGadget.hasInvestmentBoard(data.value, (slug) => bySlug(slug)?.investmentNotice === true) : false));

useSeoMeta({ title: () => head.value?.name ?? t('gadgetPage.loading') });

let seq = 0;
let abort: AbortController | null = null;

async function load(): Promise<void> {
    const mine = ++seq;
    abort?.abort();
    const controller = new AbortController();
    abort = controller;
    status.value = 'loading';
    try {
        const { res, extra } = await api.page(key.value, { signal: controller.signal, preview: preview.active.value });
        if (mine !== seq) return;
        const { page, ...rest } = res;
        if (!UtilHomeGadget.sanitize(rest)) throw new Error('bad page response');
        raw.value = { res: rest, head: page, extra };
        status.value = 'ready';
    } catch (e) {
        if (mine !== seq) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        if (e instanceof ApiError && e.status === 404) {
            raw.value = null;
            status.value = 'missing';
            return;
        }
        errorMessage.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}

// 들어올 때 · 다른 페이지로 갈 때 · 보는 사람이 바뀔 때(로그인·로그아웃) — 다시 받는 동안은 앞 화면을 그대로 둔다
watch([key, () => user.value?.id ?? null], () => void load(), { immediate: true });
// 관리자 미리보기 — 테마를 바꿔 보내면 다시 받는다(배치·머리는 받은 값 위에 덮어 그린다)
watch(theme, () => {
    if (preview.active.value) void load();
});
onBeforeUnmount(() => abort?.abort());
</script>
