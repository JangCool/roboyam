import type { NicknameImagePage, NicknameImageQuery, NicknameImageReasonRequest, NicknameImageSettingsRequest, NicknameImageSettingsView, NicknameImageView } from '~/composables/member/api/useNicknameImageApi.types';

/**
 * 닉네임 이미지 심사 API — 서버 주소 문자열(`/api/admin/nickname-images…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/member-social-contract.md`(닉네임 이미지 — 관리자 검토). 보기 `nickname-image.read`, 승인·반려·해제 `nickname-image.manage`(쓰기는 CSRF).
 *
 *  - `GET /api/admin/nickname-images?status=PENDING|APPROVED|REJECTED|REVOKED&keyword=&page=1&size=20` → `NicknameImagePage`
 *  - `POST /api/admin/nickname-images/{id}/approve` → 200 `NicknameImageView`
 *  - `POST /api/admin/nickname-images/{id}/reject` `{ reason }` → 200 `NicknameImageView`
 *  - `POST /api/admin/nickname-images/{id}/revoke` `{ reason }` → 200 `NicknameImageView`
 * 오류: `404 NICKNAME_IMAGE_NOT_FOUND` · `409 NICKNAME_IMAGE_NOT_PENDING`(이미 처리된 신청을 승인·반려) · `409 NICKNAME_IMAGE_NOT_ACTIVE`(적용 중이 아닌 그림을 해제) ·
 * `400 VALIDATION fields.reason`(1~200자) · `403 PERMISSION_DENIED`.
 *
 * 신청 조건 설정(`/settings/nickname-image`):
 *  - `GET /api/admin/nickname-image-settings` → `NicknameImageSettingsView`(`nickname-image.read`)
 *  - `PUT /api/admin/nickname-image-settings` `NicknameImageSettingsRequest` → 200 `NicknameImageSettingsView`(`nickname-image.manage`, CSRF)
 *    오류 `400 VALIDATION fields.<칸>`(등급 기준인데 등급 없음 · 범위 밖 숫자 · 없는 등급).
 */
const PATH = {
    root: '/api/admin/nickname-images',
    approve: (id: string) => `${PATH.root}/${encodeURIComponent(id)}/approve`,
    reject: (id: string) => `${PATH.root}/${encodeURIComponent(id)}/reject`,
    revoke: (id: string) => `${PATH.root}/${encodeURIComponent(id)}/revoke`,
    settings: '/api/admin/nickname-image-settings',
};

export function useNicknameImageApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 상태별 신청 한 쪽 */
        list: (query: NicknameImageQuery): Promise<NicknameImagePage> => client.get<NicknameImagePage>(PATH.root, { query: { ...query, keyword: query.keyword || undefined } }),

        /** 승인 — 회원의 닉네임 자리에 그림이 보이기 시작한다 */
        approve: (id: string): Promise<NicknameImageView> => client.post<NicknameImageView>(PATH.approve(id)),

        /** 반려 — 사유가 회원에게 보인다 */
        reject: (id: string, body: NicknameImageReasonRequest): Promise<NicknameImageView> => client.post<NicknameImageView>(PATH.reject(id), body),

        /** 해제 — 적용 중인 그림을 내린다 */
        revoke: (id: string, body: NicknameImageReasonRequest): Promise<NicknameImageView> => client.post<NicknameImageView>(PATH.revoke(id), body),

        /** 신청 조건 설정 */
        loadSettings: (): Promise<NicknameImageSettingsView> => client.get<NicknameImageSettingsView>(PATH.settings),

        /** 신청 조건을 통째로 저장한다 — 저장된 값을 돌려준다 */
        saveSettings: (body: NicknameImageSettingsRequest): Promise<NicknameImageSettingsView> => client.put<NicknameImageSettingsView>(PATH.settings, body),
    };
}
