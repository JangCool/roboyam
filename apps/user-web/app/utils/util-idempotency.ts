/**
 * 돈 요청의 멱등 키(`Idempotency-Key` 머리글) 만들기 — 포인트 상점 구매·환불. 상태도 요청도 없다.
 * 서버 규칙(`IdempotencyKeyInvalidException`): 영문·숫자·하이픈 8~64자. 같은 키로 다시 오면 서버가 처음 결과를 돌려준다(`Idempotent-Replayed: true`).
 * 키는 구매 한 번의 뜻(확인 대화상자 한 번)마다 하나 — 네트워크 실패는 같은 키로 다시 보내고, 서버가 판정해 거절(4xx)하면 새 키를 만든다(`usePointShopPurchase`).
 */

/** 서버가 받는 모양 */
const KEY_PATTERN = /^[A-Za-z0-9-]{8,64}$/;

export default class UtilIdempotency {
    /**
     * 새 멱등 키 — `crypto.randomUUID()`(보안 문맥에서만 있다)가 없으면 `crypto.getRandomValues`, 그것도 없으면 `Math.random` 으로 UUID 모양(36자)을 만든다.
     * @returns {string} 영문 소문자·숫자·하이픈 36자.
     */
    static newKey(): string {
        const c = typeof globalThis !== 'undefined' ? globalThis.crypto : undefined;
        if (c && typeof c.randomUUID === 'function') return c.randomUUID();
        const bytes = new Uint8Array(16);
        if (c && typeof c.getRandomValues === 'function') c.getRandomValues(bytes);
        else for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256);
        bytes[6] = (bytes[6]! & 0x0f) | 0x40;
        bytes[8] = (bytes[8]! & 0x3f) | 0x80;
        const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
        return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
    }

    /**
     * 서버가 받는 모양인가
     * @param {string} key - 키.
     * @returns {boolean} 영문·숫자·하이픈 8~64자이면 true.
     */
    static isValid(key: string): boolean {
        return KEY_PATTERN.test(key);
    }
}
