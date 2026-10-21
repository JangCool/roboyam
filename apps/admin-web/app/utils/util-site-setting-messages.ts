import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { SiteLogoFileProblem, SiteLogoSvgProblem, SiteSettingProblem } from '~/utils/util-site-setting';
import UtilSiteSetting from '~/utils/util-site-setting';

/**
 * 사이트 설정(일반 화면)에서 **계산이 낀 문구** — 문구 본문은 `i18n/locales/{ko,en}/site.ts` 에 있고 여기는 "어느 키를 고를지"만 안다.
 * 번역 함수 `t` 를 **첫 인자로 받는다**(`.claude/guides/frontend/i18n.md`). 서버 오류 문구는 서버 `message` 를 그대로 쓰므로 여기에 없다.
 */
export default class UtilSiteSettingMessages {
    /**
     * 화면이 미리 검사한 사이트 이름 문제를 문구로.
     * @param {Translate} t - 번역 함수.
     * @param {SiteSettingProblem} problem - 미리 검사한 문제.
     * @returns {string} 입력칸 아래에 보일 한 줄.
     */
    static siteNameProblem(t: Translate, problem: SiteSettingProblem): string {
        if (problem === 'REQUIRED') return t('site.nameRequired');
        if (problem === 'LENGTH') return t('site.nameLength', { max: UtilSiteSetting.NAME_MAX });
        return t('site.nameChars');
    }

    /**
     * 화면이 미리 검사한 한 줄 소개 문제를 문구로.
     * @param {Translate} t - 번역 함수.
     * @param {SiteSettingProblem} problem - 미리 검사한 문제.
     * @returns {string} 입력칸 아래에 보일 한 줄.
     */
    static taglineProblem(t: Translate, problem: SiteSettingProblem): string {
        if (problem === 'LENGTH') return t('site.taglineLength', { max: UtilSiteSetting.TAGLINE_MAX });
        return t('site.taglineChars');
    }

    /**
     * 화면이 미리 검사한 로고 그림 파일 문제를 문구로.
     * @param {Translate} t - 번역 함수.
     * @param {SiteLogoFileProblem} problem - 미리 검사한 문제.
     * @returns {string} 로고 줄 아래에 보일 한 줄.
     */
    static logoFileProblem(t: Translate, problem: SiteLogoFileProblem): string {
        if (problem === 'EMPTY') return t('site.logoFileEmpty');
        if (problem === 'SIZE') return t('site.logoFileSize', { max: UtilSiteSetting.LOGO_MAX_BYTES / (1024 * 1024) });
        return t('site.logoFileType');
    }

    /**
     * 화면이 미리 검사한 SVG 소스 문제를 문구로.
     * @param {Translate} t - 번역 함수.
     * @param {SiteLogoSvgProblem} problem - 미리 검사한 문제.
     * @returns {string} 소스 칸 아래에 보일 한 줄.
     */
    static logoSvgProblem(t: Translate, problem: SiteLogoSvgProblem): string {
        if (problem === 'EMPTY') return t('site.logoSvgEmpty');
        if (problem === 'LENGTH') return t('site.logoSvgLength', { max: UtilSiteSetting.SVG_MAX_LENGTH.toLocaleString('en-US') });
        return t('site.logoSvgNotSvg');
    }
}
