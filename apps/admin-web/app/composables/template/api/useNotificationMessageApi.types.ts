import type { SettingHistoryItem, SettingHistoryPage } from '~/composables/common/useSettingHistory.types';
import type { MailTestResultView } from '~/composables/mail/api/useMailSettingsApi.types';
import type { MailTemplateSaveRequest } from '~/composables/template/api/useMailTemplateApi.types';

/**
 * 알림 문구 API 의 모양 — `docs/features/message-template-contract.md` 「개정 4」(용어: **문구** = 이벤트·채널별 제목과 본문, **템플릿** = 도구별 겉모양) — 서버 `notificationtemplate.dto` 와 같은 모양(이름도 같다).
 * **문구 하나 = 대상(이벤트 또는 필수 안내 메일) × 채널 × 언어.** 대상마다 채널별로 「언어별 쓸 문구」와 「특수 상황 규칙」을 정한다(개정 3 그대로 — 이름만 바뀌었다).
 * 받는 사람(회원 · 운영자 — 「개정 2」)은 대상이 정하고, 받는 사람이 공통 변수(회원 `siteUrl` · 운영자 `adminUrl`)와 고를 수 있는 메일 템플릿을 정한다.
 * 채널이 이메일 · Slack · Mattermost · 카카오톡이면 문구가 그 채널의 템플릿(겉모양) 하나를 고른다(`templateId`). 앱 알림은 화면 모양이 정해져 템플릿이 없다.
 * 템플릿의 모양은 도구별 파일(`useMailTemplateApi.types.ts` · `useChatTemplateApi.types.ts` · `useKakaoTemplateApi.types.ts` — 주소의 주인이 다르다).
 */

/** 문구 언어 — 화면 언어와 같은 두 가지 */
export type MessageLocale = 'ko' | 'en';

export const MESSAGE_LOCALES: readonly MessageLocale[] = ['ko', 'en'];

/** 받는 사람 — 회원(공개 사이트) · 운영자(관리 사이트) */
export type MessageAudience = 'MEMBER' | 'OPERATOR';

/** 받는 사람의 차례(화면의 거르기 차례) */
export const MESSAGE_AUDIENCES: readonly MessageAudience[] = ['MEMBER', 'OPERATOR'];

/**
 * 채널 — 이벤트가 나가는 길(개정 4 — 개정 3 의 `CHAT` 은 표기법·첨부 모양이 달라 Slack · Mattermost 로 나눴다).
 * `IN_APP` 앱 알림(관리자 종 서랍 · 회원 알림함 — 제목 한 줄 + 짧은 평문, 템플릿 없음) · `EMAIL` 이메일(제목 + 간단 서식 본문 + 버튼 + 메일 템플릿) ·
 * `SLACK` · `MATTERMOST`(제목 한 줄 + 그 앱의 표기법 본문 + 도구 템플릿 — 첨부 카드) · `KAKAO` 카카오톡 알림톡(준비 중 — 승인된 알림톡 템플릿을 고를 뿐 본문을 쓰지 않는다).
 * 받는 사람별 채널: 운영자 이벤트 = 앱 알림·이메일·Slack·Mattermost(본인에게만 가는 이벤트는 Slack·Mattermost 없음)·카카오톡(준비 중), 회원 이벤트 = 앱 알림·이메일·카카오톡(준비 중), 필수 안내 메일 = 이메일.
 */
export type MessageChannel = 'IN_APP' | 'EMAIL' | 'SLACK' | 'MATTERMOST' | 'KAKAO';

/** 채널의 차례(화면 구획·거르기 차례) */
export const MESSAGE_CHANNELS: readonly MessageChannel[] = ['IN_APP', 'EMAIL', 'SLACK', 'MATTERMOST', 'KAKAO'];

/** 템플릿(겉모양)을 고르는 채널 — 앱 알림만 없다 */
export type TemplatedChannel = Exclude<MessageChannel, 'IN_APP'>;

/** 대상의 종류 — 이벤트(회원·운영자) · 필수 안내 메일(이벤트가 아닌 꼭 가는 메일 — 받는 사람이 끌 수 없다) */
export type MessageTargetType = 'EVENT' | 'REQUIRED_MAIL';

export const MESSAGE_TARGET_TYPES: readonly MessageTargetType[] = ['EVENT', 'REQUIRED_MAIL'];

