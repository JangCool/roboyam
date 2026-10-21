import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { TokenProblem } from '~/composables/account/api/useAdminAccountApi.types';
import type { NicknameProblem, PasswordProblem } from '~/utils/util-validation';

/** 쓸 수 없는 링크 화면의 제목·설명 */
export interface BlockedText {
    title: string;
    description: string;
}

/**
 * 운영자 계정 비밀번호 흐름 화면이 미리 검사한 입력 문제와 "쓸 수 없는 링크" 안내를 문구로 바꾼다.
 * **문구 본문은 `i18n/locales/{ko,en}/account.ts`** 에 있고 여기는 "어느 키를 고를지"만 안다. 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md`).
 * 서버 오류 문구는 서버 `message` 를 그대로 쓰므로 여기에 없다.
 */
export default class UtilAccountMessages {
    /**
     * 화면이 미리 검사한 새 비밀번호 문제를 문구로 바꿉니다.
     * @param {Translate} t - 번역 함수.
     * @param {PasswordProblem} problem - 미리 검사한 비밀번호 문제.
     * @returns {string} 비밀번호 칸 아래에 보일 한 줄.
     */
    static passwordProblem(t: Translate, problem: PasswordProblem): string {
        if (problem === 'REQUIRED') return t('account.passwordRequired');
        if (problem === 'LENGTH') return t('account.passwordLength');
        if (problem === 'BYTES') return t('account.passwordBytes');
        return t('account.passwordMix');
    }

    /**
     * 화면이 미리 검사한 이름(닉네임) 문제를 문구로 바꿉니다 — 규칙은 내 정보 닉네임과 같고 칸 이름이 「이름」이라 문구만 다르다.
     * @param {Translate} t - 번역 함수.
     * @param {NicknameProblem} problem - 미리 검사한 이름 문제.
     * @returns {string} 이름 칸 아래에 보일 한 줄.
     */
    static nameProblem(t: Translate, problem: NicknameProblem): string {
        if (problem === 'REQUIRED') return t('account.nameRequired');
        if (problem === 'LENGTH') return t('account.nameLength');
        return t('account.nameChars');
    }

    /**
     * 초대 링크를 쓸 수 없을 때의 화면 문구. `missing` 은 주소에 토큰이 아예 없는 경우다.
     * @param {Translate} t - 번역 함수.
     * @param {TokenProblem | 'missing'} problem - 쓸 수 없는 이유.
     * @returns {BlockedText} 제목과 설명(다음 행동 안내 포함).
     */
    static invitationBlocked(t: Translate, problem: TokenProblem | 'missing'): BlockedText {
        return { title: t(`account.invitation_${problem}Title`), description: t(`account.invitation_${problem}Body`) };
    }
}
