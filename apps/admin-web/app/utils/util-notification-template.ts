import type {
    TemplateAudience,
    TemplateChannel,
    TemplateChannelSaveRequest,
    TemplateListItemView,
    TemplateLocale,
    TemplateRuleView,
    TemplateTargetType,
    TemplateVariableView,
} from '~/composables/template/api/useNotificationTemplateApi.types';
import type { MailTemplateProblem } from '~/utils/util-mail-template';
import UtilMailTemplate from '~/utils/util-mail-template';

/**
 * 알림 템플릿의 채널 공통 규칙 — 템플릿 목록 거르기, 채팅·앱 알림 템플릿의 저장 전 검사, 채팅(간단 마크다운)·앱 알림 미리보기 그리기.
 * 이메일 채널만의 규칙(버튼 블록 · 레이아웃 · 메일 미리보기)은 `UtilMailTemplate` 이다. 계약 `docs/features/message-template-contract.md` 「개정 3」 채널·템플릿 편집.
 * 서버 단계에서 같은 규칙을 서버(가칭 `NotificationTemplateValidator`)가 다시 본다 — 화면 검사는 미리 알려 주는 용도이고 판정은 서버다(`forms.md`). 길이 상한은 가정 값이다.
 * 채팅 글을 실제로 올릴 때 서버는 지금처럼(`ChannelMessageFormatter`) 변수 값의 멘션·마크다운을 무력화하고, Slack 은 `**굵게**` 를 `*굵게*` · 링크를 `<주소|글자>` 로 바꾼다(목업 가정).
 */

/** 채팅·앱 알림 템플릿의 검사할 칸 — 서버 `fields.*` 와 같은 이름 */
export type NotificationTemplateField = 'name' | 'subject' | 'body';

export type NotificationTemplateProblems = Partial<Record<NotificationTemplateField, MailTemplateProblem>>;

/** 검사에 필요한 대상·채널 정보 */
export interface NotificationTemplateRules {
    audience: TemplateAudience;
    variables: TemplateVariableView[];
}

/** 템플릿 목록 거르기 값 — null·'ALL'·빈 글자는 거르지 않는다 */
export interface TemplateListFilter {
    channel: TemplateChannel | null;
    audience: TemplateAudience | null;
    targetType: TemplateTargetType | null;
    target: string | null;
    locale: TemplateLocale | null;
    origin: 'ALL' | 'BUILTIN' | 'USER';
    layoutId: string | null;
    keyword: string;
}

/** 대상 상세의 채널 하나를 고치는 값 — 언어별 쓸 템플릿 + 특수 상황 규칙(화면 안 줄 열쇠 `uid` 를 갖는다) */
export interface TemplateSelectionRule {
    uid: string;
    id?: string;
    conditionKey: string;
    conditionValue: string;
    templateId: string;
}

export interface TemplateSelectionModel {
    selected: Record<TemplateLocale, string>;
    rules: TemplateSelectionRule[];
}

const VAR_RE = /\{\{\s*([A-Za-z][A-Za-z0-9_]*)\s*\}\}/g;
let uidSeq = 0;

export default class UtilNotificationTemplate {
    /** 앱 알림 제목 상한(가정 — 서랍 한 줄) */
    static readonly IN_APP_TITLE_MAX = 100;
    /** 앱 알림 본문 상한(가정) */
    static readonly IN_APP_BODY_MAX = 300;
    /** 채팅 글 상한(가정 — 지금 서버 글은 제목 200 + 본문 1000) */
    static readonly CHAT_BODY_MAX = 2000;

    /**
     * 템플릿 목록 거르기 — 채널 · 받는 사람 · 대상 종류 · 대상 · 언어 · 기본/사용자 · 레이아웃 · 검색어(이름·대상 이름·대상 코드, 대소문자 무시).
     * @param items - 목록.
     * @param filter - 거르기 값.
     * @returns 걸러진 목록(원래 차례).
     */
    static filterTemplates(items: readonly TemplateListItemView[], filter: TemplateListFilter): TemplateListItemView[] {
        const keyword = filter.keyword.trim().toLowerCase();
        return items.filter((item) => {
            if (filter.channel && item.channel !== filter.channel) return false;
            if (filter.audience && item.target.audience !== filter.audience) return false;
            if (filter.targetType && item.target.type !== filter.targetType) return false;
            if (filter.target && item.target.code !== filter.target) return false;
            if (filter.locale && item.locale !== filter.locale) return false;
            if (filter.origin === 'BUILTIN' && !item.builtin) return false;
            if (filter.origin === 'USER' && item.builtin) return false;
            if (filter.layoutId && item.layoutId !== filter.layoutId) return false;
            if (keyword && ![item.name, item.targetName, item.target.code].some((text) => text.toLowerCase().includes(keyword))) return false;
            return true;
        });
    }

