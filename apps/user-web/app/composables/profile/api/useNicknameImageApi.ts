import type { MyNicknameImage, NicknameImageUploadOptions } from '~/composables/profile/api/useNicknameImageApi.types';

/**
 * 닉네임 이미지 API — 서버 주소 문자열(`/api/me/nickname-image…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 후보: `docs/features/member-social-contract.md`(모양은 community `docs/api-nickname-image.md` — 닉네임 이미지 변경권 없음). 로그인 필요(401).
 *
 *  - `GET /api/me/nickname-image` → `MyNicknameImage`(이메일 인증 전·정지 중에도 본다)
 *  - `POST /api/me/nickname-image`(multipart `file`) → `201 MyNicknameImage` — 정지 아님 + 관리자가 정한 신청 조건(`requirements`)을 모두 채움.
 *    `403 NICKNAME_IMAGE_REQUIREMENTS_NOT_MET` · `409 NICKNAME_IMAGE_REAPPLY_TOO_SOON`(`retryAfterSeconds` — 시각은 GET 의 `reapplyAt`·`request.canReapplyAt`) · `400 VALIDATION fields.file`(형식·200KB·4:1·최소 120×30·GIF 30프레임) ·
 *    `429 RATE_LIMITED`(10초·하루 10번) · `403 EMAIL_NOT_VERIFIED` · `403 ACCOUNT_RESTRICTED`. 대기 신청이 있으면 새 신청이 대체한다. 심사 대기는 운영자 알림 `NICKNAME_IMAGE_PENDING`.
 *  - `DELETE /api/me/nickname-image/request` → 204 · `404 NICKNAME_IMAGE_REQUEST_NOT_FOUND`
 *  - `DELETE /api/me/nickname-image` → 204 · `404 NICKNAME_IMAGE_NOT_FOUND`
 * 올리기·취소·해제 뒤에는 화면이 상태를 다시 받는다(낙관적으로 바꾸지 않는다).
 */
const PATH = {
    image: '/api/me/nickname-image',
    request: '/api/me/nickname-image/request',
};

export function useNicknameImageApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 닉네임 이미지 상태 — 자격·적용 중·최근 대기/반려 신청·한도 */
        status: (signal?: AbortSignal): Promise<MyNicknameImage> => client.get<MyNicknameImage>(PATH.image, { signal }),

        /** 신청(올리기) — 서버가 240×60 으로 맞춰 저장하고 운영자 심사를 기다린다 */
        apply: (file: File, options: NicknameImageUploadOptions = {}): Promise<MyNicknameImage> => {
            const form = new FormData();
            form.append('file', file, file.name);
            return client.upload<MyNicknameImage>(PATH.image, form, options);
        },

        /** 대기 중인 신청 취소 */
        cancelRequest: (): Promise<void> => client.del<void>(PATH.request),

        /** 적용 중인 이미지 해제 — 글자 닉네임으로 돌아간다 */
        remove: (): Promise<void> => client.del<void>(PATH.image),
    };
}
