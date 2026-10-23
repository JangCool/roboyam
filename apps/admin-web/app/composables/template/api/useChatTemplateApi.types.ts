import type { SettingHistoryItem, SettingHistoryPage } from '~/composables/common/useSettingHistory.types';
import type { MessageLocale, MessageSeverity, MessageVariableView } from '~/composables/template/api/useNotificationMessageApi.types';

/**
 * Slack · Mattermost 템플릿 API 의 모양 — `docs/features/message-template-contract.md` 「템플릿」 — 서버 `ChatTemplateListItemView`·`ChatTemplateView`·`ChatTemplateSaveRequest` 와 같은 모양.
 * 템플릿 = 채널에 올라가는 메시지 카드의 겉모양: 제목 줄 모양(심각도 아이콘 보이기 · 굵게) · 심각도별 색 막대(첨부 color) · 꼬리말(사이트 이름 · 보낸 시각 보이기) · 「열기」 버튼 · 본문 자리 `{{content}}`.
 * 두 종류는 같은 칸이되 올리는 모양이 다르다 — Slack 은 첨부(`attachments` — `color`·`title`·`text`·`footer`·`ts` + 버튼 블록, 표기 `*굵게*` · `<주소|글자>`),
 * Mattermost 는 `attachments`(`title`·`text`·`color`·`footer` + `actions` 버튼, 표기 `**굵게**` · `[글자](주소)`). 문구(제목·본문)는 그 종류의 표기법으로 쓴다.
 * 기본 템플릿(코드가 준다, `builtin=true`)은 종류마다 하나이고 읽기 전용이다 — 지금 서버 `ChannelMessageFormatter` 의 모양(심각도 그림 + 굵은 제목 + 본문 + 「열기」).
 */

/** 템플릿 종류 — 알림 도구 종류와 같은 이름 */
export type ChatTemplateType = 'SLACK' | 'MATTERMOST';

export const CHAT_TEMPLATE_TYPES: readonly ChatTemplateType[] = ['SLACK', 'MATTERMOST'];

/** 심각도의 차례(색 막대 고르기 차례) */
export const CHAT_SEVERITIES: readonly MessageSeverity[] = ['INFO', 'WARNING', 'CRITICAL'];

/** 색 막대 팔레트(가정 8색) — Slack·Mattermost 첨부 `color` 로 실제 색 값(`#rrggbb`)을 보낸다 */
export type ChatTemplateColor = 'GREEN' | 'BLUE' | 'TEAL' | 'PURPLE' | 'AMBER' | 'ORANGE' | 'RED' | 'GRAY';

/** `GET /api/admin/notification-templates/{slack|mattermost}` 의 한 줄 */
export interface ChatTemplateListItemView {
    id: string;
    type: ChatTemplateType;
    name: string;
    builtin: boolean;
    /** 심각도별 색(목록의 색 점) */
    colors: Record<MessageSeverity, ChatTemplateColor>;
    /** 이 템플릿을 쓰는 문구 수(기본 문구 포함) — 0 일 때만 지울 수 있다 */
    messageCount: number;
    updatedAt: string | null;
    updatedByName: string | null;
}

/** `GET /api/admin/notification-templates/{slack|mattermost}/{id}` */
export interface ChatTemplateView extends ChatTemplateListItemView {
    /** 제목 줄 앞에 심각도 그림(정보 ℹ️ · 주의 ⚠️ · 긴급 🚨 — Slack·Mattermost 그림 이름 `:information_source:` …)을 붙인다 */
    showSeverityIcon: boolean;
    /** 제목 줄을 굵게 */
    boldTitle: boolean;
    /** 꼬리말에 사이트 이름을 보인다 */
    showSiteName: boolean;
    /** 꼬리말에 보낸 시각을 보인다(받는 앱이 보는 사람의 시간대로 그린다) */
    showSentAt: boolean;
    /** 이벤트 화면으로 가는 「열기」 버튼(주소는 이벤트의 `{{link}}`) */
    showOpenButton: boolean;
    /** 언어별 버튼 글자 */
    openLabel: Record<MessageLocale, string>;
    /** 언어별 본문 틀 — 그 종류의 표기법 + `{{content}}`(문구의 본문 자리) 정확히 한 번 */
    frames: Record<MessageLocale, string>;
    siteName: string;
    /** 틀에서 쓸 수 있는 변수 — 본문 자리 `content` + 운영자 공통 변수(Slack·Mattermost 는 운영자 이벤트만 보낸다) */
    variables: MessageVariableView[];
}

/** `POST·PUT /api/admin/notification-templates/{slack|mattermost}[/{id}]` — 종류는 주소가 정한다 */
export interface ChatTemplateSaveRequest {
    name: string;
    showSeverityIcon: boolean;
    boldTitle: boolean;
    colors: Record<MessageSeverity, ChatTemplateColor>;
    showSiteName: boolean;
    showSentAt: boolean;
    showOpenButton: boolean;
    openLabel: Record<MessageLocale, string>;
    frames: Record<MessageLocale, string>;
}

/** 변경 이력 — 설정 이력과 같은 모양(바뀐 칸 `name|showSeverityIcon|boldTitle|colorInfo|colorWarning|colorCritical|showSiteName|showSentAt|showOpenButton|openLabelKo|openLabelEn|frameKo|frameEn`) */
export type ChatTemplateHistoryItemView = SettingHistoryItem;
export type ChatTemplateHistoryPage = SettingHistoryPage<ChatTemplateHistoryItemView>;

/** 종류마다 사용자 템플릿 상한(가정) */
export const CHAT_TEMPLATE_LIMIT = 20;
