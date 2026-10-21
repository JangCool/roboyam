import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { ParsedUserAgent } from '~/utils/util-user-agent';
import type { NicknameProblem } from '~/utils/util-validation';

/**
 * 내 정보 화면이 미리 검사한 입력 문제를 문구로 바꾼다. **문구 본문은 `i18n/locales/{ko,en}/profile.ts`** 에 있고 여기는 "어느 키를 고를지"만 안다.
 * 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md` — 메시지 유틸 변환 규약). 서버 오류 문구는 서버 `message` 를 그대로 쓰므로 여기에 없다.
 */
export default class UtilProfileMessages {
    /**
     * 화면이 미리 검사한 닉네임 문제를 문구로 바꿉니다.
     * @param {Translate} t - 번역 함수.
     * @param {NicknameProblem} problem - 미리 검사한 닉네임 문제.
     * @returns {string} 닉네임 칸 아래에 보일 한 줄.
     */
    static nicknameProblem(t: Translate, problem: NicknameProblem): string {
        if (problem === 'REQUIRED') return t('profile.nicknameRequired');
        if (problem === 'LENGTH') return t('profile.nicknameLength');
        return t('profile.nicknameChars');
    }

    /**
     * 로그인 실패 사유 코드를 문구로 바꿉니다. 아는 코드만 바꾸고, 모르는 코드는 그대로 돌려준다(서버가 새 코드를 더해도 화면이 비지 않게).
     * @param {Translate} t - 번역 함수.
     * @param {string} reason - 서버가 준 사유 코드.
     * @returns {string} 화면에 보일 사유 한 줄.
     */
    static loginReason(t: Translate, reason: string): string {
        if (reason === 'INVALID_CREDENTIALS') return t('profile.reasonInvalidCredentials');
        if (reason === 'LOGIN_LOCKED') return t('profile.reasonLoginLocked');
        return reason;
    }

    /**
     * 풀어낸 UA 를 한 줄 설명으로 바꿉니다 — "Chrome 126 · Windows 10/11 · 데스크톱". 브라우저·운영체제 고유명은 번역하지 않고, 기기 종류와 "알 수 없음"만 번역한다.
     * 풀어낸 것이 하나도 없으면(null·모르는 UA) "알 수 없음".
     * @param {Translate} t - 번역 함수.
     * @param {ParsedUserAgent} parsed - `UtilUserAgent.parse` 의 결과.
     * @returns {string} 화면에 보일 접속 환경 한 줄.
     */
    static agent(t: Translate, parsed: ParsedUserAgent): string {
        const parts: string[] = [];
        if (parsed.browser) parts.push([parsed.browser, parsed.browserVersion].filter(Boolean).join(' '));
        if (parsed.os) parts.push([parsed.os, parsed.osVersion].filter(Boolean).join(' '));
        if (parsed.device !== 'unknown') parts.push(t(`profile.device_${parsed.device}`));
        return parts.length > 0 ? parts.join(' · ') : t('profile.agentUnknown');
    }
}
