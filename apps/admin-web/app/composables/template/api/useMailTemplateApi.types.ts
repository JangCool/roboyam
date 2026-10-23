import type { SettingHistoryItem, SettingHistoryPage } from '~/composables/common/useSettingHistory.types';
import type { MessageAudience, MessageLocale, MessageVariableView } from '~/composables/template/api/useNotificationMessageApi.types';

/**
 * 메일 템플릿 API 의 모양 — `docs/features/message-template-contract.md` 「개정 4」(개정 3 까지의 「메일 레이아웃」 — 메일의 겉모양 틀). 화면 목업 단계 — 서버 단계에서 확정한다.
 * 칸은 개정 2 의 레이아웃 그대로다: 이름 · 쓰는 대상(회원/운영자/모두) · 로고·사이트 이름 보이기 · 머리말 띠 색 · 언어별 틀(`{{content}}` 1번) · 꼬리말.
 * 기본 메일 템플릿(코드가 준다, `builtin=true`)은 받는 사람별로 둘(「회원 기본」 · 「운영자 기본」)이고 읽기 전용이다 — 운영자가 복사해서 더 만든다. 쓰는 문구가 있는 템플릿은 지울 수 없다(409 `TEMPLATE_IN_USE`).
 * 이메일 문구는 받는 사람이 같거나 `ANY` 인 메일 템플릿만 고른다.
 */

/** 머리말 띠 색 — 팔레트(가정 8색). null 이면 띠 없이 흰 머리말 */
export type MailTemplateColor = 'BLUE' | 'INDIGO' | 'TEAL' | 'GREEN' | 'AMBER' | 'ORANGE' | 'RED' | 'SLATE';

/** 메일 템플릿을 쓰는 대상 — 받는 사람 하나 또는 모두(`ANY` — 틀·꼬리말에서 두 받는 사람이 같이 쓰는 공통 변수 `siteName`·`recipientName` 만 쓴다) */
export type MailTemplateAudience = MessageAudience | 'ANY';

/** 쓰는 대상의 차례(고르기·거르기 차례) */
export const MAIL_TEMPLATE_AUDIENCES: readonly MailTemplateAudience[] = ['MEMBER', 'OPERATOR', 'ANY'];

/** `GET /api/admin/notification-templates/mail` 의 한 줄 */
export interface MailTemplateListItemView {
    id: string;
    name: string;
    builtin: boolean;
    /** 쓰는 대상 — 기본 메일 템플릿은 받는 사람 하나(그 받는 사람의 새 이메일 문구가 이것으로 시작한다) */
    audience: MailTemplateAudience;
    headerColor: MailTemplateColor | null;
    /** 이 템플릿을 쓰는 문구 수(기본 문구 포함) — 0 일 때만 지울 수 있다 */
    messageCount: number;
    /** 쓰는 문구 수(받는 사람별) — 「모두」 템플릿은 받는 사람마다 그 문구 목록(운영자 · 회원)으로 따로 잇는다 */
    messageCounts: Record<MessageAudience, number>;
    /** UTC ISO — 기본 템플릿은 null */
    updatedAt: string | null;
    updatedByName: string | null;
}

/** `GET /api/admin/notification-templates/mail/{id}` — 메일 한 통이 이 틀 안에 들어간다. `frames` 의 `{{content}}` 자리에 문구의 본문이 들어간다 */
export interface MailTemplateView {
    id: string;
    name: string;
    builtin: boolean;
    audience: MailTemplateAudience;
    /** 맨 위에 사이트 로고를 보인다(로고는 사이트 설정 「일반」의 것) */
    showLogo: boolean;
    /** 맨 위에 사이트 이름을 보인다 */
    showSiteName: boolean;
    /** 머리말 띠 색(팔레트) — null 이면 띠 없음 */
    headerColor: MailTemplateColor | null;
    /** 언어별 꼬리말 — 평문(줄바꿈 가능) + 공통 변수. 본문 아래 가는 줄 밑에 작은 회색 글씨로 보인다 */
    footer: Record<MessageLocale, string>;
    /** 언어별 틀 — 간단 서식 HTML, `{{content}}` 정확히 한 번 + 공통 변수 */
    frames: Record<MessageLocale, string>;
    messageCount: number;
    /** 이 템플릿을 쓰는 문구 수(받는 사람별) — 쓰는 대상을 바꿀 때 맞지 않는 받는 사람의 문구가 있으면 막는다(400 `fields.audience`) */
    messageCounts: Record<MessageAudience, number>;
    updatedAt: string | null;
    updatedByName: string | null;
    /** 사이트 설정의 로고 주소(읽기 전용 — 없으면 null) */
    logoUrl: string | null;
    siteName: string;
    /** 틀·꼬리말에서 쓸 수 있는 변수 — 쓰는 대상별(그 대상의 공통 변수 + 본문 자리 `content` — `content` 는 틀에서만). 편집 중 대상을 바꾸면 화면이 그 대상의 목록으로 바꾼다 */
    variables: Record<MailTemplateAudience, MessageVariableView[]>;
}

/** `POST·PUT /api/admin/notification-templates/mail[/{id}]` — 미리보기의 저장 전 값(`MailPreviewRequest.template`)도 이 모양 */
export interface MailTemplateSaveRequest {
    name: string;
    /** 쓰는 대상 — 쓰는 문구의 받는 사람과 맞지 않으면 400 `fields.audience` */
    audience: MailTemplateAudience;
    showLogo: boolean;
    showSiteName: boolean;
    headerColor: MailTemplateColor | null;
    footer: Record<MessageLocale, string>;
    frames: Record<MessageLocale, string>;
}

/** 메일 템플릿 변경 이력 — 설정 이력과 같은 모양(바뀐 칸 `name|audience|showLogo|showSiteName|headerColor|footerKo|footerEn|frameKo|frameEn`, 틀은 글자 요약) */
export type MailTemplateHistoryItemView = SettingHistoryItem;
export type MailTemplateHistoryPage = SettingHistoryPage<MailTemplateHistoryItemView>;

/** `GET /api/admin/notification-templates/mail?audience=` 거르기(쓰는 대상 그대로) — 화면은 전부 받아 화면에서 거른다 */
export interface MailTemplateAudienceQuery {
    audience?: MailTemplateAudience;
}

/** 사용자 메일 템플릿 상한(가정 — 계약 「미확정」) */
export const MAIL_TEMPLATE_LIMIT = 20;
