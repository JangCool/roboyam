import type { SettingHistoryItem, SettingHistoryPage } from '~/composables/common/useSettingHistory.types';
import type { MailTestResultView } from '~/composables/mail/api/useMailSettingsApi.types';
import type { MailLayoutSaveRequest } from '~/composables/template/api/useMailLayoutApi.types';

/**
 * 알림 템플릿 API 의 모양 — `docs/features/message-template-contract.md` 「개정 3」(채널별 템플릿, 대상 = 이벤트 · 필수 안내 메일). 화면 목업 단계 — 서버 단계에서 확정한다.
 * **템플릿 하나 = 대상(이벤트 또는 필수 안내 메일) × 채널 × 언어.** 대상마다 채널별로 「언어별 쓸 템플릿」과 「특수 상황 규칙」을 정한다.
 * 받는 사람(회원 · 운영자 — 「개정 2」)은 대상이 정하고, 받는 사람이 공통 변수(회원 `siteUrl` · 운영자 `adminUrl`)와 이메일 레이아웃을 정한다.
 * 서버 DTO 이름은 아직 없다 — 서버를 만들 때 이 이름(`TemplateTargetView` …)을 그대로 쓰거나, 서버 이름에 맞춰 이 파일을 고친다.
 * 레이아웃(이메일 채널 전용)의 모양은 `useMailLayoutApi.types.ts`(주소의 주인이 다르다 — `/api/admin/mail-layouts`).
 */

/** 템플릿 언어 — 화면 언어와 같은 두 가지 */
export type TemplateLocale = 'ko' | 'en';

export const TEMPLATE_LOCALES: readonly TemplateLocale[] = ['ko', 'en'];

/** 받는 사람 — 회원(공개 사이트) · 운영자(관리 사이트) */
export type TemplateAudience = 'MEMBER' | 'OPERATOR';

/** 받는 사람의 차례(화면의 전환·거르기 차례) */
export const TEMPLATE_AUDIENCES: readonly TemplateAudience[] = ['MEMBER', 'OPERATOR'];

/**
 * 채널 — 이벤트가 나가는 길마다의 양식.
 * `EMAIL` 이메일(제목 + 간단 서식 본문 + 버튼 + 레이아웃) · `CHAT` 채팅(Slack·Mattermost 글 — 간단 마크다운 한 덩어리) · `IN_APP` 앱 알림(관리자 종 서랍 · 회원 알림함 — 제목 한 줄 + 짧은 평문 본문).
 * 받는 사람별 채널: 운영자 이벤트 = 앱 알림·이메일·채팅(본인에게만 가는 이벤트는 채팅 없음), 회원 이벤트 = 앱 알림·이메일, 필수 안내 메일 = 이메일.
 */
export type TemplateChannel = 'IN_APP' | 'EMAIL' | 'CHAT';

/** 채널의 차례(화면 구획·거르기 차례) */
export const TEMPLATE_CHANNELS: readonly TemplateChannel[] = ['IN_APP', 'EMAIL', 'CHAT'];

/** 대상의 종류 — 이벤트(회원·운영자) · 필수 안내 메일(이벤트가 아닌 꼭 가는 메일 — 받는 사람이 끌 수 없다) */
export type TemplateTargetType = 'EVENT' | 'REQUIRED_MAIL';

export const TEMPLATE_TARGET_TYPES: readonly TemplateTargetType[] = ['EVENT', 'REQUIRED_MAIL'];

/** 템플릿이 가리키는 대상. 목업 가정: `code` 는 종류(이벤트·필수 안내 메일)를 넘어 겹치지 않는다(화면 주소는 `?target=<code>` 만 쓴다) */
export interface TemplateTargetRef {
    type: TemplateTargetType;
    code: string;
    audience: TemplateAudience;
}

