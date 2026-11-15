/**
 * 글 태그 글자 규칙(post-tags-contract 「필드·검증」) — 서버와 같은 규칙으로 미리 바로잡는다(판정은 서버). 상태도 요청도 없다.
 *  - 앞뒤 공백 제거 · 안쪽 공백은 하나로 · 앞의 `#` 떼기 · 1~20자 · 줄바꿈·제어 문자·`#`·`,` 금지
 *  - 같은지 비교는 영문 대소문자 무시(`key`) — 저장은 처음 쓴 모양 · 글당 최대 5개 · 겹치면 하나
 * 가젯 「태그 탭」(`tagTabs`, 최대 8)도 같은 글자 규칙이다. admin-web 에 같은 규칙(`UtilTag`)이 따로 있다 — 바꿀 때 함께 바꾼다.
 */

/** 넣기 실패 까닭 — 너무 길다 · 쓸 수 없는 글자 · 다 찼다 */
export type PostTagProblem = 'TOO_LONG' | 'INVALID' | 'TOO_MANY';

// eslint-disable-next-line no-control-regex
const FORBIDDEN = /[\u0000-\u001f\u007f#,]/;

export default class UtilPostTag {
    /** 글당 최대 태그 수 */
    static readonly MAX = 5;
    /** 태그 한 개 최대 글자 수 */
    static readonly LEN_MAX = 20;
    /** 가젯 태그 탭 최대 수 */
    static readonly TAB_MAX = 8;

    /**
     * 한 태그 바로잡기 — 쓸 수 없으면 까닭
     * @param {string} raw - 입력한 글자.
     * @returns {{ tag: string } | { problem: PostTagProblem } | null} 바로잡은 태그 · 까닭 · 비었으면 null.
     */
    static check(raw: string): { tag: string } | { problem: PostTagProblem } | null {
        const text = raw.replace(/^[\s#]+/, '').replace(/\s+/g, ' ').trim();
        if (!text) return null;
        if (FORBIDDEN.test(text)) return { problem: 'INVALID' };
        if ([...text].length > UtilPostTag.LEN_MAX) return { problem: 'TOO_LONG' };
        return { tag: text };
    }

    /**
     * 한 태그 바로잡기 — 쓸 수 없으면 null
     * @param {unknown} raw - 글자.
     * @returns {string | null} 태그.
     */
    static normalize(raw: unknown): string | null {
        if (typeof raw !== 'string') return null;
        const r = UtilPostTag.check(raw);
        return r && 'tag' in r ? r.tag : null;
    }

    /**
     * 비교 키 — 영문 소문자
     * @param {string} tag - 태그.
     * @returns {string} 키.
     */
    static key(tag: string): string {
        return tag.toLowerCase();
    }

    /**
     * 받은 목록 읽기 — 쓸 수 없는 값은 빼고 겹침 없이 `max` 개까지
     * @param {unknown} value - 서버·설정의 값.
     * @param {number} max - 최대 수.
     * @returns {string[]} 태그.
     */
    static list(value: unknown, max: number = UtilPostTag.MAX): string[] {
        if (!Array.isArray(value)) return [];
        const out: string[] = [];
        const seen = new Set<string>();
        for (const raw of value) {
            const tag = UtilPostTag.normalize(raw);
            if (!tag || seen.has(UtilPostTag.key(tag))) continue;
            seen.add(UtilPostTag.key(tag));
            out.push(tag);
            if (out.length >= max) break;
        }
        return out;
    }

    /**
     * 목록에 하나 넣기 — 겹치면 그대로(문제 아님), 다 찼거나 쓸 수 없으면 까닭
     * @param {readonly string[]} list - 지금 태그.
     * @param {string} raw - 넣을 글자.
     * @param {number} max - 최대 수.
     * @returns {{ list: string[]; problem: PostTagProblem | null }} 새 목록과 까닭.
     */
    static add(list: readonly string[], raw: string, max: number = UtilPostTag.MAX): { list: string[]; problem: PostTagProblem | null } {
        const r = UtilPostTag.check(raw);
        if (!r) return { list: [...list], problem: null };
        if ('problem' in r) return { list: [...list], problem: r.problem };
        if (UtilPostTag.has(list, r.tag)) return { list: [...list], problem: null };
        if (list.length >= max) return { list: [...list], problem: 'TOO_MANY' };
        return { list: [...list, r.tag], problem: null };
    }

    /**
     * 목록에 같은 태그(대소문자 무시)가 있나
     * @param {readonly string[]} list - 태그.
     * @param {string} tag - 찾을 태그.
     * @returns {boolean} 있으면 true.
     */
    static has(list: readonly string[], tag: string): boolean {
        const k = UtilPostTag.key(tag);
        return list.some((x) => UtilPostTag.key(x) === k);
    }

    /**
     * 서버 입력 오류(`400 VALIDATION` `fields`)의 태그 칸 문구 — `tags` 또는 `tags[0]` 꼴의 첫 문구
     * @param {Record<string, string>} fields - 칸 오류.
     * @returns {string} 문구(없으면 빈 글자).
     */
    static fieldError(fields: Record<string, string>): string {
        if (fields.tags) return fields.tags;
        const key = Object.keys(fields).find((k) => /^tags\[\d+\]$/.test(k));
        return key ? (fields[key] ?? '') : '';
    }

    /**
     * 태그 칸 오류를 뺀 칸 오류
     * @param {Record<string, string>} fields - 칸 오류.
     * @returns {Record<string, string>} 새 칸 오류.
     */
    static withoutFieldErrors(fields: Record<string, string>): Record<string, string> {
        return Object.fromEntries(Object.entries(fields).filter(([k]) => k !== 'tags' && !/^tags\[\d+\]$/.test(k)));
    }

    /**
     * 두 목록이 같은가(차례 포함, 대소문자는 그대로 비교 — 고친 모양도 바뀜)
     * @param {readonly string[]} a - 목록.
     * @param {readonly string[]} b - 목록.
     * @returns {boolean} 같으면 true.
     */
    static same(a: readonly string[], b: readonly string[]): boolean {
        return a.length === b.length && a.every((x, i) => x === b[i]);
    }
}
