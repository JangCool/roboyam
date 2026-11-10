/**
 * 푸터 안내 문구(운영 주체 설정 — terms-operator-contract 「푸터 안내 문구」)의 순수 규칙 — `{사이트}` 자리표시를 사이트 이름으로 바꾸고
 * 바로 뒤 조사(은/는·이/가·을/를)를 이름 끝소리에 맞춘다. 서버는 바꾸지 않고 그대로 주므로 바꾸는 곳은 여기 한 곳이다.
 */

/** `{사이트}` + 바로 뒤 조사(있으면) */
const SITE_TOKEN = /\{사이트\}(은|는|이|가|을|를)?/g;

/** 조사 → [받침 있을 때, 받침 없을 때] */
const PARTICLES: Record<string, readonly [string, string]> = {
    은: ['은', '는'],
    는: ['은', '는'],
    이: ['이', '가'],
    가: ['이', '가'],
    을: ['을', '를'],
    를: ['을', '를'],
};

/** 숫자를 한국어로 읽을 때 받침이 있는가(영·일·삼·육·칠·팔) */
const DIGIT_FINAL: Record<string, boolean> = { 0: true, 1: true, 2: false, 3: true, 4: false, 5: false, 6: true, 7: true, 8: true, 9: false };

export default class UtilFooterNotice {
    /**
     * 낱말 끝소리에 받침이 있는가 — 한글은 끝 글자의 받침, 영문은 끝 글자 소리(l·m·n·k·p·t 로 끝나면 받침이 있는 것으로 — ROBOYAM은), 숫자는 한국어 읽기.
     * @param {string} word - 낱말.
     * @returns {boolean | null} 받침 여부. 그 밖의 글자(기호 등)로 끝나거나 비면 null(모름).
     */
    static hasFinalConsonant(word: string): boolean | null {
        const last = word.trim().slice(-1);
        if (!last) return null;
        const code = last.charCodeAt(0) - 0xac00;
        if (code >= 0 && code <= 11171) return code % 28 !== 0;
        if (/[A-Za-z]/.test(last)) return /[lmnkpt]/i.test(last);
        if (/[0-9]/.test(last)) return DIGIT_FINAL[last] ?? null;
        return null;
    }

    /**
     * 안내 한 줄의 `{사이트}` 를 사이트 이름으로 바꾼다 — 바로 뒤 조사는 이름에 맞추고, 받침을 모르면 「은(는)」 꼴로 둔다.
     * @param {string} line - 서버가 준 안내 줄.
     * @param {string} siteName - 사이트 이름(화면 표시 이름).
     * @returns {string} 바꾼 줄.
     */
    static render(line: string, siteName: string): string {
        return line.replace(SITE_TOKEN, (_match, particle?: string) => {
            if (!particle) return siteName;
            const [withFinal, withoutFinal] = PARTICLES[particle] ?? [particle, particle];
            const final = UtilFooterNotice.hasFinalConsonant(siteName);
            if (final === null) return `${siteName}${withFinal}(${withoutFinal})`;
            return `${siteName}${final ? withFinal : withoutFinal}`;
        });
    }
}
