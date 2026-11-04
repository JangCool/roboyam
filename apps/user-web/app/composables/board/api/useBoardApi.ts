import { mockBoardViewerExtras } from '~/composables/board/api/mock/board-staff-mock';
import type { BoardDetail, BoardView } from '~/composables/board/api/useBoardApi.types';
import type { DealPostListQuery, PostListQuery, PostListResponse } from '~/composables/board/api/usePostApi.types';
import UtilHotdeal from '~/utils/util-hotdeal';

/**
 * 게시판 API — 서버 주소 문자열(`/api/boards/…`)이 있는 **유일한 곳**이다 (frontend-guide 2장). 상태는 없다.
 * 글 하나·쓰기는 `usePostApi`, 댓글은 `useCommentApi`. 게시판 아래 글 목록(`/api/boards/{slug}/posts`)은 주소의 주인이 게시판이라 여기 둔다.
 * 규약: `docs/features/board-public-contract.md`.
 *
 *  - `GET /api/boards` 메뉴·허브용 켜진 게시판 전체(로그인 없이)
 *  - `GET /api/boards/writable` 지금 사용자가 글을 쓸 수 있는 게시판(글쓰기 화면의 게시판 고르기 — 판정은 서버)
 *  - `GET /api/boards/{slug}` 게시판 + 유효 정책 + 지금 보는 사람의 판정(viewer). 꺼진 게시판 `403 BOARD_INACTIVE`, 없는 게시판 `404 BOARD_NOT_FOUND`.
 *      핫딜 게시판이면 `kind DEAL` + `dealAffiliateMode` + `dealRules`(공개 설정 칸)
 *  - `GET /api/boards/{slug}/posts?prefix&sort&q&page` 글 목록(고정 공지 포함)
 *  - 핫딜 게시판(`kind DEAL`)은 같은 주소에 `?sort=latest|popular|ending&hideEnded=&store=`(응답에 쇼핑몰 칩 `stores`, 줄에 `deal`) — `dealPosts`(`hotdeal-contract.md` 「API 후보」).
 *      `hideEnded` 가 빠지면 서버는 켬으로 본다(화면은 늘 보낸다). 핫딜 게시판이 아니면 `stores` 없음·새 `sort` 값은 무시
 * 공개 표시 기본값(날짜 표시 방식·새 글 기준)은 따로 묻지 않는다 — `GET /api/site/info` 의 `boardDisplay`(`useBoardDisplay`).
 *
 * 서버 연결됨(2026-10-22). 핫딜 칸은 서버 1단계에 연결(2026-11-01).
 * ⚠ MOCK: 게시판 제재(board-sanction-contract)의 새 칸 `viewer.staffTools`·`viewer.writeRestriction` 은 서버가 아직 주지 않아 `board` 응답에 목업으로 덧붙인다(서버가 주면 그 값 그대로).
 */
const PATH = {
    boards: '/api/boards',
    writable: '/api/boards/writable',
    board: (slug: string) => `/api/boards/${encodeURIComponent(slug)}`,
    posts: (slug: string) => `/api/boards/${encodeURIComponent(slug)}/posts`,
};

export function useBoardApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 메뉴·허브용 전체 게시판 — 정렬 순서대로, 로그인 없이 된다 */
        boards: async (signal?: AbortSignal): Promise<BoardView[]> => (await client.get<{ boards: BoardView[] }>(PATH.boards, { signal })).boards,

        /** 지금 사용자가 글을 쓸 수 있는 게시판 — 글쓰기 화면의 게시판 고르기 */
        writable: async (signal?: AbortSignal): Promise<BoardView[]> => (await client.get<{ boards: BoardView[] }>(PATH.writable, { signal })).boards,

        /** 게시판 하나 — 유효 정책과 지금 사용자의 글쓰기·댓글 판정 */
        board: (slug: string, signal?: AbortSignal): Promise<BoardDetail> =>
            client
                .get<BoardDetail>(PATH.board(slug), { signal })
                // MOCK: 서버가 아직 주지 않는 `viewer.staffTools`·`viewer.writeRestriction` 을 채운다(`?mock=board-manager|board-staff|board-restricted`)
                .then(mockBoardViewerExtras),
        // REAL: board: (slug: string, signal?: AbortSignal): Promise<BoardDetail> => client.get<BoardDetail>(PATH.board(slug), { signal }),

        /** 글 목록 — 빈 말머리·검색어는 보내지 않는다 */
        posts: (slug: string, query: PostListQuery, signal?: AbortSignal): Promise<PostListResponse> =>
            client.get<PostListResponse>(PATH.posts(slug), {
                query: { prefix: query.prefix || undefined, sort: query.sort, q: query.q || undefined, page: query.page },
                signal,
            }),

        /** 핫딜 게시판 글 목록 — 정렬(최신·인기·마감 임박)·종료 숨기기. 기본값(최신·숨김 켬)도 보낸다 */
        dealPosts: (slug: string, query: DealPostListQuery, signal?: AbortSignal): Promise<PostListResponse> =>
            client.get<PostListResponse>(PATH.posts(slug), {
                query: { prefix: query.prefix || undefined, sort: UtilHotdeal.sortParam(query.sort), hideEnded: query.hideEnded, store: query.store || undefined, q: query.q || undefined, page: query.page },
                signal,
            }),
    };
}
