import type { AdminPostThumbnailRequest, AdminPostThumbnailView, PostThumbnailMode } from '~/composables/post/api/usePostApi.types';

/** 대표 썸네일 대화상자에서 고른 것 — 저장 전 화면 값 */
export interface PostThumbnailPick {
    mode: PostThumbnailMode;
    /** `IMAGE` — 고른 본문 이미지 ID */
    imageId: string | null;
    /** `UPLOAD` — 올린 파일 ID */
    fileId: string | null;
    /** `UPLOAD` — 올린 그림 미리보기 주소 */
    uploadUrl: string | null;
}

/** 올릴 그림의 문제 — 빈 파일 · 형식 · 용량 */
export type PostThumbnailFileProblem = 'EMPTY' | 'TYPE' | 'SIZE';

/**
 * 글 대표 썸네일(list-thumbnail-contract 「2단계」) 화면 규칙 — 입력만 보고 답한다. 판정은 서버가 한다(여기 검사는 미리 알려 주는 것).
 */
export default class UtilPostThumbnail {
    /** 올릴 수 있는 형식 — 글 이미지와 같다(JPEG·PNG·WebP·GIF) */
    static readonly FILE_ACCEPT = '.jpg,.jpeg,.png,.webp,.gif';
    static readonly FILE_TYPES: readonly string[] = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
    /** 올릴 수 있는 크기 — 글 이미지와 같다(10MB, 서버 설정이 정본) */
    static readonly FILE_MAX_BYTES = 10 * 1024 * 1024;

    /**
     * 올릴 그림의 문제.
     * @param {{ size: number; type: string }} file - 고른 파일.
     * @returns {PostThumbnailFileProblem | null} 문제 없으면 null.
     */
    static fileProblem(file: { size: number; type: string }): PostThumbnailFileProblem | null {
        if (file.size === 0) return 'EMPTY';
        if (!UtilPostThumbnail.FILE_TYPES.includes(file.type)) return 'TYPE';
        if (file.size > UtilPostThumbnail.FILE_MAX_BYTES) return 'SIZE';
        return null;
    }

    /**
     * 지금 상태에서 시작하는 고른 값 — 대화상자를 열 때.
     * @param {AdminPostThumbnailView} view - 글의 지금 대표 썸네일.
     * @returns {PostThumbnailPick} 고른 값.
     */
    static pickOf(view: AdminPostThumbnailView): PostThumbnailPick {
        return { mode: view.mode, imageId: view.imageId, fileId: view.fileId, uploadUrl: view.mode === 'UPLOAD' ? view.url : null };
    }

    /**
     * 고른 값이면 목록에 보일 그림 — 없으면 null(썸네일 없음).
     * @param {AdminPostThumbnailView} view - 글의 지금 대표 썸네일(이미지 목록·자동 그림).
     * @param {PostThumbnailPick} pick - 고른 값.
     * @returns {string | null} 그림 주소.
     */
    static previewUrl(view: AdminPostThumbnailView, pick: PostThumbnailPick): string | null {
        if (pick.mode === 'AUTO') return view.autoUrl;
        if (pick.mode === 'IMAGE') return view.images.find((img) => img.imageId === pick.imageId)?.url ?? null;
        if (pick.mode === 'UPLOAD') return pick.uploadUrl;
        return null;
    }

    /**
     * 저장할 것이 없나 — 지금과 같은 값인데, 운영자가 정한 상태이거나 설정 없는 자동이면 저장할 것이 없다.
     * (글쓴이가 고른 것과 같은 값을 운영자가 저장하면 「운영자가 정함」으로 바뀌어 잠기므로 그때는 저장할 수 있다)
     * @param {AdminPostThumbnailView} view - 지금 상태.
     * @param {PostThumbnailPick} pick - 고른 값.
     * @returns {boolean} 저장할 것이 없으면 true.
     */
    static unchanged(view: AdminPostThumbnailView, pick: PostThumbnailPick): boolean {
        const same = view.mode === pick.mode && (pick.mode !== 'IMAGE' || view.imageId === pick.imageId) && (pick.mode !== 'UPLOAD' || view.fileId === pick.fileId);
        // 고른 이미지를 본문에서 빼 `AUTO` 로 보이지만 설정이 남은 글(`setBy` 있음)은 「자동으로 되돌리기」로 설정을 지울 수 있어야 한다(서버 AUTO = 행 삭제 · 잠금 해제)
        return same && (view.mode === 'AUTO' ? view.setBy === null : view.setBy === 'OPERATOR');
    }

    /**
     * 고른 값을 다 채웠나 — 이미지 고르기는 이미지를, 올리기는 올린 그림을 골라야 저장할 수 있다.
     * @param {PostThumbnailPick} pick - 고른 값.
     * @returns {boolean} 저장할 수 있으면 true.
     */
    static complete(pick: PostThumbnailPick): boolean {
        if (pick.mode === 'IMAGE') return pick.imageId !== null;
        if (pick.mode === 'UPLOAD') return pick.fileId !== null;
        return true;
    }

    /**
     * 저장 요청 본문.
     * @param {PostThumbnailPick} pick - 고른 값.
     * @returns {AdminPostThumbnailRequest} 요청 본문.
     */
    static toRequest(pick: PostThumbnailPick): AdminPostThumbnailRequest {
        if (pick.mode === 'IMAGE') return { mode: 'IMAGE', imageId: pick.imageId ?? '' };
        if (pick.mode === 'UPLOAD') return { mode: 'UPLOAD', fileId: pick.fileId ?? '' };
        return { mode: pick.mode };
    }
}
