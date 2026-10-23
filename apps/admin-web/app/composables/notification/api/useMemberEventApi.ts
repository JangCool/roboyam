import type { MemberEventUpdateRequest, MemberEventView } from '~/composables/notification/api/useMemberEventApi.types';

/**
 * 회원 이벤트 API — 서버 주소 문자열(`/api/admin/member-events…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약(서버 `MemberEventController`, `docs/features/message-template-contract.md` 「서버 구현 결과(회원 이벤트·이메일 확장)」). 권한: 읽기 `notification.read` · 바꾸기 `notification.manage`.
 *   - `GET /api/admin/member-events` → 200 `MemberEventView[]`(카탈로그 차례, 준비 중 포함)
 *   - `PUT /api/admin/member-events/{code}` `{ enabled, inApp, email, memberCanTurnOff }`(네 칸 필수) → 200 바뀐 한 줄. 검사 순서:
 *       404 `MEMBER_EVENT_NOT_FOUND` → 400 `VALIDATION` `fields.<빠진 칸>` → 409 `MEMBER_EVENT_NOT_READY`(준비 중) → 400 `VALIDATION` `fields.memberCanTurnOff`(코드가 잠근 이벤트를 끌 수 있게 함). 감사 `MEMBER_EVENT_UPDATE`.
 *   이메일 칸은 저장만 된다(회원 메일 발송은 서버 다음 단계). 게시판 기본 설정 「알림」의 이벤트별 칸(옛 `memberDefaults`)은 이 자원으로 옮겨졌다(shared V58).
 */
const PATH = {
    root: '/api/admin/member-events',
    event: (code: string) => `${PATH.root}/${encodeURIComponent(code)}`,
};

export function useMemberEventApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 회원 이벤트 목록(준비 중 포함) */
        list: (): Promise<MemberEventView[]> => client.get<MemberEventView[]>(PATH.root),

        /** 이벤트 하나의 사용 여부·채널·회원이 끌 수 있는지를 바꾼다 */
        update: (code: string, body: MemberEventUpdateRequest): Promise<MemberEventView> => client.put<MemberEventView>(PATH.event(code), body),
    };
}
