import type { ChatTemplateColor, ChatTemplateSaveRequest, ChatTemplateType } from '~/composables/template/api/useChatTemplateApi.types';
import type { MessageLocale, MessageSeverity, MessageVariableView } from '~/composables/template/api/useNotificationMessageApi.types';
import type { ContentProblem } from '~/utils/util-mail-template';
import UtilMailTemplate from '~/utils/util-mail-template';
import UtilNotificationMessage from '~/utils/util-notification-message';

/**
 * Slack · Mattermost 템플릿(메시지 카드의 겉모양 — 계약 `docs/features/message-template-contract.md` 「개정 4」)의 순수 규칙 — 색 막대 팔레트, 심각도 그림, 저장 전 검사,
 * 그 앱의 표기법(Slack `*굵게*` · `<주소|글자>` / Mattermost `**굵게**` · `[글자](주소)`)을 미리보기 HTML 로 그리기, 템플릿에 문구를 씌운 카드 값 만들기.
 * 서버 단계에서 같은 검사를 서버(가칭 `ChatTemplateValidator`)가 다시 보고, 실제로 올릴 때는 지금처럼(`ChannelMessageFormatter`) 변수 값의 멘션·표기 기호를 무력화한다(목업 가정).
 * 미리보기는 화면이 견본 값으로 그린다(서버 호출 없음 — 목업 가정).
 */

/** 템플릿 칸의 검사할 곳 — 서버 `fields.*` 와 같은 이름 */
export type ChatTemplateField = 'name' | 'openLabel.ko' | 'openLabel.en' | 'frames.ko' | 'frames.en';

export type ChatTemplateProblems = Partial<Record<ChatTemplateField, ContentProblem>>;

/** 미리보기 카드 한 장의 값 — `ChatMessageCard` 가 그린다(`bodyHtml` 은 이 파일이 만든 태그뿐이다) */
export interface ChatCardModel {
    tool: ChatTemplateType;
    /** 제목 줄(평문 — 그림은 `icon`) */
    title: string;
    boldTitle: boolean;
    /** 심각도 그림(이모지 — 없으면 빈 글자) */
    icon: string;
    /** 색 막대(`#rrggbb`) */
    color: string;
    /** 본문 HTML(가린 뒤 표기법만 살린 것) */
    bodyHtml: string;
    /** 꼬리말 한 줄(없으면 빈 글자) */
    footer: string;
    /** 버튼 글자(없으면 빈 글자) */
    button: string;
}

/** 색 막대 팔레트의 실제 값 — 첨부 `color` 로 보낸다 */
const COLORS: Record<ChatTemplateColor, string> = {
    GREEN: '#2eb67d',
    BLUE: '#1d9bd1',
    TEAL: '#0f766e',
    PURPLE: '#7c3aed',
    AMBER: '#ecb22e',
    ORANGE: '#f97316',
    RED: '#e01e5a',
    GRAY: '#9ca3af',
};

/** 심각도 그림 — 서버는 Slack·Mattermost 그림 이름(`:information_source:` · `:warning:` · `:rotating_light:`)으로 보낸다. 미리보기는 같은 그림의 이모지 */
const SEVERITY_EMOJI: Record<MessageSeverity, string> = { INFO: 'ℹ️', WARNING: '⚠️', CRITICAL: '🚨' };

const CONTENT_RE = /\{\{\s*content\s*\}\}/g;

export default class UtilChatTemplate {
    /** 이름 상한(이메일 문구·메일 템플릿과 같다) */
    static readonly NAME_MAX = UtilMailTemplate.NAME_MAX;
    /** 버튼 글자 상한(가정) */
    static readonly OPEN_LABEL_MAX = 20;
    /** 틀 상한(가정) */
    static readonly FRAME_MAX = 500;
    /** 팔레트의 차례 */
    static readonly COLORS: readonly ChatTemplateColor[] = Object.keys(COLORS) as ChatTemplateColor[];
    /** 본문 자리 변수 */
    static readonly CONTENT = 'content';

