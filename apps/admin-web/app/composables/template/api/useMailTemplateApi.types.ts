import type { SettingHistoryItem, SettingHistoryPage } from '~/composables/common/useSettingHistory.types';
import type { MailTestResultView } from '~/composables/mail/api/useMailSettingsApi.types';
import type { MailLayoutSaveRequest } from '~/composables/template/api/useMailLayoutApi.types';

/**
 * 메일 템플릿 API 의 모양 — `docs/features/message-template-contract.md` 「개정」·「API 후보」(화면 목업 단계, 서버 단계에서 확정한다).
 * 서버 DTO 이름은 아직 없다 — 서버를 만들 때 이 이름(`MailKindView` …)을 그대로 쓰거나, 서버 이름에 맞춰 이 파일을 고친다.
 * 레이아웃의 모양은 `useMailLayoutApi.types.ts`(주소의 주인이 다르다 — `/api/admin/mail-layouts`).
 */

/** 템플릿 언어 — 화면 언어와 같은 두 가지 */
export type MailLocale = 'ko' | 'en';

/** 메일 종류의 묶음 — 시스템 메일 · 관리자 알림(이벤트 영역별) */
export type MailKindGroup = 'SYSTEM' | 'NOTIFICATION';

/** 종류 하나가 쓸 수 있는 변수 — 코드가 정한다(이름·설명·견본 값·필수 여부). 설명은 서버가 요청 언어로 준다 */
export interface MailVariableView {
    /** `{{이름}}` 의 이름 — 영문자·숫자 */
    name: string;
    description: string;
    /** 미리보기·시험 발송에 쓰는 견본 값 */
    sample: string;
    /** 보안 메일의 필수 변수 — 제목·본문 어디에도 없으면 저장 거절 */
    required: boolean;
    /** 링크 변수 — 「버튼」 블록의 주소로 고를 수 있는 것은 이것뿐이다 */
    link: boolean;
}

/** 조건 선택지 하나 */
export interface MailConditionOptionView {
    value: string;
    label: string;
}

/** 종류가 쓸 수 있는 조건 하나(코드가 정한 선택지만 — 자유 조건식 없음) */
export interface MailConditionView {
    /** 조건 이름(`severity`·`recipientLocale`·`signupMethod`·`inviteRole` …) */
    key: string;
    label: string;
    options: MailConditionOptionView[];
}

/** 언어별로 쓰는 템플릿(이름과 함께) */
export interface MailSelectedTemplateView {
    id: string;
    name: string;
    builtin: boolean;
}

/** `GET /api/admin/mail-templates/kinds` 의 한 줄 */
export interface MailKindView {
    /** 종류 코드 — 시스템 메일(`MEMBER_EMAIL_VERIFY` …) 또는 알림 이벤트 코드(`BATCH_RUN_FAILED` …) */
    kind: string;
    group: MailKindGroup;
    /** 알림 이벤트의 영역(`security`·`system` … — 알림 화면과 같은 값). 시스템 메일은 null */
    area: string | null;
    /** 요청 언어의 이름·설명 */
    name: string;
    description: string;
    /** 보안 메일 — 필수 변수를 지우면 저장 안 됨, 보내기를 끌 수 없음 */
    security: boolean;
    variables: MailVariableView[];
    conditions: MailConditionView[];
    selected: Record<MailLocale, MailSelectedTemplateView>;
    ruleCount: number;
    /** 이 종류의 사용자 템플릿 수(상한 `MAIL_TEMPLATE_LIMIT`) */
    templateCount: number;
}

/** 규칙의 조건 — 조건 이름 하나 + 값 하나 */
export interface MailRuleCondition {
    key: string;
    value: string;
}

/** 특수한 상황 규칙 하나 — 위에서부터 차례로 보고 맞는 첫 규칙의 템플릿을 쓴다(없으면 언어별 기본) */
export interface MailRuleView {
    id: string;
    condition: MailRuleCondition;
    templateId: string;
}

/** 템플릿 하나 — 기본 템플릿(`builtin=true`, 코드가 준다)은 읽기 전용이다 */
export interface MailTemplateView {
    id: string;
    kind: string;
    name: string;
    locale: MailLocale;
    /** 이 템플릿을 씌울 레이아웃(`/api/admin/mail-layouts`) — 기본 템플릿은 기본 레이아웃 */
    layoutId: string;
    /** 한 줄 — `{{변수}}` 가능 */
    subject: string;
    /** 간단 서식 HTML(`p br strong em u s a ul ol li blockquote`) + `{{변수}}` + 버튼 블록(`<p data-mail-button><a href="{{링크 변수}}">글자</a></p>`) 하나까지 */
    bodyHtml: string;
    builtin: boolean;
    /** UTC ISO — 기본 템플릿은 null */
    updatedAt: string | null;
    updatedByName: string | null;
}

