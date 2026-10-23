import type { MailTemplateAudience, MailTemplateColor, MailTemplateSaveRequest } from '~/composables/template/api/useMailTemplateApi.types';
import type { MessageAudience, MessageLocale, MessageVariableView } from '~/composables/template/api/useNotificationMessageApi.types';

/**
 * 이메일 문구와 메일 템플릿(겉모양 틀)의 순수 규칙 — 변수(`{{이름}}`) 찾기, 「버튼」 블록 나누기·합치기, 저장 전 검사(문구·메일 템플릿). 미리보기는 서버(`POST notification-messages/preview`)가 그린다.
 * 계약: `docs/features/message-template-contract.md` 「변수」·「문구」(받는 사람별 공통 변수 — 회원 `siteUrl` · 운영자 `adminUrl`). 같은 규칙을 서버(`MessageContentValidator`·`MailTemplateAdminService`·`MailHtml`)가 다시 본다 —
 * 화면 검사는 미리 알려 주는 용도이고 판정은 서버다(`forms.md`). 길이 상한은 서버와 같은 가정 값이다(계약 「미확정」 2).
 *
 * 버튼 블록 저장 모양: `<p data-mail-button=""><a href="{{링크 변수}}">글자</a></p>` 하나(서버 `MailHtml` 허용 목록). 에디터 안에서는 그 자리에 `{{button}}` 한 줄로 보이고,
 * 글자·링크 변수는 에디터 아래 「버튼」 칸에서 고친다(간단 에디터가 버튼 블록을 그리지 못해서다). 링크는 변수로만 — 직접 쓴 주소는 받지 않는다.
 */

/** 검사할 칸 — 서버 `fields.*` 와 같은 이름. `button` 은 화면 전용(서버는 `bodyHtml` 로 준다) */
export type MailMessageField = 'name' | 'subject' | 'bodyHtml' | 'button';

/** 메일 템플릿의 검사할 칸 — 서버 `fields.*` 와 같은 이름(`audience` 는 서버만 본다 — 쓰는 문구와 맞는가) */
export type MailTemplateField = 'name' | 'audience' | 'footer.ko' | 'footer.en' | 'frames.ko' | 'frames.en';

export type ContentProblemCode =
    | 'REQUIRED'
    | 'TOO_LONG'
    | 'MULTI_LINE'
    | 'UNKNOWN_VARIABLE'
    | 'AUDIENCE_VARIABLE'
    | 'MISSING_REQUIRED'
    | 'BUTTON_MANY'
    | 'BUTTON_LABEL'
    | 'BUTTON_LINK'
    | 'CONTENT_MISSING'
    | 'CONTENT_MANY';

export interface ContentProblem {
    code: ContentProblemCode;
    /** 문제의 변수 이름(모르는 변수 · 다른 받는 사람의 변수 · 빠진 필수 변수) */
    names?: string[];
    /** `AUDIENCE_VARIABLE` — 그 변수를 쓸 수 있는 받는 사람 */
    audience?: MessageAudience;
    /** 길이 상한 */
    max?: number;
}

export type MailMessageProblems = Partial<Record<MailMessageField, ContentProblem>>;
export type ContentProblems = Partial<Record<MailTemplateField, ContentProblem>>;

/** 에디터에서 보이는 버튼 블록 */
export interface MailButton {
    label: string;
    /** 링크 변수 이름 */
    link: string;
}

/** 검사·저장에 쓰는 편집 값 — 본문은 버튼 블록이 합쳐진 HTML */
export interface MailMessageDraft {
    name: string;
    subject: string;
    bodyHtml: string;
}

/** 검사에 필요한 대상 정보 */
export interface MailMessageRules {
    /** 받는 사람 — 다른 받는 사람의 공통 변수를 쓰면 「이 메일에서는 쓸 수 없는 변수」로 따로 알린다 */
    audience: MessageAudience;
    security: boolean;
    variables: MessageVariableView[];
}

/** 머리말 띠 색 팔레트 — 화면의 견본 점 색. 서버 `MailColor` 와 같은 값(메일은 서버가 이 색으로 그린다) */
const HEADER_COLORS: Record<MailTemplateColor, string> = {
    BLUE: '#1c5fcf',
    INDIGO: '#4f46e5',
    TEAL: '#0f766e',
    GREEN: '#15803d',
    AMBER: '#b45309',
    ORANGE: '#c2410c',
    RED: '#b91c1c',
    SLATE: '#334155',
};

/**
 * 받는 사람마다 따로 있는 공통 변수(계약 「개정 2」 변수 차이 — 서버의 대상 정의 `audience` 가 정한다). `siteName`·`recipientName` 은 둘 다 쓴다.
 * 화면은 「다른 받는 사람의 변수」를 모르는 변수와 갈라 알리는 데만 쓴다(쓸 수 있는 변수 목록은 서버가 준다).
 */
