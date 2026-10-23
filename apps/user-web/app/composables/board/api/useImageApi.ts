import type { ImageUploadResult } from '~/composables/board/api/useImageApi.types';
import type { UploadOptions } from '~/composables/common/useApiClient';

/**
 * 에디터 이미지 API — 서버 주소(`/api/images`)가 있는 유일한 곳. 상태는 없다. 글쓰기·고치기의 고급 에디터가 쓴다(`PostEditorForm` → `RichEditor` 의 `upload`).
 * `POST /api/images` multipart `file` — 로그인 회원만(CSRF), `201 {id, url, width, height}`.
 * 오류(서버 문구를 그대로 보인다): 로그인 필요 `401 UNAUTHENTICATED` · 형식·크기 `400`/`413`(코드는 서버가 정한다) · 속도 제한 `429`.
 * 이미지를 막은 게시판에서 글을 저장하면 글 API 가 `422 IMAGE_UPLOAD_NOT_ALLOWED` 로 거절한다(이 API 가 아니다).
 *
 * ⚠ 서버는 다른 세션에서 동시에 만드는 중(2026-10-23) — develop 에 들어오기 전에는 실제 호출을 확인하지 못했다.
 */
const PATH = {
    images: '/api/images',
};

export function useImageApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 이미지 한 장 올리기 — 진행률·취소를 받는다 */
        upload: (file: File, request: UploadOptions = {}): Promise<ImageUploadResult> => {
            const form = new FormData();
            form.append('file', file);
            return client.upload<ImageUploadResult>(PATH.images, form, request);
        },
    };
}
