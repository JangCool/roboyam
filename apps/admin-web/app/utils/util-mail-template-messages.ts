import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { MailLayoutAudience } from '~/composables/template/api/useMailLayoutApi.types';
import type { MailAudience, MailKindGroup } from '~/composables/template/api/useMailTemplateApi.types';
import type { MailLayoutField, MailTemplateField, MailTemplateProblem } from '~/utils/util-mail-template';
import UtilNotificationMessages from '~/utils/util-notification-messages';

/**
 * 메일 화면(메일 종류·템플릿·레이아웃)의 문구 중 계산이 끼는 것 — 문구 본문은 `i18n/locales/{ko,en}/mailTemplate.ts`. `t` 를 첫 인자로 받는다.
 * 검사 규칙은 `UtilMailTemplate`(서버가 같은 규칙으로 다시 본다 — 서버 문구가 오면 그것을 그대로 보인다).
 */
export default class UtilMailTemplateMessages {
    /**
     * 칸 아래 문구 — 화면 검사의 문제 한 건.
     * @param t - 번역 함수.
     * @param field - 칸(템플릿 또는 레이아웃의 칸).
     * @param problem - 문제.
     * @returns 한 줄.
     */
    static problem(t: Translate, field: MailTemplateField | MailLayoutField, problem: MailTemplateProblem): string {
        const names = (problem.names ?? []).map((name) => `{{${name}}}`).join(', ');
        switch (problem.code) {
            case 'REQUIRED':
                if (field === 'name') return t('mailTemplate.nameRequired');
                if (field === 'subject') return t('mailTemplate.subjectRequired');
                return t('mailTemplate.bodyRequired');
            case 'TOO_LONG':
                return t('mailTemplate.tooLong', { max: problem.max ?? 0 });
            case 'MULTI_LINE':
                return t('mailTemplate.subjectOneLine');
            case 'UNKNOWN_VARIABLE':
                return t('mailTemplate.unknownVariable', { names });
            case 'AUDIENCE_VARIABLE':
                return t('mailTemplate.audienceVariable', { names, audience: UtilMailTemplateMessages.audienceMailLabel(t, problem.audience ?? 'MEMBER') });
            case 'MISSING_REQUIRED':
                return t('mailTemplate.missingRequired', { names });
            case 'BUTTON_MANY':
                return t('mailTemplate.buttonMany');
            case 'BUTTON_LABEL':
                return t('mailTemplate.buttonLabelRequired');
            case 'BUTTON_LINK':
                return t('mailTemplate.buttonLinkRequired');
            case 'CONTENT_MISSING':
                return t('mailTemplate.contentMissing', { token: '{{content}}' });
            case 'CONTENT_MANY':
                return t('mailTemplate.contentMany', { token: '{{content}}' });
        }
        return t('mailTemplate.invalid');
    }

    /**
     * 메일 종류의 묶음 이름 — 「계정·보안 메일」 · 「알림 메일」(관리자 알림 이벤트는 「알림 메일 · 보안」 — 영역 이름은 알림 화면과 같다).
     * @param t - 번역 함수.
     * @param group - 묶음.
     * @param area - 관리자 알림 이벤트의 영역(없으면 null).
     * @returns 한 줄.
     */
    static groupLabel(t: Translate, group: MailKindGroup, area: string | null): string {
        if (group === 'ACCOUNT') return t('mailTemplate.groupAccount');
        if (!area) return t('mailTemplate.groupNotification');
        return t('mailTemplate.groupNotificationArea', { area: UtilNotificationMessages.areaLabel(t, area) });
    }

    /**
     * 받는 사람(레이아웃은 쓰는 대상) 이름 — 「회원」 · 「운영자」 · 「회원·운영자 모두」.
     * @param t - 번역 함수.
     * @param audience - 받는 사람 또는 `ANY`.
     * @returns 한 줄.
     */
    static audienceLabel(t: Translate, audience: MailLayoutAudience): string {
        if (audience === 'MEMBER') return t('mailTemplate.audienceMember');
        if (audience === 'OPERATOR') return t('mailTemplate.audienceOperator');
        return t('mailTemplate.audienceAny');
    }

    /**
     * 받는 사람의 메일 묶음 이름 — 「회원 메일」 · 「운영자 메일」.
     * @param t - 번역 함수.
     * @param audience - 받는 사람.
     * @returns 한 줄.
     */
    static audienceMailLabel(t: Translate, audience: MailAudience): string {
        return audience === 'OPERATOR' ? t('mailTemplate.audienceOperatorMail') : t('mailTemplate.audienceMemberMail');
    }

    /**
     * 받는 사람(쓰는 대상)의 아이콘 — 회원 `pi-users` · 운영자 `pi-briefcase` · 모두 `pi-globe`.
     * @param audience - 받는 사람 또는 `ANY`.
     * @returns PrimeIcons 클래스.
     */
    static audienceIcon(audience: MailLayoutAudience): string {
        if (audience === 'MEMBER') return 'pi pi-users';
        if (audience === 'OPERATOR') return 'pi pi-briefcase';
        return 'pi pi-globe';
    }

    /**
     * 템플릿 언어 이름.
     * @param t - 번역 함수.
     * @param locale - `ko` · `en`.
     * @returns 「한국어」 · 「English」.
     */
    static localeLabel(t: Translate, locale: string): string {
        if (locale === 'ko') return t('mailTemplate.localeKo');
        if (locale === 'en') return t('mailTemplate.localeEn');
        return locale;
    }
}
