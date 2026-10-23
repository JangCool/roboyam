import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { MailTemplateAudience } from '~/composables/template/api/useMailTemplateApi.types';
import type { MessageAudience, MessageChannel, MessageTargetRef, MessageTargetType, TemplatedChannel } from '~/composables/template/api/useNotificationMessageApi.types';
import type { ChatTemplateField } from '~/utils/util-chat-template';
import type { ContentProblem, MailMessageField, MailTemplateField } from '~/utils/util-mail-template';
import type { NotificationMessageField } from '~/utils/util-notification-message';
import UtilNotificationMessages from '~/utils/util-notification-messages';

/** 템플릿(겉모양)의 유형 — 화면 주소 조각이기도 하다(`/settings/notification-templates/{유형}`) */
export type TemplateKind = 'mail' | 'slack' | 'mattermost' | 'kakao';

/** 유형의 차례(메뉴 차례) */
export const TEMPLATE_KINDS: readonly TemplateKind[] = ['mail', 'slack', 'mattermost', 'kakao'];

const KIND_OF: Record<TemplatedChannel, TemplateKind> = { EMAIL: 'mail', SLACK: 'slack', MATTERMOST: 'mattermost', KAKAO: 'kakao' };

/** 알림 템플릿 화면의 부모 주소 · 알림 문구 화면의 부모 주소(계약 「개정 4」 메뉴) */
const TEMPLATES_ROOT = '/settings/notification-templates';
const MESSAGES_ROOT = '/settings/notification-messages';

/**
 * 알림 템플릿(도구별 겉모양 — 메일 · Slack · Mattermost · 카카오톡)과 알림 문구(이벤트·채널별 제목·본문 — 운영자 · 회원 · 필수 안내 메일) 화면의 문구 중 계산이 끼는 것과 화면 주소.
 * 계약 `docs/features/message-template-contract.md` 「개정 4」. 문구 본문은 `i18n/locales/{ko,en}/notificationTemplate.ts`(템플릿 화면 + 두 쪽이 같이 쓰는 말) · `notificationMessage.ts`(문구 화면). `t` 를 첫 인자로 받는다.
 * 검사 규칙은 `UtilMailTemplate`(이메일)·`UtilNotificationMessage`(앱 알림·Slack·Mattermost 문구)·`UtilChatTemplate`(Slack·Mattermost 템플릿) — 서버가 같은 규칙으로 다시 본다(서버 문구가 오면 그것을 그대로 보인다).
 */
