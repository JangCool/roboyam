import type { OperatorMemberAccountView, OperatorMemberCreateRequest, OperatorMemberCreateResult, OperatorMemberLinkRequest } from '~/composables/operator/api/useOperatorMemberAccountApi.types';

/**
 * 운영자 회원 계정 API — 서버 주소 문자열(`/api/admin/operators/{operatorId}/member-account`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 운영자 하나에 사이트 회원 계정 하나를 1:1 로 잇는다. 계약: `docs/features/operator-member-account-contract.md`(서버 연결됨 2026-11-05, 새 계정 만들기 2026-11-24):
 *   - `GET    …/member-account` → 200 `OperatorMemberAccountView` (`operator.read`. 이어져 있으면 `linked`, 아니면 같은 이메일로 가입한 회원 `candidate`(없으면 null) + `powers`)
 *   - `PUT    …/member-account` `{ userId }` → 200 `OperatorMemberAccountView` (`operator.manage`. 후보 조건을 서버가 다시 판정한다 — 이메일 인증·정상 회원·사업자 아님·다른 운영자에 안 이어짐.
 *     이으면 그 회원의 게시판 매니저·스텝 지정이 모두 끝난다)
 *   - `DELETE …/member-account` → 200 `OperatorMemberAccountView` (`operator.manage`. 바로 일반 회원이 된다)
 *   - `POST   …/member-account` `OperatorMemberCreateRequest` → 201 `OperatorMemberCreateResult` (`operator.manage`. 새 회원 계정을 만들고 바로 잇는다 — 정책 2 「같은 이메일」의 예외.
 *     닉네임 예약어는 보지 않고 형식·중복만 본다. 프로필은 비공개로 시작. 메일 한 통을 대기열에 넣는다(DIRECT → 인증 메일, MAIL → 비밀번호 정하기 링크 72시간). 응답 `email` 은 소문자로 정규화돼 온다)
 *     판정 순서: 400 `VALIDATION`(한꺼번에 `fields.email|nickname|passwordMode|password`) → 404 `OPERATOR_NOT_FOUND` → 409 `OPERATOR_ROOT_PROTECTED` → 409 `OPERATOR_MEMBER_ALREADY_LINKED` →
 *     409 `EMAIL_TAKEN` → 409 `NICKNAME_TAKEN` → 409 `REJOIN_BLOCKED`(탈퇴 뒤 재가입 제한 — 문구에 다시 가입할 수 있는 날, `retryAfterSeconds`·`extras.rejoinAvailableAt`). 권한 없음 403.
 *   최고 관리자(루트)의 연결·해제·새 계정 만들기는 루트 본인만 한다.
 * 오류 코드: `OPERATOR_NOT_FOUND` 404 · `OPERATOR_ROOT_PROTECTED` 409 · `OPERATOR_MEMBER_ALREADY_LINKED` 409 · `OPERATOR_MEMBER_NOT_CANDIDATE` 409 · `OPERATOR_MEMBER_NOT_ELIGIBLE` 409 ·
 *   `OPERATOR_MEMBER_NOT_LINKED` 409 · `EMAIL_TAKEN` 409 · `NICKNAME_TAKEN` 409 · `REJOIN_BLOCKED` 409 · `VALIDATION` 400 · `PERMISSION_DENIED` 403.
 */
const PATH = {
    account: (operatorId: string) => `/api/admin/operators/${encodeURIComponent(operatorId)}/member-account`,
};

export function useOperatorMemberAccountApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 이 운영자의 회원 계정 연결 상태 · 후보 · 힘 목록 */
        get: (operatorId: string): Promise<OperatorMemberAccountView> => client.get<OperatorMemberAccountView>(PATH.account(operatorId)),

        /** 후보 회원을 잇는다 — 응답은 이은 뒤의 상태 */
        link: (operatorId: string, body: OperatorMemberLinkRequest): Promise<OperatorMemberAccountView> => client.put<OperatorMemberAccountView>(PATH.account(operatorId), body),

        /** 연결을 끊는다 — 응답은 끊은 뒤의 상태 */
        unlink: (operatorId: string): Promise<OperatorMemberAccountView> => client.del<OperatorMemberAccountView>(PATH.account(operatorId)),

        /** 새 회원 계정을 만들고 이 운영자에 바로 잇는다 — 응답은 만든 회원 + 이은 뒤의 상태 */
        create: (operatorId: string, body: OperatorMemberCreateRequest): Promise<OperatorMemberCreateResult> => client.post<OperatorMemberCreateResult>(PATH.account(operatorId), body),
    };
}
