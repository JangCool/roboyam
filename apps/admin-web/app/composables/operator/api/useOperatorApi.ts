import { mockOperatorCancelInvitation, mockOperatorChangeStatus, mockOperatorCreate, mockOperatorInvitations, mockOperatorInvite, mockOperatorList, mockOperatorReject, mockOperatorUpdate } from '~/composables/operator/api/mock/operator-mock';
import type { OperatorCreateRequest, OperatorInvitationView, OperatorInviteRequest, OperatorPage, OperatorRejectRequest, OperatorStatusRequest, OperatorTab, OperatorUpdateRequest, OperatorView } from '~/composables/operator/api/useOperatorApi.types';

/**
 * 운영자 API — 서버 주소 문자열(`/api/admin/operators…`)이 있는 **유일한 곳**이다. 상태는 없다.
 *
 * 후보 계약(확정 전): 오류는 모두 `ApiError`(useApiClient)로 던져진다. 필요한 권한은 `util-permission-catalog.ts` 의 코드.
 *   - `GET  /api/admin/operators?tab&keyword` → 200 `OperatorPage` (`operator.read`. 이름·이메일 부분 일치, 탭마다 몇 명인지 `counts` 에 함께)
 *   - `POST /api/admin/operators` `OperatorCreateRequest` → 201 `OperatorView` (`operator.manage` + 역할을 주면 `role.grant`)
 *   - `POST /api/admin/operator-invitations` `OperatorInviteRequest` → 204 (`operator.manage` + 역할을 주면 `role.grant`. 같은 이메일로 유효한 초대가 이미 있으면 409)
 *   - `GET  /api/admin/operator-invitations` → 200 `OperatorInvitationView[]` (`operator.read`. 아직 수락하지 않은 초대 전부 — 기한이 지난 것도 `expired` 로 함께, 최근 초대가 앞)
 *   - `DELETE /api/admin/operator-invitations/{invitationId}` → 204 (`operator.manage`. 초대 링크를 못 쓰게 한다)
 *   - `POST /api/admin/operators/{operatorId}/rejection` `OperatorRejectRequest` → 204 (`operator.manage`. 승인 대기(신청) 건 반려 — 사유는 선택. 이미 승인된 계정이면 409 `OPERATOR_NOT_PENDING`)
 *   - `PUT  /api/admin/operators/{operatorId}` `OperatorUpdateRequest` → 200 `OperatorView` (`operator.manage`, `roleIds` 가 있으면 `role.grant`)
 *   - `PUT  /api/admin/operators/{operatorId}/status` `OperatorStatusRequest` → 200 `OperatorView` (`locked` 는 `operator.lock`, 나머지는 `operator.manage`)
 *   지우는 API 는 없다 — 운영자를 지우면 감사 기록의 주인이 사라진다. 「사용 중지」가 대신한다(hamonica3 와 같다).
 * 오류 코드 후보: `OPERATOR_NOT_FOUND` 404 · `OPERATOR_EMAIL_DUPLICATED` 409 · `OPERATOR_ROOT_PROTECTED` 409(루트 계정의 역할·상태·기간 변경) ·
 *   `OPERATOR_LAST_SUPER` 409(마지막 활성 최고 관리자를 막으려 할 때) · `OPERATOR_NOT_PENDING` 409(승인 대기가 아닌 계정 반려) · `INVITATION_NOT_FOUND` 404 · `INVITATION_DUPLICATED` 409 · `OPERATOR_PERIOD_REVERSED` 400 · `OPERATOR_ROLE_NOT_FOUND` 400 ·
 *   `PERMISSION_ESCALATION` 403(내 권한 밖의 권한을 가진 역할을 주려 함) · `ROLE_GENERAL_SUPER_ONLY` 403 · `PERMISSION_DENIED` 403 · `VALIDATION` 400(`fields`).
 *
 * ⚠ 미정(문서 미확정 6~9): 초대 유효기간(72시간은 가정)과 만료된 초대의 보관·재발송 방식, 신청 때 받는 항목과 이메일 인증, 승인자(어떤 권한), 반려한 신청의 보관 방식·반려 사유를 신청자에게 알릴지, 계정 기간을 쓸지, 로그인 실패 잠금 기준, 비밀번호 재설정 방식.
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/operator-mock.ts` 를 부른다(스위치는 그 파일 머리말). 서버가 붙으면 `// MOCK:` 줄을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    operators: '/api/admin/operators',
    operator: (operatorId: string) => `${PATH.operators}/${encodeURIComponent(operatorId)}`,
    status: (operatorId: string) => `${PATH.operator(operatorId)}/status`,
    rejection: (operatorId: string) => `${PATH.operator(operatorId)}/rejection`,
    invitations: '/api/admin/operator-invitations',
    invitation: (invitationId: string) => `${PATH.invitations}/${encodeURIComponent(invitationId)}`,
};

export function useOperatorApi() {
    // REAL: const client = useApiClient();
    void PATH;

    return {
        /** 탭별 목록 — 빈 키워드는 보내지 않는다 */
        list: (tab: OperatorTab, keyword: string): Promise<OperatorPage> =>
            // MOCK:
            mockOperatorList(tab, keyword),
        // REAL: client.get<OperatorPage>(PATH.operators, { query: { tab, keyword: keyword || undefined } }),

        /** 계정을 바로 만든다 */
        create: (body: OperatorCreateRequest): Promise<OperatorView> =>
            // MOCK:
            mockOperatorCreate(body),
        // REAL: client.post<OperatorView>(PATH.operators, body),

        /** 초대 링크 메일을 보낸다 */
        invite: (body: OperatorInviteRequest): Promise<void> =>
            // MOCK:
            mockOperatorInvite(body),
        // REAL: client.post<void>(PATH.invitations, body),

        /** 아직 수락하지 않은 초대 목록(기한이 지난 것 포함) */
        invitations: (): Promise<OperatorInvitationView[]> =>
            // MOCK:
            mockOperatorInvitations(),
        // REAL: client.get<OperatorInvitationView[]>(PATH.invitations),

        /** 초대를 취소한다 — 링크를 못 쓰게 한다 */
        cancelInvitation: (invitationId: string): Promise<void> =>
            // MOCK:
            mockOperatorCancelInvitation(invitationId),
        // REAL: client.del<void>(PATH.invitation(invitationId)),

        /** 이름·역할·계정 기간을 고친다 */
        update: (operatorId: string, body: OperatorUpdateRequest): Promise<OperatorView> =>
            // MOCK:
            mockOperatorUpdate(operatorId, body),
        // REAL: client.put<OperatorView>(PATH.operator(operatorId), body),

        /** 계정 상태(승인·잠금·만료·사용)를 바꾼다 */
        changeStatus: (operatorId: string, body: OperatorStatusRequest): Promise<OperatorView> =>
            // MOCK:
            mockOperatorChangeStatus(operatorId, body),
        // REAL: client.put<OperatorView>(PATH.status(operatorId), body),

        /** 승인 대기(신청) 건을 반려한다 — 사유는 선택 */
        reject: (operatorId: string, body: OperatorRejectRequest): Promise<void> =>
            // MOCK:
            mockOperatorReject(operatorId, body),
        // REAL: client.post<void>(PATH.rejection(operatorId), body),
    };
}
