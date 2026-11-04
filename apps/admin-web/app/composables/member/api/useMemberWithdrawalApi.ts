import type {
    ForceWithdrawPreview,
    ForceWithdrawRequest,
    ForceWithdrawResult,
    MemberWithdrawalDetail,
    MemberWithdrawalOfMember,
    MemberWithdrawalPage,
    MemberWithdrawalQuery,
    MemberWithdrawalReasonStats,
    MemberWithdrawalSummary,
    WithdrawalCancelRequest,
} from '~/composables/member/api/useMemberWithdrawalApi.types';

/**
 * 회원 탈퇴 관리(관리자) API — 서버 주소 문자열(`/api/admin/member-withdrawals…`·`/api/admin/members/{id}/withdrawal…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 서버: `MemberWithdrawalAdminController`(server/admin `withdrawal/controller`), 응답 모양 `MemberWithdrawalAdminView` — `useMemberWithdrawalApi.types.ts` 와 같다. 응답은 `no-store`.
 *
 *   - `GET  /api/admin/member-withdrawals?status&memberNo&requestedFrom&requestedTo&completedFrom&completedTo&sort&page&size` → `MemberWithdrawalPage`  (`member.read`)
 *     기간은 UTC 순간 반열림 `[from, to)` — 화면은 끝 날짜의 **다음 날 0시**를 보낸다(`UtilMemberWithdrawal.toQuery`). 끝 < 시작이면 `400 VALIDATION`(`fields.requestedTo|completedTo`).
 *     `sort`: `requestedAt`(기본 desc) · `expectedCompleteAt` · `completedAt` · `canceledAt` · `pendingCount` + `asc|desc`. `size` 1~100.
 *   - `GET  /api/admin/member-withdrawals/summary` → `MemberWithdrawalSummary`(진행 중 · 오늘 완료 · 이번 달 완료 · 10일 넘긴 진행 중 — 서비스 하루 기준)
 *   - `GET  /api/admin/member-withdrawals/reasons?requestedFrom&requestedTo` → `MemberWithdrawalReasonStats`(사유 6종 + 고르지 않음, 신청일 기간 `[from, to)`, 상태 무관)
 *   - `GET  /api/admin/member-withdrawals/{id}` → `MemberWithdrawalDetail`  · 404 `WITHDRAWAL_NOT_FOUND`
 *   - `POST /api/admin/member-withdrawals/{id}/cancel` `WithdrawalCancelRequest` → 바뀐 `MemberWithdrawalDetail`  (`member.withdraw`, 감사 `MEMBER_WITHDRAW_CANCEL`)
 *     운영자 대리 취소 — 진행 중이고 회원이 신청한 탈퇴만. 오류 차례: `400 VALIDATION`(`fields.requestChannel|memo|memberConfirmed`) → `404 WITHDRAWAL_NOT_FOUND` → `409 WITHDRAWAL_NOT_SCHEDULED` → `409 WITHDRAWAL_NOT_MEMBER_REQUESTED`
 *   - `GET  /api/admin/members/{memberId}/withdrawal` → 그 회원의 최근 탈퇴 기록(`MemberWithdrawalDetail`) · 404 `WITHDRAWAL_NOT_FOUND`(신청한 적 없음) · 404 `MEMBER_NOT_FOUND`
 *   - `GET  /api/admin/members/{memberId}/withdrawal/preview` → `ForceWithdrawPreview`  (`member.withdraw`) · 404 `MEMBER_NOT_FOUND` · 409 `WITHDRAWAL_ALREADY_SCHEDULED` · 409 `MEMBER_ALREADY_WITHDRAWN`
 *   - `POST /api/admin/members/{memberId}/withdrawal` `ForceWithdrawRequest` → `ForceWithdrawResult`  (`member.withdraw`, 감사 `MEMBER_WITHDRAW_FORCE`)
 *     오류: `400 VALIDATION`(`fields.reasonKind|memo`) · `403` · `404 MEMBER_NOT_FOUND` · `409 WITHDRAWAL_ALREADY_SCHEDULED` · `409 MEMBER_ALREADY_WITHDRAWN`
 */
const PATH = {
    root: '/api/admin/member-withdrawals',
    summary: () => `${PATH.root}/summary`,
    reasons: () => `${PATH.root}/reasons`,
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    cancel: (id: string) => `${PATH.one(id)}/cancel`,
    member: (memberId: string) => `/api/admin/members/${encodeURIComponent(memberId)}/withdrawal`,
    preview: (memberId: string) => `${PATH.member(memberId)}/preview`,
};

export function useMemberWithdrawalApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 거른 목록 한 쪽 — 빈 값은 보내지 않는다(`toQuery` 가 이미 뺐다) */
        search: (query: MemberWithdrawalQuery): Promise<MemberWithdrawalPage> => client.get<MemberWithdrawalPage>(PATH.root, { query: { ...query } }),

        summary: (): Promise<MemberWithdrawalSummary> => client.get<MemberWithdrawalSummary>(PATH.summary()),

        /** 사유 분포 — 신청일 기간을 따른다 */
        reasons: (query: Pick<MemberWithdrawalQuery, 'requestedFrom' | 'requestedTo'>): Promise<MemberWithdrawalReasonStats> =>
            client.get<MemberWithdrawalReasonStats>(PATH.reasons(), { query: { ...query } }),

        detail: (id: string): Promise<MemberWithdrawalDetail> => client.get<MemberWithdrawalDetail>(PATH.one(id)),

        /** 운영자 대리 취소 — 회원이 고객센터·전화 등으로 요청한 진행 중 탈퇴만. 바뀐 상세를 돌려준다 */
        cancel: (id: string, body: WithdrawalCancelRequest): Promise<MemberWithdrawalDetail> => client.post<MemberWithdrawalDetail>(PATH.cancel(id), body),

        /** 회원 한 명의 최근 탈퇴 기록(회원 상세 서랍의 「탈퇴 신청」·「탈퇴 완료」 블록) */
        forMember: (memberId: string): Promise<MemberWithdrawalOfMember> => client.get<MemberWithdrawalOfMember>(PATH.member(memberId)),

        /** 강제 탈퇴 전 남은 일·돈 미리보기 */
        forcePreview: (memberId: string): Promise<ForceWithdrawPreview> => client.get<ForceWithdrawPreview>(PATH.preview(memberId)),

        /** 강제 탈퇴 — 즉시 완료 또는 진행 중 */
        force: (memberId: string, body: ForceWithdrawRequest): Promise<ForceWithdrawResult> => client.post<ForceWithdrawResult>(PATH.member(memberId), body),
    };
}