    /**
     * 채팅 템플릿 저장 전 검사 — 이름(필수·50자) · 본문(필수·2000자·모르는 변수 없음).
     * @param draft - 이름·본문.
     * @param rules - 받는 사람과 변수 목록.
     * @returns 칸별 문제(없으면 빈 객체).
     */
    static validateChat(draft: { name: string; body: string }, rules: NotificationTemplateRules): NotificationTemplateProblems {
        const out: NotificationTemplateProblems = {};
        const name = UtilNotificationTemplate.nameProblem(draft.name);
        if (name) out.name = name;
        const body = UtilNotificationTemplate.textProblem(draft.body, UtilNotificationTemplate.CHAT_BODY_MAX, rules, true);
        if (body) out.body = body;
        return out;
    }

    /**
     * 앱 알림 템플릿 저장 전 검사 — 이름 · 제목(필수·한 줄·100자) · 본문(비어도 된다·300자) · 모르는 변수 없음.
     * @param draft - 이름·제목·본문.
     * @param rules - 받는 사람과 변수 목록.
     * @returns 칸별 문제(없으면 빈 객체).
     */
    static validateInApp(draft: { name: string; subject: string; body: string }, rules: NotificationTemplateRules): NotificationTemplateProblems {
        const out: NotificationTemplateProblems = {};
        const name = UtilNotificationTemplate.nameProblem(draft.name);
        if (name) out.name = name;
        if (/[\r\n]/.test(draft.subject)) out.subject = { code: 'MULTI_LINE' };
        else {
            const subject = UtilNotificationTemplate.textProblem(draft.subject, UtilNotificationTemplate.IN_APP_TITLE_MAX, rules, true);
            if (subject) out.subject = subject;
        }
        const body = UtilNotificationTemplate.textProblem(draft.body, UtilNotificationTemplate.IN_APP_BODY_MAX, rules, false);
        if (body) out.body = body;
        return out;
    }

    /** 이름 — 필수·50자(이메일 템플릿과 같다) */
    private static nameProblem(value: string): MailTemplateProblem | null {
        const name = value.trim();
        if (!name) return { code: 'REQUIRED' };
        if (name.length > UtilMailTemplate.NAME_MAX) return { code: 'TOO_LONG', max: UtilMailTemplate.NAME_MAX };
        return null;
    }

