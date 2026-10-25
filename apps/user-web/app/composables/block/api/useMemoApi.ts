import type { BlockPageQuery } from '~/composables/block/api/useBlockApi.types';
import type { MemoList, MemoResult } from '~/composables/block/api/useMemoApi.types';

/**
 * 이용자 메모 API — 서버 주소 문자열(`/api/users/{id}/memo`·`/api/me/memos`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 후보: `docs/features/member-social-contract.md`(모양은 community `docs/api-block-scrap.md` 「이용자 메모」). 모두 로그인 필요(401).
 *
 *  - `PUT /api/users/{id}/memo` `{ memo }` → `MemoResult` — **빈 메모(공백뿐 포함)를 보내면 지운다**(`{ memo: null }`).
 *    `400 VALIDATION fields.memo`(20자·한 줄) · `400 MEMO_SELF` · `404 PROFILE_NOT_FOUND` · `409 MEMO_LIMIT_REACHED`(500개 — 처음 남길 때만 센다). 운영진에게도 남길 수 있다.
 *  - `DELETE /api/users/{id}/memo` → 204(멱등)
 *  - `GET /api/me/memos?size&cursor` → `MemoList`(처음 남긴 것이 최근인 순서)
 */
const PATH = {
    userMemo: (id: string) => `/api/users/${encodeURIComponent(id)}/memo`,
    memos: '/api/me/memos',
};

export function useMemoApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 메모 남기기·고치기 — 빈 메모면 지워서 `null` */
        save: (userId: string, memo: string): Promise<MemoResult> => client.put<MemoResult>(PATH.userMemo(userId), { memo }),

        /** 메모 지우기(멱등 204) */
        remove: (userId: string): Promise<void> => client.del<void>(PATH.userMemo(userId)),

        /** 내가 남긴 메모 — 커서 더보기 */
        list: (query: BlockPageQuery = {}, signal?: AbortSignal): Promise<MemoList> =>
            client.get<MemoList>(PATH.memos, { query: { size: query.size, cursor: query.cursor }, signal }),
    };
}
