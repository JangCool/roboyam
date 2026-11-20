import type { JjalCollectRequest, JjalLookupResponse, JjalOrderRequest, JjalSaveResult, JjalUploadRequest, MyJjalListResponse } from '~/composables/jjal/api/useJjalApi.types';

/**
 * 내 짤 API — 서버 주소(`/api/me/jjals…`·`/api/jjals`)가 있는 유일한 곳. 상태는 없다. 계약 `docs/features/my-jjal-contract.md` 「화면 확정 결과」·「서버 구현 결과」.
 * **서버 연결됨**(server/api `MyJjalController`·`JjalController`). 회원 API 는 로그인 필요(비회원 401), 쓰기는 CSRF(`useApiClient` 가 붙인다). 응답은 `no-store`.
 *   - `GET    /api/me/jjals` → 목록(회원이 정한 순서대로)·개수·상한·처음 안내 확인 여부. 내려간 짤은 `url: null`
 *   - `POST   /api/me/jjals` (multipart `file`) → `201` 올리기(브라우저가 긴 변 300px 로 줄인 GIF·PNG·JPEG).
 *     자격 `403 ACCOUNT_RESTRICTED`·`EMAIL_NOT_VERIFIED`(짤 문구) → 2MB 초과 `413 JJAL_TOO_LARGE`(multipart 한도 10MB 초과는 `413 FILE_TOO_LARGE`) → 형식·크기·프레임 `400 fields.file` →
 *     상한 `409 JJAL_LIMIT_REACHED` → 속도 `429 JJAL_RATE_LIMITED`(1분)·`JJAL_DAILY_LIMIT`(24시간, 둘 다 `retryAfterSeconds`) → 내린 짤과 같은 파일 `409 JJAL_BLOCKED` · 이미 있음 `409 JJAL_ALREADY_IN_BOX`
 *   - `POST   /api/me/jjals/collect { jjalId, postId, commentId? }` → `201` 담기. 이미 있음 `409 JJAL_ALREADY_IN_BOX` · 꽉 참 `409 JJAL_LIMIT_REACHED` · 내려간 짤 `409 JJAL_BLOCKED` ·
 *     안 보이는 글·그 글에 안 쓰인 짤 `404 JJAL_NOT_FOUND` · 19금 게시판·19금 글 `403 JJAL_NOT_COLLECTABLE` · 자격 `403 ACCOUNT_RESTRICTED`·`EMAIL_NOT_VERIFIED`
 *   - `DELETE /api/me/jjals/{id}` → `204` 내 보관함에서 빼기(파일은 남는다) · 없음 `404 JJAL_NOT_FOUND`
 *   - `PUT    /api/me/jjals/order { ids }` → `204` 순서 저장(전체 ID 한 번). 보관함과 맞지 않으면 `409 JJAL_ORDER_CONFLICT`
 *   - `POST   /api/me/jjals/intro-ack` → `204` 처음 안내 확인(계정에 기록)
 *   - `GET    /api/jjals?ids=a,b&postId=` → 글·댓글 속 짤의 표시 정보(비회원도). 한 번에 100개까지 — 넘으면 나눠 묻는다. `postId` 를 주면 그 글 맥락의 `collectable`
 * 글·댓글 저장 때의 짤 오류(`422 JJAL_NOT_ALLOWED` · `409 JJAL_NOT_IN_BOX`·`JJAL_BLOCKED` — 최상위 `jjalId`)는 글·댓글 API 가 받는다(`UtilJjalMessages.bodyError`).
 */
const PATH = {
    mine: '/api/me/jjals',
    order: '/api/me/jjals/order',
    collect: '/api/me/jjals/collect',
    introAck: '/api/me/jjals/intro-ack',
    one: (id: string) => `/api/me/jjals/${encodeURIComponent(id)}`,
    lookup: '/api/jjals',
};

/** `GET /api/jjals` 한 번에 물을 수 있는 ID 수(서버 `JjalLookupService.IDS_MAX`) */
const LOOKUP_MAX = 100;

export function useJjalApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 짤 목록 */
        list: (): Promise<MyJjalListResponse> => client.get<MyJjalListResponse>(PATH.mine),

        /** 올리기 — 브라우저가 만든 파일 하나(201) */
        upload: (body: JjalUploadRequest): Promise<JjalSaveResult> => {
            const form = new FormData();
            form.append('file', body.file, body.filename);
            return client.upload<JjalSaveResult>(PATH.mine, form);
        },

        /** 글·댓글의 짤을 내 보관함에 담기(201) — 짤이 보이는 글·댓글을 함께 보낸다 */
        collect: (body: JjalCollectRequest): Promise<JjalSaveResult> =>
            client.post<JjalSaveResult>(PATH.collect, { jjalId: body.jjalId, postId: body.postId, commentId: body.commentId || undefined }),

        /** 내 보관함에서 빼기 */
        remove: (id: string): Promise<void> => client.del<void>(PATH.one(id)),

        /** 순서 저장 — 보관함 전체 ID(내려간 짤 포함)를 새 순서로 */
        saveOrder: (body: JjalOrderRequest): Promise<void> => client.put<void>(PATH.order, { ids: [...body.ids] }),

        /** 처음 안내 확인 */
        acknowledgeIntro: (): Promise<void> => client.post<void>(PATH.introAck),

        /**
         * 글·댓글 속 짤의 표시 정보(모르는 ID 는 빠진다) — 100개가 넘으면 나눠 묻고 합친다.
         * @param ids - 물을 짤 ID 들.
         * @param postId - 짤이 보이는 글(주면 그 글 맥락의 `collectable`). 없으면 맥락 없이.
         */
        lookup: async (ids: readonly string[], postId?: string | null): Promise<JjalLookupResponse> => {
            const chunks: string[][] = [];
            for (let i = 0; i < ids.length; i += LOOKUP_MAX) chunks.push(ids.slice(i, i + LOOKUP_MAX));
            const results = await Promise.all(
                chunks.map((chunk) => client.get<JjalLookupResponse>(PATH.lookup, { query: { ids: chunk.join(','), postId: postId || undefined } })),
            );
            return { items: results.flatMap((r) => r.items) };
        },
    };
}
