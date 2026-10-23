/**
 * 검색어 규칙 — 서버 검색 규칙(community `docs/api-board.md`「검색 (2-4)」, 후보 서버 클래스 `SearchQueryPolicy`)을 화면이 미리 검사하고, 결과의 일치 부분을 나눈다. 상태도 요청도 없다.
 * 판정은 서버다 — 여기서 걸러도 서버가 `400 VALIDATION fields.q` 로 다시 알려 준다.
 *  - 앞뒤·이어진 공백은 하나로, 전체 2~50자, 단어마다 2글자 이상, 단어는 5개까지(넘는 것·중복은 버린다). 단어는 모두 들어 있어야 한다(AND, 대소문자 무시).
 */

/** 검색어가 걸리는 까닭 — 너무 짧다 · 너무 길다 · 2글자보다 짧은 단어가 있다 */
export type SearchProblem = 'TOO_SHORT' | 'TOO_LONG' | 'SHORT_WORD';

/** 일치 강조용 조각 — `match` 면 굵게 */
export interface HighlightPart {
    text: string;
    match: boolean;
}

export default class UtilSearchRules {
    static readonly MIN_TOKEN = 2;
    static readonly MAX_QUERY = 50;
    static readonly MAX_TOKENS = 5;

    /**
     * 글자 수 — 유니코드 한 글자씩
     * @param {string} text - 글자.
     * @returns {number} 글자 수.
     */
    static length(text: string): number {
        return Array.from(text).length;
    }

    /**
     * 검색어 다듬기 — 널 문자를 빼고, 이어진 공백을 하나로, 앞뒤 공백을 뗀다
     * @param {string} raw - 입력한 글자.
     * @returns {string} 다듬은 검색어.
     */
    static normalize(raw: string): string {
        // eslint-disable-next-line no-control-regex
        return raw.replace(/\u0000/g, '').replace(/\s+/g, ' ').trim();
    }

    /**
     * 검색어의 단어들(중복·5개 초과는 버린다)
     * @param {string} raw - 검색어.
     * @returns {string[]} 단어들.
     */
    static distinctWords(raw: string): string[] {
        const n = UtilSearchRules.normalize(raw);
        const out: string[] = [];
        for (const w of n ? n.split(' ') : []) {
            if (!out.includes(w) && out.length < UtilSearchRules.MAX_TOKENS) out.push(w);
        }
        return out;
    }

    /**
     * 검색어를 보낼 수 있는가
     * @param {string} raw - 검색어.
     * @returns {SearchProblem | null} 걸리는 까닭. 보낼 수 있으면 null.
     */
    static check(raw: string): SearchProblem | null {
        const n = UtilSearchRules.normalize(raw);
        const total = UtilSearchRules.length(n);
        if (total < UtilSearchRules.MIN_TOKEN) return 'TOO_SHORT';
        if (total > UtilSearchRules.MAX_QUERY) return 'TOO_LONG';
        if (n.split(' ').some((w) => UtilSearchRules.length(w) < UtilSearchRules.MIN_TOKEN)) return 'SHORT_WORD';
        return null;
    }

    /**
     * 글자에 단어가 모두 들어 있는가(대소문자 무시)
     * @param {string} text - 글자(제목·닉네임 등).
     * @param {readonly string[]} tokens - 단어들.
     * @returns {boolean} 모두 들어 있으면 true. 단어가 없으면 true.
     */
    static containsAll(text: string, tokens: readonly string[]): boolean {
        const lower = text.toLowerCase();
        return tokens.every((token) => lower.includes(token.toLowerCase()));
    }

    /**
     * 일치 강조 — 글자를 단어가 걸린 조각과 아닌 조각으로 나눈다(대소문자 무시, 긴 단어 먼저). 화면은 글자로만 그린다(HTML 로 해석하지 않는다)
     * @param {string} text - 글자.
     * @param {readonly string[]} tokens - 강조할 단어들(서버 응답의 `tokens`).
     * @returns {HighlightPart[]} 조각들. 단어가 없으면 글자 하나.
     */
    static highlightParts(text: string, tokens: readonly string[]): HighlightPart[] {
        const words = [...new Set(tokens.filter((w) => w.length > 0))].sort((a, b) => b.length - a.length);
        if (!text || words.length === 0) return [{ text, match: false }];
        const escaped = words.map((w) => w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
        const parts: HighlightPart[] = [];
        let last = 0;
        for (const m of text.matchAll(new RegExp(escaped.join('|'), 'gi'))) {
            const at = m.index ?? 0;
            if (at > last) parts.push({ text: text.slice(last, at), match: false });
            parts.push({ text: m[0], match: true });
            last = at + m[0].length;
        }
        if (last < text.length) parts.push({ text: text.slice(last), match: false });
        return parts.length ? parts : [{ text, match: false }];
    }
}
