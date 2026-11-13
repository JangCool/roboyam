import type { ContentFormat, PostThumbnailInput, PostThumbnailView } from '~/composables/board/api/usePostApi.types';
import { AUTO_THUMBNAIL, type PostBodyImage, type PostThumbnailChoice } from '~/components/board/PostThumbnailPicker.types';

/** 따로 올릴 그림의 문제 — 빈 파일 · 형식 · 용량 */
export type PostThumbnailFileProblem = 'EMPTY' | 'TYPE' | 'SIZE';

/**
 * 글 대표 썸네일(list-thumbnail-contract 「2단계」) 화면 규칙 — 입력만 보고 답한다. 판정은 서버가 한다(여기 검사는 미리 알려 주는 것).
 * 서버 `PostThumbnails` 의 자동 규칙(본문 첫 이미지 → 없으면 첫 YouTube 영상 `hqdefault`)을 미리보기용으로 같게 둔다.
 */
export default class UtilPostThumbnail {
    /** 따로 올릴 수 있는 형식 — 글 이미지와 같다(JPEG·PNG·WebP·GIF) */
    static readonly FILE_ACCEPT = 'image/jpeg,image/png,image/webp,image/gif';
    static readonly FILE_TYPES: readonly string[] = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'];
    /** 따로 올릴 수 있는 크기 — 글 이미지와 같다(10MB, 서버 설정이 정본) */
    static readonly FILE_MAX_BYTES = 10 * 1024 * 1024;

    /**
     * 따로 올릴 그림의 문제.
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
     * 본문 이미지(본문 순서) — 에디터 HTML 의 `img[data-image-id]`(주소가 있는 것만). 평문 글은 빈 목록.
     * @param {string} content - 본문.
     * @param {ContentFormat} format - 본문 형식.
     * @returns {PostBodyImage[]} 본문 이미지.
     */
    static bodyImages(content: string, format: ContentFormat): PostBodyImage[] {
        if (format !== 'HTML' || !content) return [];
        const seen = new Set<string>();
        const images: PostBodyImage[] = [];
        for (const img of Array.from(new DOMParser().parseFromString(content, 'text/html').body.querySelectorAll<HTMLImageElement>('img[data-image-id]'))) {
            const imageId = img.getAttribute('data-image-id') ?? '';
            const url = img.getAttribute('src') ?? '';
            if (!imageId || !url || seen.has(imageId)) continue;
            seen.add(imageId);
            images.push({ imageId, url });
        }
        return images;
    }

    /**
     * 본문 첫 YouTube 영상의 썸네일 주소(서버와 같은 `hqdefault`) — 없으면 null.
     * @param {string} content - 본문.
     * @param {ContentFormat} format - 본문 형식.
     * @returns {string | null} 주소.
     */
    static firstVideoUrl(content: string, format: ContentFormat): string | null {
        if (format !== 'HTML' || !content) return null;
        const id = new DOMParser().parseFromString(content, 'text/html').body.querySelector('div[data-youtube-id]')?.getAttribute('data-youtube-id');
        return id && /^[A-Za-z0-9_-]{11}$/.test(id) ? `https://i.ytimg.com/vi/${id}/hqdefault.jpg` : null;
    }

    /**
     * 실제로 쓰일 고른 값 — 고른 본문 이미지가 본문에서 빠졌으면 자동으로 본다(저장도 이 값으로 한다).
     * @param {PostThumbnailChoice} choice - 고른 값.
     * @param {PostBodyImage[]} images - 지금 본문 이미지.
     * @returns {PostThumbnailChoice} 쓰일 값.
     */
    static effective(choice: PostThumbnailChoice, images: PostBodyImage[]): PostThumbnailChoice {
        if (choice.mode === 'IMAGE' && !images.some((img) => img.imageId === choice.imageId)) return { ...AUTO_THUMBNAIL, fileId: choice.fileId, uploadUrl: choice.uploadUrl };
        if (choice.mode === 'UPLOAD' && !choice.fileId) return { ...AUTO_THUMBNAIL };
        return choice;
    }

    /**
     * 고른 이미지가 본문에서 빠져 자동으로 돌아갔나.
     * @param {PostThumbnailChoice} choice - 고른 값.
     * @param {PostBodyImage[]} images - 지금 본문 이미지.
     * @returns {boolean} 빠졌으면 true.
     */
    static fellBack(choice: PostThumbnailChoice, images: PostBodyImage[]): boolean {
        return choice.mode === 'IMAGE' && !images.some((img) => img.imageId === choice.imageId);
    }

    /**
     * 목록에 보일 그림 — 없으면 null.
     * @param {PostThumbnailChoice} choice - 쓰일 값(`effective`).
     * @param {PostBodyImage[]} images - 본문 이미지.
     * @param {string | null} videoUrl - 본문 첫 영상 썸네일.
     * @returns {string | null} 주소.
     */
    static previewUrl(choice: PostThumbnailChoice, images: PostBodyImage[], videoUrl: string | null): string | null {
        if (choice.mode === 'AUTO') return images[0]?.url ?? videoUrl;
        if (choice.mode === 'IMAGE') return images.find((img) => img.imageId === choice.imageId)?.url ?? null;
        if (choice.mode === 'UPLOAD') return choice.uploadUrl;
        return null;
    }

    /**
     * 글 보기 응답의 대표 썸네일 → 고른 값(고치기 화면을 열 때). 없으면 자동.
     * @param {PostThumbnailView | null | undefined} view - 응답의 대표 썸네일.
     * @returns {PostThumbnailChoice} 고른 값.
     */
    static choiceOf(view: PostThumbnailView | null | undefined): PostThumbnailChoice {
        if (!view || view.mode === 'AUTO') return { ...AUTO_THUMBNAIL };
        return { mode: view.mode, imageId: view.imageId, fileId: view.fileId, uploadUrl: view.mode === 'UPLOAD' ? view.url : null };
    }

    /**
     * 같은 선택인가(바꿨는지 — 고치기 화면).
     * @param {PostThumbnailChoice} a - 하나.
     * @param {PostThumbnailChoice} b - 다른 하나.
     * @returns {boolean} 같으면 true.
     */
    static same(a: PostThumbnailChoice, b: PostThumbnailChoice): boolean {
        if (a.mode !== b.mode) return false;
        if (a.mode === 'IMAGE') return a.imageId === b.imageId;
        if (a.mode === 'UPLOAD') return a.fileId === b.fileId;
        return true;
    }

    /**
     * 저장 요청의 `thumbnail` 칸.
     * @param {PostThumbnailChoice} choice - 쓰일 값(`effective`).
     * @returns {PostThumbnailInput} 요청 칸.
     */
    static inputOf(choice: PostThumbnailChoice): PostThumbnailInput {
        if (choice.mode === 'IMAGE') return { mode: 'IMAGE', imageId: choice.imageId ?? '' };
        if (choice.mode === 'UPLOAD') return { mode: 'UPLOAD', fileId: choice.fileId ?? '' };
        return { mode: choice.mode };
    }
}
