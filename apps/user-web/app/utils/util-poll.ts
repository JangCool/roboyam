import type { PollInput, PollOption, PollView } from '~/composables/board/api/usePollApi.types';
import type { PollFormValues } from '~/components/board/PollSettingsFields.types';

/** 질문 칸 문제 — 비었음 · 너무 김 */
export type PollQuestionProblem = 'REQUIRED' | 'TOO_LONG';
/** 선택지 문제 — 채운 선택지가 2개 미만 · 6개 초과 · 한 칸이 너무 김 · 같은 선택지(대소문자 무시) */
export type PollOptionsProblem = 'TOO_FEW' | 'TOO_MANY' | 'TOO_LONG' | 'DUPLICATE';
/** 마감 문제 — 읽을 수 없음 · 지금+1분보다 이름 · 365일 뒤보다 늦음 */
export type PollCloseProblem = 'INVALID' | 'TOO_SOON' | 'TOO_LATE';

export interface PollProblems {
    question: PollQuestionProblem | null;
    options: PollOptionsProblem | null;
    closesAt: PollCloseProblem | null;
}

/** 결과 막대 한 줄 — 퍼센트 = 득표 / 투표한 사람 수(복수 선택은 합이 100 을 넘을 수 있다) */
export interface PollResultRow {
    option: PollOption;
    votes: number;
    percent: number;
    /** 내가 고른 선택지 */
    mine: boolean;
}

/**
 * 게시글 투표 화면의 판정·변환 — 순수 함수만(docs/features/post-poll-contract.md 「규칙」). 화면 검사는 미리 알려 주는 용도이고 판정은 서버다.
 * 글자 수는 서버처럼 코드포인트로 센다(이모지 한 개 = 1자).
 */
export default class UtilPoll {
    static readonly QUESTION_MAX = 100;
    static readonly OPTION_MAX = 50;
    static readonly OPTIONS_MIN = 2;
    static readonly OPTIONS_MAX = 6;
    /** 마감은 지금부터 1분 뒤 이후 */
    static readonly CLOSE_MIN_LEAD_MS = 60_000;
    /** 마감은 지금부터 365일 안 */
    static readonly CLOSE_MAX_DAYS = 365;

    /**
     * 글자 수(코드포인트).
     * @param {string} text - 글자.
     * @returns {number} 글자 수.
     */
    static length(text: string): number {
        return [...text].length;
    }

    /**
     * 질문 정리 — 공백 묶음을 한 칸으로, 앞뒤 공백을 뗀다(서버 「공백 정리」와 같게).
     * @param {string} question - 입력한 질문.
     * @returns {string} 보낼 질문.
     */
    static cleanQuestion(question: string): string {
        return question.replace(/\s+/g, ' ').trim();
    }

    /**
     * 보낼 선택지 — 앞뒤 공백을 떼고 빈 칸을 뺀다(차례는 그대로).
     * @param {string[]} options - 선택지 칸.
     * @returns {string[]} 채운 선택지.
     */
    static filledOptions(options: readonly string[]): string[] {
        return options.map((o) => o.trim()).filter((o) => o !== '');
    }

    /**
     * 새 투표 칸 — 붙이기 끔, 빈 선택지 두 칸, 복수 선택 끔, 마감 없음, 다시 투표 끔.
     * @returns {PollFormValues} 새 값.
     */
    static emptyForm(): PollFormValues {
        return { enabled: false, question: '', options: ['', ''], multiple: false, closesAt: '', changeAllowed: false };
    }

    /**
     * 글의 투표 → 칸 값(고치기). 투표가 없으면 새 투표 칸.
     * @param {PollView | null | undefined} poll - 글 상세의 `poll`.
     * @returns {PollFormValues} 칸 값.
     */
    static formOf(poll: PollView | null | undefined): PollFormValues {
        if (!poll) return UtilPoll.emptyForm();
        const options = poll.options.map((o) => o.label);
        while (options.length < UtilPoll.OPTIONS_MIN) options.push('');
        return { enabled: true, question: poll.question, options, multiple: poll.multiple, closesAt: poll.closesAt ?? '', changeAllowed: poll.changeAllowed };
    }

