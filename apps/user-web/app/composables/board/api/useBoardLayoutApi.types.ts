/**
 * 공개 게시판 레이아웃(`GET /api/boards/{slug}/layout`, 공개 · `no-store`)이 주는 모양 — 서버 `BoardLayoutResponse`(board-layout-contract).
 * 지금 사이트 테마의 게시판 레이아웃(테마마다 하나, 모든 게시판에 같이)을 그 게시판 기준으로 채워 준다 — 배치·가젯은 홈 가젯 응답(`HomeGadgetsResponse`)과 같은 모양이고 더하는 것은:
 *   · 고정 칸 「게시판 본문」(`type: 'BOARD_BODY'`, `data: null`·`empty: false`) — 화면이 그 자리에 게시판 목록(·글 보기) 화면을 그린다. ID 는 서버가 정한다(종류로 찾는다).
 *   · 적용 화면 `screens` — 끈 화면(게시판 목록 `list` · 글 보기 `post`)은 본문만 그린다.
 *   · 대상 범위 `BOARD`(지금 보는 게시판) 가젯은 서버가 이 게시판 slug 로 골라 `data` 를 채운다.
 * 저장한 적 없는 테마는 본문만(전체 폭) — 지금 공개 게시판 화면과 똑같다.
 */
import type { HomeGadgetsResponse } from '~/composables/home/api/useHomeGadgetApi.types';

/** 적용 화면 — 게시판 목록 · 글 보기 */
export type BoardLayoutScreen = 'LIST' | 'POST';

export interface BoardLayoutScreens {
    list: boolean;
    post: boolean;
}

/** 게시판 레이아웃 응답 */
export interface BoardLayoutResponse extends HomeGadgetsResponse {
    screens: BoardLayoutScreens;
}
