import type { Component } from 'vue';
import type { BoardDetail, BoardView, PostChipPosition } from '~/composables/board/api/useBoardApi.types';
import type { BoardViewMode } from '~/composables/board/useBoardViewMode';
import HotdealPostGrid from '~/components/hotdeal/HotdealPostGrid.vue';
import HotdealPostList from '~/components/hotdeal/HotdealPostList.vue';
import UtilBoard from '~/utils/util-board';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilTheme from '~/utils/util-theme';

/** 그릴 목록 유형 — 게시판이 정하고(카드형은 늘 카드), 보기 전환이 켜져 있으면 읽는 사람이 목록형·갤러리형을 고른다 */
export type BoardListKind = 'LIST' | 'CARD' | 'GALLERY';

/**
 * 게시판 글 목록을 어떤 부품으로 그릴지 — 게시판 목록 화면(`/boards/{slug}`)과 글 상세 아래 목록(`PostDetailBoardList`, post-detail-board-list-contract)이 같이 쓴다.
 * 테마(`postList:<유형>`) · 게시판 `layout` · 보기 전환(`viewToggle` — 고른 모양은 이 브라우저에 게시판 slug 로 기억, `useBoardViewMode`) · 핫딜(`kind DEAL` — 가격 줄 목록) 분기를 한 곳에 둔다.
 * 서버 호출은 없다. 그리기는 고른 부품이 한다(계약 `BoardPostListProps`).
 *
 * ⚠ setup 에서 부른다.
 * @param {MaybeRefOrGetter<BoardView | BoardDetail | null | undefined>} board - 게시판(요약 또는 상세). 없으면 목록형·일반 게시판으로 본다.
 */
export function useBoardListView(board: MaybeRefOrGetter<BoardView | BoardDetail | null | undefined>) {
    const viewMode = useBoardViewMode();
    const { theme } = useTheme();

    const current = computed(() => toValue(board) ?? null);
    /** 핫딜 게시판 — 판정은 `UtilHotdeal.isDealBoard` 한 곳 */
    const isDeal = computed(() => UtilHotdeal.isDealBoard(current.value));
    /** 보기 전환 단추 — 게시판 설정 `viewToggle` 이 켜져 있고 기본 모양이 목록형·갤러리형일 때만 */
    const canToggleView = computed(() => Boolean(current.value?.viewToggle) && current.value?.layout !== 'CARD');
    const activeView = computed<BoardViewMode>(() => {
        const slug = current.value?.slug ?? '';
        const chosen = canToggleView.value && slug ? viewMode.chosen(slug) : null;
        return chosen ?? (current.value?.layout === 'GALLERY' ? 'GALLERY' : 'LIST');
    });
    /** 그릴 목록 유형 — 카드형은 전환 없이 늘 카드, 모르는 값은 목록형. 유형은 게시판이 정하고 테마는 그 유형의 생김새만 바꾼다 */
    const listKind = computed<BoardListKind>(() => {
        if (current.value?.layout === 'CARD') return 'CARD';
        return activeView.value === 'GALLERY' ? 'GALLERY' : 'LIST';
    });
    /** 그릴 목록 부품 — 핫딜은 가격 줄을 붙인 핫딜 목록(테마 슬롯 없이 하나), 아니면 현재 테마의 `postList:<유형>`(없으면 기본 테마 것, `UtilTheme.resolve`) */
    const listComponent = computed<Component>(() => (isDeal.value ? (listKind.value === 'LIST' ? HotdealPostList : HotdealPostGrid) : UtilTheme.resolve(theme.value, 'postList', listKind.value)));
    /** 부품에 더 줄 속성 — 핫딜 카드·갤러리형의 모양 */
    const listExtra = computed<Record<string, unknown>>(() => (isDeal.value && listKind.value !== 'LIST' ? { variant: listKind.value } : {}));
    /** 글 줄의 말머리·상태 칩 위치 — 게시판 정보의 `chipPosition`(서버가 사이트 기본까지 푼 값), 칸이 없으면 제목 위(post-chip-position-contract) */
    const chipPosition = computed<PostChipPosition>(() => UtilBoard.chipPosition(current.value?.chipPosition));

    /** 보기 전환 — 이 게시판에 고른 모양을 기억한다 */
    function chooseView(mode: BoardViewMode): void {
        const slug = current.value?.slug;
        if (slug) viewMode.choose(slug, mode);
    }

    return { isDeal, canToggleView, activeView, listKind, listComponent, listExtra, chipPosition, chooseView };
}