const AUDIENCE_VARIABLES: Record<MessageAudience, readonly string[]> = { MEMBER: ['siteUrl'], OPERATOR: ['adminUrl'] };

const VAR_RE = /\{\{\s*([A-Za-z][A-Za-z0-9_]*)\s*\}\}/g;
const BUTTON_BLOCK_RE = /<p data-mail-button(?:="[^"]*")?>\s*<a href="\{\{\s*([A-Za-z][A-Za-z0-9_]*)\s*\}\}"[^>]*>([\s\S]*?)<\/a>\s*<\/p>/;
const BUTTON_TOKEN_P_RE = /<p>\s*\{\{\s*button\s*\}\}\s*<\/p>/;
const BUTTON_TOKEN_RE = /\{\{\s*button\s*\}\}/g;
const CONTENT_RE = /\{\{\s*content\s*\}\}/g;

export default class UtilMailTemplate {
    /** 이름 상한(가정) */
    static readonly NAME_MAX = 50;
    /** 제목 상한(가정) */
    static readonly SUBJECT_MAX = 200;
    /** 본문 HTML 상한(가정 — 계약 「미확정」 3) */
    static readonly BODY_MAX = 20000;
    /** 버튼 글자 상한(가정) */
    static readonly BUTTON_LABEL_MAX = 40;
    /** 버튼 자리 표시 변수(에디터 안에서만) */
    static readonly BUTTON = 'button';
    /** 메일 템플릿의 본문 자리 */
    static readonly CONTENT = 'content';
    /** 메일 템플릿 꼬리말 상한(가정 — 언어마다) */
    static readonly FOOTER_MAX = 500;
    /** 머리말 띠 색 팔레트의 차례 */
    static readonly HEADER_COLORS: readonly MailTemplateColor[] = Object.keys(HEADER_COLORS) as MailTemplateColor[];

    /**
     * 이메일 문구(받는 사람)가 이 메일 템플릿을 쓸 수 있는가 — 쓰는 대상이 같거나 `ANY`.
     * @param layout - 메일 템플릿의 쓰는 대상.
     * @param audience - 문구(대상)의 받는 사람.
     * @returns 쓸 수 있으면 true.
     */
    static templateFits(layout: MailTemplateAudience, audience: MessageAudience): boolean {
        return layout === 'ANY' || layout === audience;
    }

    /**
     * 모르는 변수의 문제 — 다른 받는 사람의 공통 변수(운영자 메일의 `siteUrl` …)가 섞였으면 그것을 따로 알린다.
     * @param unknown - 쓸 수 없는 변수 이름들(비어 있지 않다).
     * @param audience - 이 글의 받는 사람(메일 템플릿은 `ANY` 일 수 있다).
     * @returns 문제.
     */
    static unknownProblem(unknown: readonly string[], audience: MailTemplateAudience): ContentProblem {
        for (const owner of ['MEMBER', 'OPERATOR'] as const) {
            if (owner === audience) continue;
            const names = unknown.filter((name) => AUDIENCE_VARIABLES[owner].includes(name));
            if (names.length) return { code: 'AUDIENCE_VARIABLE', names, audience: owner };
        }
        return { code: 'UNKNOWN_VARIABLE', names: [...unknown] };
    }

    /**
     * 글 안의 변수 이름들(나온 차례, 겹침 없음).
     * @param text - 제목이나 본문.
     * @returns 변수 이름 목록.
     */
    static variablesIn(text: string): string[] {
        const out: string[] = [];
        for (const match of text.matchAll(VAR_RE)) {
            const name = match[1] as string;
            if (!out.includes(name)) out.push(name);
        }
        return out;
    }

    /**
     * `{{이름}}` 한 토막.
     * @param name - 변수 이름.
     * @returns 넣을 글자.
     */
    static token(name: string): string {
        return `{{${name}}}`;
    }

    /**
     * 저장된 본문을 에디터 값과 버튼으로 나눈다 — 버튼 블록은 `{{button}}` 한 줄로 바뀐다.
     * @param bodyHtml - 저장된 본문.
     * @returns 에디터에 넣을 HTML 과 버튼(없으면 null).
     */
    static splitBody(bodyHtml: string): { editorHtml: string; button: MailButton | null } {
        const match = BUTTON_BLOCK_RE.exec(bodyHtml);
        if (!match) return { editorHtml: bodyHtml, button: null };
        const button = { link: match[1] as string, label: UtilMailTemplate.unescape((match[2] as string).replace(/<[^>]*>/g, '')).trim() };
        return { editorHtml: bodyHtml.replace(BUTTON_BLOCK_RE, `<p>${UtilMailTemplate.token(UtilMailTemplate.BUTTON)}</p>`), button };
    }