/** 이벤트의 심각도 — Slack · Mattermost 템플릿의 색 막대·아이콘을 고른다(운영자 이벤트만, 그 밖은 null) */
export type MessageSeverity = 'INFO' | 'WARNING' | 'CRITICAL';

/** 문구가 가리키는 대상. 가정(계약 미확정 9): `code` 는 종류(이벤트·필수 안내 메일)를 넘어 겹치지 않는다(화면 주소는 `?target=<code>` 만 쓴다) */
export interface MessageTargetRef {
    type: MessageTargetType;
    code: string;
    audience: MessageAudience;
}

/** 대상·채널 하나가 쓸 수 있는 변수 — 코드가 정한다(이름·설명·견본 값·필수 여부). 설명은 서버가 요청 언어로 준다 */
export interface MessageVariableView {
    /** `{{이름}}` 의 이름 — 영문자·숫자 */
    name: string;
    description: string;
    /** 미리보기·시험 발송에 쓰는 견본 값 */
    sample: string;
    /** 보안 메일의 필수 변수 — 제목·본문 어디에도 없으면 저장 거절 */
    required: boolean;
    /** 링크 변수 — 이메일 「버튼」 블록의 주소로 고를 수 있는 것은 이것뿐이다 */
    link: boolean;
}

/** 조건 선택지 하나 */
export interface MessageConditionOptionView {
    value: string;
    label: string;
}

/** 대상·채널이 쓸 수 있는 조건 하나(코드가 정한 선택지만 — 자유 조건식 없음) */
export interface MessageConditionView {
    /** 조건 이름(`recipientSource`·`jobName`·`signupMethod`·`inviteRole`·`grouped` …) */
    key: string;
    label: string;
    options: MessageConditionOptionView[];
}

/** 언어별로 쓰는 문구(이름과 함께) — 카카오톡처럼 쓸 문구가 아직 없으면 null */
export interface MessageSelectedView {
    id: string;
    name: string;
    builtin: boolean;
}

/** 대상의 채널 하나 요약 — 목록(이벤트 목록의 「쓰는 문구」 · 필수 안내 메일 목록)과 편집 화면(변수·조건)이 쓴다 */
export interface MessageChannelSummaryView {
    channel: MessageChannel;
    /** 준비 중인 채널(카카오톡 — 알림 도구에서 카카오가 준비 중) — 보기만 하고 고르거나 만들 수 없다 */
    preparing: boolean;
    /** 쓸 수 있는 변수 — 받는 사람의 공통 변수(`siteName` · `siteUrl`|`adminUrl` · `recipientName`) + 대상·채널의 변수 */
    variables: MessageVariableView[];
    conditions: MessageConditionView[];
    selected: Record<MessageLocale, MessageSelectedView | null>;
    ruleCount: number;
    /** 이 대상·채널의 사용자 문구 수(상한 `MESSAGE_LIMIT`) */
    messageCount: number;
}

/** `GET /api/admin/notification-messages/targets` 의 한 줄 — 대상 하나와 채널별 요약 */
export interface MessageTargetView {
    type: MessageTargetType;
    code: string;
    audience: MessageAudience;
    /** 이벤트의 영역(`security`·`system`·`member`·`content` … — 알림 화면과 같은 값). 필수 안내 메일은 null */
    area: string | null;
    /** 운영자 이벤트의 심각도(Slack · Mattermost 템플릿의 색 막대) — 그 밖은 null */
    severity: MessageSeverity | null;
    /** 요청 언어의 이름·설명 */
    name: string;
    description: string;
    /** 보안 메일(필수 안내 메일 중 인증·초대·임시 비밀번호·승인번호) — 필수 변수를 지우면 저장 안 됨 */
    security: boolean;
    /** 이 대상이 나가는 채널(차례 `MESSAGE_CHANNELS`) */
    channels: MessageChannelSummaryView[];
}

/** 특수 상황 규칙의 조건 — 조건 이름 하나 + 값 하나 */
export interface MessageRuleCondition {
    key: string;
    value: string;
}

/** 특수 상황 규칙 하나 — 위에서부터 차례로 보고 맞는 첫 규칙의 문구를 쓴다(없으면 언어별 쓸 문구) */
export interface MessageRuleView {
    id: string;
    condition: MessageRuleCondition;
    messageId: string;
}