/** `GET /api/admin/mail-templates/templates` 의 한 줄 — 모든 종류의 템플릿(본문 없이). 거르기·검색은 화면이 한다(목업 가정 — 수가 적다) */
export interface MailTemplateListItemView {
    id: string;
    kind: string;
    /** 요청 언어의 종류 이름 */
    kindName: string;
    /** 종류가 보안 메일인가 */
    security: boolean;
    name: string;
    locale: MailLocale;
    layoutId: string;
    layoutName: string;
    builtin: boolean;
    updatedAt: string | null;
    updatedByName: string | null;
    /** 이 템플릿을 「기본으로 쓰는」 언어(저장된 상태) */
    defaultFor: MailLocale[];
    /** 이 템플릿을 쓰는 특수한 상황 규칙 수 */
    ruleCount: number;
}

/** `GET /api/admin/mail-templates/kinds/{kind}` */
export interface MailKindDetailView {
    kind: string;
    /** 종류 정의(목록 한 줄과 같은 모양 — 이름·설명·보안 여부·변수·조건) */
    summary: MailKindView;
    /** 언어별 기본 템플릿 id */
    defaults: Record<MailLocale, string>;
    /** 언어별 「기본으로 쓸 템플릿」 id */
    selected: Record<MailLocale, string>;
    rules: MailRuleView[];
    /** 이 종류의 템플릿 전부(기본 먼저, 그다음 사용자 템플릿 — 만든 차례) */
    templates: MailTemplateView[];
}

/** `PUT /api/admin/mail-templates/kinds/{kind}` — 규칙은 목록 전체(차례 그대로). 새 규칙은 `id` 없이 */
export interface MailKindSaveRequest {
    selected: Record<MailLocale, string>;
    rules: { id?: string; condition: MailRuleCondition; templateId: string }[];
}

/** `POST·PUT /api/admin/mail-templates/templates[/{id}]` — 고치기(`PUT`)는 `kind` 를 바꾸지 못한다(서버가 원래 값으로 본다) */
export interface MailTemplateSaveRequest {
    kind: string;
    name: string;
    locale: MailLocale;
    layoutId: string;
    subject: string;
    bodyHtml: string;
}

/** `POST /api/admin/mail-templates/preview` */
export interface MailPreviewRequest {
    /** 메일 종류(변수 견본 값) — 레이아웃 미리보기는 비운다(공통 변수만) */
    kind?: string;
    locale: MailLocale;
    subject: string;
    bodyHtml: string;
    /** 씌울 레이아웃(저장된 것) — `layout` 이 없을 때 쓴다. 둘 다 없으면 기본 레이아웃 */
    layoutId?: string;
    /** 레이아웃을 저장 전 값으로 미리 볼 때(레이아웃 편집 화면) */
    layout?: MailLayoutSaveRequest;
}

/** 견본 값을 채우고 레이아웃을 씌운 결과 — `html` 은 문서 전체(iframe `srcdoc` 로만 보인다) */
export interface MailPreviewView {
    subject: string;
    html: string;
}

/** `POST /api/admin/mail-templates/test` — 저장된 템플릿(`templateId`) 또는 고치는 중인 값(`subject`·`bodyHtml`·`layoutId`) */
export interface MailTemplateTestRequest {
    kind: string;
    locale: MailLocale;
    templateId?: string;
    layoutId?: string;
    subject?: string;
    bodyHtml?: string;
    to: string;
}

/** 시험 발송 결과 — 메일 서버 설정의 시험과 같은 모양(실패도 200) */
export type MailTemplateTestResultView = MailTestResultView;

/** 템플릿 변경 이력 — 설정 이력과 같은 모양(바뀐 칸 `name|locale|layoutId|subject|bodyHtml`, 본문은 글자 요약, 레이아웃은 이름) */
export type MailTemplateHistoryItemView = SettingHistoryItem;
export type MailTemplateHistoryPage = SettingHistoryPage<MailTemplateHistoryItemView>;

/** 종류당 사용자 템플릿 상한(계약 「미확정」 3 — 20 가정) */
export const MAIL_TEMPLATE_LIMIT = 20;