    /**
     * 에디터 값과 버튼을 저장할 본문으로 합친다 — 첫 `{{button}}` 자리가 버튼 블록이 된다(두 번째부터는 그대로 남아 검사가 막는다).
     * @param editorHtml - 에디터 HTML.
     * @param button - 버튼(없으면 null).
     * @returns 저장할 본문.
     */
    static joinBody(editorHtml: string, button: MailButton | null): string {
        if (!button) return editorHtml;
        const block = `<p data-mail-button=""><a href="${UtilMailTemplate.token(button.link)}">${UtilMailTemplate.escape(button.label)}</a></p>`;
        if (BUTTON_TOKEN_P_RE.test(editorHtml)) return editorHtml.replace(BUTTON_TOKEN_P_RE, block);
        return editorHtml.replace(/\{\{\s*button\s*\}\}/, block);
    }

    /**
     * 에디터 값 안의 `{{button}}` 수.
     * @param editorHtml - 에디터 HTML.
     * @returns 개수.
     */
    static buttonTokens(editorHtml: string): number {
        return (editorHtml.match(BUTTON_TOKEN_RE) ?? []).length;
    }

    /**
     * 에디터 값에서 `{{button}}` 을 모두 걷는다(「버튼 빼기」). 그 한 줄만 있던 문단은 문단째 지운다.
     * @param editorHtml - 에디터 HTML.
     * @returns 걷은 HTML.
     */
    static removeButtonTokens(editorHtml: string): string {
        return editorHtml.replace(new RegExp(BUTTON_TOKEN_P_RE.source, 'g'), '').replace(BUTTON_TOKEN_RE, '');
    }

    /**
     * 저장 전 검사 — 서버 규칙(계약 「변수」)과 같다: 모르는 변수 · 보안 메일의 필수 변수 없음 · 길이 · 제목 한 줄 · 버튼 하나까지(링크 변수만).
     * @param draft - 편집 값(본문은 버튼이 합쳐진 HTML).
     * @param rules - 대상의 보안 여부와 변수 목록.
     * @returns 칸별 문제(없으면 빈 객체).
     */
    static validateMessage(draft: MailMessageDraft, rules: MailMessageRules): MailMessageProblems {
        const out: MailMessageProblems = {};
        const known = new Set(rules.variables.map((v) => v.name));
        const name = draft.name.trim();
        if (!name) out.name = { code: 'REQUIRED' };
        else if (name.length > UtilMailTemplate.NAME_MAX) out.name = { code: 'TOO_LONG', max: UtilMailTemplate.NAME_MAX };

        const subject = draft.subject;
        const subjectUnknown = UtilMailTemplate.variablesIn(subject).filter((v) => !known.has(v));
        if (!subject.trim()) out.subject = { code: 'REQUIRED' };
        else if (/[\r\n]/.test(subject)) out.subject = { code: 'MULTI_LINE' };
        else if (subject.length > UtilMailTemplate.SUBJECT_MAX) out.subject = { code: 'TOO_LONG', max: UtilMailTemplate.SUBJECT_MAX };
        else if (subjectUnknown.length) out.subject = UtilMailTemplate.unknownProblem(subjectUnknown, rules.audience);

        const { editorHtml, button } = UtilMailTemplate.splitBody(draft.bodyHtml);
        const tokens = UtilMailTemplate.buttonTokens(editorHtml);
        if (button) {
            const linkVar = rules.variables.find((v) => v.name === button.link);
            if (!button.label.trim()) out.button = { code: 'BUTTON_LABEL' };
            else if (button.label.trim().length > UtilMailTemplate.BUTTON_LABEL_MAX) out.button = { code: 'TOO_LONG', max: UtilMailTemplate.BUTTON_LABEL_MAX };
            else if (!linkVar?.link) out.button = { code: 'BUTTON_LINK' };
        } else if (tokens > 0) {
            out.button = { code: 'BUTTON_LINK' };
        }

        const bodyText = UtilMailTemplate.plainText(draft.bodyHtml);
        const bodyVars = UtilMailTemplate.variablesIn(editorHtml).filter((v) => v !== UtilMailTemplate.BUTTON);
        const bodyUnknown = bodyVars.filter((v) => !known.has(v));
        if (!bodyText.trim() && !button) out.bodyHtml = { code: 'REQUIRED' };
        else if (draft.bodyHtml.length > UtilMailTemplate.BODY_MAX) out.bodyHtml = { code: 'TOO_LONG', max: UtilMailTemplate.BODY_MAX };
        else if (tokens > 1) out.bodyHtml = { code: 'BUTTON_MANY' };
        else if (bodyUnknown.length) out.bodyHtml = UtilMailTemplate.unknownProblem(bodyUnknown, rules.audience);
        else if (rules.security) {
            const used = new Set([...UtilMailTemplate.variablesIn(subject), ...bodyVars, ...(button ? [button.link] : [])]);
            const missing = rules.variables.filter((v) => v.required && !used.has(v.name)).map((v) => v.name);
            if (missing.length) out.bodyHtml = { code: 'MISSING_REQUIRED', names: missing };
        }
        return out;
    }

