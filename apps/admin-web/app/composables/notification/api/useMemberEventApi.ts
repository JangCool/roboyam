import { mockMemberEvents, mockUpdateMemberEvent } from '~/composables/notification/api/mock/member-event-mock';
import type { MemberEventUpdateRequest, MemberEventView } from '~/composables/notification/api/useMemberEventApi.types';

/**
 * 회원 이벤트 API — 서버 주소 문자열(`/api/admin/member-events…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 후보 계약(`docs/features/message-template-contract.md` 「개정 3」 — 서버 단계에서 확정). 권한: 읽기 `notification.read` · 바꾸기 `notification.manage`.
 *   - `GET /api/admin/member-events` → 200 `MemberEventView[]`(영역·코드순, 준비 중 포함)
 *   - `PUT /api/admin/member-events/{code}` `{ enabled, inApp, email, memberCanTurnOff }` → 200 바뀐 한 줄
 *       · 404 `MEMBER_EVENT_NOT_FOUND` · 409 `MEMBER_EVENT_NOT_READY`(준비 중) · 400 `VALIDATION` `fields.memberCanTurnOff`(코드가 잠근 이벤트를 끌 수 있게 함). 감사 후보 `MEMBER_EVENT_UPDATE`.
 *   게시판 기본 설정 「알림」(`/api/admin/boards/defaults/notifications`)의 `memberDefaults` 는 서버 단계에서 이 자원으로 옮긴다.
 *
 * ⚠ MOCK: 서버가 없어서 `./mock/member-event-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄(과 목업 import)을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    root: '/api/admin/member-events',
    event: (code: string) => `${PATH.root}/${encodeURIComponent(code)}`,
};

export function useMemberEventApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    const { can } = useAdminPermission();
    void PATH;

    return {
        /** 회원 이벤트 목록(준비 중 포함) */
        list: (): Promise<MemberEventView[]> =>
            // MOCK:
            mockMemberEvents(can),
        // REAL: client.get<MemberEventView[]>(PATH.root),

        /** 이벤트 하나의 사용 여부·채널·회원이 끌 수 있는지를 바꾼다 */
        update: (code: string, body: MemberEventUpdateRequest): Promise<MemberEventView> =>
            // MOCK:
            mockUpdateMemberEvent(can, code, body),
        // REAL: client.put<MemberEventView>(PATH.event(code), body),
    };
}
