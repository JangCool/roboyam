import type {
    MessageAudience,
    MessageChannel,
    MessageChannelSaveRequest,
    MessageListItemView,
    MessageLocale,
    MessageRuleView,
    MessageTargetType,
    MessageVariableView,
} from '~/composables/template/api/useNotificationMessageApi.types';
import type { ContentProblem } from '~/utils/util-mail-template';
import UtilMailTemplate from '~/utils/util-mail-template';

/**
 * 알림 문구(이벤트·채널별 제목과 본문 — 계약 `docs/features/message-template-contract.md` 「개정 4」)의 채널 공통 규칙 — 문구 목록 거르기, 앱 알림·Slack·Mattermost 문구의 저장 전 검사, 견본 값 채우기,
 * 대상 상세의 「쓸 문구」·특수 상황 규칙 고치기. 이메일만의 규칙(버튼 블록 · 메일 미리보기)은 `UtilMailTemplate`, Slack·Mattermost 카드 그리기는 `UtilChatTemplate`, 알림톡은 `UtilKakaoTemplate` 이다.
 * 서버 단계에서 같은 규칙을 서버(가칭 `NotificationMessageValidator`)가 다시 본다 — 화면 검사는 미리 알려 주는 용도이고 판정은 서버다(`forms.md`). 길이 상한은 가정 값이다.
 */

/** 앱 알림·Slack·Mattermost 문구의 검사할 칸 — 서버 `fields.*` 와 같은 이름 */
export type NotificationMessageField = 'name' | 'subject' | 'body';

export type NotificationMessageProblems = Partial<Record<NotificationMessageField, ContentProblem>>;

/** 검사에 필요한 대상·채널 정보 */
export interface NotificationMessageRules {
    audience: MessageAudience;
    variables: MessageVariableView[];
}

/** 문구 목록 거르기 값 — null·'ALL'·빈 글자는 거르지 않는다 */
export interface MessageListFilter {
    channel: MessageChannel | null;
    audience: MessageAudience | null;
    targetType: MessageTargetType | null;
    target: string | null;
    locale: MessageLocale | null;
    origin: 'ALL' | 'BUILTIN' | 'USER';
    /** 쓰는 템플릿(겉모양) id */
    templateId: string | null;
    keyword: string;
}

/** 대상 상세의 채널 하나를 고치는 값 — 언어별 쓸 문구 + 특수 상황 규칙(화면 안 줄 열쇠 `uid` 를 갖는다) */
export interface MessageSelectionRule {
    uid: string;
    id?: string;
    conditionKey: string;
    conditionValue: string;
    messageId: string;
}

export interface MessageSelectionModel {
    selected: Record<MessageLocale, string>;
    rules: MessageSelectionRule[];
}

const VAR_RE = /\{\{\s*([A-Za-z][A-Za-z0-9_]*)\s*\}\}/g;
let uidSeq = 0;

export default class UtilNotificationMessage {
    /** 앱 알림 제목 상한(가정 — 서랍 한 줄) */
    static readonly IN_APP_TITLE_MAX = 100;
    /** 앱 알림 본문 상한(가정) */
    static readonly IN_APP_BODY_MAX = 300;
    /** Slack·Mattermost 제목 상한(가정 — 지금 서버 글은 제목 200) */
    static readonly CHAT_TITLE_MAX = 200;
    /** Slack·Mattermost 본문 상한(가정 — 지금 서버 글은 본문 1000, 템플릿 틀까지 합쳐 2000 안쪽) */
    static readonly CHAT_BODY_MAX = 1500;

    /**
     * 템플릿을 고르는 채널인가 — 앱 알림만 아니다.
     * @param channel - 채널.
     * @returns 템플릿(겉모양)을 고르면 true.
     */
    static templated(channel: MessageChannel): boolean {
        return channel !== 'IN_APP';
    }

    /**
     * 받는 사람이 쓰는 채널(차례 `MESSAGE_CHANNELS`) — 운영자 = 앱 알림·이메일·Slack·Mattermost·카카오톡(준비 중), 회원 = 앱 알림·이메일·카카오톡(준비 중). 필수 안내 메일(이메일)은 둘 다에 들어 있다.
     * 문구 목록의 채널 영역이 이것만 보인다(받는 사람이 안 쓰는 채널은 숨긴다).
     * @param audience - 받는 사람.
     * @returns 채널 목록.
     */
    static channelsOf(audience: MessageAudience): MessageChannel[] {
        return audience === 'OPERATOR' ? ['IN_APP', 'EMAIL', 'SLACK', 'MATTERMOST', 'KAKAO'] : ['IN_APP', 'EMAIL', 'KAKAO'];
    }

    /**
     * 준비 중인 채널인가 — 카카오톡(알림 도구에서 카카오가 업체 계약 전). 서버도 대상 요약의 `preparing` 으로 준다.
     * @param channel - 채널.
     * @returns 준비 중이면 true.
     */
    static isPreparing(channel: MessageChannel): boolean {
        return channel === 'KAKAO';
    }

