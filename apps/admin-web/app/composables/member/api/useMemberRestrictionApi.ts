import type { MemberRestriction, MemberRestrictionCreateRequest, MemberRestrictionLiftRequest } from '~/composables/member/api/useMemberRestrictionApi.types';

/**
 * 회원 기능 제한 API — 서버 주소 문자열(`/api/admin/members/{memberId}/restrictions…`)이 있는 **유일한 곳**이다. 상태는 없다(`useMemberRestrictions` 가 든다).
 * 계약: `docs/features/commerce-foundation-contract.md` 「0-4」(서버 `MemberRestrictionController`), commerce.md 12-3. 경로의 회원 번호 = `memberNo`.
 *
 *  - `GET /api/admin/members/{memberId}/restrictions` → `MemberRestriction[]`(진행 중이 먼저, 그다음 최근 것부터)  (`member.read`)
 *  - `POST /api/admin/members/{memberId}/restrictions` `{ type, until|null, reason, adminMemo }` → 201 + 만든 한 건  (`sanction.manage`, 감사, 회원에게 알림 — 운영 메모는 보내지 않는다)
 *  - `POST /api/admin/members/{memberId}/restrictions/{id}/lift` `{ reason }` → 해제한 한 건  (`sanction.manage`, 감사)
 *    오류: `400 VALIDATION`(`fields.type|until|reason|adminMemo`) · `403 PERMISSION_DENIED` · `404 MEMBER_NOT_FOUND|RESTRICTION_NOT_FOUND` ·
 *    `409 MEMBER_WITHDRAWN`(탈퇴 회원에게 걸기 — 화면은 단추를 두지 않는다) · `409 RESTRICTION_NOT_ACTIVE`(이미 해제·만료된 것 해제)
 */
const PATH = {
    root: (memberId: string) => `/api/admin/members/${encodeURIComponent(memberId)}/restrictions`,
    lift: (memberId: string, restrictionId: string) => `${PATH.root(memberId)}/${encodeURIComponent(restrictionId)}/lift`,
};

export function useMemberRestrictionApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 이 회원의 기능 제한 목록 */
        list: (memberId: string): Promise<MemberRestriction[]> => client.get<MemberRestriction[]>(PATH.root(memberId)),

        /** 제한 걸기 */
        create: (memberId: string, body: MemberRestrictionCreateRequest): Promise<MemberRestriction> => client.post<MemberRestriction>(PATH.root(memberId), body),

        /** 진행 중인 제한 해제 */
        lift: (memberId: string, restrictionId: string, body: MemberRestrictionLiftRequest): Promise<MemberRestriction> =>
            client.post<MemberRestriction>(PATH.lift(memberId, restrictionId), body),
    };
}
