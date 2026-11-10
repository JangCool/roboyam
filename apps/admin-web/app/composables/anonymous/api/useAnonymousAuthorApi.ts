import type { AnonymousRevealRequest, AnonymousRevealResult } from '~/composables/anonymous/api/useAnonymousAuthorApi.types';

/**
 * 익명 작성자 열람 API — 서버 주소(`/api/admin/anonymous-authors…`)가 있는 **유일한 곳**이다. 상태는 없다. 계약 `docs/features/anonymous-board-contract.md` 정책 10.
 *   - `POST /api/admin/anonymous-authors/reveal { postId?, commentId?, reason, memo }` (`anonymous-author.reveal`) → `AnonymousRevealResult` · 오류는 `AnonymousRevealRequest` 주석
 * 열람마다 서버가 감사 기록(대상·사유·메모)을 남기고 다른 운영자에게 알린다. 쓰기는 CSRF(`useApiClient` 가 붙인다).
 */
const PATH = {
    reveal: '/api/admin/anonymous-authors/reveal',
};

export function useAnonymousAuthorApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 봉인된 작성자를 연다 */
        reveal: (body: AnonymousRevealRequest): Promise<AnonymousRevealResult> => client.post<AnonymousRevealResult>(PATH.reveal, body),
    };
}