    /**
     * 메일 템플릿 틀 검사 — `{{content}}` 가 꼭 한 번, 모르는 변수 없음.
     * @param frame - 틀 HTML.
     * @param known - 쓸 수 있는 변수 이름(쓰는 대상의 공통 변수).
     * @param audience - 메일 템플릿의 쓰는 대상(다른 받는 사람의 변수를 갈라 알린다).
     * @returns 문제(없으면 null).
     */
    static validateFrame(frame: string, known: readonly string[], audience: MailTemplateAudience): ContentProblem | null {
        const count = (frame.match(CONTENT_RE) ?? []).length;
        if (count === 0) return { code: 'CONTENT_MISSING' };
        if (count > 1) return { code: 'CONTENT_MANY' };
        if (frame.length > UtilMailTemplate.BODY_MAX) return { code: 'TOO_LONG', max: UtilMailTemplate.BODY_MAX };
        const unknown = UtilMailTemplate.variablesIn(frame).filter((v) => v !== UtilMailTemplate.CONTENT && !known.includes(v));
        return unknown.length ? UtilMailTemplate.unknownProblem(unknown, audience) : null;
    }

    /**
     * 메일 템플릿 꼬리말 검사 — 평문, 길이, 모르는 변수 없음(`{{content}}` 도 쓸 수 없다). 비어도 된다.
     * @param footer - 꼬리말 글.
     * @param known - 쓸 수 있는 변수 이름(쓰는 대상의 공통 변수).
     * @param audience - 메일 템플릿의 쓰는 대상.
     * @returns 문제(없으면 null).
     */
    static validateFooter(footer: string, known: readonly string[], audience: MailTemplateAudience): ContentProblem | null {
        if (footer.length > UtilMailTemplate.FOOTER_MAX) return { code: 'TOO_LONG', max: UtilMailTemplate.FOOTER_MAX };
        const unknown = UtilMailTemplate.variablesIn(footer).filter((v) => !known.includes(v));
        return unknown.length ? UtilMailTemplate.unknownProblem(unknown, audience) : null;
    }

    /**
     * 메일 템플릿 저장 전 검사 — 서버 규칙과 같다: 이름(필수·50자) · 언어별 꼬리말 · 언어별 틀(`{{content}}` 한 번). 변수는 쓰는 대상(`draft.audience`)의 공통 변수만.
     * 쓰는 대상이 쓰는 문구와 맞는가(`fields.audience`)는 서버가 본다 — 화면은 맞지 않는 대상을 고르지 못하게 막는다.
     * @param draft - 편집 값.
     * @param known - 쓸 수 있는 변수 이름(쓰는 대상의 공통 변수 — `content` 빼고).
     * @returns 칸별 문제(없으면 빈 객체).
     */
    static validateTemplate(draft: MailTemplateSaveRequest, known: readonly string[]): ContentProblems {
        const out: ContentProblems = {};
        const name = draft.name.trim();
        if (!name) out.name = { code: 'REQUIRED' };
        else if (name.length > UtilMailTemplate.NAME_MAX) out.name = { code: 'TOO_LONG', max: UtilMailTemplate.NAME_MAX };
        for (const locale of ['ko', 'en'] as const) {
            const footer = UtilMailTemplate.validateFooter(draft.footer[locale] ?? '', known, draft.audience);
            if (footer) out[`footer.${locale}`] = footer;
            const frame = UtilMailTemplate.validateFrame(draft.frames[locale] ?? '', known, draft.audience);
            if (frame) out[`frames.${locale}`] = frame;
        }
        return out;
    }

    /**
     * 머리말 띠 색의 실제 값.
     * @param color - 팔레트 색(없으면 null).
     * @returns `#rrggbb`(없으면 null).
     */
    static colorHex(color: MailTemplateColor | null): string | null {
        return color ? HEADER_COLORS[color] : null;
    }

    /**
     * 태그를 뗀 글자(이력의 본문 요약·「비었는가」).
     * @param html - HTML.
     * @returns 글자.
     */
    static plainText(html: string): string {
        return UtilMailTemplate.unescape(html.replace(/<br\s*\/?>/gi, ' ').replace(/<\/p>/gi, ' ').replace(/<[^>]*>/g, ''))
            .replace(/\s+/g, ' ')
            .trim();
    }

    /**
     * HTML 로 가린다.
     * @param text - 글자.
     * @returns 가린 글자.
     */
    static escape(text: string): string {
        return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    private static unescape(text: string): string {
        return text.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&');
    }
}
