import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { MailLayoutAudience } from '~/composables/template/api/useMailLayoutApi.types';
import type { TemplateAudience, TemplateChannel, TemplateTargetRef, TemplateTargetType } from '~/composables/template/api/useNotificationTemplateApi.types';
import type { MailLayoutField, MailTemplateField, MailTemplateProblem } from '~/utils/util-mail-template';
import type { NotificationTemplateField } from '~/utils/util-notification-template';
import UtilNotificationMessages from '~/utils/util-notification-messages';

/**
 * 알림 템플릿 화면(이벤트 상세·템플릿·메일 레이아웃·필수 안내 메일)의 문구 중 계산이 끼는 것 — 문구 본문은 `i18n/locales/{ko,en}/notificationTemplate.ts`. `t` 를 첫 인자로 받는다.
 * 검사 규칙은 `UtilMailTemplate`(이메일)·`UtilNotificationTemplate`(채팅·앱 알림) — 서버가 같은 규칙으로 다시 본다(서버 문구가 오면 그것을 그대로 보인다).
 */
export default class UtilNotificationTemplateMessages {
    /**
     * 칸 아래 문구 — 화면 검사의 문제 한 건.
     * @param t - 번역 함수.
     * @param field - 칸(템플릿 또는 레이아웃의 칸).
     * @param problem - 문제.
     * @returns 한 줄.
     */
    static problem(t: Translate, field: MailTemplateField | MailLayoutField | NotificationTemplateField, problem: MailTemplateProblem): string {
        const names = (problem.names ?? []).map((name) => `{{${name}}}`).join(', ');
        switch (problem.code) {
            case 'REQUIRED':
                if (field === 'name') return t('notificationTemplate.nameRequired');
                if (field === 'subject') return t('notificationTemplate.subjectRequired');
                return t('notificationTemplate.bodyRequired');
            case 'TOO_LONG':
                return t('notificationTemplate.tooLong', { max: problem.max ?? 0 });
            case 'MULTI_LINE':
                return t('notificationTemplate.subjectOneLine');
            case 'UNKNOWN_VARIABLE':
                return t('notificationTemplate.unknownVariable', { names });
            case 'AUDIENCE_VARIABLE':
                return t('notificationTemplate.audienceVariable', { names, audience: UtilNotificationTemplateMessages.audienceLabel(t, problem.audience ?? 'MEMBER') });
            case 'MISSING_REQUIRED':
                return t('notificationTemplate.missingRequired', { names });
            case 'BUTTON_MANY':
                return t('notificationTemplate.buttonMany');
            case 'BUTTON_LABEL':
                return t('notificationTemplate.buttonLabelRequired');
            case 'BUTTON_LINK':
                return t('notificationTemplate.buttonLinkRequired');
            case 'CONTENT_MISSING':
                return t('notificationTemplate.contentMissing', { token: '{{content}}' });
            case 'CONTENT_MANY':
                return t('notificationTemplate.contentMany', { token: '{{content}}' });
        }
        return t('notificationTemplate.invalid');
    }

    /**
     * 받는 사람(레이아웃은 쓰는 대상) 이름 — 「회원」 · 「운영자」 · 「회원·운영자 모두」.
     * @param t - 번역 함수.
     * @param audience - 받는 사람 또는 `ANY`.
     * @returns 한 줄.
     */
    static audienceLabel(t: Translate, audience: MailLayoutAudience): string {
        if (audience === 'MEMBER') return t('notificationTemplate.audienceMember');
        if (audience === 'OPERATOR') return t('notificationTemplate.audienceOperator');
        return t('notificationTemplate.audienceAny');
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
     * 채널 이름 — 「앱 알림」 · 「이메일」 · 「채팅」.
     * @param t - 번역 함수.
     * @param channel - 채널.
     * @returns 한 줄.
     */
    static channelLabel(t: Translate, channel: TemplateChannel): string {
        return t(`notificationTemplate.channel.${channel}`);
    }

    /**
     * 채널 아이콘 — 앱 알림 `pi-bell` · 이메일 `pi-envelope` · 채팅 `pi-comments`.
     * @param channel - 채널.
     * @returns PrimeIcons 클래스.
     */
    static channelIcon(channel: TemplateChannel): string {
        if (channel === 'IN_APP') return 'pi pi-bell';
        if (channel === 'EMAIL') return 'pi pi-envelope';
        return 'pi pi-comments';
    }

    /**
     * 대상 묶음 이름 — 「회원 이벤트」 · 「운영자 이벤트」 · 「필수 안내 메일」(이벤트는 영역을 붙여 「운영자 이벤트 · 보안」).
     * @param t - 번역 함수.
     * @param type - 대상 종류.
     * @param audience - 받는 사람.
     * @param area - 이벤트 영역(없으면 null — 붙이지 않는다).
     * @returns 한 줄.
     */
    static targetGroupLabel(t: Translate, type: TemplateTargetType, audience: TemplateAudience, area: string | null = null): string {
        if (type === 'REQUIRED_MAIL') return t('notificationTemplate.targetRequiredMail');
        const base = audience === 'MEMBER' ? t('notificationTemplate.targetMemberEvent') : t('notificationTemplate.targetOperatorEvent');
        return area ? `${base} · ${UtilNotificationMessages.areaLabel(t, area)}` : base;
    }

    /**
     * 대상 화면 주소 — 이벤트는 이벤트 상세, 필수 안내 메일은 그 상세.
     * @param target - 대상.
     * @returns 주소.
     */
    static targetPath(target: Pick<TemplateTargetRef, 'type' | 'code' | 'audience'>): string {
        if (target.type === 'REQUIRED_MAIL') return `/settings/notification-templates/required-mails/${encodeURIComponent(target.code)}`;
        return `/settings/notifications/${target.audience === 'MEMBER' ? 'member' : 'operator'}/${encodeURIComponent(target.code)}`;
    }

    /**
     * 템플릿 언어 이름.
     * @param t - 번역 함수.
     * @param locale - `ko` · `en`.
     * @returns 「한국어」 · 「English」.
     */
    static localeLabel(t: Translate, locale: string): string {
        if (locale === 'ko') return t('notificationTemplate.localeKo');
        if (locale === 'en') return t('notificationTemplate.localeEn');
        return locale;
    }
}
