import type { LoginHistoryResponse, MyDisplayRequest, MyDisplayResponse, MyNotificationRequest, MyNotificationResponse, ProfileResponse, ProfileUpdateRequest } from '~/composables/profile/api/useProfileApi.types';

/**
 * 내 정보 API — 서버 주소 문자열(`/api/admin/profile…`)이 있는 **유일한 곳**이다. 상태는 없다(화면이 쓰는 상태는 `useProfile` · `useLoginHistory` · `useMyDisplay`).
 *
 * 계약(서버 `ProfileController`, `docs/features/roles-contract.md` 「4단계 구현 결과」): 로그인한 본인의 것만 읽고 쓴다(경로에 ID 없음, 역할이 없어도 200). 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 *   - `GET   /api/admin/profile` → 200 `{ id, email, nickname, role, isSuper, roles: [{ roleId, roleName }], status, createdAt, lastLoginAt|null }` (시각은 UTC ISO)
 *   - `PATCH /api/admin/profile` `{ nickname }` → 200 갱신된 프로필 · 400 VALIDATION(`fields.nickname` — 비어 있음/2~12자/허용 문자) · 409 NICKNAME_TAKEN(다른 운영자가 같은 이름, 대소문자 무시)
 *   - `GET   /api/admin/profile/login-history` → 200 `{ items: [{ id, at(UTC ISO), ip|null, agent|null, result: 'OK'|'FAIL', reason|null }] }` 내 계정 것만, 최근 것부터 **최근 20건 고정**
 *       `agent` 는 접속 환경 요약(`Chrome / Windows / Desktop` — 6단계부터 서버가 원문 대신 요약을 남긴다. 옛 기록·UA 없는 요청은 null) · `reason` 은 늘 `INVALID_CREDENTIALS`
 *   - `GET   /api/admin/profile/display` → 200 `{ use, values, inherited, effectiveFrom: 'SYSTEM'|'USER' }` — `inherited` 는 **사이트 표시 기본값**(일반 설정에서 저장한 값)이라 바꾸면 다음 조회부터 따라온다
 *   - `PUT   /api/admin/profile/display` `{ use, values }` → 200 저장된 모양 · 400 VALIDATION(`fields.use|language|zoneId|timeFormat|dateFormat`)
 *   - `GET·PUT /api/admin/profile/notification` `{ emailEnabled }` → 200 같은 모양 · 400 VALIDATION(`fields.emailEnabled`). 알림 메일 수신만 정한다(시스템 메일은 끌 수 없다). 이벤트별 끄기는 `useNotificationEventApi`
 * 닉네임 규칙은 공개 사이트와 같은 `UserPolicy`(2~12자, 한글·영문·숫자·밑줄, 단어 사이 공백 한 칸)다. 화면이 미리 검사하지만(`UtilValidation.nickname`) 판정은 서버다.
 */
const PATH = {
    root: '/api/admin/profile',
    loginHistory: () => `${PATH.root}/login-history`,
    display: () => `${PATH.root}/display`,
    notification: () => `${PATH.root}/notification`,
};

export function useProfileApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 정보 */
        get: (): Promise<ProfileResponse> => client.get<ProfileResponse>(PATH.root),

        /** 닉네임을 바꾼다. 갱신된 프로필을 돌려준다 */
        update: (body: ProfileUpdateRequest): Promise<ProfileResponse> => client.patch<ProfileResponse>(PATH.root, body),

        /** 내 로그인 이력 — 최근 20건 */
        loginHistory: (): Promise<LoginHistoryResponse> => client.get<LoginHistoryResponse>(PATH.loginHistory()),

        /** 내 표시 형식 — 내 값·사이트 표시 기본값·지금 적용되는 출처 */
        display: (): Promise<MyDisplayResponse> => client.get<MyDisplayResponse>(PATH.display()),

        /** 내 표시 형식을 저장한다(쓸지 + 값). 꺼도 값은 남는다 */
        saveDisplay: (body: MyDisplayRequest): Promise<MyDisplayResponse> => client.put<MyDisplayResponse>(PATH.display(), body),

        /** 알림 수신 설정 */
        notification: (): Promise<MyNotificationResponse> => client.get<MyNotificationResponse>(PATH.notification()),

        /** 알림 수신 설정을 바꾼다 */
        saveNotification: (body: MyNotificationRequest): Promise<MyNotificationResponse> => client.put<MyNotificationResponse>(PATH.notification(), body),
    };
}
