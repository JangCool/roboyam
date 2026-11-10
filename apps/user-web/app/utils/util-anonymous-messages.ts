import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { AnonymousMark, PostAuthor } from '~/composables/board/api/usePostApi.types';

/**
 * 익명 게시판(anonymous-board-contract)의 계산이 낀 문구 — 작성자 이름(「익명」·「익명3」)·댓글 입력 자리표시. **문구 본문은 `i18n/locales/{ko,en}/anonymous.ts`**.
 * 번호는 서버가 매긴다(글 안에서 처음 댓글을 다는 회원 순서, 글쓴이는 번호 없이 `op`). 화면은 받은 표시를 그대로 글자로 바꾼다.
 */
export default class UtilAnonymousMessages {
    /**
     * 익명 작성자 이름 — 번호가 있으면 「익명{n}」, 없으면(글 작성자·목록·옛 응답) 「익명」. 「글쓴이」 칩은 이름에 넣지 않는다(댓글 줄이 따로 그린다).
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {AnonymousMark | null | undefined} mark - 작성자 칸의 `anonymous`.
     * @returns {string} 보일 이름.
     */
    static name(t: Translate, mark: AnonymousMark | null | undefined): string {
        return mark?.number != null ? t('anonymous.numbered', { n: mark.number }) : t('anonymous.name');
    }

    /**
     * 글자로만 쓰는 작성자 이름(답글 상대 「@익명3」·접근성 이름) — 익명이면 `name`, 그 밖은 닉네임(탈퇴면 「탈퇴한 회원」). 그리는 자리는 `PostAuthorLabel` 을 쓴다.
     * @param {Translate} t - 번역 함수.
     * @param {PostAuthor} author - 글·댓글의 작성자 칸.
     * @returns {string} 보일 이름.
     */
    static authorName(t: Translate, author: PostAuthor): string {
        if (author.kind === 'ANONYMOUS') return UtilAnonymousMessages.name(t, author.anonymous);
        return author.name ?? t('board.authorWithdrawn');
    }

    /**
     * 익명 글의 댓글 입력 칸 자리표시 — 글쓴이면 「익명(글쓴이)(으)로…」, 번호를 받았으면 「익명{n}(으)로…」, 받기 전이면 「익명으로…」.
     * @param {Translate} t - 번역 함수.
     * @param {AnonymousMark} viewer - 댓글 목록의 `viewerAnonymous`.
     * @returns {string} 자리표시 문구.
     */
    static placeholder(t: Translate, viewer: AnonymousMark): string {
        if (viewer.op) return t('anonymous.placeholderOp');
        if (viewer.number != null) return t('anonymous.placeholderNumbered', { n: viewer.number });
        return t('anonymous.placeholderPlain');
    }
}
