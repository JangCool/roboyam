import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { CommentDelayNotice } from '~/composables/board/api/useBoardApi.types';

/** 공개까지 남은 시간 — 1시간 이상은 시간(올림), 미만은 분(올림). 이미 지났거나 공개 시각을 모르면 null(「곧」) */
type DelayRemaining = { unit: 'HOUR' | 'MINUTE'; n: number } | null;

const HOUR_MS = 3_600_000;
const MINUTE_MS = 60_000;

/**
 * 댓글 지연 노출(비회원)의 화면 문구 — 가려진 본문 자리·접근성 이름·로그인 안내 시트. **문구 본문은 `i18n/locales/{ko,en}/prompt.ts`** 에 있다.
 * 설계서 `docs/specs/COM-001.md` 3-1 ③⑤⑥ · 3-3 · 3-4.
 *
 * 회원 문구에는 운영 주체가 나오지 않는다(규칙 22).
 * 안내 방식은 사이트 공개 표시 기본값 `boardDisplay.commentDelayNotice`(board-public-contract 결정 2, 정정 2026-11-12)가 정한다:
 *  - `TIME`(기본): 남은 시간을 알린다 — 「3시간 후에 볼 수 있어요 · 지금 보려면 로그인하세요」(남은 시간 = 댓글의 `visibleAt` − 지금, 1시간 이상은 시간 올림·미만은 분 올림,
 *    지났거나 `visibleAt` 이 없으면 「곧 볼 수 있어요」). 안내 시트도 「N시간 뒤 공개 · 로그인하면 지금 바로」 취지(`viewSheet`).
 *  - `LOGIN`: 시간 숫자를 알리지 않는다 — 「로그인하면 바로 볼 수 있어요」(`prompt.delayPlaceholder`, 시트는 `prompt.viewTitle`·`prompt.viewDescription` 그대로).
 */
export default class UtilCommentDelayMessages {
    /**
     * 공개까지 남은 시간.
     * @param {string | null | undefined} visibleAt - 댓글의 공개 시각(ISO, 서버가 `DELAYED` 일 때만 준다).
     * @param {number} now - 지금(밀리초).
     * @returns {DelayRemaining} 1시간 이상이면 시간 올림, 미만이면 분 올림. 지났거나 시각이 없거나 읽을 수 없으면 null.
     */
    static remaining(visibleAt: string | null | undefined, now: number): DelayRemaining {
        if (!visibleAt) return null;
        const at = Date.parse(visibleAt);
        if (Number.isNaN(at)) return null;
        const left = at - now;
        if (left <= 0) return null;
        if (left >= HOUR_MS) return { unit: 'HOUR', n: Math.ceil(left / HOUR_MS) };
        return { unit: 'MINUTE', n: Math.ceil(left / MINUTE_MS) };
    }

    /**
     * 가려진 본문 자리 단추의 한 줄.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {CommentDelayNotice} notice - 안내 방식(`boardDisplay.commentDelayNotice`).
     * @param {string | null | undefined} visibleAt - 댓글의 공개 시각.
     * @param {number} now - 지금(밀리초).
     * @returns {string} `TIME` 「N시간 후에 볼 수 있어요 · 지금 보려면 로그인하세요」(분·「곧」 포함) · `LOGIN` 「로그인하면 바로 볼 수 있어요」.
     */
    static placeholder(t: Translate, notice: CommentDelayNotice, visibleAt: string | null | undefined, now: number): string {
        if (notice === 'LOGIN') return t('prompt.delayPlaceholder');
        const left = UtilCommentDelayMessages.remaining(visibleAt, now);
        if (!left) return t('prompt.delaySoon');
        return t(left.unit === 'HOUR' ? 'prompt.delayAfterHours' : 'prompt.delayAfterMinutes', { n: left.n });
    }

    /**
     * 가려진 본문 자리 단추의 접근성 이름 — 아이콘은 `aria-hidden` 이라 이 이름이 읽힌다. 안내 부분은 `placeholder` 와 같다.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {string | null | undefined} nickname - 댓글쓴이 닉네임.
     * @param {CommentDelayNotice} notice - 안내 방식.
     * @param {string | null | undefined} visibleAt - 댓글의 공개 시각.
     * @param {number} now - 지금(밀리초).
     * @returns {string} "{닉네임}님의 댓글 — {안내}". 닉네임이 없으면 안내만.
     */
    static placeholderLabel(t: Translate, nickname: string | null | undefined, notice: CommentDelayNotice, visibleAt: string | null | undefined, now: number): string {
        const who = (nickname ?? '').trim();
        if (notice === 'LOGIN') return who ? t('prompt.delayPlaceholderLabel', { name: who }) : t('prompt.delayPlaceholder');
        const text = UtilCommentDelayMessages.placeholder(t, notice, visibleAt, now);
        return who ? t('prompt.delayNoticeLabel', { name: who, notice: text }) : text;
    }

    /**
     * 가려진 댓글을 눌렀을 때 뜨는 로그인 안내 시트(종류 `view`)의 제목·설명 — `TIME` 일 때만. `LOGIN` 이면 null(시트의 기본 문구 `prompt.viewTitle`·`prompt.viewDescription`).
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {CommentDelayNotice} notice - 안내 방식.
     * @param {string | null | undefined} visibleAt - 댓글의 공개 시각.
     * @param {number} now - 지금(밀리초).
     * @returns {{ title: string; description: string } | null} 「N시간 뒤 공개되는 댓글이에요」 + 「로그인하면 지금 바로 …」. 시간을 모르면 「곧 공개되는 댓글이에요」.
     */
    static viewSheet(t: Translate, notice: CommentDelayNotice, visibleAt: string | null | undefined, now: number): { title: string; description: string } | null {
        if (notice === 'LOGIN') return null;
        const left = UtilCommentDelayMessages.remaining(visibleAt, now);
        const title = !left ? t('prompt.viewTimeSoonTitle') : t(left.unit === 'HOUR' ? 'prompt.viewTimeHoursTitle' : 'prompt.viewTimeMinutesTitle', { n: left.n });
        return { title, description: t('prompt.viewTimeDescription') };
    }
}
