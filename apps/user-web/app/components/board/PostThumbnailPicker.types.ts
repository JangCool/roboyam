import type { PostThumbnailMode } from '~/composables/board/api/usePostApi.types';

/** 글쓰기·고치기 화면의 「대표 이미지」 고른 값 — `PostThumbnailPicker` 의 `v-model` (list-thumbnail-contract 「2단계」) */
export interface PostThumbnailChoice {
    mode: PostThumbnailMode;
    /** `IMAGE` — 고른 본문 이미지 ID(`<img data-image-id>`) */
    imageId: string | null;
    /** `UPLOAD` — 따로 올린 그림 ID */
    fileId: string | null;
    /** `UPLOAD` — 따로 올린 그림 미리보기 주소 */
    uploadUrl: string | null;
}

/** 기본값 — 자동(본문 첫 이미지). 쓰는 곳은 `{ ...AUTO_THUMBNAIL }` 로 복사해 쓴다 */
export const AUTO_THUMBNAIL: Readonly<PostThumbnailChoice> = { mode: 'AUTO', imageId: null, fileId: null, uploadUrl: null };

/** 본문 이미지 하나 — 고르기 작은 그림 */
export interface PostBodyImage {
    imageId: string;
    url: string;
}