/**
 * 문구 하나 — 기본 문구(`builtin=true`, 코드가 준다)는 읽기 전용이다. 칸의 뜻은 채널마다 다르다:
 *   · `IN_APP`: `subject` 제목 한 줄 · `body` 짧은 평문 · `templateId` null.
 *   · `EMAIL`: `subject` 제목(한 줄) · `body` 간단 서식 HTML + 버튼 블록 하나까지 · `templateId` 메일 템플릿.
 *   · `SLACK`: `subject` 제목 한 줄(템플릿이 굵게·심각도 아이콘을 붙인다) · `body` Slack 표기(`*굵게*` · `<주소|글자>` · `- 목록`) — 템플릿의 `{{content}}` 자리에 들어간다 · `templateId` Slack 템플릿.
 *   · `MATTERMOST`: Slack 과 같되 본문은 Mattermost 표기(`**굵게**` · `[글자](주소)` · `- 목록`) · `templateId` Mattermost 템플릿.
 *   · `KAKAO`: `subject`·`body` 는 빈 글자(카카오에 승인된 알림톡 본문을 그대로 보낸다 — 변수 대응은 템플릿이 정한다) · `templateId` 알림톡 템플릿.
 */
export interface MessageView {
    id: string;
    target: MessageTargetRef;
    channel: MessageChannel;
    name: string;
    locale: MessageLocale;
    /** 이 채널의 템플릿(겉모양) — 메일은 받는 사람이 같거나 `ANY` 인 메일 템플릿, 기본 문구는 그 채널(받는 사람)의 기본 템플릿. 앱 알림은 null */
    templateId: string | null;
    subject: string;
    body: string;
    builtin: boolean;
    /** UTC ISO — 기본 문구는 null */
    updatedAt: string | null;
    updatedByName: string | null;
}

/** `GET /api/admin/notification-messages/messages` 의 한 줄 — 모든 대상·채널의 문구(본문 없이). 거르기·검색은 화면이 한다(서버도 페이지 없이 전부 준다 — 수가 적다) */
export interface MessageListItemView {
    id: string;
    target: MessageTargetRef;
    /** 요청 언어의 대상 이름 */
    targetName: string;
    /** 대상의 영역(이벤트) — 필수 안내 메일은 null */
    targetArea: string | null;
    /** 대상이 보안 메일인가 */
    security: boolean;
    channel: MessageChannel;
    /** 준비 중인 채널의 문구(카카오톡) — 목록에서 흐리게 */
    preparing: boolean;
    name: string;
    locale: MessageLocale;
    templateId: string | null;
    templateName: string | null;
    builtin: boolean;
    updatedAt: string | null;
    updatedByName: string | null;
    /** 이 문구를 「쓸 문구」로 고른 언어(저장된 상태) */
    defaultFor: MessageLocale[];
    /** 이 문구를 쓰는 특수 상황 규칙 수 */
    ruleCount: number;
}

/** 대상 상세의 채널 하나 */
export interface MessageChannelDetailView {
    channel: MessageChannel;
    /** 언어별 기본 문구 id — 기본 문구가 없는 채널(카카오톡 — 알림톡 템플릿을 카카오에 먼저 등록해야 한다)은 null */
    defaults: Record<MessageLocale, string | null>;
    /** 언어별 「쓸 문구」 id(없으면 빈 글자 — 카카오톡) */
    selected: Record<MessageLocale, string>;
    rules: MessageRuleView[];
    /** 이 대상·채널의 문구 전부(기본 먼저, 그다음 사용자 문구 — 만든 차례) */
    messages: MessageView[];
}

/** `GET /api/admin/notification-messages/targets/{type}/{code}` */
export interface MessageTargetDetailView {
    /** 대상 정의(목록 한 줄과 같은 모양 — 이름·설명·보안 여부·채널별 변수·조건) */
    summary: MessageTargetView;
    /** 대상이 나가는 채널마다(차례 `MESSAGE_CHANNELS`) */
    channels: MessageChannelDetailView[];
}

/** 채널 하나의 저장 값 — 규칙은 목록 전체(차례 그대로). 새 규칙은 `id` 없이 */
export interface MessageChannelSaveRequest {
    selected: Record<MessageLocale, string>;
    rules: { id?: string; condition: MessageRuleCondition; messageId: string }[];
}

