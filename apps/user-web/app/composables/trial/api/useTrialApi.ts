import type {
    MyTrial,
    PurchaseProof,
    TrialApplyRequest,
    TrialCard,
    TrialListStatus,
    TrialMine,
    TrialObjectionRequest,
    TrialPublicDetail,
    TrialWritable,
} from '~/composables/trial/api/useTrialApi.types';

/**
 * 체험단 API(공개 사이트) — 서버 주소 문자열(`/api/trials…`·`/api/me/trials…`·`/api/boards/{slug}/staff/purchase-proofs…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 `docs/features/trial-group-contract.md` 「구현 결정」 7·9 — 서버 `TrialController`·`BoardPurchaseProofController`.
 *
 *  - `GET    /api/trials?status=RECRUITING|RUNNING|CLOSED` → `TrialCard[]`(비회원도, 모집 중은 뽑는 중 포함)
 *  - `GET    /api/trials/{id}` → `TrialPublicDetail`(비회원이면 `mine` null · 끝난 모집이면 `reviews[]`)
 *  - `POST   /api/trials/{id}/applications` `{ addressId, consents[] }` → `TrialMine` — 본인인증·AI/운영팀·신청 금지·창 전·마감은 서버 오류(문구 그대로)
 *  - `DELETE /api/trials/{id}/applications/mine` → 204(뽑기 전만)
 *  - `GET    /api/me/trials` → `MyTrial[]` · `GET /api/me/trials/writable?board=` → `TrialWritable[]`(글쓰기 「뽑힌 체험단 사용기로 쓰기」 후보)
 *  - `POST   /api/me/trials/participants/{id}/received` → 204 · `POST /api/me/trials/damage-reports/{id}/objection` `{ text }` → 204
 *  - `GET    /api/boards/{slug}/staff/purchase-proofs?status=PENDING|ALL` → `PurchaseProof[]`(그 게시판 매니저만 — 아니면 404·403)
 *    · 사진 `GET …/{id}/photo`(JPEG · 열람 기록 · 파기되면 410) · `POST …/{id}/approve`(본문 없음) · `POST …/{id}/reject` `{ reason }` → 204
 */
const PATH = {
    list: '/api/trials',
    one: (id: string) => `/api/trials/${encodeURIComponent(id)}`,
    apply: (id: string) => `/api/trials/${encodeURIComponent(id)}/applications`,
    cancel: (id: string) => `/api/trials/${encodeURIComponent(id)}/applications/mine`,
    mine: '/api/me/trials',
    writable: '/api/me/trials/writable',
    received: (participantId: string) => `/api/me/trials/participants/${encodeURIComponent(participantId)}/received`,
    objection: (reportId: string) => `/api/me/trials/damage-reports/${encodeURIComponent(reportId)}/objection`,
    staffProofs: (slug: string) => `/api/boards/${encodeURIComponent(slug)}/staff/purchase-proofs`,
    staffProof: (slug: string, id: string, action: 'photo' | 'approve' | 'reject') => `/api/boards/${encodeURIComponent(slug)}/staff/purchase-proofs/${encodeURIComponent(id)}/${action}`,
};

export function useTrialApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 체험단 페이지 탭 목록 */
        list: (status: TrialListStatus, signal?: AbortSignal): Promise<TrialCard[]> => {
            return client.get<TrialCard[]>(PATH.list, { query: { status }, signal });
        },

        /** 모집 상세 */
        detail: (id: string, signal?: AbortSignal): Promise<TrialPublicDetail> => {
            return client.get<TrialPublicDetail>(PATH.one(id), { signal });
        },

        /** 신청 — 배송지 하나 + 동의 → 바뀐 내 신청 상태 */
        apply: (id: string, body: TrialApplyRequest): Promise<TrialMine> => {
            return client.post<TrialMine>(PATH.apply(id), body);
        },

        /** 내 신청 취소(뽑기 전) */
        cancel: (id: string): Promise<void> => {
            return client.del<void>(PATH.cancel(id));
        },

        /** 내 체험단 */
        mine: (signal?: AbortSignal): Promise<MyTrial[]> => {
            return client.get<MyTrial[]>(PATH.mine, { signal });
        },

        /** 이 게시판에 사용기로 연결할 수 있는 내 뽑힌 체험단 */
        writable: (board: string, signal?: AbortSignal): Promise<TrialWritable[]> => {
            return client.get<TrialWritable[]>(PATH.writable, { query: { board }, signal });
        },

        /** 「받음」 */
        received: (participantId: string): Promise<void> => {
            return client.post<void>(PATH.received(participantId));
        },

        /** 파손 신고 이의 제기(신고 뒤 7일 안) */
        objection: (reportId: string, body: TrialObjectionRequest): Promise<void> => {
            return client.post<void>(PATH.objection(reportId), body);
        },

        /** 게시판 스텝 「구매 인증」 목록 */
        staffProofs: (slug: string, status: 'PENDING' | 'ALL', signal?: AbortSignal): Promise<PurchaseProof[]> => {
            return client.get<PurchaseProof[]>(PATH.staffProofs(slug), { query: { status }, signal });
        },

        /** 구매 인증 사진 주소(비공개 — `<img>` 가 로그인 쿠키로 받는다, 열람 기록) */
        staffProofPhotoUrl: (slug: string, id: string): string => {
            return PATH.staffProof(slug, id, 'photo');
        },

        /** 게시판 스텝 구매 인증 승인 */
        staffApprove: (slug: string, id: string): Promise<void> => {
            return client.post<void>(PATH.staffProof(slug, id, 'approve'));
        },

        /** 게시판 스텝 구매 인증 거절 — 사유 필수 */
        staffReject: (slug: string, id: string, reason: string): Promise<void> => {
            return client.post<void>(PATH.staffProof(slug, id, 'reject'), { reason });
        },
    };
}