/** 대상·채널 하나가 쓸 수 있는 변수 — 코드가 정한다(이름·설명·견본 값·필수 여부). 설명은 서버가 요청 언어로 준다 */
export interface TemplateVariableView {
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
export interface TemplateConditionOptionView {
    value: string;
    label: string;
}

/** 대상·채널이 쓸 수 있는 조건 하나(코드가 정한 선택지만 — 자유 조건식 없음) */
export interface TemplateConditionView {
    /** 조건 이름(`severity`·`recipientLocale`·`signupMethod`·`inviteRole`·`grouped` …) */
    key: string;
    label: string;
    options: TemplateConditionOptionView[];
}

/** 언어별로 쓰는 템플릿(이름과 함께) */
export interface TemplateSelectedView {
    id: string;
    name: string;
    builtin: boolean;
}

/** 대상의 채널 하나 요약 — 목록(이벤트 목록의 「쓰는 템플릿」 · 필수 안내 메일 목록)과 편집 화면(변수·조건)이 쓴다 */
export interface TemplateChannelSummaryView {
    channel: TemplateChannel;
    /** 쓸 수 있는 변수 — 받는 사람의 공통 변수(`siteName` · `siteUrl`|`adminUrl` · `recipientName`) + 대상·채널의 변수 */
    variables: TemplateVariableView[];
    conditions: TemplateConditionView[];
    selected: Record<TemplateLocale, TemplateSelectedView>;
    ruleCount: number;
    /** 이 대상·채널의 사용자 템플릿 수(상한 `TEMPLATE_LIMIT`) */
    templateCount: number;
}

/** `GET /api/admin/notification-templates/targets` 의 한 줄 — 대상 하나와 채널별 요약 */
export interface TemplateTargetView {
    type: TemplateTargetType;
    code: string;
    audience: TemplateAudience;
    /** 이벤트의 영역(`security`·`system`·`member`·`content` … — 알림 화면과 같은 값). 필수 안내 메일은 null */
    area: string | null;
    /** 요청 언어의 이름·설명 */
    name: string;
    description: string;
    /** 보안 메일(필수 안내 메일 중 인증·초대·임시 비밀번호·승인번호) — 필수 변수를 지우면 저장 안 됨 */
    security: boolean;
    /** 이 대상이 나가는 채널(차례 `TEMPLATE_CHANNELS`) */
    channels: TemplateChannelSummaryView[];
}

/** 특수 상황 규칙의 조건 — 조건 이름 하나 + 값 하나 */
export interface TemplateRuleCondition {
    key: string;
    value: string;
}

/** 특수 상황 규칙 하나 — 위에서부터 차례로 보고 맞는 첫 규칙의 템플릿을 쓴다(없으면 언어별 쓸 템플릿) */
export interface TemplateRuleView {
    id: string;
    condition: TemplateRuleCondition;
    templateId: string;
}

/**
 * 템플릿 하나 — 기본 템플릿(`builtin=true`, 코드가 준다)은 읽기 전용이다. 칸의 뜻은 채널마다 다르다:
 *   · `EMAIL`: `subject` 제목(한 줄) · `body` 간단 서식 HTML + 버튼 블록 하나까지 · `layoutId` 레이아웃.
 *   · `CHAT`: `subject` 는 빈 글자(쓰지 않는다) · `body` 간단 마크다운(굵게 `**` · 링크 `[글자](주소)` · 목록 `- `) · `layoutId` null.
 *   · `IN_APP`: `subject` 제목 한 줄 · `body` 짧은 평문 · `layoutId` null.
 */
export interface TemplateView {
    id: string;
    target: TemplateTargetRef;
    channel: TemplateChannel;
    name: string;
    locale: TemplateLocale;
    /** 이메일만 — 받는 사람이 같거나 `ANY` 인 레이아웃(`/api/admin/mail-layouts`). 기본 템플릿은 그 받는 사람의 기본 레이아웃. 다른 채널은 null */
    layoutId: string | null;
    subject: string;
    body: string;
    builtin: boolean;
    /** UTC ISO — 기본 템플릿은 null */
    updatedAt: string | null;
    updatedByName: string | null;
}

/** `GET /api/admin/notification-templates/templates` 의 한 줄 — 모든 대상·채널의 템플릿(본문 없이). 거르기·검색은 화면이 한다(목업 가정 — 수가 적다) */
export interface TemplateListItemView {
    id: string;
    target: TemplateTargetRef;
    /** 요청 언어의 대상 이름 */
    targetName: string;
    /** 대상의 영역(이벤트) — 필수 안내 메일은 null */
    targetArea: string | null;
    /** 대상이 보안 메일인가 */
    security: boolean;
    channel: TemplateChannel;
    name: string;
    locale: TemplateLocale;
    layoutId: string | null;
    layoutName: string | null;
    builtin: boolean;
    updatedAt: string | null;
    updatedByName: string | null;
    /** 이 템플릿을 「쓸 템플릿」으로 고른 언어(저장된 상태) */
    defaultFor: TemplateLocale[];
    /** 이 템플릿을 쓰는 특수 상황 규칙 수 */
    ruleCount: number;
}

/** 대상 상세의 채널 하나 */
export interface TemplateChannelDetailView {
    channel: TemplateChannel;
    /** 언어별 기본 템플릿 id */
    defaults: Record<TemplateLocale, string>;
    /** 언어별 「쓸 템플릿」 id */
    selected: Record<TemplateLocale, string>;
    rules: TemplateRuleView[];
    /** 이 대상·채널의 템플릿 전부(기본 먼저, 그다음 사용자 템플릿 — 만든 차례) */
    templates: TemplateView[];
}

/** `GET /api/admin/notification-templates/targets/{type}/{code}` */
export interface TemplateTargetDetailView {
    /** 대상 정의(목록 한 줄과 같은 모양 — 이름·설명·보안 여부·채널별 변수·조건) */
    summary: TemplateTargetView;
    /** 대상이 나가는 채널마다(차례 `TEMPLATE_CHANNELS`) */
    channels: TemplateChannelDetailView[];
}

/** 채널 하나의 저장 값 — 규칙은 목록 전체(차례 그대로). 새 규칙은 `id` 없이 */
export interface TemplateChannelSaveRequest {
    selected: Record<TemplateLocale, string>;
    rules: { id?: string; condition: TemplateRuleCondition; templateId: string }[];
}

/** `PUT /api/admin/notification-templates/targets/{type}/{code}` — 채널마다(보낸 채널만 바뀐다) */
export interface TemplateTargetSaveRequest {
    channels: Partial<Record<TemplateChannel, TemplateChannelSaveRequest>>;
}

/** `POST·PUT /api/admin/notification-templates/templates[/{id}]` — 고치기(`PUT`)는 대상·채널을 바꾸지 못한다(서버가 원래 값으로 본다). 받는 사람은 대상이 정하므로 보내지 않는다 */
export interface TemplateSaveRequest {
    targetType: TemplateTargetType;
    targetCode: string;
    channel: TemplateChannel;
    name: string;
    locale: TemplateLocale;
    /** 이메일만(다른 채널은 null) */
    layoutId: string | null;
    subject: string;
    body: string;
}

/** `GET /api/admin/notification-templates/targets?type=&audience=` 거르기(선택) — 화면은 대개 전부 받아 화면에서 거른다 */
export interface TemplateTargetQuery {
    type?: TemplateTargetType;
    audience?: TemplateAudience;
}

/** `POST /api/admin/notification-templates/preview` — 이메일만(채팅·앱 알림 미리보기는 화면이 견본 값으로 그린다 — 목업 가정) */
export interface MailPreviewRequest {
    /** 대상(변수 견본 값) — 레이아웃 미리보기는 비운다(레이아웃 쓰는 대상의 공통 변수만) */
    target?: { type: TemplateTargetType; code: string };
    locale: TemplateLocale;
    subject: string;
    bodyHtml: string;
    /** 씌울 레이아웃(저장된 것) — `layout` 이 없을 때 쓴다. 둘 다 없으면 받는 사람의 기본 레이아웃 */
    layoutId?: string;
    /** 레이아웃을 저장 전 값으로 미리 볼 때(레이아웃 편집 화면) */
    layout?: MailLayoutSaveRequest;
}

/** 견본 값을 채우고 레이아웃을 씌운 결과 — `html` 은 문서 전체(iframe `srcdoc` 로만 보인다) */
export interface MailPreviewView {
    subject: string;
    html: string;
}

/** `POST /api/admin/notification-templates/test` — 이메일 시험 발송. 고치는 중인 값(`subject`·`bodyHtml`·`layoutId`) */
export interface MailTemplateTestRequest {
    target: { type: TemplateTargetType; code: string };
    locale: TemplateLocale;
    layoutId?: string;
    subject: string;
    bodyHtml: string;
    to: string;
}

/** 이메일 시험 발송 결과 — 메일 서버 설정의 시험과 같은 모양(실패도 200) */
export type MailTemplateTestResultView = MailTestResultView;

/** `POST /api/admin/notification-templates/chat-test` — 채팅 글을 알림 도구(Slack·Mattermost) 하나로 시험해 올린다. 변수는 견본 값 */
export interface ChatTemplateTestRequest {
    target: { type: TemplateTargetType; code: string };
    locale: TemplateLocale;
    body: string;
    /** 알림 도구 id(`/api/admin/notification-channels` 의 Slack·Mattermost 도구) */
    channelId: string;
}

/** 채팅 시험 결과 — 알림 도구 연결 시험과 같은 모양(서버가 요청 언어로 푼 한 줄, 실패도 200) */
export interface ChatTemplateTestResultView {
    ok: boolean;
    message: string;
}

/** 템플릿 변경 이력 — 설정 이력과 같은 모양(바뀐 칸 `name|locale|layoutId|subject|body`, 본문은 글자 요약, 레이아웃은 이름) */
export type TemplateHistoryItemView = SettingHistoryItem;
export type TemplateHistoryPage = SettingHistoryPage<TemplateHistoryItemView>;

/** 대상·채널당 사용자 템플릿 상한(계약 「미확정」 3 — 20 가정) */
export const TEMPLATE_LIMIT = 20;

/** 특수 상황 규칙 수 상한(채널마다 — 목업 가정) */
export const TEMPLATE_RULE_MAX = 10;
