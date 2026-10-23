/*
 * 쪽지 입력 규칙 — 본문 다듬기·글자 수·받는 사람 검색어·주소의 `to`. 서버 후보 `DmBodies.normalize`(본문)·`GET /api/dm/recipients`(검색어 2~16자)와
 * 같은 규칙이다(community `docs/api-dm.md`). 화면이 미리 알려 주는 것일 뿐 **판정은 늘 서버가 한다**. 문구는 `UtilDmMessages`.
 */

/** 본문을 보낼 수 없는 까닭 */
export type DmBodyProblem = 'EMPTY' | 'TOO_LONG' | 'INVALID_CHAR';

/** 받는 사람 검색어를 찾을 수 없는 까닭 */
export type DmSearchProblem = 'TOO_SHORT' | 'TOO_LONG';

/** 줄바꿈(`\n`)만 빼고 서버가 거절하는 제어 문자 — C0·DEL·C1 과 글자 방향을 뒤집는 문자 */
// eslint-disable-next-line no-control-regex
const INVALID_CHAR = /[\u0000-\u0009\u000B-\u001F\u007F-\u009F‪-‮⁦-⁩]/;

/** 회원 ID — Snowflake 십진 문자열 */
const USER_ID = /^[1-9]\d{0,19}$/;

export default class UtilDmRules {
    /** 쪽지 한 통의 최대 글자 수(서버 `rules.bodyMax`) */
    static readonly BODY_MAX = 500;
    /** 대화방 입력창에 글자 수가 보이기 시작하는 수 */
    static readonly COUNTER_FROM = 450;
    /** 쪽지를 보낼 수 있게 되는 가입 후 일수(서버 `rules.minAccountAgeDays`) */
    static readonly MIN_ACCOUNT_AGE_DAYS = 7;
    /** 받는 사람 검색어 글자 수 */
    static readonly SEARCH_MIN = 2;
    static readonly SEARCH_MAX = 16;

    /**
     * 서버가 다듬는 것과 같게 — `\r\n`·`\r` 은 `\n`, 탭은 공백, 앞뒤 공백은 자른다.
     * @param {string | null | undefined} raw - 입력.
     * @returns {string} 보낼 글자.
     */
    static normalize(raw: string | null | undefined): string {
        return (raw ?? '').replace(/\r\n/g, '\n').replace(/\r/g, '\n').replace(/\t/g, ' ').trim();
    }

    /**
     * 글자 수 — 유니코드 글자(코드 포인트) 단위.
     * @param {string | null | undefined} text - 글자.
     * @returns {number} 수.
     */
    static length(text: string | null | undefined): number {
        return Array.from(text ?? '').length;
    }

    /**
     * 다듬은 본문의 글자 수(카운터).
     * @param {string | null | undefined} raw - 입력.
     * @returns {number} 수.
     */
    static count(raw: string | null | undefined): number {
        return UtilDmRules.length(UtilDmRules.normalize(raw));
    }

    /**
     * 본문을 보낼 수 있는지 — 비었음 → 글자 수 → 쓸 수 없는 문자 차례.
     * @param {string | null | undefined} raw - 입력.
     * @param {number} max - 최대 글자 수.
     * @returns {DmBodyProblem | null} 문제.
     */
    static bodyProblem(raw: string | null | undefined, max: number = UtilDmRules.BODY_MAX): DmBodyProblem | null {
        const body = UtilDmRules.normalize(raw);
        if (body === '') return 'EMPTY';
        if (UtilDmRules.length(body) > max) return 'TOO_LONG';
        if (INVALID_CHAR.test(body)) return 'INVALID_CHAR';
        return null;
    }

    /**
     * 글자 수 카운터를 보일 때인가 — 450자부터.
     * @param {number} count - 다듬은 글자 수.
     * @param {number} max - 최대 글자 수.
     * @returns {boolean} 보여야 하면 true.
     */
    static counterVisible(count: number, max: number = UtilDmRules.BODY_MAX): boolean {
        return count >= max - (UtilDmRules.BODY_MAX - UtilDmRules.COUNTER_FROM);
    }

    /**
     * 받는 사람 검색어를 다듬는다 — 앞뒤 공백 자르기.
     * @param {string | null | undefined} raw - 입력.
     * @returns {string} 검색어.
     */
    static normalizeQuery(raw: string | null | undefined): string {
        return (raw ?? '').trim();
    }

    /**
     * 검색어를 보낼 수 있는지 — 2~16자.
     * @param {string | null | undefined} raw - 입력.
     * @returns {DmSearchProblem | null} 문제.
     */
    static searchProblem(raw: string | null | undefined): DmSearchProblem | null {
        const n = UtilDmRules.length(UtilDmRules.normalizeQuery(raw));
        if (n < UtilDmRules.SEARCH_MIN) return 'TOO_SHORT';
        if (n > UtilDmRules.SEARCH_MAX) return 'TOO_LONG';
        return null;
    }

    /**
     * 주소의 `?to=`(받는 사람 회원 ID)를 검증한다 — 문자열 그대로.
     * @param {unknown} value - 쿼리 값.
     * @returns {string | null} 회원 ID.
     */
    static parseTo(value: unknown): string | null {
        const raw = Array.isArray(value) ? value[0] : value;
        return typeof raw === 'string' && USER_ID.test(raw) ? raw : null;
    }
}
