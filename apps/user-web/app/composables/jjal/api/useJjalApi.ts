import type { JjalCollectRequest, JjalLookupResponse, JjalOrderRequest, JjalSaveResult, JjalUploadRequest, MyJjalListResponse } from '~/composables/jjal/api/useJjalApi.types';
import { mockAckJjalIntro, mockCollectJjal, mockLookupJjals, mockMyJjals, mockOrderJjals, mockRemoveJjal, mockUploadJjal } from '~/composables/jjal/api/mock/jjal-mock';

/**
 * 내 짤 API — 서버 주소(`/api/me/jjals…`·`/api/jjals`)가 있는 유일한 곳. 상태는 없다. 계약 `docs/features/my-jjal-contract.md` 「API 후보」.
 *   - `GET    /api/me/jjals` → 목록(회원이 정한 순서대로)·개수·상한·처음 안내 확인 여부
 *   - `POST   /api/me/jjals` (multipart `file`) → 올리기(브라우저가 긴 변 300px 로 줄인 GIF·PNG·JPEG). 자격 `403 ACCOUNT_RESTRICTED` · 2MB 초과 `413` · 형식·크기·프레임 `400` · 상한 `409 JJAL_LIMIT_REACHED` · 속도 `429`
 *   - `POST   /api/me/jjals/collect { jjalId }` → 담기. 이미 있음 `409 JJAL_ALREADY_IN_BOX` · 꽉 참 `409 JJAL_LIMIT_REACHED` · 내려간 짤 `409 JJAL_BLOCKED`(오류 코드 이름은 가정)
 *   - `DELETE /api/me/jjals/{id}` → 내 보관함에서 빼기(파일은 남는다)
 *   - `PUT    /api/me/jjals/order { ids }` → 순서 저장(전체 ID 한 번, 204). 보관함과 맞지 않으면 `409 JJAL_ORDER_CONFLICT`(코드 이름 가정)
 *   - `POST   /api/me/jjals/intro-ack` → 처음 안내 확인(계정에 기록)
 *   - `GET    /api/jjals?ids=` → 글·댓글 속 짤의 표시 정보(가정 — 응답에 실을지 따로 받을지는 구현 때 정한다)
 * 모두 목업이다(`// MOCK:` 줄을 지우고 `// REAL:` 줄을 살린다).
 */
const PATH = {
    mine: '/api/me/jjals',
    order: '/api/me/jjals/order',
    collect: '/api/me/jjals/collect',
    introAck: '/api/me/jjals/intro-ack',
    one: (id: string) => `/api/me/jjals/${encodeURIComponent(id)}`,
    lookup: '/api/jjals',
};

export function useJjalApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    // 목업 동안 쓰지 않는 값 — REAL 줄이 살아나면 쓴다
    void client;
    void PATH;

    return {
        /** 내 짤 목록 */
        list: (): Promise<MyJjalListResponse> =>
            // MOCK:
            mockMyJjals(),
        // REAL: client.get<MyJjalListResponse>(PATH.mine),

        /** 올리기 — 브라우저가 만든 파일 하나 */
        upload: (body: JjalUploadRequest): Promise<JjalSaveResult> => {
            // MOCK:
            return mockUploadJjal(body.file);
            // REAL: const form = new FormData();
            // REAL: form.append('file', body.file, body.filename);
            // REAL: return client.upload<JjalSaveResult>(PATH.mine, form);
        },

        /** 글·댓글의 짤을 내 보관함에 담기 */
        collect: (body: JjalCollectRequest): Promise<JjalSaveResult> =>
            // MOCK:
            mockCollectJjal(body.jjalId),
        // REAL: client.post<JjalSaveResult>(PATH.collect, body),

        /** 내 보관함에서 빼기 */
        remove: (id: string): Promise<void> =>
            // MOCK:
            mockRemoveJjal(id),
        // REAL: client.del<void>(PATH.one(id)),

        /** 순서 저장 — 보관함 전체 ID(내려간 짤 포함)를 새 순서로 */
        saveOrder: (body: JjalOrderRequest): Promise<void> =>
            // MOCK:
            mockOrderJjals(body),
        // REAL: client.put<void>(PATH.order, body),

        /** 처음 안내 확인 */
        acknowledgeIntro: (): Promise<void> =>
            // MOCK:
            mockAckJjalIntro(),
        // REAL: client.post<void>(PATH.introAck),

        /** 글·댓글 속 짤의 표시 정보(모르는 ID 는 빠진다) */
        lookup: (ids: readonly string[]): Promise<JjalLookupResponse> =>
            // MOCK:
            mockLookupJjals(ids),
        // REAL: client.get<JjalLookupResponse>(PATH.lookup, { query: { ids: ids.join(',') } }),
    };
}