    /** 글 한 칸 — 필수 여부 · 길이 · 모르는 변수(다른 받는 사람의 공통 변수는 따로 알린다) */
    private static textProblem(text: string, max: number, rules: NotificationTemplateRules, required: boolean): MailTemplateProblem | null {
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
    static fillSamples(text: string, variables: readonly TemplateVariableView[]): string {
        const values = new Map(variables.map((v) => [v.name, v.sample]));
        return text.replace(VAR_RE, (whole, name: string) => values.get(name) ?? whole);
    }

    /**
     * 채팅 미리보기 HTML — 견본 값을 채운 뒤 통째로 가리고, 간단 마크다운(줄 맨 앞 `- ` 목록 · `**굵게**` · `[글자](http… 주소)`)만 살린다.
     * 결과는 이 화면이 만든 태그(`strong` `a` `ul` `li` `br`)뿐이라 `v-html` 로 보여도 된다(링크는 http·https 만, 새 창).
     * @param body - 채팅 템플릿 본문.
     * @param variables - 변수(견본 값).
     * @returns 미리보기 HTML.
     */
    static renderChat(body: string, variables: readonly TemplateVariableView[]): string {
        const filled = UtilMailTemplate.escape(UtilNotificationTemplate.fillSamples(body, variables));
        const inline = (line: string) =>
            line
                .replace(/\*\*([^*\n]+?)\*\*/g, '<strong>$1</strong>')
                .replace(/\[([^\]\n]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
        const out: string[] = [];
        let list: string[] = [];
        const flush = () => {
            if (list.length) out.push(`<ul>${list.map((item) => `<li>${inline(item)}</li>`).join('')}</ul>`);
            list = [];
        };
        for (const line of filled.split(/\r?\n/)) {
            const item = /^\s*[-*]\s+(.*)$/.exec(line);
            if (item) list.push(item[1] as string);
            else {
                flush();
                out.push(inline(line) || '&nbsp;');
            }
        }
        flush();
        return out.map((part) => (part.startsWith('<ul>') ? part : `${part}<br>`)).join('').replace(/<br>$/, '');
    }

    /* ── 쓸 템플릿·특수 상황 규칙 고치기(대상 상세의 채널 하나) ── */

    /**
     * 받은 채널 상세를 고치는 값으로.
     * @param detail - 채널의 저장된 쓸 템플릿과 규칙.
     * @returns 고치는 값(규칙마다 새 `uid`).
     */
    static toSelection(detail: { selected: Record<TemplateLocale, string>; rules: TemplateRuleView[] }): TemplateSelectionModel {
        return {
            selected: { ...detail.selected },
            rules: detail.rules.map((rule) => ({ uid: UtilNotificationTemplate.newUid(), id: rule.id, conditionKey: rule.condition.key, conditionValue: rule.condition.value, templateId: rule.templateId })),
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
    static toSaveRequest(model: TemplateSelectionModel): TemplateChannelSaveRequest {
        return {
            selected: { ...model.selected },
            rules: model.rules.map((rule) => ({ ...(rule.id ? { id: rule.id } : {}), condition: { key: rule.conditionKey, value: rule.conditionValue }, templateId: rule.templateId })),
        };
    }

    /**
     * 저장된 값에서 바꾼 칸 수 — 언어별 쓸 템플릿 하나하나 + 규칙 줄(늘고 준 수 + 바뀐 줄).
     * @param saved - 저장된 쓸 템플릿과 규칙.
     * @param model - 고치는 값.
     * @returns 바꾼 칸 수.
     */
    static selectionChanges(saved: { selected: Record<TemplateLocale, string>; rules: TemplateRuleView[] }, model: TemplateSelectionModel): number {
        let n = (['ko', 'en'] as const).filter((locale) => model.selected[locale] !== saved.selected[locale]).length;
        n += Math.abs(saved.rules.length - model.rules.length);
        model.rules.forEach((rule, at) => {
            const before = saved.rules[at];
            if (before && (before.id !== rule.id || before.condition.key !== rule.conditionKey || before.condition.value !== rule.conditionValue || before.templateId !== rule.templateId)) n += 1;
        });
        return n;
    }

    /**
     * 같은 조건이 위에 있어 쓰이지 않는 규칙인가 — 위 규칙의 순번(0부터, 없으면 null).
     * @param rules - 규칙 줄.
     * @param at - 볼 줄.
     * @returns 가리는 위 규칙의 순번.
     */
    static shadowedBy(rules: readonly TemplateSelectionRule[], at: number): number | null {
        const rule = rules[at];
        if (!rule) return null;
        const found = rules.findIndex((other, index) => index < at && other.conditionKey === rule.conditionKey && other.conditionValue === rule.conditionValue);
        return found < 0 ? null : found;
    }

    /**
     * 화면이 미리 막는 칸 — 비어 있는 쓸 템플릿 · 템플릿을 고르지 않은 규칙(서버 문구는 부르는 쪽이 더한다).
     * @param model - 고치는 값.
     * @returns 문제 칸 이름(`selected.ko` · `rules[0].templateId`).
     */
    static selectionProblems(model: TemplateSelectionModel): string[] {
        const out: string[] = [];
        for (const locale of ['ko', 'en'] as const) if (!model.selected[locale]) out.push(`selected.${locale}`);
        model.rules.forEach((rule, at) => {
            if (!rule.templateId) out.push(`rules[${at}].templateId`);
        });
        return out;
    }

    /**
     * 앱 알림 미리보기 글 — 견본 값으로 채운 제목·본문(평문 — 그리는 쪽이 글자로 보인다).
     * @param subject - 제목.
     * @param body - 본문.
     * @param variables - 변수(견본 값).
     * @returns 채운 제목·본문.
     */
    static renderInApp(subject: string, body: string, variables: readonly TemplateVariableView[]): { title: string; body: string } {
        return { title: UtilNotificationTemplate.fillSamples(subject, variables), body: UtilNotificationTemplate.fillSamples(body, variables) };
    }
}
