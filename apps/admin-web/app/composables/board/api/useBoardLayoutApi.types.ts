/**
 * 관리자 게시판 레이아웃 API(`/api/admin/board-layouts/**`)가 주고받는 모양 — 서버 `BoardLayoutView`·`BoardLayoutBodyView`·`BoardLayoutSaveRequest`(board-layout-contract).
 * 게시판 레이아웃은 **테마마다 하나**이고 모든 게시판에 같이 쓴다(게시판별 덮어쓰기는 범위 밖). 배치·가젯 모양은 홈 배치(`useHomeLayoutApi.types.ts`)와 같고 더하는 것은:
 *   · 고정 칸 「게시판 본문」(`type: 'BOARD_BODY'`) — 꼭 하나, 옮기기·크기만(빼기·두 번째·⚙ 없음). 최소 크기 12칸 기준 폭 6 · 높이 20. 공개 화면은 그 자리에 게시판 목록(·글 보기) 화면을 그린다.
 *     ID 는 서버가 정한다(기본 배치 `seed-1`, 저장하면 Snowflake — 모르는 ID 의 `BOARD_BODY` 는 새 본문으로 받는다). 화면은 종류로 찾는다(`UtilBoardLayout.bodyOf`). `style`·`config` 는 null, `desktop`·`mobile` 은 늘 true.
 *   · 적용 화면 `screens` — 게시판 목록(`list`) · 글 보기(`post`)를 각각 켜고 끈다(기본 둘 다 켬). 끈 화면은 지금처럼 본문만.
 *   · 게시판을 고르는 가젯(글 목록·갤러리·게시판 카드)의 대상 범위 `BOARD`(지금 보는 게시판) — 게시판 레이아웃에서만.
 */
import type { HomeLayoutBody } from '~/composables/home/api/useHomeLayoutApi.types';

/** 적용 화면 — 게시판 목록 `/boards/{slug}` · 글 보기 `/posts/{id}` */
export type BoardLayoutScreen = 'LIST' | 'POST';

/** 적용 화면 켜고 끄기 */
export interface BoardLayoutScreens {
    list: boolean;
    post: boolean;
}

/** 배치 + 가젯 목록 + 적용 화면 */
export interface BoardLayoutBody extends HomeLayoutBody {
    screens: BoardLayoutScreens;
}

/** `GET /api/admin/board-layouts/{theme}` · `PUT` 응답 — 저장한 적 없으면 기본 배치(본문만) + `updatedAt/updatedBy: null` */
export interface BoardLayoutView extends BoardLayoutBody {
    theme: string;
    updatedAt: string | null;
    updatedBy: string | null;
}

/** `PUT /api/admin/board-layouts/{theme}` 본문 — 홈 저장과 같은 가젯 규칙(빠진 가젯은 지우고, 모르는 ID 는 새로 만든다) + 본문 꼭 하나(보낸 `style`·`config` 는 무시) + 적용 화면 */
export type BoardLayoutSaveRequest = BoardLayoutBody;
