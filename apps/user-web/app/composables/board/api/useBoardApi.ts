import { mockBoards } from '~/composables/board/api/mock/board-mock';
import type { BoardView } from '~/composables/board/api/useBoardApi.types';

/**
 * 게시판·글·댓글 API — 서버 주소 문자열(`/api/boards/…`)이 있는 **유일한 곳**이다 (frontend-guide 2장). 상태는 없다.
 * 규약: community `docs/api-board.md`.
 *
 * ⚠ 단순 버전 — 원본(community)은 게시판 하나·글 목록·글 상세·댓글 등 게시판 화면 전체의 메서드를 가진다. 셸(메뉴)이 쓰는 전체 게시판 목록(`boards`)만 남겼다.
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/board-mock.ts` 의 목업을 부른다. 서버가 붙으면 `// MOCK:` 줄을 `// REAL:` 줄로 바꾼다.
 */
const PATH = {
    boards: '/api/boards',
};

export function useBoardApi() {
    // REAL: const client = useApiClient({ refreshCsrf: () => useAuthApi().me() });
    void PATH;

    return {
        /** 메뉴·허브용 전체 게시판 — 정렬 순서대로, 로그인 없이 된다 */
        boards: (signal?: AbortSignal): Promise<BoardView[]> =>
            // MOCK:
            mockBoards(signal),
        // REAL: async (): Promise<BoardView[]> => (await client.get<{ boards: BoardView[] }>(PATH.boards, { signal })).boards,
    };
}
