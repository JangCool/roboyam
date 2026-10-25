import type { WithdrawnContact, WithdrawnContactRevealRequest, WithdrawnContactRevealResult } from '~/composables/member/api/useWithdrawnContactApi.types';

/**
 * 탈퇴 회원 연락처(분리 보관) API — 서버 주소 문자열(`/api/admin/members/{memberId}/withdrawn-contact…`)이 있는 **유일한 곳**이다. 상태는 없다(`useWithdrawnContact` 가 든다).
 * 계약: `docs/features/commerce-foundation-contract.md` 「0-4」(서버 `WithdrawnContactController`), commerce.md 17-2·10-9. 응답은 `no-store`. 경로의 회원 번호 = `memberNo`.
 *
 *  - `GET /api/admin/members/{memberId}/withdrawn-contact` → `{ stored, withdrawnAt, purgeAfter, reveals }`  (`member.read` — 값은 오지 않는다. 탈퇴하지 않은 회원도 `stored:false`)
 *  - `POST /api/admin/members/{memberId}/withdrawn-contact/reveal` `{ reason }` → `{ email, maskAt }`  (`member.withdrawn.contact`, 감사를 남긴 뒤에만 값)
 *    오류: `400 VALIDATION`(`fields.reason`) · `403 PERMISSION_DENIED` · `404 MEMBER_NOT_FOUND` · `404 WITHDRAWN_CONTACT_NOT_FOUND`(탈퇴하지 않았거나 보관한 것이 없음)
 */
const PATH = {
    root: (memberId: string) => `/api/admin/members/${encodeURIComponent(memberId)}/withdrawn-contact`,
    reveal: (memberId: string) => `${PATH.root(memberId)}/reveal`,
};

export function useWithdrawnContactApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 보관 여부·파기 예정일·열람 기록 */
        get: (memberId: string): Promise<WithdrawnContact> => client.get<WithdrawnContact>(PATH.root(memberId)),

        /** 사유를 남기고 이메일을 연다 — 값은 화면 메모리에만 두고 `maskAt` 에 지운다 */
        reveal: (memberId: string, body: WithdrawnContactRevealRequest): Promise<WithdrawnContactRevealResult> =>
            client.post<WithdrawnContactRevealResult>(PATH.reveal(memberId), body),
    };
}
