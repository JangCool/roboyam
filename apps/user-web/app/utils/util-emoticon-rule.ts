import UtilRichEmoticon from '#layers/rich-editor/utils/util-rich-emoticon';

/**
 * 글·댓글 본문의 로보티콘 토큰 규칙 — 서버 `EmoticonGuard` 후보와 **같은 규칙**(community `docs/api-emoticon.md` 「핵심 설계」·7절 5~7번, 계약 가정 10).
 * 화면의 미리 막기(댓글·글 폼 — `useEmoticonBodyCheck`)가 이 함수를 쓴다. 저장 때 판정은 서버 `EmoticonGuard`(같은 규칙). 순수 함수.
 *
 *  1. 개수: 정규식 `:emo-(\d{1,19}):` 로 **본문 전체 글자**(HTML 이면 정화된 HTML 전체 — 링크·코드 안도)에서 센다. 같은 토큰을 여러 번 써도, 없는 ID 여도,
 *     long 범위를 넘는 19자리 ID 여도 센다. 21개부터 `400 VALIDATION`(운영진·최고 관리자도 같다).
 *  2. 자격: 본문 순서로 토큰을 보며 **ID 로 읽히고(long 범위) 카탈로그에 있는** 로보티콘이 그 작성자가 가진 팩의 것이 아니면 `409 EMOTICON_NOT_OWNED`(처음 걸린 ID).
 *     없는 ID·범위를 넘는 ID 는 그냥 글자라 오류가 아니다. 운영진·최고 관리자(`unlimited`)는 자격을 보지 않는다.
 *     운영자 회원 계정(operator-member-account-contract 15 — 서버 `EmoticonGuard` 의 운영자 회원 계정 판정)은 **판매 중** 팩을 가진 것으로 본다(`operatorOnSale`).
 *     판매 중단 팩은 일반 규칙(실제로 가졌는가) 그대로이고, 개수 상한도 같다.
 *  순서는 개수(400) → 자격(409).
 */

/** `long` 최댓값 — 이보다 큰 19자리 숫자는 ID 로 읽지 않는다 */
const LONG_MAX = 9_223_372_036_854_775_807n;

/** 본문 검사 결과 */
export type EmoticonBodyProblem = { code: 'TOO_MANY'; count: number; max: number } | { code: 'NOT_OWNED'; emoticonId: string };

/** 검사에 쓰는 사실들 — 서버는 DB 에서, 화면은 카탈로그·내 팩에서 */
export interface EmoticonBodyContext {
    /** 이모티콘 ID → 팩 ID(카탈로그에 없으면 null) */
    packOf: (emoticonId: string) => string | null;
    /** 작성자가 그 팩을 가졌는가 */
    owns: (packId: string) => boolean;
    /** 운영진·최고 관리자 — 자격을 보지 않는다(개수 제한은 같다) */
    unlimited: boolean;
    /** 운영자 회원 계정이면 「판매 중 팩인가」 — 참이면 가진 것으로 본다. 운영자 회원 계정이 아니면 생략(null) */
    operatorOnSale?: ((packId: string) => boolean) | null;
}

export default class UtilEmoticonRule {
    /** 본문 하나의 토큰 상한 */
    static readonly MAX = UtilRichEmoticon.MAX_PER_BODY;

    /**
     * 토큰 글자의 숫자가 ID 로 읽히는가(1~19자리 + long 범위).
     * @param {string} digits - 토큰 안의 숫자.
     * @returns {boolean} ID 로 읽히면 참.
     */
    static isId(digits: string): boolean {
        return /^\d{1,19}$/.test(digits) && BigInt(digits) <= LONG_MAX;
    }

    /**
     * 본문을 검사한다.
     * @param {string | null | undefined} content - 저장할 본문(평문 또는 HTML).
     * @param {EmoticonBodyContext} ctx - 카탈로그·보유·운영진 여부.
     * @returns {EmoticonBodyProblem | null} 처음 걸린 문제(없으면 null).
     */
    static check(content: string | null | undefined, ctx: EmoticonBodyContext): EmoticonBodyProblem | null {
        const count = UtilRichEmoticon.count(content);
        if (count === 0) return null;
        if (count > UtilEmoticonRule.MAX) return { code: 'TOO_MANY', count, max: UtilEmoticonRule.MAX };
        if (ctx.unlimited) return null;
        for (const id of UtilRichEmoticon.ids(content)) {
            if (!UtilEmoticonRule.isId(id)) continue;
            const packId = ctx.packOf(id);
            if (packId === null || ctx.owns(packId) || ctx.operatorOnSale?.(packId)) continue;
            return { code: 'NOT_OWNED', emoticonId: id };
        }
        return null;
    }

    /**
     * 본문에 든 로보티콘들의 팩 — 저장 뒤 「사용함」을 찍을 때(카탈로그에 있는 ID 만, 겹치면 한 번).
     * @param {string | null | undefined} content - 저장한 본문.
     * @param {(emoticonId: string) => string | null} packOf - ID → 팩.
     * @returns {string[]} 팩 ID 들(본문 순서).
     */
    static packsUsed(content: string | null | undefined, packOf: (emoticonId: string) => string | null): string[] {
        const out: string[] = [];
        for (const id of UtilRichEmoticon.ids(content)) {
            if (!UtilEmoticonRule.isId(id)) continue;
            const packId = packOf(id);
            if (packId !== null && !out.includes(packId)) out.push(packId);
        }
        return out;
    }
}
