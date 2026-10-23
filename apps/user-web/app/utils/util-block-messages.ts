import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { BlockedBy } from '~/composables/block/api/useBlockApi.types';
import type { MemoProblem, WordProblem } from '~/utils/util-block';
import UtilBoardMessages from '~/utils/util-board-messages';

/** 접히는 것의 종류 — 글(목록·상세) · 댓글 */
export type FoldKind = 'post' | 'comment';

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
/** 며칠 전까지는 "N일 전에 차단", 그 뒤는 날짜로 */
const RECENT_DAYS = 7;

/**
 * 차단·이용자 메모 화면의 **계산이 낀** 문구 — 접힌 줄, 차단한 시각, 실패 한 줄, 입력 검사 문구. 상수 문구는 컴포넌트가 `t('block.*')` 로 쓴다.
 * 서버 `message` 는 그대로 쓴다(상한·중복 같은 거절 문구는 서버가 정한다). 분기는 `code` 로 한다.
 */
export default class UtilBlockMessages {
    /**
     * 접힌 줄의 글자 — "차단한 사용자의 글이에요" · "차단한 단어가 들어간 댓글이에요".
     * @param {Translate} t - 번역 함수.
     * @param {BlockedBy} reason - 접힌 까닭.
     * @param {FoldKind} kind - 글인지 댓글인지.
     * @returns {string} 접힌 줄의 글자.
     */
    static folded(t: Translate, reason: BlockedBy, kind: FoldKind): string {
        if (reason === 'USER') return kind === 'post' ? t('block.foldedUserPost') : t('block.foldedUserComment');
        return kind === 'post' ? t('block.foldedWordPost') : t('block.foldedWordComment');
    }

    /**
     * 접힌 줄의 「보기」 단추를 스크린리더가 읽는 이름.
     * @param {Translate} t - 번역 함수.
     * @param {BlockedBy} reason - 접힌 까닭.
     * @param {FoldKind} kind - 글인지 댓글인지.
     * @returns {string} "차단한 사용자의 글 보기" 꼴.
     */
    static showLabel(t: Translate, reason: BlockedBy, kind: FoldKind): string {
        if (reason === 'USER') return kind === 'post' ? t('block.showUserPost') : t('block.showUserComment');
        return kind === 'post' ? t('block.showWordPost') : t('block.showWordComment');
    }

    /**
     * 차단한 시각 — "방금 차단" · "55분 전에 차단" · "3시간 전에 차단" · "3일 전에 차단" · 일주일이 지나면 "2026.09.10에 차단"(표시 날짜 형식).
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} iso - 차단한 시각.
     * @param {DisplayTime} time - 표시 값에 묶은 시각 함수 모음.
     * @param {number} now - 지금(밀리초).
     * @returns {string} 글자. 시각이 없거나 잘못됐으면 빈 문자열.
     */
    static blockedAt(t: Translate, iso: string | null | undefined, time: DisplayTime, now: number = Date.now()): string {
        const ms = iso ? Date.parse(iso) : Number.NaN;
        if (Number.isNaN(ms)) return '';
        const diff = now - ms;
        if (diff < MINUTE) return t('block.blockedJustNow');
        if (diff < HOUR) return t('block.blockedMinutes', { n: Math.floor(diff / MINUTE) });
        if (diff < DAY) return t('block.blockedHours', { n: Math.floor(diff / HOUR) });
        const days = Math.floor(diff / DAY);
        if (days < RECENT_DAYS) return t('block.blockedDays', { n: days });
        return t('block.blockedOn', { date: time.date(iso) });
    }

    /**
     * 실패 토스트 — 하려던 일 + 서버 문구.
     * @param {Translate} t - 번역 함수.
     * @param {string} what - 하려던 일("차단하지 못했어요").
     * @param {unknown} e - 잡은 오류.
     * @returns {string} 한 줄.
     */
    static failure(t: Translate, what: string, e: unknown): string {
        return t('block.failureLine', { what, reason: UtilBoardMessages.of(t, e) });
    }

    /**
     * 차단 단어 입력 문구 — 서버 `fields.word` 와 같은 뜻. 아직 안 쓴 것(`EMPTY`)은 빈 문자열.
     * @param {Translate} t - 번역 함수.
     * @param {WordProblem | null} problem - 화면이 미리 본 문제.
     * @param {number} min - 최소 글자 수.
     * @param {number} max - 최대 글자 수.
     * @returns {string} 한 줄(문제 없으면 빈 문자열).
     */
    static wordProblem(t: Translate, problem: WordProblem | null, min: number, max: number): string {
        if (problem === 'TOO_SHORT' || problem === 'TOO_LONG') return t('block.wordLength', { min, max });
        if (problem === 'MULTILINE') return t('block.wordMultiline');
        return '';
    }

    /**
     * 메모 입력 문구 — 서버 `fields.memo` 와 같은 뜻.
     * @param {Translate} t - 번역 함수.
     * @param {MemoProblem | null} problem - 화면이 미리 본 문제.
     * @param {number} max - 최대 글자 수.
     * @returns {string} 한 줄(문제 없으면 빈 문자열).
     */
    static memoProblem(t: Translate, problem: MemoProblem | null, max: number): string {
        if (problem === 'MULTILINE') return t('block.memoMultiline');
        if (problem === 'TOO_LONG') return t('block.memoTooLong', { max });
        return '';
    }
}
