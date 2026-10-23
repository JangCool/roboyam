import { mockNicknameImageApply, mockNicknameImageCancel, mockNicknameImageRemove, mockNicknameImageStatus } from '~/composables/profile/api/mock/nickname-image-mock';
import type { MyNicknameImage, NicknameImageUploadOptions } from '~/composables/profile/api/useNicknameImageApi.types';

/**
 * 닉네임 그림 API — 서버 주소 문자열(`/api/me/nickname-image…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 후보: `docs/features/member-social-contract.md`(모양은 community `docs/api-nickname-image.md` — 아이템샵 변경권 없음). 로그인 필요(401).
 *
 *  - `GET /api/me/nickname-image` → `MyNicknameImage`(이메일 인증 전·정지 중에도 본다)
 *  - `POST /api/me/nickname-image`(multipart `file`) → `201 MyNicknameImage` — 이메일 인증 + 정지 아님 + 단골 이상.
 *    `403 NICKNAME_IMAGE_GRADE_TOO_LOW` · `409 NICKNAME_IMAGE_REAPPLY_TOO_SOON`(`retryAfterSeconds`·`extras.canReapplyAt`) · `400 VALIDATION fields.file`(형식·200KB·4:1·최소 120×30·GIF 30프레임) ·
 *    `429 RATE_LIMITED`(10초·하루 10번) · `403 EMAIL_NOT_VERIFIED` · `403 ACCOUNT_RESTRICTED`. 대기 신청이 있으면 새 신청이 대체한다. 검토 대기는 운영자 알림 `NICKNAME_IMAGE_PENDING`.
 *  - `DELETE /api/me/nickname-image/request` → 204 · `404 NICKNAME_IMAGE_REQUEST_NOT_FOUND`
 *  - `DELETE /api/me/nickname-image` → 204 · `404 NICKNAME_IMAGE_NOT_FOUND`
 * 올리기·취소·해제 뒤에는 화면이 상태를 다시 받는다(낙관적으로 바꾸지 않는다).
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/nickname-image-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    image: '/api/me/nickname-image',
    request: '/api/me/nickname-image/request',
};

export function useNicknameImageApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 자격(등급·정지·이메일 인증)은 로그인 정보로 정한다
    const { user, restriction } = useAuth();
    const restricted = () => restriction.value?.status === 'SUSPENDED' || restriction.value?.status === 'BANNED';
    void PATH;

    return {
        /** 내 닉네임 그림 상태 — 자격·적용 중·최근 대기/반려 신청·한도 */
        status: (signal?: AbortSignal): Promise<MyNicknameImage> =>
            // MOCK:
            (void signal, mockNicknameImageStatus(user.value, restricted())),
        // REAL: client.get<MyNicknameImage>(PATH.image, { signal }),

        /** 신청(올리기) — 서버가 240×60 으로 맞춰 저장하고 운영자 검토를 기다린다 */
        apply: (file: File, options: NicknameImageUploadOptions = {}): Promise<MyNicknameImage> =>
            // MOCK:
            mockNicknameImageApply(user.value, restricted(), file, options),
        // REAL: client.upload<MyNicknameImage>(PATH.image, (() => { const form = new FormData(); form.append('file', file, file.name); return form; })(), options),

        /** 대기 중인 신청 취소 */
        cancelRequest: (): Promise<void> =>
            // MOCK:
            mockNicknameImageCancel(user.value, restricted()),
        // REAL: client.del<void>(PATH.request),

        /** 적용 중인 그림 해제 — 글자 닉네임으로 돌아간다 */
        remove: (): Promise<void> =>
            // MOCK:
            mockNicknameImageRemove(user.value, restricted()),
        // REAL: client.del<void>(PATH.image),
    };
}
