import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { AdminPostThumbnailView } from '~/composables/post/api/usePostApi.types';
import UtilPostThumbnail, { type PostThumbnailFileProblem } from '~/utils/util-post-thumbnail';

/** 글 대표 썸네일 — 분기가 낀 문구 */
export default class UtilPostThumbnailMessages {
    /**
     * 출처 표시 — 자동: 첫 이미지 / 자동: 첫 영상 / 글쓴이가 고름 / 글쓴이가 올림 / 운영자가 고름 / 운영자가 올림 / 썸네일 없음.
     * @param {Translate} t - 번역 함수.
     * @param {AdminPostThumbnailView} view - 지금 대표 썸네일.
     * @returns {string} 출처 글자.
     */
    static source(t: Translate, view: AdminPostThumbnailView): string {
        const operator = view.setBy === 'OPERATOR';
        if (view.mode === 'AUTO') {
            if (view.autoFrom === 'IMAGE') return t('postThumbnail.sourceAutoImage');
            if (view.autoFrom === 'VIDEO') return t('postThumbnail.sourceAutoVideo');
            return t('postThumbnail.sourceAutoEmpty');
        }
        if (view.mode === 'IMAGE') return operator ? t('postThumbnail.sourceOperatorPicked') : t('postThumbnail.sourceAuthorPicked');
        if (view.mode === 'UPLOAD') return operator ? t('postThumbnail.sourceOperatorUploaded') : t('postThumbnail.sourceAuthorUploaded');
        return operator ? t('postThumbnail.sourceNoneOperator') : t('postThumbnail.sourceNoneAuthor');
    }

    /**
     * 올릴 그림의 문제 문구.
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
