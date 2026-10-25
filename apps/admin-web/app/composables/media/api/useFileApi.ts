import type { FilePurpose, FileUploadResponse } from '~/composables/media/api/useFileApi.types';

/**
 * 파일 올리기 API — 서버 주소 `/api/admin/files` 가 있는 **유일한 곳**이다. 상태는 없다.
 * 서버 `media.controller.FileController`: multipart `file` + `purpose` → **201** `{ fileId }`. 올린 파일은 그 쓰임의 API(로고 지정·하이라이트 만들기/고치기의 `imageFileId`)로 지정해야 쓰인다.
 *   - 권한: `SITE_LOGO` 는 `site.manage`, `HIGHLIGHT_IMAGE` 는 `highlight.manage`(서버는 둘 중 하나를 요구하고 쓰임마다 다시 본다)
 *   - 검사(로고·하이라이트 같다): PNG·JPG·GIF·WEBP, 2MB, 한 변 4000px, GIF 는 첫 장면 PNG, 다시 인코딩. 거절은 400 VALIDATION `fields.file`(지금 크기·용량 포함) · `fields.purpose`
 *   - 그 밖: 413 `FILE_TOO_LARGE`(multipart 10MB 초과) · 503 `STORAGE_UNAVAILABLE` · 503 `IMAGE_BUSY` · 403 `PERMISSION_DENIED`
 */
const PATH = {
    files: '/api/admin/files',
};

/** 올리기 본문 — 바이트는 JSON 이 아니라 multipart 다(Base64 로 부풀리지 않는다) */
function uploadForm(file: File, purpose: FilePurpose): FormData {
    const form = new FormData();
    form.append('file', file);
    form.append('purpose', purpose);
    return form;
}

export function useFileApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 파일을 올린다 — 받은 `fileId` 를 그 쓰임의 API 에 넘겨야 쓰인다 */
        upload: (file: File, purpose: FilePurpose): Promise<FileUploadResponse> => client.post<FileUploadResponse>(PATH.files, uploadForm(file, purpose)),
    };
}