    /**
     * Slack · Mattermost 채널인가.
     * @param channel - 채널.
     * @returns 둘 중 하나면 true.
     */
    static isChat(channel: MessageChannel): channel is 'SLACK' | 'MATTERMOST' {
        return channel === 'SLACK' || channel === 'MATTERMOST';
    }

    /**
     * 문구 목록 거르기 — 채널 · 받는 사람 · 대상 종류 · 대상 · 언어 · 기본/사용자 · 템플릿 · 검색어(이름·대상 이름·대상 코드, 대소문자 무시).
     * @param items - 목록.
     * @param filter - 거르기 값.
     * @returns 걸러진 목록(원래 차례).
     */
    static filterMessages(items: readonly MessageListItemView[], filter: MessageListFilter): MessageListItemView[] {
        const keyword = filter.keyword.trim().toLowerCase();
        return items.filter((item) => {
            if (filter.channel && item.channel !== filter.channel) return false;
            if (filter.audience && item.target.audience !== filter.audience) return false;
            if (filter.targetType && item.target.type !== filter.targetType) return false;
            if (filter.target && item.target.code !== filter.target) return false;
            if (filter.locale && item.locale !== filter.locale) return false;
            if (filter.origin === 'BUILTIN' && !item.builtin) return false;
            if (filter.origin === 'USER' && item.builtin) return false;
            if (filter.templateId && item.templateId !== filter.templateId) return false;
            if (keyword && ![item.name, item.targetName, item.target.code].some((text) => text.toLowerCase().includes(keyword))) return false;
            return true;
        });
    }

    /**
     * Slack·Mattermost 문구 저장 전 검사 — 이름(필수·50자) · 제목(필수·한 줄·200자) · 본문(비어도 된다·1500자) · 모르는 변수 없음.
     * @param draft - 이름·제목·본문.
     * @param rules - 받는 사람과 변수 목록.
     * @returns 칸별 문제(없으면 빈 객체).
     */
    static validateChat(draft: { name: string; subject: string; body: string }, rules: NotificationMessageRules): NotificationMessageProblems {
        return UtilNotificationMessage.validateTitled(draft, rules, UtilNotificationMessage.CHAT_TITLE_MAX, UtilNotificationMessage.CHAT_BODY_MAX);
    }

    /**
     * 앱 알림 문구 저장 전 검사 — 이름 · 제목(필수·한 줄·100자) · 본문(비어도 된다·300자) · 모르는 변수 없음.
     * @param draft - 이름·제목·본문.
     * @param rules - 받는 사람과 변수 목록.
     * @returns 칸별 문제(없으면 빈 객체).
     */
    static validateInApp(draft: { name: string; subject: string; body: string }, rules: NotificationMessageRules): NotificationMessageProblems {
        return UtilNotificationMessage.validateTitled(draft, rules, UtilNotificationMessage.IN_APP_TITLE_MAX, UtilNotificationMessage.IN_APP_BODY_MAX);
    }

    /**
     * 카카오톡 문구 저장 전 검사 — 이름만(본문은 고른 알림톡 템플릿의 승인 본문이다).
     * @param draft - 이름.
     * @returns 칸별 문제(없으면 빈 객체).
     */
    static validateKakao(draft: { name: string }): NotificationMessageProblems {
        const name = UtilNotificationMessage.nameProblem(draft.name);
        return name ? { name } : {};
    }

    /** 제목 한 줄 + 본문 — 앱 알림·Slack·Mattermost 가 같은 꼴이다(상한만 다르다) */
    private static validateTitled(draft: { name: string; subject: string; body: string }, rules: NotificationMessageRules, titleMax: number, bodyMax: number): NotificationMessageProblems {
        const out: NotificationMessageProblems = {};
        const name = UtilNotificationMessage.nameProblem(draft.name);
        if (name) out.name = name;
        if (/[\r\n]/.test(draft.subject)) out.subject = { code: 'MULTI_LINE' };
        else {
            const subject = UtilNotificationMessage.textProblem(draft.subject, titleMax, rules, true);
            if (subject) out.subject = subject;
        }
        const body = UtilNotificationMessage.textProblem(draft.body, bodyMax, rules, false);
        if (body) out.body = body;
        return out;
    }

    /** 이름 — 필수·50자(이메일 문구와 같다) */
    private static nameProblem(value: string): ContentProblem | null {
        const name = value.trim();
        if (!name) return { code: 'REQUIRED' };
        if (name.length > UtilMailTemplate.NAME_MAX) return { code: 'TOO_LONG', max: UtilMailTemplate.NAME_MAX };
        return null;
    }

    /** 글 한 칸 — 필수 여부 · 길이 · 모르는 변수(다른 받는 사람의 공통 변수는 따로 알린다) */
    private static textProblem(text: string, max: number, rules: NotificationMessageRules, required: boolean): ContentProblem | null {
        if (required && !text.trim()) return { code: 'REQUIRED' };
        if (text.length > max) return { code: 'TOO_LONG', max };
        const known = new Set(rules.variables.map((v) => v.name));
        const unknown = UtilMailTemplate.variablesIn(text).filter((name) => !known.has(name));
        return unknown.length ? UtilMailTemplate.unknownProblem(unknown, rules.audience) : null;
    }

