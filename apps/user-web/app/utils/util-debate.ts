import type { CommentDebate, DebateSettingsBody, DebateStance, DebateSummary, DebateVotes, OpinionKind, PostDebateOpen } from '~/composables/board/api/useDebateApi.types';
import { DEFAULT_DEBATE_SETTINGS, type DebateSettingsValues } from '~/components/board/DebateSettingsFields.types';

/** 비율 막대 한 칸 — 사람 수와 그 비율(0~100, 반올림) */
export interface DebateShare {
    stance: DebateStance;
    count: number;
    percent: number;
}

/**
 * 게시글 찬반 토론 화면의 판정·색·설정 값 — 순수 함수만(docs/features/popular-highlight-contract.md 「서버 구현 결과」 공개 사이트 토론, docs/features/post-debate-contract.md).
 * 색은 `main.css` 「토론 입장 색」 유틸(`stance-{pro,con,neutral}-{text,soft,solid}`)이다 — 늘 입장 글자와 함께 쓴다.
 */
export default class UtilDebate {
    static readonly STANCES: readonly DebateStance[] = ['PRO', 'CON', 'NEUTRAL'];
    static readonly EMPTY_VOTES: DebateVotes = { pro: 0, con: 0, neutral: 0, total: 0 };

    /**
     * 토론 구획을 보일까 — 지금 열렸거나 이미 표·의견이 있다(닫힌 토론은 읽기 전용).
     * @param {CommentDebate | DebateSummary | null | undefined} debate - 댓글 목록의 `debate`(의견 수가 있음) 또는 글 상세의 `debate`.
     * @returns {boolean} 보이면 true.
     */
    static visible(debate: CommentDebate | DebateSummary | null | undefined): boolean {
        if (!debate) return false;
        if (debate.enabled || (debate.votes?.total ?? 0) > 0) return true;
        return 'counts' in debate && debate.counts.opinions > 0;
    }

    /**
     * 중립 칸(투표 단추·칩·막대)을 보일까 — 토론이 중립을 받거나 이미 중립 표·의견이 있다.
     * @param {CommentDebate} debate - 댓글 목록의 `debate`.
     * @returns {boolean} 보이면 true.
     */
    static neutralVisible(debate: CommentDebate): boolean {
        return debate.neutralEnabled || debate.votes.neutral > 0 || debate.counts.neutral > 0;
    }

    /**
     * 보일 입장 차례 — 찬성 · 반대 (· 중립).
     * @param {boolean} withNeutral - 중립 칸을 보이는가.
     * @returns {DebateStance[]} 입장 목록.
     */
    static stances(withNeutral: boolean): DebateStance[] {
        return withNeutral ? [...UtilDebate.STANCES] : ['PRO', 'CON'];
    }

    /**
     * 입장 하나의 표 수.
     * @param {DebateVotes} votes - 투표 수.
     * @param {DebateStance} stance - 입장.
     * @returns {number} 그 입장을 고른 사람 수.
     */
    static voteOf(votes: DebateVotes, stance: DebateStance): number {
        if (stance === 'PRO') return votes.pro;
        if (stance === 'CON') return votes.con;
        return votes.neutral;
    }

    /**
     * 비율 막대 — 보이는 입장끼리 나눈 비율(표가 없으면 모두 0).
     * @param {DebateVotes} votes - 투표 수.
     * @param {DebateStance[]} stances - 보일 입장.
     * @returns {DebateShare[]} 입장별 수·비율.
     */
    static shares(votes: DebateVotes, stances: DebateStance[]): DebateShare[] {
        const total = stances.reduce((sum, s) => sum + UtilDebate.voteOf(votes, s), 0);
        return stances.map((stance) => {
            const count = UtilDebate.voteOf(votes, stance);
            return { stance, count, percent: total > 0 ? Math.round((count / total) * 100) : 0 };
        });
    }

