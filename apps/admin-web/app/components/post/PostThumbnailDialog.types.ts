import type { PostThumbnailUploadResult } from '~/composables/post/api/usePostApi.types';

/** 페이지가 `PostThumbnailDialog` 에 내려 주는 서버 동작 — 실패하면 `ApiError` 를 던진다 */
export interface PostThumbnailActions {
    /** 대표 썸네일 그림 올리기 — 올린 파일 ID 와 미리보기 주소 */
    upload: (file: File) => Promise<PostThumbnailUploadResult>;
}