    /**
     * 칸 값 → 본문의 `poll`(붙이지 않으면 null).
     * @param {PollFormValues} values - 칸 값.
     * @returns {PollInput | null} 본문 칸.
     */
    static inputOf(values: PollFormValues): PollInput | null {
        if (!values.enabled) return null;
        return {
            question: UtilPoll.cleanQuestion(values.question),
            options: UtilPoll.filledOptions(values.options),
            multiple: values.multiple,
            closesAt: values.closesAt ? values.closesAt : null,
            changeAllowed: values.changeAllowed,
        };
    }

    /**
     * 두 칸 값이 같은 투표인가 — 보낼 모양으로 비교한다(빈 선택지 칸·공백 차이는 같은 것으로, 마감은 같은 순간이면 같은 것으로).
     * @returns {boolean} 같으면 true.
     */
    static same(a: PollFormValues, b: PollFormValues): boolean {
        const x = UtilPoll.inputOf(a);
        const y = UtilPoll.inputOf(b);
        if (!x || !y) return x === y;
        return (
            x.question === y.question &&
            x.multiple === y.multiple &&
            x.changeAllowed === y.changeAllowed &&
            UtilPoll.sameInstant(x.closesAt, y.closesAt) &&
            x.options.length === y.options.length &&
            x.options.every((o, i) => o === y.options[i])
        );
    }

    /**
     * 같은 순간인가(시간대 표기가 달라도) — 둘 다 없으면 같다.
     * @returns {boolean} 같으면 true.
     */
    static sameInstant(a: string | null, b: string | null): boolean {
        if (!a || !b) return !a && !b;
        const x = Date.parse(a);
        const y = Date.parse(b);
        return Number.isNaN(x) || Number.isNaN(y) ? a === b : x === y;
    }

    /**
     * 같은 선택지(대소문자·앞뒤 공백 무시)가 있는 칸의 번호 — 빈 칸은 빼고, 둘째 것부터가 아니라 겹치는 칸 모두.
     * @param {string[]} options - 선택지 칸.
     * @returns {Set<number>} 칸 번호.
     */
    static duplicateIndexes(options: readonly string[]): Set<number> {
        const seen = new Map<string, number[]>();
        options.forEach((o, i) => {
            const key = o.trim().toLocaleLowerCase();
            if (!key) return;
            seen.set(key, [...(seen.get(key) ?? []), i]);
        });
        const out = new Set<number>();
        for (const indexes of seen.values()) if (indexes.length > 1) indexes.forEach((i) => out.add(i));
        return out;
    }

    /**
     * 마감으로 고를 수 있는 범위 — 지금+1분 ~ 지금+365일(ISO).
     * @param {number} now - 지금(밀리초).
     * @returns {{ min: string; max: string }} 범위.
     */
    static closeRange(now: number): { min: string; max: string } {
        return { min: new Date(now + UtilPoll.CLOSE_MIN_LEAD_MS).toISOString(), max: new Date(now + UtilPoll.CLOSE_MAX_DAYS * 86_400_000).toISOString() };
    }

    /**
     * 칸 값의 문제 — 붙이지 않으면 문제 없음. 고치기에서 처음 값과 같은 마감 시각은 다시 검사하지 않는다(서버도 고치지 않은 지난 마감은 보지 않는다).
     * @param {PollFormValues} values - 칸 값.
     * @param {number} now - 지금(밀리초).
     * @param {string} initialClosesAt - 고치기의 처음 마감(글쓰기는 '').
     * @returns {PollProblems} 칸별 문제.
     */
    static problems(values: PollFormValues, now: number, initialClosesAt = ''): PollProblems {
        const none: PollProblems = { question: null, options: null, closesAt: null };
        if (!values.enabled) return none;
        return {
            question: UtilPoll.questionProblem(values.question),
            options: UtilPoll.optionsProblem(values.options),
            closesAt: initialClosesAt && UtilPoll.sameInstant(values.closesAt, initialClosesAt) ? null : UtilPoll.closeProblem(values.closesAt, now),
        };
    }

    /**
     * 문제가 하나라도 있나.
     * @param {PollProblems} problems - 칸별 문제.
     * @returns {boolean} 있으면 true.
     */
    static hasProblem(problems: PollProblems): boolean {
        return Boolean(problems.question || problems.options || problems.closesAt);
    }

