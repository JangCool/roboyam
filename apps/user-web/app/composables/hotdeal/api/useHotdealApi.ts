import {
    mockDealEnd,
    mockDealEndRequest,
    mockDealImageUpload,
    mockDealKeep,
    mockDealPrefill,
    mockDealReopen,
    mockDealSame,
    mockLinkCheck,
} from '~/composables/hotdeal/api/mock/hotdeal-mock';
import type { DealEndReason, DealImageUpload, DealLinkCheck, DealPrefill, DealSameRef, PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';

/**
 * 회원 핫딜 API — 서버 주소 문자열(`/api/deals/…`·`/api/posts/{id}/deal/…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 글쓰기·고치기 본문의 `deal`, 글 줄·글 상세의 `deal`, 핫딜 게시판 목록 조건은 글·게시판 API(`usePostApi`·`useBoardApi`)가 나른다.
 * 계약 후보 `docs/features/hotdeal-contract.md` 「API 후보」:
 *  - `GET /api/deals/link-check?url=&board=` → `DealLinkCheck`(호스트·최종 호스트·규칙·대형몰·쇼핑몰 이름·OG·같은 딜)
 *  - `GET /api/deals/prefill?from={postId}` → `DealPrefill`(끝난 딜에서 새 딜 — 링크·몰·이미지·제목)
 *  - `POST /api/posts/{id}/deal/end` 작성자 종료 → `PostDeal`
 *  - `POST /api/posts/{id}/deal/reopen` 작성자 되돌리기(D2 — 작성자 종료 뒤 24시간 안 1번) → `PostDeal` · `409 DEAL_REOPEN_EXPIRED`·`DEAL_REOPEN_USED`
 *  - `POST /api/posts/{id}/deal/keep` 「아직 진행 중이에요」 → `PostDeal` · 횟수를 다 썼으면 `409 DEAL_KEEP_LIMIT`
 *  - `POST /api/posts/{id}/deal/end-requests` `{ reason }` 품절·가격 변경 알리기 → `PostDeal`(가정: 새 집계를 돌려준다)
 *      오류: 로그인 `401` · 작성자 `403 DEAL_END_REQUEST_OWN` · 자격(쓰기 등급·이메일 인증·정지 아님) `403 DEAL_END_REQUEST_NOT_ALLOWED` · 이미 보냄 `409 DEAL_END_REQUEST_EXISTS` ·
 *      끝난 딜 `409 DEAL_ENDED` · 하루 한도 `429 DEAL_END_REQUEST_DAILY_LIMIT` · 같은 작성자 기간 한도 `429 DEAL_END_REQUEST_AUTHOR_LIMIT`(가정: `extras` 에 `limit`·`days`)
 *  - `GET /api/posts/{id}/deal/same` → `DealSameRef[]`(진행 중인 같은 딜, 이 글 제외)
 *  - 상품 이미지 올리기 — 가정: 글 이미지 API(`POST /api/images`)를 그대로 쓴다(`useImageApi`)
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/hotdeal-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    linkCheck: '/api/deals/link-check',
    prefill: '/api/deals/prefill',
    reopen: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/deal/reopen`,
    end: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/deal/end`,
    keep: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/deal/keep`,
    endRequests: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/deal/end-requests`,
    same: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/deal/same`,
};

export function useHotdealApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // REAL: const images = useImageApi();
    void PATH;

    return {
        /** 링크 판정 + OG + 같은 딜 */
        linkCheck: (url: string, board: string, signal?: AbortSignal): Promise<DealLinkCheck> =>
            // MOCK:
            (void board, void signal, mockLinkCheck(url)),
        // REAL: client.get<DealLinkCheck>(PATH.linkCheck, { query: { url, board }, signal }),

        /** 끝난 딜에서 새 딜 올리기 — 미리 채울 값 */
        prefill: (fromPostId: string): Promise<DealPrefill> =>
            // MOCK:
            mockDealPrefill(fromPostId),
        // REAL: client.get<DealPrefill>(PATH.prefill, { query: { from: fromPostId } }),

        /** 작성자 되돌리기(D2) — 작성자 종료 → 다시 진행 중 */
        reopen: (postId: string): Promise<PostDeal> =>
            // MOCK:
            mockDealReopen(postId),
        // REAL: client.post<PostDeal>(PATH.reopen(postId)),

        /** 작성자 종료 */
        end: (postId: string): Promise<PostDeal> =>
            // MOCK:
            mockDealEnd(postId),
        // REAL: client.post<PostDeal>(PATH.end(postId)),

        /** 「아직 진행 중이에요」(의심 해제) */
        keep: (postId: string): Promise<PostDeal> =>
            // MOCK:
            mockDealKeep(postId),
        // REAL: client.post<PostDeal>(PATH.keep(postId)),

        /** 품절·가격 변경 알리기 */
        endRequest: (postId: string, reason: DealEndReason): Promise<PostDeal> =>
            // MOCK:
            mockDealEndRequest(postId, reason),
        // REAL: client.post<PostDeal>(PATH.endRequests(postId), { reason }),

        /** 진행 중인 같은 딜 */
        same: (postId: string, signal?: AbortSignal): Promise<DealSameRef[]> =>
            // MOCK:
            (void signal, mockDealSame(postId)),
        // REAL: client.get<DealSameRef[]>(PATH.same(postId), { signal }),

        /** 상품 이미지 한 장 올리기 */
        uploadImage: (file: File): Promise<DealImageUpload> =>
            // MOCK:
            mockDealImageUpload(file),
        // REAL: images.upload(file),
    };
}
