import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { OperatorMemberCreateMail, OperatorMemberIneligibleReason } from '~/composables/operator/api/useOperatorMemberAccountApi.types';
import type { EmailProblem, NicknameProblem } from '~/utils/util-validation';

/** 화면이 아는 이을 수 없는 까닭(`OperatorMemberIneligibleReason`) — 서버가 모르는 값을 보내면 버린다 */
const INELIGIBLE_REASONS: readonly OperatorMemberIneligibleReason[] = ['EMAIL_NOT_VERIFIED', 'MEMBER_RESTRICTED', 'BUSINESS_ACCOUNT', 'LINKED_TO_OTHER'];

/**
 * 운영자 회원 계정 「새 계정 만들기」가 미리 검사한 입력 문제·보낸 메일, 연결 실패의 까닭을 문구로 바꾼다. **문구 본문은 `i18n/locales/{ko,en}/operatorMember.ts`** 의 `create.*` 이고
 * 여기는 "어느 키를 고를지"만 안다. 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md`). 서버 오류 문구는 서버 `message` 를 그대로 쓰므로 여기에 없다.
 * 비밀번호 문제는 새 비밀번호 칸(`NewPasswordFields`)과 같은 `UtilAccountMessages.passwordProblem` 을 쓴다.
 */
export default class UtilOperatorMemberMessages {
    /**
     * 이메일 칸 문제.
     * @param {Translate} t - 번역 함수.
     * @param {EmailProblem} problem - 미리 검사한 문제.
     * @returns {string} 칸 아래 한 줄.
     */
    static emailProblem(t: Translate, problem: EmailProblem): string {
        if (problem === 'REQUIRED') return t('operatorMember.create.emailRequired');
        return t('operatorMember.create.emailFormat');
    }

    /**
     * 닉네임 칸 문제 — 형식만(예약어는 운영자 계정이라 보지 않는다, 중복은 서버만 안다).
     * @param {Translate} t - 번역 함수.
     * @param {NicknameProblem} problem - 미리 검사한 문제.
     * @returns {string} 칸 아래 한 줄.
     */
    static nicknameProblem(t: Translate, problem: NicknameProblem): string {
        if (problem === 'REQUIRED') return t('operatorMember.create.nicknameRequired');
        if (problem === 'LENGTH') return t('operatorMember.create.nicknameLength');
        return t('operatorMember.create.nicknameChars');
    }

    /**
     * 연결 실패 `409 OPERATOR_MEMBER_NOT_ELIGIBLE` 의 까닭 문구 — 오류 본문 최상위 `ineligibleReasons`(`ApiError.extras.ineligibleReasons`)를 읽는다.
     * 모르는 값은 버린다. `LINKED_TO_OTHER` 의 운영자 이름은 `extras.linkedOperatorName` → 고른 회원의 이름 순으로 찾고, 없으면 이름 없는 문구.
     * @param {Translate} t - 번역 함수.
     * @param {Record<string, unknown> | undefined} extras - `ApiError.extras`.
     * @param {string | null} fallbackName - 고른 회원의 `linkedOperatorName`.
     * @returns {string[]} 까닭 한 줄씩(없으면 빈 배열).
     */
    static ineligibleReasons(t: Translate, extras: Record<string, unknown> | undefined, fallbackName: string | null): string[] {
        const raw = extras?.ineligibleReasons;
        if (!Array.isArray(raw)) return [];
        const fromServer = typeof extras?.linkedOperatorName === 'string' ? extras.linkedOperatorName : '';
        const name = fromServer || fallbackName || '';
        return raw
            .filter((reason): reason is OperatorMemberIneligibleReason => typeof reason === 'string' && (INELIGIBLE_REASONS as readonly string[]).includes(reason))
            .map((reason) => (reason === 'LINKED_TO_OTHER' && !name ? t('operatorMember.linkedToOtherNoName') : t(`operatorMember.ineligible.${reason}`, { name })));
    }

    /**
     * 만들면서 보낸 메일 한 줄.
     * @param {Translate} t - 번역 함수.
     * @param {OperatorMemberCreateMail} mail - 보낸 메일 종류.
     * @param {string} email - 받는 주소.
     * @returns {string} 결과 화면의 한 줄.
     */
    static mailSent(t: Translate, mail: OperatorMemberCreateMail, email: string): string {
        if (mail === 'PASSWORD_SETUP') return t('operatorMember.create.mailPasswordSetup', { email });
        return t('operatorMember.create.mailVerification', { email });
    }
}