    /**
     * 거르기 칩의 입장 — 의견 전체는 null.
     * @param {OpinionKind} kind - 칩.
     * @returns {DebateStance | null} 입장.
     */
    static kindStance(kind: OpinionKind): DebateStance | null {
        return kind === 'OPINION' ? null : kind;
    }

    /**
     * 입장을 바꿀 수 없는 토론인가(「입장 바꾸기 허용」 꺼짐). 옛 응답에 칸이 없으면 바꿀 수 있다고 본다.
     * @param {CommentDebate | DebateSummary | null | undefined} debate - 토론 요약.
     * @returns {boolean} 잠겼으면 true.
     */
    static stanceLocked(debate: CommentDebate | DebateSummary | null | undefined): boolean {
        return debate?.stanceChangeAllowed === false;
    }

    /**
     * 글의 토론 요약 → 설정 세 칸 값. 닫혀 있으면 서버가 두 설정을 true 로 준다(= 새로 열 때의 기본값).
     * @param {CommentDebate | DebateSummary | null | undefined} debate - 토론 요약.
     * @returns {DebateSettingsValues} 칸 값.
     */
    static settingsOf(debate: CommentDebate | DebateSummary | null | undefined): DebateSettingsValues {
        if (!debate) return { ...DEFAULT_DEBATE_SETTINGS };
        return { enabled: debate.enabled, neutralEnabled: debate.neutralEnabled ?? true, stanceChangeAllowed: debate.stanceChangeAllowed ?? true };
    }

    /**
     * 설정 두 값이 같은가 — 닫힌 채로 두는 동안의 두 설정은 뜻이 없어 비교하지 않는다.
     * @returns {boolean} 같으면 true.
     */
    static sameSettings(a: DebateSettingsValues, b: DebateSettingsValues): boolean {
        if (a.enabled !== b.enabled) return false;
        if (!a.enabled) return true;
        return a.neutralEnabled === b.neutralEnabled && a.stanceChangeAllowed === b.stanceChangeAllowed;
    }

    /**
     * 설정 칸 → `PUT /api/posts/{id}/debate` 본문.
     * @param {DebateSettingsValues} values - 칸 값.
     * @returns {DebateSettingsBody} 요청 본문.
     */
    static settingsBody(values: DebateSettingsValues): DebateSettingsBody {
        return { enabled: values.enabled, neutralEnabled: values.neutralEnabled, stanceChangeAllowed: values.stanceChangeAllowed };
    }

    /**
     * 글쓰기 칸 → `POST /api/posts` 본문의 `debate`(열지 않으면 null).
     * @param {DebateSettingsValues} values - 칸 값.
     * @returns {PostDebateOpen | null} 본문 칸.
     */
    static openBody(values: DebateSettingsValues): PostDebateOpen | null {
        return values.enabled ? { neutralEnabled: values.neutralEnabled, stanceChangeAllowed: values.stanceChangeAllowed } : null;
    }

    /**
     * 입장 글자색 유틸.
     * @param {DebateStance} stance - 입장.
     * @returns {string} 클래스.
     */
    static textClass(stance: DebateStance): string {
        if (stance === 'PRO') return 'stance-pro-text';
        if (stance === 'CON') return 'stance-con-text';
        return 'stance-neutral-text';
    }

    /**
     * 입장 옅은 칩(배지) 유틸.
     * @param {DebateStance} stance - 입장.
     * @returns {string} 클래스.
     */
    static softClass(stance: DebateStance): string {
        if (stance === 'PRO') return 'stance-pro-soft';
        if (stance === 'CON') return 'stance-con-soft';
        return 'stance-neutral-soft';
    }

    /**
     * 입장 채운 칩(고른 투표 단추·막대) 유틸.
     * @param {DebateStance} stance - 입장.
     * @returns {string} 클래스.
     */
    static solidClass(stance: DebateStance): string {
        if (stance === 'PRO') return 'stance-pro-solid';
        if (stance === 'CON') return 'stance-con-solid';
        return 'stance-neutral-solid';
    }
}
