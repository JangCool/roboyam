import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { PostThumbnailChoice } from '~/components/board/PostThumbnailPicker.types';
import UtilPostThumbnail, { type PostThumbnailFileProblem } from '~/utils/util-post-thumbnail';

/** 글 대표 썸네일 — 분기가 낀 문구 */
export default class UtilPostThumbnailMessages {
    /**
     * 지금 고른 것이 목록에서 어떻게 보이는지 한 줄.
     * @param {Translate} t - 번역 함수.
     * @param {PostThumbnailChoice} choice - 쓰일 값(`effective`).
     * @param {{ hasImage: boolean; hasVideo: boolean }} body - 본문에 이미지·영상이 있나.
     * @returns {string} 문구.
     */
    static status(t: Translate, choice: PostThumbnailChoice, body: { hasImage: boolean; hasVideo: boolean }): string {
        if (choice.mode === 'AUTO') {
            if (body.hasImage) return t('postThumbnail.statusAutoImage');
            if (body.hasVideo) return t('postThumbnail.statusAutoVideo');
            return t('postThumbnail.statusAutoEmpty');
        }
        if (choice.mode === 'IMAGE') return t('postThumbnail.statusImage');
        if (choice.mode === 'UPLOAD') return t('postThumbnail.statusUpload');
        return t('postThumbnail.statusNone');
    }

    /**
     * 따로 올릴 그림의 문제 문구.
     * @param {Translate} t - 번역 함수.
     * @param {PostThumbnailFileProblem} problem - 문제.
     * @returns {string} 문구.
     */
    static fileProblem(t: Translate, problem: PostThumbnailFileProblem): string {
        if (problem === 'EMPTY') return t('postThumbnail.fileEmpty');
        if (problem === 'SIZE') return t('postThumbnail.fileSize', { max: UtilPostThumbnail.FILE_MAX_BYTES / (1024 * 1024) });
        return t('postThumbnail.fileType');
    }
}
