import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { PollVoteBlockedReason } from '~/composables/board/api/usePollApi.types';
import type { PollFieldErrors } from '~/components/board/PollSettingsFields.types';
import UtilPoll, { type PollCloseProblem, type PollOptionsProblem, type PollProblems, type PollQuestionProblem } from '~/utils/util-poll';

/**
 * 게시글 투표 화면의 계산이 낀 문구 — 칸 문제·투표할 수 없는 까닭. 문구 본문은 `i18n/locales/{ko,en}/poll.ts`.
 * 상수 문구는 컴포넌트가 `t('poll.키')` 로 부른다.
 */
export default class UtilPollMessages {
    /**
     * 질문 칸 문제 문구.
     * @param {Translate} t - 번역 함수.
     * @param {PollQuestionProblem | null} problem - 문제.
     * @returns {string} 문구. 문제가 없으면 빈 글자.
     */
    static question(t: Translate, problem: PollQuestionProblem | null): string {
        if (problem === 'REQUIRED') return t('poll.questionRequired');
        if (problem === 'TOO_LONG') return t('poll.questionTooLong', { max: UtilPoll.QUESTION_MAX });
        return '';
    }

    /**
     * 선택지 문제 문구.
     * @param {Translate} t - 번역 함수.
     * @param {PollOptionsProblem | null} problem - 문제.
     * @returns {string} 문구. 문제가 없으면 빈 글자.
     */
    static options(t: Translate, problem: PollOptionsProblem | null): string {
        if (problem === 'TOO_FEW') return t('poll.optionsTooFew', { min: UtilPoll.OPTIONS_MIN });
        if (problem === 'TOO_MANY') return t('poll.optionsTooMany', { max: UtilPoll.OPTIONS_MAX });
        if (problem === 'TOO_LONG') return t('poll.optionTooLong', { max: UtilPoll.OPTION_MAX });
        if (problem === 'DUPLICATE') return t('poll.optionsDuplicate');
        return '';
    }

    /**
     * 마감 문제 문구.
     * @param {Translate} t - 번역 함수.
     * @param {PollCloseProblem | null} problem - 문제.
     * @returns {string} 문구. 문제가 없으면 빈 글자.
     */
    static closesAt(t: Translate, problem: PollCloseProblem | null): string {
        if (problem === 'INVALID') return t('poll.closesAtInvalid');
        if (problem === 'TOO_SOON') return t('poll.closesAtTooSoon');
        if (problem === 'TOO_LATE') return t('poll.closesAtTooLate', { days: UtilPoll.CLOSE_MAX_DAYS });
        return '';
    }

    /**
     * 투표 칸별 오류 — 서버 문구(`fields.poll`·`"poll.question"`·`"poll.options"`(·`"poll.options[0]"` 같은 칸별 키)·`"poll.closesAt"`)가 먼저, 없으면 화면 검사 문구.
     * 칸에 붙지 않는 `poll.*` 키는 `poll` 줄에 모은다.
     * @param {Translate} t - 번역 함수.
     * @param {Record<string, string>} fields - 서버 `fields`.
     * @param {PollProblems | null} problems - 화면 검사 결과(아직 [등록]을 누르지 않았으면 null).
     * @returns {PollFieldErrors} 칸별 문구.
     */
    static fieldErrors(t: Translate, fields: Record<string, string>, problems: PollProblems | null): PollFieldErrors {
        const server: PollFieldErrors = { poll: '', question: '', options: '', closesAt: '' };
        const extra: string[] = [];
        for (const [key, message] of Object.entries(fields)) {
            if (key === 'poll') extra.push(message);
            else if (key === 'poll.question') server.question = message;
            else if (key === 'poll.options' || key.startsWith('poll.options[') || key.startsWith('poll.options.')) server.options ||= message;
            else if (key === 'poll.closesAt') server.closesAt = message;
            else if (key.startsWith('poll.')) extra.push(message);
        }
        return {
            poll: extra.join(' '),
            question: server.question || (problems ? UtilPollMessages.question(t, problems.question) : ''),
            options: server.options || (problems ? UtilPollMessages.options(t, problems.options) : ''),
            closesAt: server.closesAt || (problems ? UtilPollMessages.closesAt(t, problems.closesAt) : ''),
        };
    }

    /**
     * 지금 투표할 수 없는 까닭 안내.
     * @param {Translate} t - 번역 함수.
     * @param {PollVoteBlockedReason | null} reason - 서버의 `voteBlockedReason`.
     * @returns {string} 문구. 까닭이 없으면 빈 글자.
     */
    static blocked(t: Translate, reason: PollVoteBlockedReason | null): string {
        return reason ? t(`poll.blocked.${reason}`) : '';
    }
}