/** `PUT /api/admin/notification-messages/targets/{type}/{code}` — 채널마다(보낸 채널만 바뀐다 — 준비 중 채널은 보내지 않는다) */
export interface MessageTargetSaveRequest {
    channels: Partial<Record<MessageChannel, MessageChannelSaveRequest>>;
}

/** `POST·PUT /api/admin/notification-messages/messages[/{id}]` — 고치기(`PUT`)는 대상·채널을 바꾸지 못한다(서버가 원래 값으로 본다). 받는 사람은 대상이 정하므로 보내지 않는다 */
export interface MessageSaveRequest {
    targetType: MessageTargetType;
    targetCode: string;
    channel: MessageChannel;
    name: string;
    locale: MessageLocale;
    /** 앱 알림만 null */
    templateId: string | null;
    subject: string;
    body: string;
}

/** `GET /api/admin/notification-messages/targets?type=&audience=` 거르기(선택) — 화면은 대개 전부 받아 화면에서 거른다 */
export type MessageTargetQuery = {
    type?: MessageTargetType;
    audience?: MessageAudience;
};

/** `POST /api/admin/notification-messages/preview` — 이메일만(Slack·Mattermost·카카오톡·앱 알림 미리보기는 화면이 견본 값으로 그린다) */
export interface MailPreviewRequest {
    /** 대상(변수 견본 값) — 메일 템플릿 미리보기는 비운다(템플릿 쓰는 대상의 공통 변수만) */
    target?: { type: MessageTargetType; code: string };
    locale: MessageLocale;
    subject: string;
    bodyHtml: string;
    /** 씌울 메일 템플릿(저장된 것) — `template` 이 없을 때 쓴다. 둘 다 없으면 받는 사람의 기본 메일 템플릿 */
    templateId?: string;
    /** 메일 템플릿을 저장 전 값으로 미리 볼 때(메일 템플릿 편집 화면) */
    template?: MailTemplateSaveRequest;
}

/** 견본 값을 채우고 메일 템플릿을 씌운 결과 — `html` 은 문서 전체(iframe `srcdoc` 로만 보인다) */
export interface MailPreviewView {
    subject: string;
    html: string;
}

/** `POST /api/admin/notification-messages/test` — 이메일 시험 발송. 고치는 중인 값(`subject`·`bodyHtml`·`templateId`) */
export interface MailMessageTestRequest {
    target: { type: MessageTargetType; code: string };
    locale: MessageLocale;
    templateId?: string;
    subject: string;
    bodyHtml: string;
    to: string;
}

/** 이메일 시험 발송 결과 — 메일 서버 설정의 시험과 같은 모양(실패도 200) */
export type MailMessageTestResultView = MailTestResultView;

/** `POST /api/admin/notification-messages/chat-test` — Slack·Mattermost 문구를 그 종류의 알림 도구 하나로 시험해 올린다(고른 템플릿을 씌우고 변수는 견본 값) */
export interface ChatMessageTestRequest {
    target: { type: MessageTargetType; code: string };
    channel: 'SLACK' | 'MATTERMOST';
    locale: MessageLocale;
    templateId: string;
    subject: string;
    body: string;
    /** 알림 도구 id(`/api/admin/notification-channels` 의 그 종류 도구) */
    channelId: string;
}

/** Slack·Mattermost 시험 결과 — 알림 도구 연결 시험과 같은 모양(서버가 요청 언어로 푼 한 줄, 실패도 200) */
export interface ChatMessageTestResultView {
    ok: boolean;
    message: string;
}

/** 문구 변경 이력 — 설정 이력과 같은 모양(바뀐 칸 `name|locale|templateId|subject|body`, 본문은 글자 요약, 템플릿은 이름) */
export type MessageHistoryItemView = SettingHistoryItem;
export type MessageHistoryPage = SettingHistoryPage<MessageHistoryItemView>;

/** 대상·채널당 사용자 문구 상한(계약 「미확정」 3 — 20 가정) */
export const MESSAGE_LIMIT = 20;

/** 특수 상황 규칙 수 상한(채널마다 — 서버 `RULE_MAX` 와 같다) */
export const MESSAGE_RULE_MAX = 10;
