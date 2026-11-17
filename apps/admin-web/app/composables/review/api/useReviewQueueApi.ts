import type { ReviewQueueList, ReviewQueueStatus, ReviewQueueType, SponsorHiddenRequest } from '~/composables/review/api/useReviewQueueApi.types';
import type { SanctionLine } from '~/composables/sanction/api/useSanctionApi.types';

/**
 * 확인할 일 목록 API(관리자 — trial-group-contract 「구현 결정」 7, 서버 admin `AdminReviewQueueController`) — 서버 주소 문자열(`/api/admin/review-queue…`)이 있는 **유일한 곳**이다. 상태는 없다.
 *  - `GET  /api/admin/review-queue?type=&status=PENDING|DONE|ALL` → `{ items, pending }` — 칭호 신청(`title.review`·`title.read`) + 구매 인증·반납 확인·파손 신고·협찬 숨김(`trial.review`).
 *    종류를 비우면 볼 수 있는 종류를 모두 섞어 준다. 볼 수 없는 종류를 고르면 403.
 *  - `POST /api/admin/review-queue/sponsor-hidden/{postId}/confirm` `{ note?, sanction? }` → 200 `data` = 곁들인 제재 줄(없으면 null) — 글을 협찬으로 고정(글쓴이가 못 바꾼다),
 *    그 글의 협찬 숨김 신고 사건도 처리 완료(`SPONSOR_LABEL`)로 닫는다. `sanction` 은 신고 처리와 같은 `{ kind, skipStep, confirmNickname, reason }` — 제재 권한이 없으면 403·전부 롤백(점검 D2).
 *  - `POST …/dismiss` `{ note? }` → 204(사건은 문제 없음으로) · 404 `SPONSOR_REVIEW_NOT_FOUND` · 409 `SPONSOR_REVIEW_CLOSED`
 * 그 밖의 처리(칭호 승인·거절, 구매 인증, 반납 받음, 파손 판정)는 각 자원의 API(`useTitleApi`·`useTrialApi`)가 한다.
 */
const PATH = {
    queue: '/api/admin/review-queue',
    sponsor: (postId: string, action: 'confirm' | 'dismiss') => `/api/admin/review-queue/sponsor-hidden/${encodeURIComponent(postId)}/${action}`,
};

export function useReviewQueueApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 확인할 일 — 종류(null = 볼 수 있는 전체)·상태 */
        list: (type: ReviewQueueType | null, status: ReviewQueueStatus): Promise<ReviewQueueList> => {
            return client.get<ReviewQueueList>(PATH.queue, { query: { type: type ?? undefined, status } });
        },

        /** 협찬 숨김 확인(협찬으로 고정 + 제재 곁들이기) → 곁들인 제재 줄(없으면 null) */
        confirmSponsor: (postId: string, body: SponsorHiddenRequest): Promise<SanctionLine | null> => {
            return client.post<SanctionLine | null>(PATH.sponsor(postId, 'confirm'), body).then((line) => line ?? null);
        },

        /** 협찬 숨김 무시 → 204 */
        dismissSponsor: (postId: string, note: string): Promise<void> => {
            return client.post<void>(PATH.sponsor(postId, 'dismiss'), { note });
        },
    };
}