export default class UtilNotificationTemplateMessages {
    /**
     * 칸 아래 문구 — 화면 검사의 문제 한 건.
     * @param t - 번역 함수.
     * @param field - 칸(문구 또는 템플릿의 칸).
     * @param problem - 문제.
     * @returns 한 줄.
     */
    static problem(t: Translate, field: MailMessageField | MailTemplateField | NotificationMessageField | ChatTemplateField, problem: ContentProblem): string {
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
     * 받는 사람(메일 템플릿은 쓰는 대상) 이름 — 「회원」 · 「운영자」 · 「회원·운영자 모두」.
     * @param t - 번역 함수.
     * @param audience - 받는 사람 또는 `ANY`.
     * @returns 한 줄.
     */
    static audienceLabel(t: Translate, audience: MailTemplateAudience): string {
        if (audience === 'MEMBER') return t('notificationTemplate.audienceMember');
        if (audience === 'OPERATOR') return t('notificationTemplate.audienceOperator');
        return t('notificationTemplate.audienceAny');
    }

    /**
     * 받는 사람(쓰는 대상)의 아이콘 — 회원 `pi-users` · 운영자 `pi-briefcase` · 모두 `pi-globe`.
     * @param audience - 받는 사람 또는 `ANY`.
     * @returns PrimeIcons 클래스.
     */
    static audienceIcon(audience: MailTemplateAudience): string {
        if (audience === 'MEMBER') return 'pi pi-users';
        if (audience === 'OPERATOR') return 'pi pi-briefcase';
        return 'pi pi-globe';
    }

    /**
     * 채널 이름 — 「앱 알림」 · 「이메일」 · 「Slack」 · 「Mattermost」 · 「카카오톡」.
     * @param t - 번역 함수.
     * @param channel - 채널.
     * @returns 한 줄.
     */
    static channelLabel(t: Translate, channel: MessageChannel): string {
        return t(`notificationMessage.channel.${channel}`);
    }

    /**
     * 채널 아이콘 — 앱 알림 `pi-bell` · 이메일 `pi-envelope` · Slack `pi-slack` · Mattermost `pi-comments` · 카카오톡 `pi-comment`(알림 도구 종류 아이콘과 같다).
     * @param channel - 채널.
     * @returns PrimeIcons 클래스.
     */
    static channelIcon(channel: MessageChannel): string {
        if (channel === 'IN_APP') return 'pi pi-bell';
        if (channel === 'EMAIL') return 'pi pi-envelope';
        if (channel === 'SLACK') return 'pi pi-slack';
        if (channel === 'MATTERMOST') return 'pi pi-comments';
        return 'pi pi-comment';
    }

    /**
     * 대상 묶음 이름 — 「회원 이벤트」 · 「운영자 이벤트」 · 「필수 안내 메일」(이벤트는 영역을 붙여 「운영자 이벤트 · 보안」).
     * @param t - 번역 함수.
     * @param type - 대상 종류.
     * @param audience - 받는 사람.
     * @param area - 이벤트 영역(없으면 null — 붙이지 않는다).
     * @returns 한 줄.
     */
    static targetGroupLabel(t: Translate, type: MessageTargetType, audience: MessageAudience, area: string | null = null): string {
        if (type === 'REQUIRED_MAIL') return t('notificationMessage.targetRequiredMail');
        const base = audience === 'MEMBER' ? t('notificationMessage.targetMemberEvent') : t('notificationMessage.targetOperatorEvent');
        return area ? `${base} · ${UtilNotificationMessages.areaLabel(t, area)}` : base;
    }

    /**
     * 대상 화면 주소 — 이벤트는 이벤트 상세, 필수 안내 메일은 그 상세.
     * @param target - 대상.
     * @returns 주소.
     */
    static targetPath(target: Pick<MessageTargetRef, 'type' | 'code' | 'audience'>): string {
        if (target.type === 'REQUIRED_MAIL') return `${MESSAGES_ROOT}/required-mails/${encodeURIComponent(target.code)}`;
        return `/settings/notifications/${target.audience === 'MEMBER' ? 'member' : 'operator'}/${encodeURIComponent(target.code)}`;
    }

    /** 필수 안내 메일 목록 주소 */
    static readonly REQUIRED_MAILS_PATH = `${MESSAGES_ROOT}/required-mails`;

    /**
     * 받는 사람의 문구 목록 주소 — 메뉴 「문구 › 운영자」 · 「문구 › 회원」(받는 사람을 목록 안에서 고르지 않는다 — 메뉴가 곧 받는 사람).
     * @param audience - 받는 사람.
     * @returns `/settings/notification-messages/operator|member`.
     */
    static messageListPath(audience: MessageAudience): string {
        return `${MESSAGES_ROOT}/${audience === 'MEMBER' ? 'member' : 'operator'}`;
    }

    /**
     * 문구 편집 주소(두 받는 사람 메뉴가 같이 쓴다 — `new` 는 새로 만들기).
     * @param id - 문구 id 또는 `new`.
     * @returns `/settings/notification-messages/{id}`.
     */
    static messagePath(id: string): string {
        return `${MESSAGES_ROOT}/${encodeURIComponent(id)}`;
    }

    /**
     * 채널이 고르는 템플릿의 유형.
     * @param channel - 앱 알림이 아닌 채널.
     * @returns 유형(주소 조각).
     */
    static templateKindOf(channel: TemplatedChannel): TemplateKind {
        return KIND_OF[channel];
    }

    /**
     * 유형 이름 — 「메일」 · 「Slack」 · 「Mattermost」 · 「카카오톡」.
     * @param t - 번역 함수.
     * @param kind - 유형.
     * @returns 한 줄.
     */
    static templateKindLabel(t: Translate, kind: TemplateKind): string {
        return t(`notificationTemplate.kind.${kind}`);
    }

    /**
     * 유형의 템플릿 목록 주소.
     * @param kind - 유형.
     * @returns `/settings/notification-templates/{유형}`.
     */
    static templateListPath(kind: TemplateKind): string {
        return `${TEMPLATES_ROOT}/${kind}`;
    }

    /**
     * 템플릿 편집 주소(`new` 는 새로 만들기).
     * @param kind - 유형.
     * @param id - 템플릿 id 또는 `new`.
     * @returns `/settings/notification-templates/{유형}/{id}`.
     */
    static templatePath(kind: TemplateKind, id: string): string {
        return `${TEMPLATES_ROOT}/${kind}/${encodeURIComponent(id)}`;
    }

    /**
     * 메일 템플릿의 「쓰는 문구」 링크 — 받는 사람 하나짜리 템플릿은 그 받는 사람 목록 하나, 「모두」(`ANY`)는 받는 사람마다 따로(운영자 먼저, 쓰는 문구가 있는 쪽만).
     * 목록은 이메일 · 이 템플릿으로 걸러 연다(`?channel=EMAIL&template=<id>`).
     * @param template - 메일 템플릿(id · 쓰는 대상 · 받는 사람별 쓰는 문구 수).
     * @returns 링크들(쓰는 문구가 없으면 빈 배열).
     */
    static mailTemplateMessageLinks(template: { id: string; audience: MailTemplateAudience; messageCounts: Record<MessageAudience, number> }): { audience: MessageAudience; count: number; to: { path: string; query: Record<string, string> } }[] {
        const audiences: MessageAudience[] = template.audience === 'ANY' ? ['OPERATOR', 'MEMBER'] : [template.audience];
        return audiences
            .map((audience) => ({ audience, count: template.messageCounts[audience] ?? 0, to: { path: UtilNotificationTemplateMessages.messageListPath(audience), query: { channel: 'EMAIL', template: template.id } } }))
            .filter((link) => link.count > 0);
    }

    /**
     * Slack·Mattermost·카카오톡 템플릿의 「쓰는 문구」 링크 — 그 채널 · 이 템플릿으로 거른 문구 목록(Slack·Mattermost 는 운영자 이벤트만 보낸다, 카카오톡은 받는 사람을 함께 받는다).
     * @param channel - 채널.
     * @param id - 템플릿 id.
     * @param audience - 문구 목록의 받는 사람(기본 운영자).
     * @returns 링크.
     */
    static templateMessageLink(channel: Exclude<TemplatedChannel, 'EMAIL'>, id: string, audience: MessageAudience = 'OPERATOR'): { path: string; query: Record<string, string> } {
        return { path: UtilNotificationTemplateMessages.messageListPath(audience), query: { channel, template: id } };
    }

    /**
     * 옛 주소(개정 3 의 `/settings/notification-templates/templates…` · 그 이전 `/settings/mail-templates…`)의 쿼리를 문구 화면의 쿼리로 — `channel=CHAT` 은 `SLACK`(채팅이 Slack · Mattermost 로 나뉘었다), `layout=` 은 `template=`, `kind=` 는 `target=`.
     * @param query - 옛 쿼리(`route.query`).
     * @returns 새 쿼리(나머지 칸은 그대로).
     */
    static legacyMessageQuery(query: Record<string, unknown>): Record<string, string> {
        const out: Record<string, string> = {};
        for (const [key, value] of Object.entries(query)) {
            if (typeof value !== 'string' || !value) continue;
            if (key === 'layout') out.template = value;
            else if (key === 'kind') out.target = value;
            else if (key === 'channel') out.channel = value === 'CHAT' ? 'SLACK' : value;
            else out[key] = value;
        }
        return out;
    }

    /**
     * 언어 이름.
     * @param t - 번역 함수.
     * @param locale - `ko` · `en`.
     * @returns 「한국어」 · 「영어」.
     */
    static localeLabel(t: Translate, locale: string): string {
        if (locale === 'ko') return t('notificationTemplate.localeKo');
        if (locale === 'en') return t('notificationTemplate.localeEn');
        return locale;
    }
}
