import type { MailSettingHistoryPage, MailSettingsSaveRequest, MailSettingsView, MailTestResultView } from '~/composables/mail/api/useMailSettingsApi.types';

/**
 * 메일 서버 설정 API — 서버 주소 문자열(`/api/admin/mail-settings…`)이 있는 **유일한 곳**이다. 상태는 없다.
 *
 * 계약(서버 `MailSettingsController`, `docs/features/mail-settings-contract.md`): 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 *   - `GET /api/admin/mail-settings` → 200 `MailSettingsView`  (`site.read`) — 설치 마법사가 넣은 행 그대로. 없으면 `configured:false`
 *   - `PUT /api/admin/mail-settings` `{ host, port, security, username, password?, fromAddress, senderName, subjectPrefix }` → 200 `MailSettingsView`  (`site.secret`)
 *       · 400 `VALIDATION` `fields.host|port|security|username|password|fromAddress|senderName|subjectPrefix`(한꺼번에)
 *       · 비밀번호를 빼면 유지 — 다만 사용자 이름·서버 주소(대소문자 무시)·보안 방식을 바꾸며 빼면 `fields.password`(다시 입력). 사용자 이름을 비우면 비밀번호도 지운다
 *       · 새 비밀번호인데 암호화 키가 없으면 503 `CRYPTO_KEY_MISSING`. 값이 하나도 안 바뀌면 아무것도 쓰지 않는다
 *   - `POST /api/admin/mail-settings/test` `{ to }` → 200 `{ succeeded, reason, detail }`  (`site.secret`) — **저장된 설정으로** 실제 한 통. 실패도 200
 *       · 400 `fields.to`(주소 형식) · 429 `MAIL_TEST_RATE_LIMITED` + `retryAfterSeconds`(운영자당 분당 5번)
 *   - `GET /api/admin/mail-settings/history?page&size` → 200 `{ items, total, page, size }`  (`site.read`) — 최신이 위
 */
const PATH = {
    root: '/api/admin/mail-settings',
    test: () => `${PATH.root}/test`,
    history: () => `${PATH.root}/history`,
};

export function useMailSettingsApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        load: (): Promise<MailSettingsView> => client.get<MailSettingsView>(PATH.root),

        /** 저장 — 비밀번호는 이 호출의 인자로만 지나가고 어디에도 남기지 않는다 */
        save: (body: MailSettingsSaveRequest): Promise<MailSettingsView> => client.put<MailSettingsView>(PATH.root, body),

        test: (to: string): Promise<MailTestResultView> => client.post<MailTestResultView>(PATH.test(), { to }),

        history: (page: number, size: number): Promise<MailSettingHistoryPage> => client.get<MailSettingHistoryPage>(PATH.history(), { query: { page, size } }),
    };
}
