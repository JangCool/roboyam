/**
 * 글 태그 글자 규칙(post-tags-contract 「필드·검증」) — 관리자 가젯 ⚙ 「태그 탭」이 미리 바로잡는다(판정은 서버). 상태도 요청도 없다.
 *  - 앞뒤 공백 제거 · 안쪽 공백은 하나로 · 앞의 `#` 떼기 · 1~20자 · 줄바꿈·제어 문자·`#`·`,` 금지
 *  - 같은지 비교는 영문 대소문자 무시(`key`) — 저장은 처음 쓴 모양 · 탭은 최대 8개 · 겹치면 하나
 * user-web 에 같은 규칙(`UtilPostTag`)이 따로 있다 — 바꿀 때 함께 바꾼다.
 */

/** 넣기 실패 까닭 — 너무 길다 · 쓸 수 없는 글자 · 다 찼다 */
export type TagProblem = 'TOO_LONG' | 'INVALID' | 'TOO_MANY';

// eslint-disable-next-line no-control-regex
const FORBIDDEN = /[\u0000-\u001f\u007f#,]/;

export default class UtilTag {
    /** 태그 한 개 최대 글자 수 */
    static readonly LEN_MAX = 20;
    /** 가젯 태그 탭 최대 수 */
    static readonly TAB_MAX = 8;

    /**
     * 한 태그 바로잡기 — 쓸 수 없으면 까닭
     * @param {string} raw - 입력한 글자.
     * @returns {{ tag: string } | { problem: TagProblem } | null} 바로잡은 태그 · 까닭 · 비었으면 null.
     */
    static check(raw: string): { tag: string } | { problem: TagProblem } | null {
        const text = raw.replace(/^[\s#]+/, '').replace(/\s+/g, ' ').trim();
        if (!text) return null;
        if (FORBIDDEN.test(text)) return { problem: 'INVALID' };
        if ([...text].length > UtilTag.LEN_MAX) return { problem: 'TOO_LONG' };
        return { tag: text };
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
     * @param {unknown} value - 설정의 값.
     * @param {number} max - 최대 수.
     * @returns {string[]} 태그.
     */
    static list(value: unknown, max: number = UtilTag.TAB_MAX): string[] {
        if (!Array.isArray(value)) return [];
        const out: string[] = [];
        for (const raw of value) {
            if (typeof raw !== 'string') continue;
            const r = UtilTag.check(raw);
            if (!r || !('tag' in r) || UtilTag.has(out, r.tag)) continue;
            out.push(r.tag);
            if (out.length >= max) break;
        }
        return out;
    }

    /**
     * 목록에 같은 태그(대소문자 무시)가 있나
     * @param {readonly string[]} list - 태그.
     * @param {string} tag - 찾을 태그.
     * @returns {boolean} 있으면 true.
     */
    static has(list: readonly string[], tag: string): boolean {
        const k = UtilTag.key(tag);
        return list.some((x) => UtilTag.key(x) === k);
    }

    /**
     * 목록에 하나 넣기 — 겹치면 그대로(문제 아님), 다 찼거나 쓸 수 없으면 까닭
     * @param {readonly string[]} list - 지금 태그.
     * @param {string} raw - 넣을 글자.
     * @param {number} max - 최대 수.
     * @returns {{ list: string[]; problem: TagProblem | null }} 새 목록과 까닭.
     */
    static add(list: readonly string[], raw: string, max: number = UtilTag.TAB_MAX): { list: string[]; problem: TagProblem | null } {
        const r = UtilTag.check(raw);
        if (!r) return { list: [...list], problem: null };
        if ('problem' in r) return { list: [...list], problem: r.problem };
        if (UtilTag.has(list, r.tag)) return { list: [...list], problem: null };
        if (list.length >= max) return { list: [...list], problem: 'TOO_MANY' };
        return { list: [...list, r.tag], problem: null };
    }

    /**
     * 한 칸 옮기기(위 -1 · 아래 +1) — 끝을 넘으면 그대로
     * @param {readonly string[]} list - 태그.
     * @param {number} i - 옮길 차례.
     * @param {-1 | 1} step - 방향.
     * @returns {string[]} 새 목록.
     */
    static move(list: readonly string[], i: number, step: -1 | 1): string[] {
        const j = i + step;
        if (i < 0 || j < 0 || i >= list.length || j >= list.length) return [...list];
        const out = [...list];
        [out[i], out[j]] = [out[j]!, out[i]!];
        return out;
    }
}
