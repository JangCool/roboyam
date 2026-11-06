import type { AvatarUploadPage, AvatarUploadQuery, AvatarUploadReasonRequest, AvatarUploadView } from '~/composables/member/api/useAvatarUploadApi.types';

/**
 * 아바타 올리기 심사 API — 서버 주소 문자열(`/api/admin/avatar-uploads`)이 있는 **유일한 곳**이다. 상태는 없다. **서버 연결됨**(server/admin `AvatarUploadAdminController`).
 * 계약: `docs/features/commerce-motion-avatar-contract.md` 「API 계약」 아바타 올리기(admin). 닉네임 이미지 심사(`useNicknameImageApi`)와 같은 모양·같은 권한(목록 `nickname-image.read`, 처리 `.manage`).
 *  - `GET /api/admin/avatar-uploads?status=&keyword=&page=&size=` → `AvatarUploadPage`(`status` 기본 PENDING, 쪽 1부터, 크기 기본 20)
 *  - `POST …/{id}/approve` → 변경권 사용 · 회원의 「내가 올린 아바타」가 되고 바로 프로필 아바타(움직이는 GIF 면 회원의 움직이는 닉네임 이미지는 쉰다)
 *  - `POST …/{id}/reject { reason }` → 예약된 변경권 반환 · 회원 알림 `AVATAR_UPLOAD_REVIEWED`(사유)
 *  - `POST …/{id}/revoke { reason }` → 내린다(쓰는 중이면 마지막 정지 아바타로), 변경권 반환 없음 · 회원 알림(사유)
 *  - 오류 `404 AVATAR_UPLOAD_NOT_FOUND` · `409 AVATAR_UPLOAD_NOT_PENDING`·`AVATAR_UPLOAD_NOT_ACTIVE` · `400 VALIDATION fields.reason`(1~200자)
 */
const PATH = {
    root: '/api/admin/avatar-uploads',
    approve: (id: string) => `${PATH.root}/${encodeURIComponent(id)}/approve`,
    reject: (id: string) => `${PATH.root}/${encodeURIComponent(id)}/reject`,
    revoke: (id: string) => `${PATH.root}/${encodeURIComponent(id)}/revoke`,
};

export function useAvatarUploadApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 상태별 신청 한 쪽 */
        list: (query: AvatarUploadQuery): Promise<AvatarUploadPage> => client.get<AvatarUploadPage>(PATH.root, { query: { ...query, keyword: query.keyword || undefined } }),

        /** 승인 */
        approve: (id: string): Promise<AvatarUploadView> => client.post<AvatarUploadView>(PATH.approve(id)),

        /** 반려 — 사유가 회원에게 보이고 예약된 변경권이 돌아간다 */
        reject: (id: string, body: AvatarUploadReasonRequest): Promise<AvatarUploadView> => client.post<AvatarUploadView>(PATH.reject(id), body),

        /** 해제 — 지금 쓰는 그림을 내린다(변경권 반환 없음) */
        revoke: (id: string, body: AvatarUploadReasonRequest): Promise<AvatarUploadView> => client.post<AvatarUploadView>(PATH.revoke(id), body),
    };
}
