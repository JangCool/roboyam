<template>
    <!--
        공개 홈 `/` — 가젯 홈(community `docs/home-gadgets.md` 를 옮김). **템플릿 = 사이트 테마**(사용자 결정 2026-10-28): 기본 테마 = 기본형 · 다음식 = 다음식 · 로보얌 = 로보얌형 · 네이버식 = 네이버형.
        `GET /api/home/gadgets`(목업) 가 준 격자 배치(테마마다 운영자가 정한 칸 수·가젯별 위치·크기) + 가젯 목록을 지금 테마의 홈 템플릿(`UtilTheme.resolve(theme, 'home')`, 계약 `HomeTemplate.types.ts` — 겉모양만)이 공통 격자 `HomeGadgetGrid` 로 그리고,
        가젯 하나하나는 테마 공통 부품(`components/home/`, `UtilHomeGadgetRegistry`)이 그린다. 모르는 종류는 건너뛴다.
        홈은 한 번에 받으므로 오류도 하나다(다시 시도). 응답에 보는 사람 몫(차단·메모·보는 사람)이 섞여 캐시하지 않고, 로그인·로그아웃하면 다시 받는다.
        산업·주식(투자 고지) 게시판 글이 보이면 맨 아래에 투자 고지를 붙인다.
    -->
    <div>
        <h1 class="sr-only">{{ siteLabel }}</h1>

        <ErrorState v-if="status === 'error'" :title="t('home.loadFailed')" :description="errorMessage" retry @retry="load" />

        <div v-else :aria-busy="status === 'loading'">
            <HomeGadgetSkeleton v-if="!data" />
            <component :is="template" v-else :layout="data.layout" :gadgets="data.gadgets" />

            <BoardInvestmentNotice v-if="hasInvestment" variant="home" class="mt-4" data-testid="home-investment" />
        </div>
    </div>
</template>

<script setup lang="ts">
import type { HomeGadgetsResponse } from '~/composables/home/api/useHomeGadgetApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilAdminPreview from '~/utils/util-admin-preview';
import UtilHomeGadget from '~/utils/util-home-gadget';
import UtilSiteName from '~/utils/util-site-name';
import UtilTheme from '~/utils/util-theme';

const { t } = useI18n();
const api = useHomeGadgetApi();
const { user } = useAuth();
const { theme } = useTheme();
const { siteName, siteLabel } = useSiteName();
const { tagline } = useSiteTagline();
const { bySlug, ensureLoaded: ensureBoards } = useBoards();
const { ensureLoaded: ensureDisplay } = useBoardDisplay();
void ensureBoards();
void ensureDisplay();

// 홈의 탭 제목은 서버가 첫 HTML 에 넣는 「이름 — 소개」 와 같게 둔다(앱이 뜬 뒤 제목이 바뀌지 않게). 이름을 모를 때(설치 전·못 받음)만 "홈" 이다.
useSeoMeta({ title: () => UtilSiteName.homeTitle(siteName.value, tagline.value) ?? t('shell.tabHome') });

// 이 방문에서 가젯들이 나눠 쓰는 것 — 폭(lg)·지금 시각
provideHomeGadgetShared();

/** 지금 테마의 홈 템플릿(없으면 기본 테마 것) */
const template = computed(() => UtilTheme.resolve(theme.value, 'home'));

const status = ref<'loading' | 'ready' | 'error'>('loading');
const data = ref<HomeGadgetsResponse | null>(null);
const errorMessage = ref('');

/** 투자 고지 — 홈에 투자 고지 게시판(게시판 목록의 `investmentNotice`)의 글이 보이면 */
const hasInvestment = computed(() => (data.value ? UtilHomeGadget.hasInvestmentBoard(data.value, (slug) => bySlug(slug)?.investmentNotice === true) : false));

const preview = useAdminPreview();

let seq = 0;
let abort: AbortController | null = null;

async function load(): Promise<void> {
    const mine = ++seq;
    abort?.abort();
    const controller = new AbortController();
    abort = controller;
    status.value = 'loading';
    try {
        const raw = await api.gadgets(controller.signal);
        // 관리자 미리보기면 관리자의 저장 전 홈 구성을 덮는다(`useAdminPreview`)
        const res = UtilHomeGadget.sanitize(preview.home.value ? UtilAdminPreview.applyHome(raw, preview.home.value) : raw);
        if (mine !== seq) return;
        if (!res) throw new Error('bad home response');
        data.value = res;
        status.value = 'ready';
    } catch (e) {
        if (mine !== seq) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        errorMessage.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}

// 들어올 때 한 번, 그리고 보는 사람이 바뀔 때(로그인·로그아웃) — 다시 받는 동안은 앞 화면을 그대로 둔다
watch(
    () => user.value?.id ?? null,
    () => void load(),
    { immediate: true },
);
// 관리자 미리보기 — 테마·홈 구성을 바꿔 보내면 다시 그린다
watch([theme, () => preview.home.value], () => {
    if (preview.active.value) void load();
});
onBeforeUnmount(() => abort?.abort());
</script>
