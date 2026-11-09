import type { PostFeedDebate } from '~/composables/board/api/usePostApi.types';
import type { DebateStance } from '~/composables/board/api/useDebateApi.types';

type Translate = (key: string, named?: Record<string, unknown>) => string;

/** 입장 한 칸 — 입장과 사람 수 */
export interface FeedDebatePart {
    stance: DebateStance;
    count: number;
}

/**
 * 섞인 목록 줄·하이라이트의 찬반 토론 수(서버 `PostFeedDebateView`, feed-debate-counts-contract) — 순수 함수만.
 * 중립을 받지 않는 토론(`neutral` null)은 찬·반만 다룬다.
 */
export default class UtilFeedDebate {
    /** 작은 띠에서 줄여 쓰는 한도 — 이보다 크면 「99+」 */
    static readonly CAP = 99;

    /**
     * 보일 칸 — 찬성 · 반대 (· 중립).
     * @param {PostFeedDebate} debate - 토론 수.
     * @returns {FeedDebatePart[]} 칸.
     */
    static parts(debate: PostFeedDebate): FeedDebatePart[] {
        const out: FeedDebatePart[] = [
            { stance: 'PRO', count: debate.pro },
            { stance: 'CON', count: debate.con },
        ];
        if (debate.neutral !== null && debate.neutral !== undefined) out.push({ stance: 'NEUTRAL', count: debate.neutral });
        return out;
    }

    /**
     * 작은 띠의 숫자 — 99 를 넘으면 「99+」.
     * @param {number} n - 사람 수.
     * @returns {string} 글자.
     */
    static capped(n: number): string {
        return n > UtilFeedDebate.CAP ? `${UtilFeedDebate.CAP}+` : String(n);
    }

    /**
     * 화면 읽기 문장 — 「찬성 4, 반대 9, 중립 2」(중립을 받지 않으면 찬·반만).
     * @param {PostFeedDebate} debate - 토론 수.
     * @param {Translate} t - 번역 함수.
     * @returns {string} 문장.
     */
    static summary(debate: PostFeedDebate, t: Translate): string {
        const named = { pro: debate.pro.toLocaleString(), con: debate.con.toLocaleString(), neutral: (debate.neutral ?? 0).toLocaleString() };
        return t(debate.neutral === null || debate.neutral === undefined ? 'feed.debateSummaryNoNeutral' : 'feed.debateSummary', named);
    }

    /**
     * 툴팁 문장 — 「찬성 128 · 반대 97 · 중립 15」(전체 수).
     * @param {PostFeedDebate} debate - 토론 수.
     * @param {Translate} t - 번역 함수.
     * @returns {string} 문장.
     */
    static tip(debate: PostFeedDebate, t: Translate): string {
        const named = { pro: debate.pro.toLocaleString(), con: debate.con.toLocaleString(), neutral: (debate.neutral ?? 0).toLocaleString() };
        return t(debate.neutral === null || debate.neutral === undefined ? 'feed.debateTipNoNeutral' : 'feed.debateTip', named);
    }
}