    static questionProblem(question: string): PollQuestionProblem | null {
        const length = UtilPoll.length(UtilPoll.cleanQuestion(question));
        if (length === 0) return 'REQUIRED';
        return length > UtilPoll.QUESTION_MAX ? 'TOO_LONG' : null;
    }

    static optionsProblem(options: readonly string[]): PollOptionsProblem | null {
        const filled = UtilPoll.filledOptions(options);
        if (filled.length < UtilPoll.OPTIONS_MIN) return 'TOO_FEW';
        if (filled.length > UtilPoll.OPTIONS_MAX) return 'TOO_MANY';
        if (filled.some((o) => UtilPoll.length(o) > UtilPoll.OPTION_MAX)) return 'TOO_LONG';
        return UtilPoll.duplicateIndexes(options).size > 0 ? 'DUPLICATE' : null;
    }

    static closeProblem(closesAt: string, now: number): PollCloseProblem | null {
        if (!closesAt) return null;
        const at = Date.parse(closesAt);
        if (Number.isNaN(at)) return 'INVALID';
        if (at < now + UtilPoll.CLOSE_MIN_LEAD_MS) return 'TOO_SOON';
        return at > now + UtilPoll.CLOSE_MAX_DAYS * 86_400_000 ? 'TOO_LATE' : null;
    }

    /**
     * 서버 칸 오류가 투표 칸의 것인가 — `poll` 또는 `poll.` 으로 시작하는 키.
     * @param {string} key - `fields` 의 키.
     * @returns {boolean} 투표 칸이면 true.
     */
    static isPollField(key: string): boolean {
        return key === 'poll' || key.startsWith('poll.');
    }

    /**
     * 투표 칸 오류를 뺀 서버 칸 오류 — 투표 칸을 고치면 낡은 투표 오류만 지운다(지울 것이 없으면 같은 객체).
     * @param {Record<string, string>} fields - 서버 `fields`.
     * @returns {Record<string, string>} 투표 칸 오류를 뺀 것.
     */
    static withoutPollFields(fields: Record<string, string>): Record<string, string> {
        if (!Object.keys(fields).some(UtilPoll.isPollField)) return fields;
        return Object.fromEntries(Object.entries(fields).filter(([k]) => !UtilPoll.isPollField(k)));
    }

    /**
     * 고른 선택지가 보낼 만한가 — 단일 = 정확히 1개, 복수 = 1개 이상.
     * @param {PollView} poll - 투표.
     * @param {string[]} selected - 고른 선택지 id.
     * @returns {boolean} 보낼 수 있으면 true.
     */
    static selectionOk(poll: PollView, selected: readonly string[]): boolean {
        const ids = UtilPoll.ordered(poll, selected);
        return poll.multiple ? ids.length >= 1 : ids.length === 1;
    }

    /**
     * 고른 선택지를 투표의 선택지 차례로(없어진 선택지는 뺀다).
     * @param {PollView} poll - 투표.
     * @param {string[]} selected - 고른 선택지 id.
     * @returns {string[]} 차례대로 고른 id.
     */
    static ordered(poll: PollView, selected: readonly string[]): string[] {
        const set = new Set(selected);
        return poll.options.filter((o) => set.has(o.id)).map((o) => o.id);
    }

    /**
     * 같은 선택인가(차례 무관).
     * @returns {boolean} 같으면 true.
     */
    static sameSelection(a: readonly string[], b: readonly string[]): boolean {
        if (a.length !== b.length) return false;
        const set = new Set(a);
        return b.every((id) => set.has(id));
    }

    /**
     * 결과 막대 — 결과를 볼 수 없으면 null. 퍼센트 = 득표 / 투표한 사람 수(반올림, 참여 0 이면 0).
     * @param {PollView} poll - 투표.
     * @returns {PollResultRow[] | null} 선택지 차례의 줄.
     */
    static rows(poll: PollView): PollResultRow[] | null {
        if (!poll.resultsVisible || !poll.results) return null;
        const votes = new Map(poll.results.map((r) => [r.optionId, r.votes]));
        const total = poll.voterCount ?? 0;
        const mine = new Set(poll.myOptionIds);
        return poll.options.map((option) => {
            const n = votes.get(option.id) ?? 0;
            return { option, votes: n, percent: total > 0 ? Math.round((n / total) * 100) : 0, mine: mine.has(option.id) };
        });
    }
}
