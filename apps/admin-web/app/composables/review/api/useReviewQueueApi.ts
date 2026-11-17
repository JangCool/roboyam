// MOCK: 서버를 붙이기 전까지 `TRIAL_ADMIN_MOCK` 분기로 목업을 돌려준다(칭호 신청 줄은 실제 칭호 서버에서) — 서버가 붙으면 이 import 와 `// MOCK:` 줄을 지운다
import TrialAdminMock, { TRIAL_ADMIN_MOCK } from '~/composables/trial/api/mock/trial-admin-mock';
import type { ReviewQueueList, ReviewQueueStatus, ReviewQueueType } from '~/composables/review/api/useReviewQueueApi.types';

/**
 * 확인할 일 목록 API(관리자 — trial-group-contract 「구현 결정」 7, 서버 admin `AdminReviewQueueController`) — 서버 주소 문자열(`/api/admin/review-queue…`)이 있는 **유일한 곳**이다. 상태는 없다.
 *  - `GET  /api/admin/review-queue?type=&status=PENDING|DONE|ALL` → `{ items, pending }` — 칭호 신청(`title.review`·`title.read`) + 구매 인증·반납 확인·파손 신고·협찬 숨김(`trial.review`).
 *    종류를 비우면 볼 수 있는 종류를 모두 섞어 준다. 볼 수 없는 종류를 고르면 403.
 *  - `POST /api/admin/review-queue/sponsor-hidden/{postId}/confirm|dismiss` `{ note }` → 204(협찬 숨김 확인 — 글을 협찬으로 바꾸고 표시를 붙인다 · 무시)
 * 그 밖의 처리(칭호 승인·거절, 구매 인증, 반납 받음, 파손 판정)는 각 자원의 API(`useTitleApi`·`useTrialApi`)가 한다.
 */
const PATH = {
    queue: '/api/admin/review-queue',
    sponsor: (postId: string, action: 'confirm' | 'dismiss') => `/api/admin/review-queue/sponsor-hidden/${encodeURIComponent(postId)}/${action}`,
};

export function useReviewQueueApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    const titleApi = useTitleApi(); // MOCK: 목업이 칭호 신청 줄을 실제 칭호 서버에서 받는다

    return {
        /** 확인할 일 — 종류(null = 볼 수 있는 전체)·상태 */
        list: (type: ReviewQueueType | null, status: ReviewQueueStatus): Promise<ReviewQueueList> => {
            // MOCK: 칭호 줄은 실제 칭호 서버(처리할 것이면 PENDING, 그 밖은 ALL)
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.reviewQueue(type, status, async () => (type && type !== 'TITLE_APPLICATION' ? [] : (await titleApi.applications(status === 'PENDING' ? 'PENDING' : 'ALL', null)).items));
            return client.get<ReviewQueueList>(PATH.queue, { query: { type: type ?? undefined, status } });
        },

        /** 협찬 숨김 확인(협찬 표시를 붙이고 제재 대상) · 무시 */
        decideSponsor: (postId: string, confirm: boolean, note: string): Promise<void> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.sponsor(postId, confirm); // MOCK:
            return client.post<void>(PATH.sponsor(postId, confirm ? 'confirm' : 'dismiss'), { note });
        },
    };
}
