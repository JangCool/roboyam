import { mockAddWord, mockBlock, mockBlockedUsers, mockBlockedWords, mockBlockSnapshot, mockRemoveWord, mockUnblock } from '~/composables/block/api/mock/block-mock';
import type { BlockedUserList, BlockedWordList, BlockedWordView, BlockPageQuery, BlockResult } from '~/composables/block/api/useBlockApi.types';

/**
 * 차단 API — 서버 주소 문자열(`/api/users/{id}/block`·`/api/me/blocks/…`)이 있는 **유일한 곳**이다. 상태는 없다(이 기기의 표시는 `useBlockOverrides`).
 * 계약 후보: `docs/features/member-social-contract.md`(모양은 community `docs/api-block-scrap.md`). 모두 로그인 필요(401) — 이메일 인증 전·정지 중에도 쓰는 개인 설정이다.
 *
 *  - `PUT /api/users/{id}/block` → `BlockResult`(멱등) · `400 BLOCK_SELF` · `403 BLOCK_TARGET_PROTECTED`(운영진) · `404 PROFILE_NOT_FOUND` · `409 BLOCK_LIMIT_REACHED`(500명)
 *  - `DELETE /api/users/{id}/block` → 204(멱등)
 *  - `GET /api/me/blocks/users?size&cursor` → `BlockedUserList` · 틀린 커서 `400 VALIDATION fields.cursor`
 *  - `GET /api/me/blocks/words` → `BlockedWordList` · `POST` `{ word }` → `201 BlockedWordView` · `400 VALIDATION fields.word`(2~20자) · `409 BLOCKED_WORD_DUPLICATE` · `409 BLOCKED_WORD_LIMIT`(50개)
 *  - `DELETE /api/me/blocks/words/{id}` → 204(멱등 — 남의 항목도 204)
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/block-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    userBlock: (id: string) => `/api/users/${encodeURIComponent(id)}/block`,
    users: '/api/me/blocks/users',
    words: '/api/me/blocks/words',
    word: (id: string) => `/api/me/blocks/words/${encodeURIComponent(id)}`,
};

export function useBlockApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 차단할 회원의 이름은 명부·실제 게시판 글에서 찾고, 나 자신인지는 로그인 정보로 안다
    const boardApi = useBoardApi();
    const source = { boards: () => boardApi.boards(), posts: boardApi.posts };
    const { user } = useAuth();
    void PATH;

    return {
        /** 회원 차단(멱등) — 상대에게 알리지 않는다 */
        block: (userId: string): Promise<BlockResult> =>
            // MOCK:
            mockBlock(source, user.value?.id ?? null, userId),
        // REAL: client.put<BlockResult>(PATH.userBlock(userId)),

        /** 차단 풀기(멱등 204) */
        unblock: (userId: string): Promise<void> =>
            // MOCK:
            mockUnblock(userId),
        // REAL: client.del<void>(PATH.userBlock(userId)),

        /** 내가 차단한 회원 — 차단한 시각이 최근인 것이 위, 커서 더보기 */
        users: (query: BlockPageQuery = {}, signal?: AbortSignal): Promise<BlockedUserList> =>
            // MOCK:
            (void signal, mockBlockedUsers(query)),
        // REAL: client.get<BlockedUserList>(PATH.users, { query: { size: query.size, cursor: query.cursor }, signal }),

        /** 내 차단 단어 — 최근에 더한 것이 위 */
        words: (signal?: AbortSignal): Promise<BlockedWordList> =>
            // MOCK:
            (void signal, mockBlockedWords()),
        // REAL: client.get<BlockedWordList>(PATH.words, { signal }),

        /** 차단 단어 더하기 — 응답은 더해진 항목 */
        addWord: (word: string): Promise<BlockedWordView> =>
            // MOCK:
            mockAddWord(word),
        // REAL: client.post<BlockedWordView>(PATH.words, { word }),

        /** 차단 단어 지우기(멱등 204) */
        removeWord: (id: string): Promise<void> =>
            // MOCK:
            mockRemoveWord(id),
        // REAL: client.del<void>(PATH.word(id)),

        /**
         * MOCK 전용 — 지금 목업의 차단·단어·메모 한 벌. 서버는 글·댓글·작성자 응답에 `blocked`·`memo` 를 실어 주므로 이 호출이 없다.
         * 목업 단계에서 이 기기의 표시(`useBlockOverrides`)를 목업 상태로 맞추는 데만 쓴다. 서버가 붙으면 이 메서드와 부르는 곳을 함께 지운다.
         */
        // MOCK:
        mockSnapshot: () => mockBlockSnapshot(),
    };
}
