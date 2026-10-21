import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { SocialField, SocialFieldErrors } from '~/utils/util-admin-social';
import UtilAdminSocial, { type SocialProblem, type SocialProblems } from '~/utils/util-admin-social';

/**
 * 소셜 로그인 키 입력에 보이는 문구 — **설치 마법사의 소셜 단계**가 쓴다(`util-install-messages.ts`). **문구 본문은 `i18n/locales/{ko,en}/social.ts`** 에 있고 여기는 "어느 키를 고를지"만 안다.
 * 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md` — 메시지 유틸 변환 규약).
 *
 * **서버 `message` 가 있으면 그것을 먼저 쓴다**(frontend-guide 6-1) — 여기 있는 것은 화면이 미리 검사한 오류의 문구다.
 * 분기는 문구가 아니라 코드(`SocialProblem`)로 한다. 운영자 설정 화면의 문구(상태 설명·저장·삭제 안내)는 관리자 앱(`admin/`)으로 옮겼다.
 * 한국어 조사(`를/을`·`는/은`)는 칸마다 문장 키로 나눠 두었다 — 영어는 조사가 없다.
 */

const FIELD_MAX: Record<SocialField, number> = { clientId: UtilAdminSocial.CLIENT_ID_MAX, clientSecret: UtilAdminSocial.CLIENT_SECRET_MAX };

/** 칸 × 문제 → `social.*` 키 */
const PROBLEM_KEYS: Record<SocialField, { required: string; length: string; space: string }> = {
    clientId: { required: 'social.clientIdRequired', length: 'social.clientIdLength', space: 'social.clientIdSpace' },
    clientSecret: { required: 'social.clientSecretRequired', length: 'social.clientSecretLength', space: 'social.clientSecretSpace' },
};

export default class UtilAdminSocialMessages {
    /**
     * 화면이 미리 검사한 입력 문제의 문구.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {SocialField} field - 어느 칸인지.
     * @param {SocialProblem} problem - 문제의 갈래.
     * @returns {string} 그 칸 아래에 보일 한 줄.
     */
    static problem(t: Translate, field: SocialField, problem: SocialProblem): string {
        const keys = PROBLEM_KEYS[field];
        if (problem === 'REQUIRED') return t(keys.required);
        if (problem === 'LENGTH') return t(keys.length, { max: FIELD_MAX[field] });
        return t(keys.space);
    }

    /**
     * 미리 검사한 문제들을 칸별 문구로 바꾼다.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {SocialProblems} problems - 칸별 문제.
     * @returns {SocialFieldErrors} 칸별 문구.
     */
    static fieldErrors(t: Translate, problems: SocialProblems): SocialFieldErrors {
        const out: SocialFieldErrors = {};
        for (const field of ['clientId', 'clientSecret'] as const) {
            const problem = problems[field];
            if (problem) out[field] = UtilAdminSocialMessages.problem(t, field, problem);
        }
        return out;
    }
}
