import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { SiteOperatorStage } from '~/composables/site/api/useSiteOperatorApi.types';
import UtilSiteOperator, { type SiteOperatorField, type SiteOperatorProblem } from '~/utils/util-site-operator';

/**
 * 「운영 주체」 구획의 계산이 낀 문구 — 칸 문제(칸마다 「비었음」 문장 키, 법인의 상호는 「회사명」)와 단계 전환 확인 창.
 */
export default class UtilSiteOperatorMessages {
    /**
     * 칸 문제 문구.
     * @param {Translate} t - 번역 함수.
     * @param {SiteOperatorField} field - 칸.
     * @param {SiteOperatorProblem} problem - 문제.
     * @param {SiteOperatorStage} stage - 지금 고른 단계(법인이면 상호 → 회사명).
     * @returns {string} 칸 아래 한 줄.
     */
    static problem(t: Translate, field: SiteOperatorField, problem: SiteOperatorProblem, stage: SiteOperatorStage): string {
        switch (problem) {
            case 'REQUIRED':
                return t(`operatorStage.required.${field === 'businessName' && stage === 'CORPORATION' ? 'companyName' : field}`);
            case 'LENGTH':
                return t('operatorStage.problemLength', { max: UtilSiteOperator.maxOf(field) });
            case 'EMAIL':
                return t('operatorStage.problemEmail');
            case 'PHONE':
                return t('operatorStage.problemPhone');
            case 'BIZ_NUMBER':
                return t('operatorStage.problemBizNumber');
            case 'NOTICE_LINES':
                return t('operatorStage.footerNoticeProblemLines', { max: UtilSiteOperator.NOTICE_LINES_MAX });
            case 'NOTICE_LINE_LENGTH':
                return t('operatorStage.footerNoticeProblemLineLength', { max: UtilSiteOperator.NOTICE_LINE_MAX });
        }
    }

    /**
     * 단계를 바꾸고 저장할 때의 확인 창 본문 — 법인으로 바꿀 때는 회원 알림 의무(개인정보보호법 제27조)를 덧붙인다.
     * @param {Translate} t - 번역 함수.
     * @param {SiteOperatorStage} to - 바꿀 단계.
     * @returns {string} 확인 창 본문(줄바꿈 포함).
     */
    static changeMessage(t: Translate, to: SiteOperatorStage): string {
        const base = t('operatorStage.changeConfirmMessage');
        return to === 'CORPORATION' ? `${base}\n\n${t('operatorStage.changeConfirmCorporation')}` : base;
    }
}
