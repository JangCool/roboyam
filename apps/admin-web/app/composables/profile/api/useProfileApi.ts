import { mockLoginHistory, mockMyDisplay, mockMyNotification, mockProfileGet, mockProfileUpdate, mockSaveMyDisplay, mockSaveMyNotification } from '~/composables/profile/api/mock/profile-mock';
import type { LoginHistoryResponse, MyDisplayRequest, MyDisplayResponse, MyNotificationRequest, MyNotificationResponse, ProfileResponse, ProfileUpdateRequest } from '~/composables/profile/api/useProfileApi.types';

/**
 * 내 정보 API — 서버 주소 문자열(`/api/admin/profile…`)이 있는 **유일한 곳**이다. 상태는 없다(화면이 쓰는 상태는 `useProfile` · `useLoginHistory`).
 *
 * 후보 계약(확정 전): 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 *   - `GET   /api/admin/profile` → 200 `{ id, email, nickname, role, status, createdAt, lastLoginAt }` (시각은 UTC ISO, 최근 로그인은 없으면 null)
 *   - `PATCH /api/admin/profile` `{ nickname }` → 200 갱신된 프로필 · 400 VALIDATION(+`fields.nickname`) · 409 NICKNAME_TAKEN
 *   - `GET   /api/admin/profile/login-history` → 200 `{ items: [{ id, at(UTC ISO), ip|null, agent|null, result: 'OK'|'FAIL', reason|null }] }` 본인 것만, 최근 것부터 **최근 20건 고정**(페이지·건수 정책 미정)
 *       ⚠ 서버에 이 데이터가 아직 없다 — 시도마다 남기는 저장소가 새로 필요하다(`LoginHistoryItem` 주석). `agent` 는 로그인 요청의 User-Agent 헤더 원문이다.
 *   - `GET   /api/admin/profile/display` → 200 `{ use, values: DisplayValues, inherited: DisplayValues, effectiveFrom: 'SYSTEM'|'USER' }` (내 표기 층: 제품 기준 ← 내 표기)
 *   - `PUT   /api/admin/profile/display` `{ use, values }` → 200 저장된 모양 · 400 VALIDATION(`fields.language|zoneId|timeFormat|dateFormat`)
 *   - `GET·PUT /api/admin/profile/notification` `{ emailEnabled }` → 200 같은 모양(관리자 알림 이메일 수신 on/off)
 *       ⚠ 위 둘은 서버에 아직 없다 — 제품 기준 값(사이트/제품 기본 표기)의 출처와 알림을 보내는 쪽(수신자 해석)도 정책이 정해져야 한다.
 *   닉네임 규칙은 서버 `UserPolicy`(2~12자, 한글·영문·숫자·밑줄, 단어 사이 공백 한 칸)다. 닉네임 변경 횟수·기간 제한이 있는지는 아직 정해지지 않았다.
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/profile-mock.ts` 를 부른다(스위치는 그 파일 머리말). 서버가 붙으면 `// MOCK:` 줄을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    root: '/api/admin/profile',
    loginHistory: () => `${PATH.root}/login-history`,
    display: () => `${PATH.root}/display`,
    notification: () => `${PATH.root}/notification`,
};

export function useProfileApi() {
    // REAL: const client = useApiClient();
    void PATH;

    return {
        /** 내 정보 */
        get: (): Promise<ProfileResponse> =>
            // MOCK:
            mockProfileGet(),
        // REAL: client.get<ProfileResponse>(PATH.root),

        /** 닉네임을 바꾼다. 갱신된 프로필을 돌려준다 */
        update: (body: ProfileUpdateRequest): Promise<ProfileResponse> =>
            // MOCK:
            mockProfileUpdate(body),
        // REAL: client.patch<ProfileResponse>(PATH.root, body),

        /** 내 로그인 이력 — 최근 20건 */
        loginHistory: (): Promise<LoginHistoryResponse> =>
            // MOCK:
            mockLoginHistory(),
        // REAL: client.get<LoginHistoryResponse>(PATH.loginHistory()),

        /** 내 표기(표시 값) — 내 값·제품 기준·지금 적용되는 출처 */
        display: (): Promise<MyDisplayResponse> =>
            // MOCK:
            mockMyDisplay(),
        // REAL: client.get<MyDisplayResponse>(PATH.display()),

        /** 내 표기를 저장한다(내 표기를 쓸지 + 값). 꺼도 값은 남는다 */
        saveDisplay: (body: MyDisplayRequest): Promise<MyDisplayResponse> =>
            // MOCK:
            mockSaveMyDisplay(body),
        // REAL: client.put<MyDisplayResponse>(PATH.display(), body),

        /** 알림 수신 설정 */
        notification: (): Promise<MyNotificationResponse> =>
            // MOCK:
            mockMyNotification(),
        // REAL: client.get<MyNotificationResponse>(PATH.notification()),

        /** 알림 수신 설정을 바꾼다 */
        saveNotification: (body: MyNotificationRequest): Promise<MyNotificationResponse> =>
            // MOCK:
            mockSaveMyNotification(body),
        // REAL: client.put<MyNotificationResponse>(PATH.notification(), body),
    };
}
