import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { BoardRecommendItem } from '~/composables/board/api/useBoardApi.types';

/**
 * 게시판 추천 글 카드(board-recommend-contract 「화면」)의 계산이 낀 문구 — 문구 본문은 `i18n/locales/{ko,en}/boardRecommend.ts`.
 */
export default class UtilBoardRecommendMessages {
    /**
     * 카드의 작은 글자 한 줄 — 고른 글은 하이라이트 요약(비면 「운영자 추천」), 자동 글은 「추천 n」(+ 말머리가 있으면 「· 말머리」)
     * @param {Translate} t - 번역 함수.
     * @param {BoardRecommendItem} item - 카드 하나.
     * @returns {string} 작은 글자.
     */
    static label(t: Translate, item: BoardRecommendItem): string {
        if (item.kind === 'PICKED') return item.label?.trim() || t('boardRecommend.picked');
        const prefix = item.prefix?.trim();
        return prefix ? t('boardRecommend.likesPrefix', { n: item.likeCount, prefix }) : t('boardRecommend.likes', { n: item.likeCount });
    }
}
