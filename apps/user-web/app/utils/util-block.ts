import type { Role } from '~/composables/auth/api/useAuthApi.types';
import type { BlockedBy } from '~/composables/block/api/useBlockApi.types';

/** 한 줄이 아닌 글자 — 줄바꿈 같은 제어 문자(C0·C1) */
// eslint-disable-next-line no-control-regex
const CONTROL_CHARS = /[\u0000-\u001f\u007f-\u009f]/;

/** 차단 단어 입력의 문제 — `EMPTY` 는 아직 아무것도 안 쓴 것이라 오류로 보이지 않는다 */
export type WordProblem = 'EMPTY' | 'TOO_SHORT' | 'TOO_LONG' | 'MULTILINE';

/** 이용자 메모 입력의 문제 */
export type MemoProblem = 'TOO_LONG' | 'MULTILINE';

/**
 * 차단·이용자 메모의 순수 규칙 — 차단할 수 있는 대상, 이 기기에서 방금 바꾼 것을 서버 표시에 겹치는 규칙, 차단 단어·메모의 입력 규칙.
 * 상태도 요청도 없다. 입력 규칙은 서버 후보(`BlockPolicy`·`MemoPolicy` — community `docs/api-block-scrap.md`)와 같고 판정은 늘 서버가 한다.
 * 문구는 `UtilBlockMessages`.
 */
export default class UtilBlock {
    /** 차단 단어 최소·최대 글자 수와 개수의 기본값 — 서버 `GET /api/me/blocks/words` 의 `minLength`·`maxLength`·`limit` 이 오면 그것을 쓴다 */
    static readonly WORD_MIN = 2;
    static readonly WORD_MAX = 20;
    static readonly WORD_LIMIT = 50;

    /** 메모 최대 글자 수 — 닉네임 옆 좁은 자리를 생각한 값 */
    static readonly MEMO_MAX = 20;

    /** 차단·해제 완료 토스트가 떠 있는 시간(ms) — 「실행 취소」를 누를 틈 */
    static readonly UNDO_LIFE_MS = 6000;

    /**
     * 이 회원을 차단할 수 있는가 — 운영진·운영자가 아니면 된다(서버도 `403 BLOCK_TARGET_PROTECTED`).
     * @param {Role | null | undefined} role - 대상 회원의 역할.
     * @returns {boolean} 운영진(`STAFF`)·운영자(`ADMIN`)가 아니면 true.
     */
    static canBlock(role: Role | null | undefined): boolean {
        return role !== 'STAFF' && role !== 'ADMIN';
    }

    /**
     * 서버가 준 차단 표시에 **이 기기에서 방금 바꾼 것**을 겹친다 — 방금 차단했으면 `USER`, 방금 풀었으면 서버의 `USER` 만 걷어 낸다(`WORD` 는 그대로).
     * @param {BlockedBy | null} server - 서버가 준 차단 표시.
     * @param {boolean | undefined} override - 이 기기에서 방금 바꾼 닉네임 차단 여부(바꾼 적 없으면 undefined).
     * @returns {BlockedBy | null} 화면이 따를 차단 표시.
     */
    static resolve(server: BlockedBy | null, override: boolean | undefined): BlockedBy | null {
        if (override === true) return 'USER';
        if (override === false) return server === 'USER' ? null : server;
        return server;
    }

    /**
     * 서버에 보낼 단어 — 앞뒤 공백을 자르고 속의 연속 공백은 한 칸으로.
     * @param {string} text - 입력한 단어.
     * @returns {string} 정리한 단어.
     */
    static normalizeWord(text: string): string {
        return text.trim().replace(/\s+/g, ' ');
    }

    /**
     * 차단 단어가 문제 없으면 null.
     * @param {string} text - 입력한 단어.
     * @param {number} min - 최소 글자 수.
     * @param {number} max - 최대 글자 수.
     * @returns {WordProblem | null} 문제.
     */
    static wordProblem(text: string, min: number = UtilBlock.WORD_MIN, max: number = UtilBlock.WORD_MAX): WordProblem | null {
        const word = UtilBlock.normalizeWord(text);
        if (word === '') return 'EMPTY';
        if (CONTROL_CHARS.test(word)) return 'MULTILINE';
        const length = Array.from(word).length;
        if (length < min) return 'TOO_SHORT';
        if (length > max) return 'TOO_LONG';
        return null;
    }

    /**
     * 서버에 보낼 메모 — 앞뒤 공백을 자른다(빈 문자열이면 서버가 메모를 지운다).
     * @param {string} text - 입력한 메모.
     * @returns {string} 보낼 메모.
     */
    static memoToSend(text: string): string {
        return text.trim();
    }

    /**
     * 메모의 글자 수 — 앞뒤 공백을 떼고 유니코드 한 글자씩(서버도 이렇게 센다).
     * @param {string} text - 입력한 메모.
     * @returns {number} 글자 수.
     */
    static memoLength(text: string): number {
        return Array.from(UtilBlock.memoToSend(text)).length;
    }

    /**
     * 메모가 문제 없으면 null(빈 메모는 지우는 것이라 문제가 아니다).
     * @param {string} text - 입력한 메모.
     * @param {number} max - 최대 글자 수.
     * @returns {MemoProblem | null} 문제.
     */
    static memoProblem(text: string, max: number = UtilBlock.MEMO_MAX): MemoProblem | null {
        const memo = UtilBlock.memoToSend(text);
        if (Array.from(memo).length > max) return 'TOO_LONG';
        if (CONTROL_CHARS.test(memo)) return 'MULTILINE';
        return null;
    }
}
