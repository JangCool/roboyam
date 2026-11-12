import type { Language } from '#layers/i18n/utils/util-i18n';

/**
 * 사이트 로고 4칸(언어 × 바탕) — 어느 칸의 그림을 쓸지 고르는 규칙 한 곳(사용자 결정 2026-11-06: 그림 로고 「아이로빗 아바타 머리 + 글자」, 언어·바탕마다 다른 로고).
 * 상태도 요청도 없다. ⚠ admin-web `utils/util-site-logo.ts` 와 같은 규칙이다 — 한쪽을 고치면 다른 쪽도 고친다(API 확정 때 공유 레이어로 옮길 후보).
 *
 * 칸: ① `koLight` 한글·밝은 바탕용 · ② `koDark` 한글·어두운 바탕용 · ③ `enLight` 영문·밝은 바탕용 · ④ `enDark` 영문·어두운 바탕용.
 * 대체 순서(빈 칸·못 불러온 그림은 건너뛴다): 영문이 비면 같은 바탕의 한글 → 어두운 바탕용이 비면 같은 언어의 밝은 바탕용 → 그래도 없으면 ① → ①도 없으면 이름 글자만(null).
 */
export type SiteLogoVariant = 'koLight' | 'koDark' | 'enLight' | 'enDark';

/** 로고가 놓이는 면의 바탕 */
export type SiteLogoTone = 'light' | 'dark';

/** 4칸의 그림 주소 — 상대 주소·`http(s)` 주소·이미지 data URI(그대로 `<img src>`, `v-html` 금지). 비어 있으면 null */
export type SiteLogoSet = Record<SiteLogoVariant, string | null>;

/** 고른 결과 — 실제로 쓰는 칸과 그 주소 */
export interface SiteLogoPick {
    variant: SiteLogoVariant;
    url: string;
}

/** 칸마다 찾는 순서(자기 칸이 맨 앞). ④는 바탕을 언어보다 먼저 맞춘다 — 밝은 바탕용 로고는 어두운 바 위에서 안 보일 수 있다 */
const CHAIN: Record<SiteLogoVariant, readonly SiteLogoVariant[]> = {
    koLight: ['koLight'],
    koDark: ['koDark', 'koLight'],
    enLight: ['enLight', 'koLight'],
    enDark: ['enDark', 'koDark', 'enLight', 'koLight'],
};

export default class UtilSiteLogo {
    /** 칸 차례(①~④) */
    static readonly VARIANTS: readonly SiteLogoVariant[] = ['koLight', 'koDark', 'enLight', 'enDark'];

    /** 네 칸이 모두 빈 값 */
    static readonly EMPTY: SiteLogoSet = { koLight: null, koDark: null, enLight: null, enDark: null };

    /**
     * 언어와 바탕으로 칸을 정한다.
     * @param {Language} language - 화면 언어(`en` 이 아니면 한글 칸).
     * @param {SiteLogoTone} tone - 놓이는 면의 바탕.
     * @returns {SiteLogoVariant} 칸.
     */
    static variantOf(language: Language, tone: SiteLogoTone): SiteLogoVariant {
        if (language === 'en') return tone === 'dark' ? 'enDark' : 'enLight';
        return tone === 'dark' ? 'koDark' : 'koLight';
    }

    /**
     * 칸의 바탕.
     * @param {SiteLogoVariant} variant - 칸.
     * @returns {SiteLogoTone} 어두운 바탕용 칸이면 `dark`.
     */
    static toneOf(variant: SiteLogoVariant): SiteLogoTone {
        return variant === 'koDark' || variant === 'enDark' ? 'dark' : 'light';
    }

    /**
     * 이 칸에 실제로 쓸 그림을 대체 순서대로 찾는다.
     * @param {SiteLogoSet} set - 4칸 값.
     * @param {SiteLogoVariant} variant - 원하는 칸.
     * @param {readonly string[]} [skip] - 건너뛸 주소(못 불러온 그림).
     * @returns {SiteLogoPick | null} 쓸 칸과 주소, 쓸 그림이 없으면 null(이름 글자만).
     */
    static resolve(set: SiteLogoSet, variant: SiteLogoVariant, skip: readonly string[] = []): SiteLogoPick | null {
        for (const candidate of CHAIN[variant]) {
            const url = set[candidate];
            if (url && !skip.includes(url)) return { variant: candidate, url };
        }
        return null;
    }
}
