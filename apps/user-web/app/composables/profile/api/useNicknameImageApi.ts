import type { MyNicknameImage, NicknameImageUploadOptions } from '~/composables/profile/api/useNicknameImageApi.types';
import type { MyProfile } from '~/composables/profile/api/useProfileApi.types';

/**
 * 닉네임 이미지 API — 서버 주소 문자열(`/api/me/nickname-image…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/member-social-contract.md`(모양은 community `docs/api-nickname-image.md`) · 변경권·쉬는 중·다시 쓰기는 `docs/features/commerce-motion-avatar-contract.md`. 로그인 필요(401). **서버 연결됨**.
 *
 *  - `GET /api/me/nickname-image` → `MyNicknameImage`(이메일 인증 전·정지 중에도 본다)
 *  - `POST /api/me/nickname-image`(multipart `file`) → `201 MyNicknameImage` — 정지 아님 + 관리자가 정한 신청 조건(`requirements`)을 모두 채움 + 닉네임 이미지 변경권 한 장(그 신청에 예약 — 대체는 같은 장을 넘겨받는다).
 *    `409 NICKNAME_IMAGE_TICKET_REQUIRED`(맨 위 `ticketCount`·`ticketPrice` — 파일 처리 전) ·
 *    `403 NICKNAME_IMAGE_REQUIREMENTS_NOT_MET` · `409 NICKNAME_IMAGE_REAPPLY_TOO_SOON`(`retryAfterSeconds` — 시각은 GET 의 `reapplyAt`·`request.canReapplyAt`) · `400 VALIDATION fields.file`(형식·200KB·4:1·최소 120×30·GIF 30프레임) ·
 *    `429 RATE_LIMITED`(10초·하루 10번) · `403 EMAIL_NOT_VERIFIED` · `403 ACCOUNT_RESTRICTED`. 대기 신청이 있으면 새 신청이 대체한다. 심사 대기는 운영자 알림 `NICKNAME_IMAGE_PENDING`.
 *  - `DELETE /api/me/nickname-image/request` → 204(예약한 변경권 반환) · `404 NICKNAME_IMAGE_REQUEST_NOT_FOUND`
 *  - `POST /api/me/nickname-image/resume` → 200 `MyProfile` — 쉬는 중인 움직이는 그림을 다시 쓴다(아바타는 마지막으로 쓴 정지 아바타로, 심사 없음)
 *  - `DELETE /api/me/nickname-image` → 204(변경권 반환 없음) · `404 NICKNAME_IMAGE_NOT_FOUND`
 * 올리기·취소·해제 뒤에는 화면이 상태를 다시 받는다(낙관적으로 바꾸지 않는다).
 */
const PATH = {
    image: '/api/me/nickname-image',
    request: '/api/me/nickname-image/request',
    resume: '/api/me/nickname-image/resume',
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

        /** 쉬는 중인 움직이는 그림을 다시 쓴다 — 응답은 내 프로필(아바타가 마지막 정지 아바타로 돌아갔을 수 있다) */
        resume: (): Promise<MyProfile> => client.post<MyProfile>(PATH.resume),

        /** 적용 중인 이미지 해제 — 글자 닉네임으로 돌아간다 */
        remove: (): Promise<void> => client.del<void>(PATH.image),
    };
}
