import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { SiteField, SiteFieldErrors } from '~/utils/util-site';
import UtilSite, { type SiteProblem, type SiteProblems } from '~/utils/util-site';

/**
 * 사이트 이름·공개 주소 입력에 보이는 문구(설치 마법사 3단계 · 운영자 설정 화면) — **문구 본문은 `i18n/locales/{ko,en}/site.ts`** 에 있고 여기는 "어느 키를 고를지"만 안다.
 *
 * 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md` — 메시지 유틸 변환 규약). 상수 문구는 이 클래스에 두지 않는다: 컴포넌트가 `t('site.xxx')` 를 바로 부른다.
 * **서버 `message`·`fields` 가 있으면 그것을 먼저 쓴다**(frontend-guide 6-1) — 여기 있는 것은 화면이 미리 검사한 오류의 문구다.
 * 분기는 문구가 아니라 코드(`SiteProblem`)로 한다.
 */

export default class UtilSiteMessages {
    /**
     * 화면이 미리 검사한 입력 문제의 문구.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {SiteField} field - 어느 칸인지.
     * @param {SiteProblem} problem - 문제의 갈래.
     * @returns {string} 그 칸 아래에 보일 한 줄.
     */
    static problem(t: Translate, field: SiteField, problem: SiteProblem): string {
        if (field === 'name') {
            return problem === 'LENGTH' ? t('site.nameLength', { max: UtilSite.NAME_MAX }) : t('site.nameRequired');
        }
        if (field === 'tagline') {
            return problem === 'CHARS' ? t('site.taglineChars') : t('site.taglineLength', { max: UtilSite.TAGLINE_MAX });
        }
        if (problem === 'REQUIRED') return t('site.publicUrlRequired');
        if (problem === 'SCHEME') return t('site.publicUrlScheme');
        if (problem === 'PATH') return t('site.publicUrlPath');
        return t('site.publicUrlFormat');
    }

    /**
     * 미리 검사한 문제들을 칸별 문구로 바꾼다.
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {SiteProblems} problems - 칸별 문제.
     * @returns {SiteFieldErrors} 칸별 문구.
     */
    static fieldErrors(t: Translate, problems: SiteProblems): SiteFieldErrors {
        const out: SiteFieldErrors = {};
        for (const field of ['name', 'publicUrl', 'tagline'] as const) {
            const problem = problems[field];
            if (problem) out[field] = UtilSiteMessages.problem(t, field, problem);
        }
        return out;
    }
}