    /**
     * 팔레트 색의 실제 값.
     * @param color - 팔레트 색.
     * @returns `#rrggbb`.
     */
    static colorHex(color: ChatTemplateColor): string {
        return COLORS[color];
    }

    /**
     * 심각도 그림(미리보기 이모지).
     * @param severity - 심각도(없으면 안내).
     * @returns 이모지.
     */
    static severityEmoji(severity: MessageSeverity | null): string {
        return SEVERITY_EMOJI[severity ?? 'INFO'];
    }

    /**
     * 저장 전 검사 — 이름(필수·50자) · 「열기」 버튼을 보이면 언어별 글자(필수·20자) · 언어별 틀(`{{content}}` 정확히 한 번 · 500자 · 쓸 수 있는 변수만).
     * @param draft - 편집 값.
     * @param known - 틀에서 쓸 수 있는 변수 이름(`content` 빼고).
     * @returns 칸별 문제(없으면 빈 객체).
     */
    static validate(draft: ChatTemplateSaveRequest, known: readonly string[]): ChatTemplateProblems {
        const out: ChatTemplateProblems = {};
        const name = draft.name.trim();
        if (!name) out.name = { code: 'REQUIRED' };
        else if (name.length > UtilChatTemplate.NAME_MAX) out.name = { code: 'TOO_LONG', max: UtilChatTemplate.NAME_MAX };
        for (const locale of ['ko', 'en'] as const) {
            if (draft.showOpenButton) {
                const label = (draft.openLabel[locale] ?? '').trim();
                if (!label) out[`openLabel.${locale}`] = { code: 'BUTTON_LABEL' };
                else if (label.length > UtilChatTemplate.OPEN_LABEL_MAX) out[`openLabel.${locale}`] = { code: 'TOO_LONG', max: UtilChatTemplate.OPEN_LABEL_MAX };
            }
            const frame = draft.frames[locale] ?? '';
            const count = (frame.match(CONTENT_RE) ?? []).length;
            if (count === 0) out[`frames.${locale}`] = { code: 'CONTENT_MISSING' };
            else if (count > 1) out[`frames.${locale}`] = { code: 'CONTENT_MANY' };
            else if (frame.length > UtilChatTemplate.FRAME_MAX) out[`frames.${locale}`] = { code: 'TOO_LONG', max: UtilChatTemplate.FRAME_MAX };
            else {
                const unknown = UtilMailTemplate.variablesIn(frame).filter((v) => v !== UtilChatTemplate.CONTENT && !known.includes(v));
                if (unknown.length) out[`frames.${locale}`] = UtilMailTemplate.unknownProblem(unknown, 'OPERATOR');
            }
        }
        return out;
    }

