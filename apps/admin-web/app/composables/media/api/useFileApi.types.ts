/**
 * 파일 올리기 API 가 주고받는 모양 — 서버 `media.controller.FileController`(`POST /api/admin/files`).
 */

/** 올리는 파일의 쓰임 — 서버 `FilePurpose`(로고 · 하이라이트 대표 이미지) */
export type FilePurpose = 'SITE_LOGO' | 'HIGHLIGHT_IMAGE';

/** `POST /api/admin/files` 201 — 올린 파일의 ID(아직 어디에도 쓰이지 않았다. 지정하지 않은 파일은 「올리다 만 파일 정리」가 지운다) */
export interface FileUploadResponse {
    fileId: string;
}
