<template>
    <!--
        게시판 레이아웃 틀(2026-10-30 — `useBoardLayoutApi`, `GET /api/boards/{slug}/layout`) — 게시판 목록(`pages/boards/[slug].vue`)·글 보기(`pages/posts/[id]/index.vue`)가 자기 화면 내용을 기본 슬롯으로 감싼다.
        지금 테마의 게시판 레이아웃(테마마다 하나, 모든 게시판에 같이)이 이 화면(`screen`)에 켜져 있고 본문 말고 다른 가젯이 있거나 본문이 전체 폭이 아니면 `BoardLayoutGrid` 로 그린다 —
        격자의 고정 칸 「게시판 본문」 자리에 슬롯이 그대로 들어간다. 그 밖(본문만 · 끈 화면 · 아직 못 받음 · 받기 실패(404 `BOARD_NOT_FOUND`·403 `BOARD_INACTIVE` 포함) · 게시판 모름)은 **슬롯만 그대로** — 지금 공개 화면과 똑같다.
        게시판이 바뀔 때 · 로그인한 사람이 바뀔 때 다시 받는다(가젯 페이지 `pages/p/[key].vue` 와 같다).
        화면의 동작·쿼리·data-testid 는 슬롯 안에 그대로 있다(이 틀은 감싸기만 한다).
        **관리자 미리보기**(관리자 테마 화면 「레이아웃 › 게시판」의 iframe, `useAdminPreview`): 관리자가 `home` 자리로 보낸 저장 전 게시판 레이아웃(본문 칸 포함)·적용 화면(`board`)을 덮어 그린다.
          새로 넣은 가젯은 홈 가젯 응답의 같은 종류 데이터를 빌린다(`UtilAdminPreview.applyHome` — 「지금 보는 게시판」 범위도 저장 전에는 받은 글 그대로). 편집 중이면 본문만이어도 격자로 그려 손잡이가 얹힌다.
    -->
    <BoardLayoutGrid v-if="resolved && board" :layout="resolved.layout" :gadgets="resolved.gadgets" :board="board"><slot /></BoardLayoutGrid>
    <slot v-else />
</template>

<script setup lang="ts">
import type { BoardLayoutResponse, BoardLayoutScreen, BoardLayoutScreens } from '~/composables/board/api/useBoardLayoutApi.types';
import type { HomeGadget, HomeGadgetsResponse } from '~/composables/home/api/useHomeGadgetApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAdminPreview, { BOARD_BODY } from '~/utils/util-admin-preview';
import UtilHomeGadget from '~/utils/util-home-gadget';

interface Props {
    /** 이 화면 — 게시판 목록 · 글 보기 */
    screen: BoardLayoutScreen;
    /** 지금 보는 게시판 slug — 모르면(글을 아직 못 받음 · 소모임 글) null: 슬롯만 */
    board: string | null;
}
const props = withDefaults(defineProps<Props>(), {});

const api = useBoardLayoutApi();
const preview = useAdminPreview();
const { theme } = useTheme();
const { user } = useAuth();

const raw = ref<{ slug: string; res: BoardLayoutResponse; extra: HomeGadget[] } | null>(null);

function applies(screens: BoardLayoutScreens): boolean {
    return props.screen === 'LIST' ? screens.list !== false : screens.post !== false;
}

/** 그릴 레이아웃 — null 이면 슬롯만(지금 화면 그대로) */
const resolved = computed<HomeGadgetsResponse | null>(() => {
    const r = raw.value;
    if (!r || r.slug !== props.board) return null;
    let data: HomeGadgetsResponse | null;
    if (preview.active.value) {
        // 관리자 미리보기 — 관리자가 보낸 게시판 레이아웃(본문 칸이 든 것)만 덮는다
        const home = preview.home.value;
        const screens = preview.board.value?.screens;
        if (!home || !screens || !home.gadgets.some((g) => g.type === BOARD_BODY) || !applies(screens)) return null;
        data = UtilHomeGadget.sanitize(UtilAdminPreview.applyHome(r.res, home, r.extra));
    } else {
        if (!applies(r.res.screens)) return null;
        data = UtilHomeGadget.sanitize(r.res);
    }
    if (!data) return null;
    const body = data.gadgets.find((g) => g.type === BOARD_BODY);
    const bodyItem = body ? data.layout.items.find((it) => it.gadgetId === body.id) : undefined;
    if (!body || !bodyItem) return null;
    // 본문만 전체 폭이면 격자 없이(편집 중에는 손잡이를 얹게 격자로)
    const alone = data.gadgets.every((g) => g.type === BOARD_BODY) && bodyItem.x === 0 && bodyItem.w === data.layout.columns;
    return alone && !preview.editing.value ? null : data;
});

let seq = 0;
let abort: AbortController | null = null;

async function load(): Promise<void> {
    const slug = props.board;
    const mine = ++seq;
    abort?.abort();
    if (!slug) return;
    const controller = new AbortController();
    abort = controller;
    try {
        const { res, extra } = await api.layout(slug, { signal: controller.signal, preview: preview.active.value, theme: preview.active.value ? theme.value : undefined });
        if (mine !== seq) return;
        raw.value = { slug, res, extra };
    } catch (e) {
        if (mine !== seq) return;
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        // 레이아웃을 못 받으면 본문만(화면 오류는 본문이 그린다)
        raw.value = null;
    }
}

// 게시판이 바뀔 때 · 보는 사람이 바뀔 때(가젯 글 줄에 보는 사람 몫) — 다시 받는 동안은 앞 레이아웃을 그대로 둔다
watch([() => props.board, () => user.value?.id ?? null], () => void load(), { immediate: true });
// 관리자 미리보기 — 테마를 바꿔 보내면 다시 받는다
watch(theme, () => {
    if (preview.active.value) void load();
});
onBeforeUnmount(() => abort?.abort());
</script>