    /**
     * 그 앱의 표기법을 미리보기 HTML 로 — 통째로 가린 뒤 표기만 살린다(결과는 `strong` `em` `del` `code` `a` `ul` `li` `br` 뿐이라 `v-html` 로 보여도 된다. 링크는 http·https 만, 새 창).
     *   · Slack: `*굵게*` · `_기울임_` · `~취소선~` · `` `코드` `` · `<주소|글자>` · `<주소>` · 줄 맨 앞 `- `·`• ` 목록.
     *   · Mattermost: `**굵게**` · `*기울임*`·`_기울임_` · `~~취소선~~` · `` `코드` `` · `[글자](주소)` · 줄 맨 앞 `- `·`* ` 목록.
     * @param tool - 종류.
     * @param text - 견본 값을 채운 글.
     * @returns 미리보기 HTML.
     */
    static renderMarkup(tool: ChatTemplateType, text: string): string {
        const link = (href: string, label: string) => `<a href="${href}" target="_blank" rel="noopener noreferrer">${label}</a>`;
        const inline = (line: string) => {
            let out = line.replace(/`([^`\n]+)`/g, '<code>$1</code>');
            if (tool === 'SLACK') {
                out = out
                    .replace(/&lt;(https?:\/\/[^|\s&]+(?:&amp;[^|\s&]+)*)\|([^&\n]+?)&gt;/g, (_w, href: string, label: string) => link(href, label))
                    .replace(/&lt;(https?:\/\/[^\s&]+(?:&amp;[^\s&]+)*)&gt;/g, (_w, href: string) => link(href, href))
                    .replace(/(^|[\s(])\*([^*\n]+?)\*(?=$|[\s).,!?])/g, '$1<strong>$2</strong>')
                    .replace(/(^|[\s(])_([^_\n]+?)_(?=$|[\s).,!?])/g, '$1<em>$2</em>')
                    .replace(/(^|[\s(])~([^~\n]+?)~(?=$|[\s).,!?])/g, '$1<del>$2</del>');
            } else {
                out = out
                    .replace(/\[([^\]\n]+)\]\((https?:\/\/[^\s)]+)\)/g, (_w, label: string, href: string) => link(href, label))
                    .replace(/\*\*([^*\n]+?)\*\*/g, '<strong>$1</strong>')
                    .replace(/~~([^~\n]+?)~~/g, '<del>$1</del>')
                    .replace(/(^|[\s(])[*_]([^*_\n]+?)[*_](?=$|[\s).,!?])/g, '$1<em>$2</em>');
            }
            return out;
        };
        const escaped = UtilMailTemplate.escape(text);
        const out: string[] = [];
        let list: string[] = [];
        const flush = () => {
            if (list.length) out.push(`<ul>${list.map((item) => `<li>${inline(item)}</li>`).join('')}</ul>`);
            list = [];
        };
        const bullet = tool === 'SLACK' ? /^\s*[-•]\s+(.*)$/ : /^\s*[-*]\s+(.*)$/;
        for (const line of escaped.split(/\r?\n/)) {
            const item = bullet.exec(line);
            if (item) list.push(item[1] as string);
            else {
                flush();
                out.push(inline(line) || '&nbsp;');
            }
        }
        flush();
        return out
            .map((part) => (part.startsWith('<ul>') ? part : `${part}<br>`))
            .join('')
            .replace(/<br>$/, '');
    }

    /**
     * 템플릿에 문구를 씌운 카드 값 — 틀의 `{{content}}` 자리에 문구 본문, 변수는 견본 값, 색·그림은 심각도로.
     * @param input - 템플릿 값 · 문구 제목·본문 · 변수(견본 값) · 언어 · 심각도 · 사이트 이름 · 보낸 시각 글자.
     * @returns 카드 값.
     */
    static card(input: {
        tool: ChatTemplateType;
        template: ChatTemplateSaveRequest;
        subject: string;
        body: string;
        variables: readonly Pick<MessageVariableView, 'name' | 'sample'>[];
        locale: MessageLocale;
        severity: MessageSeverity | null;
        siteName: string;
        sentAt: string;
    }): ChatCardModel {
        const { template, locale } = input;
        const frame = template.frames[locale] ?? '{{content}}';
        // 본문 자리에 문구 본문을 먼저 넣고, 그다음 변수를 견본 값으로 채운다
        const merged = frame.replace(CONTENT_RE, () => input.body);
        const filled = UtilNotificationMessage.fillSamples(merged, [...input.variables, { name: 'siteName', sample: input.siteName }]);
        const footer = [template.showSiteName ? input.siteName : '', template.showSentAt ? input.sentAt : ''].filter(Boolean).join(' · ');
        return {
            tool: input.tool,
            title: UtilNotificationMessage.fillSamples(input.subject, input.variables),
            boldTitle: template.boldTitle,
            icon: template.showSeverityIcon ? UtilChatTemplate.severityEmoji(input.severity) : '',
            color: UtilChatTemplate.colorHex(template.colors[input.severity ?? 'INFO']),
            bodyHtml: UtilChatTemplate.renderMarkup(input.tool, filled.trim()),
            footer,
            button: template.showOpenButton ? (template.openLabel[locale] ?? '').trim() : '',
        };
    }
}
