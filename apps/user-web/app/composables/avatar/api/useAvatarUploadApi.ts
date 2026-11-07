import type { AvatarUploadOptions, MyAvatarUpload } from '~/composables/avatar/api/useAvatarUploadApi.types';

/**
 * 아바타 올리기 API — 서버 주소 문자열(`/api/me/avatar-upload…`)이 있는 **유일한 곳**이다. 상태는 없다. 로그인 필요(401). **서버 연결됨**(server/api `AvatarUploadController`).
 * 계약: `docs/features/commerce-motion-avatar-contract.md` 「API 계약」 아바타 올리기(api).
 *
 *  - `GET /api/me/avatar-upload` → `MyAvatarUpload`(이메일 인증 전·정지 중에도 본다)
 *  - `POST /api/me/avatar-upload`(multipart `file`) → `201 MyAvatarUpload` — 이메일 인증·정지 아님 + 아바타 변경권 한 장(그 신청에 예약). 대기 신청이 있으면 새 신청이 대체하고 예약된 장을 넘겨받는다.
 *    오류 차례: `403 EMAIL_NOT_VERIFIED` → `403 ACCOUNT_RESTRICTED` → `409 AVATAR_UPLOAD_TICKET_REQUIRED`(맨 위 `ticketCount`·`ticketPrice` — 파일 처리 전) →
 *    `400 VALIDATION fields.file`(빈 파일·300KB·형식) → `429 RATE_LIMITED`(10초·하루 10번, `retryAfterSeconds`) → `400 VALIDATION fields.file`(정사각형·96px·GIF 30장면·움직이는 WebP)
 *  - `DELETE /api/me/avatar-upload/request` → 204(대기 신청 취소 — 변경권 반환) · `404 AVATAR_UPLOAD_REQUEST_NOT_FOUND`
 *  - `DELETE /api/me/avatar-upload` → 204(내가 올린 아바타 지우기 — 반환 없음, 쓰는 중이면 마지막 정지 아바타로) · `404 AVATAR_UPLOAD_NOT_FOUND`
 * 다시 쓰기는 아바타 고르기 그대로(`useAvatarApi().select({ avatarId: UtilAvatar.UPLOAD_ID })`). 올리기·취소·지우기 뒤에는 화면이 상태를 다시 받는다(낙관적으로 바꾸지 않는다).
 */
const PATH = {
    upload: '/api/me/avatar-upload',
    request: '/api/me/avatar-upload/request',
};

export function useAvatarUploadApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 아바타 올리기 상태 — 변경권·최근 대기/반려 신청·내가 올린 아바타·한도 */
        status: (signal?: AbortSignal): Promise<MyAvatarUpload> => client.get<MyAvatarUpload>(PATH.upload, { signal }),

        /** 신청(올리기) — 서버가 검사·변환해 저장하고 운영자 심사를 기다린다(진행률·취소는 닉네임 이미지 올리기와 같다) */
        apply: (file: File, options: AvatarUploadOptions = {}): Promise<MyAvatarUpload> => {
            const form = new FormData();
            form.append('file', file, file.name);
            return client.upload<MyAvatarUpload>(PATH.upload, form, options);
        },

        /** 대기 중인 신청 취소 — 예약된 변경권이 돌아온다 */
        cancelRequest: (): Promise<void> => client.del<void>(PATH.request),

        /** 내가 올린 아바타 지우기 — 쓴 변경권은 돌아오지 않는다 */
        remove: (): Promise<void> => client.del<void>(PATH.upload),
    };
}
