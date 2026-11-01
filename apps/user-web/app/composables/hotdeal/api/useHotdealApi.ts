import type { DealEndReason, DealImageUpload, DealLinkCheck, DealPrefill, DealSameRef, PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';

/**
 * 회원 핫딜 API — 서버 주소 문자열(`/api/deals/…`·`/api/posts/{id}/deal/…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 글쓰기·고치기 본문의 `deal`, 글 줄·글 상세의 `deal`, 핫딜 게시판 목록 조건은 글·게시판 API(`usePostApi`·`useBoardApi`)가 나른다.
 * 계약 `docs/features/hotdeal-contract.md` 「API 후보」(서버 `DealController`):
 *  - `GET /api/deals/link-check?url=&board=` → `DealLinkCheck` — 로그인 + 그 게시판 쓰기 자격(게시판 판정 + 핫딜 자격 `403 GRADE_TOO_LOW`·`EMAIL_NOT_VERIFIED`·`ACCOUNT_RESTRICTED`) ·
 *      핫딜 게시판이 아니면 `400 fields.board` · 주소가 틀리면 `400 fields.url` · 회원 분당 10회 `429 DEAL_LINK_CHECK_RATE_LIMIT`(+`retryAfterSeconds`)
 *  - `GET /api/deals/prefill?from={postId}` → `DealPrefill` — 로그인, **끝난** 딜만(진행 중이면 `409 DEAL_NOT_ENDED`). 이미지는 OG 사본일 때만(올린 이미지는 null)
 *  - `POST /api/posts/{id}/deal/end` 작성자 종료 → `PostDeal`
 *  - `POST /api/posts/{id}/deal/reopen` 작성자 되돌리기(D2 — 작성자 종료 뒤 24시간 안 1번) → `PostDeal` · `409 DEAL_REOPEN_EXPIRED`·`DEAL_REOPEN_USED`
 *  - `POST /api/posts/{id}/deal/keep` 「아직 진행 중이에요」 → `PostDeal` · 횟수를 다 썼으면 `409 DEAL_KEEP_LIMIT` · 의심이 아니면 `409 DEAL_NOT_FLAGGED`
 *  - `POST /api/posts/{id}/deal/end-requests` `{ reason }` 품절·가격 변경 알리기 → 새 `PostDeal`
 *      오류: 로그인 `401` · 작성자 `403 DEAL_END_REQUEST_OWN` · 자격(쓰기 등급·이메일 인증·정지 아님) `403 DEAL_END_REQUEST_NOT_ALLOWED` · 이미 보냄 `409 DEAL_END_REQUEST_EXISTS` ·
 *      끝난 딜 `409 DEAL_ENDED` · 하루 한도 `429 DEAL_END_REQUEST_DAILY_LIMIT` · 같은 작성자 기간 한도 `429 DEAL_END_REQUEST_AUTHOR_LIMIT`(`extras` 에 `limit`·`days`)
 *  - `GET /api/posts/{id}/deal/same` → `DealSameRef[]`(공개·진행 중인 같은 딜, 이 글 제외 — 로그인 없이)
 *  - 상품 이미지 올리기 — 글 이미지 API(`POST /api/images`, `useImageApi`)를 그대로 쓴다. 글 저장 때 서버가 `imageId` 가 그 회원이 올린 이미지인지 본다
 *  - `POST /api/posts/{id}/deal/moderate/end` `{ reason }`(필수 1~200, 앞뒤 공백 뗌) 게시판 매니저·스텝 종료 → 새 `PostDeal`(`endedBy MANAGER`, 작성자 알림에 사유)
 *  - `POST /api/posts/{id}/deal/moderate/reopen` `{ reason? }`(0~200) 게시판 매니저·스텝 재개(종료된 딜만) → 새 `PostDeal`
 *      오류: 로그인 `401` · 그 게시판 매니저·스텝 아님·정지 `403 DEAL_MODERATE_FORBIDDEN` · 이미 종료 `409 DEAL_ENDED` · 종료 아님 `409 DEAL_NOT_ENDED` ·
 *      핫딜 글 아님 `404 DEAL_NOT_FOUND` · 사유 `400 fields.reason`(사용자 결정 2026-11-01 — 공용 메모 4절)
 * 종료·되돌리기·keep·종료 요청은 서버 2단계(주소·모양은 계약대로 확정).
 * 사업자 글(2차 — `deal.source BUSINESS`)도 같은 주소로 종료 요청·keep·종료를 받는다(되돌리기 없음 — 종료는 환불 없음·노출도 멈춤).
 *   사업자 글의 작성자 되돌리기는 `403 DEAL_BUSINESS_POST_LOCKED`, 작성자 종료는 신청 건 사업자 종료와 같은 효과(처리 기록 `DEAL_ENDED_BY_BUSINESS`).
 */
const PATH = {
    linkCheck: '/api/deals/link-check',
    prefill: '/api/deals/prefill',
    moderateEnd: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/deal/moderate/end`,
    moderateReopen: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/deal/moderate/reopen`,
    reopen: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/deal/reopen`,
    end: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/deal/end`,
    keep: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/deal/keep`,
    endRequests: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/deal/end-requests`,
    same: (postId: string) => `/api/posts/${encodeURIComponent(postId)}/deal/same`,
};

export function useHotdealApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    const images = useImageApi();

    return {
        /** 링크 판정 + OG + 같은 딜 */
        linkCheck: (url: string, board: string, signal?: AbortSignal): Promise<DealLinkCheck> => client.get<DealLinkCheck>(PATH.linkCheck, { query: { url, board }, signal }),

        /** 끝난 딜에서 새 딜 올리기 — 미리 채울 값 */
        prefill: (fromPostId: string): Promise<DealPrefill> => client.get<DealPrefill>(PATH.prefill, { query: { from: fromPostId } }),

        /** 작성자 되돌리기(D2) — 작성자 종료 → 다시 진행 중 */
        reopen: (postId: string): Promise<PostDeal> => client.post<PostDeal>(PATH.reopen(postId)),

        /** 작성자 종료 */
        end: (postId: string): Promise<PostDeal> => client.post<PostDeal>(PATH.end(postId)),

        /** 「아직 진행 중이에요」(의심 해제) */
        keep: (postId: string): Promise<PostDeal> => client.post<PostDeal>(PATH.keep(postId)),

        /** 게시판 매니저·스텝 종료 — 사유 필수(1~200자) */
        moderateEnd: (postId: string, reason: string): Promise<PostDeal> => client.post<PostDeal>(PATH.moderateEnd(postId), { reason }),

        /** 게시판 매니저·스텝 재개(다시 진행 중으로) — 사유 선택(0~200자, 비면 null 로 보낸다) */
        moderateReopen: (postId: string, reason: string | null): Promise<PostDeal> => client.post<PostDeal>(PATH.moderateReopen(postId), { reason }),

        /** 품절·가격 변경 알리기 */
        endRequest: (postId: string, reason: DealEndReason): Promise<PostDeal> => client.post<PostDeal>(PATH.endRequests(postId), { reason }),

        /** 진행 중인 같은 딜 */
        same: (postId: string, signal?: AbortSignal): Promise<DealSameRef[]> => client.get<DealSameRef[]>(PATH.same(postId), { signal }),

        /** 상품 이미지 한 장 올리기 */
        uploadImage: (file: File): Promise<DealImageUpload> => images.upload(file),
    };
}
