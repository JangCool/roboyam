import type { AuthUser } from '~/composables/auth/api/useAuthApi.types';
import type { BoardDetail, BoardView } from '~/composables/board/api/useBoardApi.types';
import type { DealPostListQuery, PostListQuery, PostListResponse } from '~/composables/board/api/usePostApi.types';
import { AUTH_USER_STATE_KEY } from '~/composables/common/useApiClient';
import { isMockDealBoard, mockDealBoard, mockDealPosts, mockWithDealBoard, type MockDealViewer } from '~/composables/board/api/mock/board-deal-mock';
import UtilHotdeal from '~/utils/util-hotdeal';

/**
 * 게시판 API — 서버 주소 문자열(`/api/boards/…`)이 있는 **유일한 곳**이다 (frontend-guide 2장). 상태는 없다.
 * 글 하나·쓰기는 `usePostApi`, 댓글은 `useCommentApi`. 게시판 아래 글 목록(`/api/boards/{slug}/posts`)은 주소의 주인이 게시판이라 여기 둔다.
 * 규약: `docs/features/board-public-contract.md`.
 *
 *  - `GET /api/boards` 메뉴·허브용 켜진 게시판 전체(로그인 없이)
 *  - `GET /api/boards/writable` 지금 사용자가 글을 쓸 수 있는 게시판(글쓰기 화면의 게시판 고르기 — 판정은 서버)
 *  - `GET /api/boards/{slug}` 게시판 + 유효 정책 + 지금 보는 사람의 판정(viewer). 꺼진 게시판 `403 BOARD_INACTIVE`, 없는 게시판 `404 BOARD_NOT_FOUND`
 *  - `GET /api/boards/{slug}/posts?prefix&sort&q&page` 글 목록(고정 공지 포함)
 *  - 핫딜 게시판(`kind DEAL`)은 같은 주소에 `?sort=latest|popular|ending&hideEnded=` — `dealPosts`(`hotdeal-contract.md` 「API 후보」)
 * 공개 표시 기본값(날짜 표시 방식·새 글 기준)은 따로 묻지 않는다 — `GET /api/site/info` 의 `boardDisplay`(`useBoardDisplay`).
 *
 * 서버 연결됨(2026-10-22). ⚠ MOCK: 핫딜 1차(서버 없음) — 목업 게시판 `hotdeal` 만 `./mock/board-deal-mock.ts` 로 돌린다(`// MOCK:` 분기). 서버가 `kind`·`deal` 을 주면 분기와 목업 import 를 지운다.
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
    // MOCK: 목업 핫딜 게시판의 「보는 사람」(로그인 여부·이름) — 지금 로그인 상태를 읽기만 한다
    const authUser = useState<AuthUser | null>(AUTH_USER_STATE_KEY, () => null);
    const mockViewer = (): MockDealViewer => ({ loggedIn: Boolean(authUser.value), name: authUser.value?.nickname ?? null });

    return {
        /** 메뉴·허브용 전체 게시판 — 정렬 순서대로, 로그인 없이 된다 */
        boards: async (signal?: AbortSignal): Promise<BoardView[]> =>
            // MOCK: 목업 핫딜 게시판을 끝에 더한다
            mockWithDealBoard((await client.get<{ boards: BoardView[] }>(PATH.boards, { signal })).boards),
        // REAL: (await client.get<{ boards: BoardView[] }>(PATH.boards, { signal })).boards,

        /** 지금 사용자가 글을 쓸 수 있는 게시판 — 글쓰기 화면의 게시판 고르기 */
        writable: async (signal?: AbortSignal): Promise<BoardView[]> =>
            // MOCK: 목업 핫딜 게시판을 쓸 수 있으면 더한다
            mockWithDealBoard((await client.get<{ boards: BoardView[] }>(PATH.writable, { signal })).boards, mockViewer()),
        // REAL: (await client.get<{ boards: BoardView[] }>(PATH.writable, { signal })).boards,

        /** 게시판 하나 — 유효 정책과 지금 사용자의 글쓰기·댓글 판정 */
        board: (slug: string, signal?: AbortSignal): Promise<BoardDetail> =>
            // MOCK:
            isMockDealBoard(slug) ? mockDealBoard(mockViewer()) :
            client.get<BoardDetail>(PATH.board(slug), { signal }),

        /** 글 목록 — 빈 말머리·검색어는 보내지 않는다 */
        posts: (slug: string, query: PostListQuery, signal?: AbortSignal): Promise<PostListResponse> =>
            // MOCK: 핫딜 게시판인지 아직 모를 때(게시판 정보 전) 목업 게시판이면 기본 조건으로
            isMockDealBoard(slug) ? mockDealPosts({ prefix: query.prefix, sort: 'LATEST', hideEnded: true, q: query.q, page: query.page }, mockViewer()) :
            client.get<PostListResponse>(PATH.posts(slug), {
                query: { prefix: query.prefix || undefined, sort: query.sort, q: query.q || undefined, page: query.page },
                signal,
            }),

        /** 핫딜 게시판 글 목록 — 정렬(최신·인기·마감 임박)·종료 숨기기. 기본값(최신·숨김 켬)도 보낸다 */
        dealPosts: (slug: string, query: DealPostListQuery, signal?: AbortSignal): Promise<PostListResponse> =>
            // MOCK:
            isMockDealBoard(slug) ? mockDealPosts(query, mockViewer()) :
            client.get<PostListResponse>(PATH.posts(slug), {
                query: { prefix: query.prefix || undefined, sort: UtilHotdeal.sortParam(query.sort), hideEnded: query.hideEnded, q: query.q || undefined, page: query.page },
                signal,
            }),
    };
}