    /**
     * 변수를 견본 값으로 채운다(모르는 변수는 그대로 둔다). 값은 가리지 않는다 — 그리는 쪽이 가린다.
     * @param text - 글.
     * @param variables - 변수(견본 값).
     * @returns 채운 글.
     */
    static fillSamples(text: string, variables: readonly Pick<MessageVariableView, 'name' | 'sample'>[]): string {
        const values = new Map(variables.map((v) => [v.name, v.sample]));
        return text.replace(VAR_RE, (whole, name: string) => values.get(name) ?? whole);
    }

    /**
     * 앱 알림 미리보기 글 — 견본 값으로 채운 제목·본문(평문 — 그리는 쪽이 글자로 보인다).
     * @param subject - 제목.
     * @param body - 본문.
     * @param variables - 변수(견본 값).
     * @returns 채운 제목·본문.
     */
    static renderInApp(subject: string, body: string, variables: readonly MessageVariableView[]): { title: string; body: string } {
        return { title: UtilNotificationMessage.fillSamples(subject, variables), body: UtilNotificationMessage.fillSamples(body, variables) };
    }

    /* ── 쓸 문구·특수 상황 규칙 고치기(대상 상세의 채널 하나) ── */

    /**
     * 받은 채널 상세를 고치는 값으로.
     * @param detail - 채널의 저장된 쓸 문구와 규칙.
     * @returns 고치는 값(규칙마다 새 `uid`).
     */
    static toSelection(detail: { selected: Record<MessageLocale, string>; rules: MessageRuleView[] }): MessageSelectionModel {
        return {
            selected: { ...detail.selected },
            rules: detail.rules.map((rule) => ({ uid: UtilNotificationMessage.newUid(), id: rule.id, conditionKey: rule.condition.key, conditionValue: rule.condition.value, messageId: rule.messageId })),
        };
    }

    /** 새 규칙 줄의 열쇠 */
    static newUid(): string {
        uidSeq += 1;
        return `r${uidSeq}`;
    }

    /**
     * 고치는 값을 저장 요청으로.
     * @param model - 고치는 값.
     * @returns 채널 하나의 저장 값.
     */
    static toSaveRequest(model: MessageSelectionModel): MessageChannelSaveRequest {
        return {
            selected: { ...model.selected },
            rules: model.rules.map((rule) => ({ ...(rule.id ? { id: rule.id } : {}), condition: { key: rule.conditionKey, value: rule.conditionValue }, messageId: rule.messageId })),
        };
    }

    /**
     * 저장된 값에서 바꾼 칸 수 — 언어별 쓸 문구 하나하나 + 규칙 줄(늘고 준 수 + 바뀐 줄).
     * @param saved - 저장된 쓸 문구와 규칙.
     * @param model - 고치는 값.
     * @returns 바꾼 칸 수.
     */
    static selectionChanges(saved: { selected: Record<MessageLocale, string>; rules: MessageRuleView[] }, model: MessageSelectionModel): number {
        let n = (['ko', 'en'] as const).filter((locale) => model.selected[locale] !== saved.selected[locale]).length;
        n += Math.abs(saved.rules.length - model.rules.length);
        model.rules.forEach((rule, at) => {
            const before = saved.rules[at];
            if (before && (before.id !== rule.id || before.condition.key !== rule.conditionKey || before.condition.value !== rule.conditionValue || before.messageId !== rule.messageId)) n += 1;
        });
        return n;
    }

    /**
     * 같은 조건이 위에 있어 쓰이지 않는 규칙인가 — 위 규칙의 순번(0부터, 없으면 null).
     * @param rules - 규칙 줄.
     * @param at - 볼 줄.
     * @returns 가리는 위 규칙의 순번.
     */
    static shadowedBy(rules: readonly MessageSelectionRule[], at: number): number | null {
        const rule = rules[at];
        if (!rule) return null;
        const found = rules.findIndex((other, index) => index < at && other.conditionKey === rule.conditionKey && other.conditionValue === rule.conditionValue);
        return found < 0 ? null : found;
    }

    /**
     * 화면이 미리 막는 칸 — 비어 있는 쓸 문구 · 문구를 고르지 않은 규칙(서버 문구는 부르는 쪽이 더한다).
     * @param model - 고치는 값.
     * @returns 문제 칸 이름(`selected.ko` · `rules[0].messageId`).
     */
    static selectionProblems(model: MessageSelectionModel): string[] {
        const out: string[] = [];
        for (const locale of ['ko', 'en'] as const) if (!model.selected[locale]) out.push(`selected.${locale}`);
        model.rules.forEach((rule, at) => {
            if (!rule.messageId) out.push(`rules[${at}].messageId`);
        });
        return out;
    }
}
