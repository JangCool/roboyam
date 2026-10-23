import type { SettingHistoryItem, SettingHistoryPage } from '~/composables/common/useSettingHistory.types';
import type { TemplateAudience, TemplateLocale, TemplateVariableView } from '~/composables/template/api/useNotificationTemplateApi.types';

/**
 * 메일 레이아웃 API 의 모양 — `docs/features/message-template-contract.md` 「개정」(레이아웃 여러 개, 템플릿마다 고른다)·「개정 2」(쓰는 대상). 화면 목업 단계 — 서버 단계에서 확정한다.
 * 기본 레이아웃(코드가 준다, `builtin=true`)은 받는 사람별로 둘(「회원 기본」 · 「운영자 기본」)이고 읽기 전용이다 — 운영자가 복사해서 더 만든다. 쓰는 템플릿이 있는 레이아웃은 지울 수 없다(409 `LAYOUT_IN_USE`).
 * 템플릿은 받는 사람이 같거나 `ANY` 인 레이아웃만 고른다.
 */

/** 머리말 띠 색 — 팔레트(가정 8색). null 이면 띠 없이 흰 머리말 */
export type MailLayoutColor = 'BLUE' | 'INDIGO' | 'TEAL' | 'GREEN' | 'AMBER' | 'ORANGE' | 'RED' | 'SLATE';

/** 레이아웃을 쓰는 대상 — 받는 사람 하나 또는 모두(`ANY` — 틀·꼬리말에서 두 받는 사람이 같이 쓰는 공통 변수 `siteName`·`recipientName` 만 쓴다) */
export type MailLayoutAudience = TemplateAudience | 'ANY';

/** 쓰는 대상의 차례(고르기·거르기 차례) */
export const MAIL_LAYOUT_AUDIENCES: readonly MailLayoutAudience[] = ['MEMBER', 'OPERATOR', 'ANY'];

/** `GET /api/admin/mail-layouts` 의 한 줄 */
export interface MailLayoutListItemView {
    id: string;
    name: string;
    builtin: boolean;
    /** 쓰는 대상 — 기본 레이아웃은 받는 사람 하나(그 받는 사람의 새 템플릿이 이것으로 시작한다) */
    audience: MailLayoutAudience;
    headerColor: MailLayoutColor | null;
    /** 이 레이아웃을 쓰는 템플릿 수(기본 템플릿 포함) — 0 일 때만 지울 수 있다 */
    templateCount: number;
    /** UTC ISO — 기본 레이아웃은 null */
    updatedAt: string | null;
    updatedByName: string | null;
}

/** `GET /api/admin/mail-layouts/{id}` — 메일 한 통이 이 틀 안에 들어간다. `frames` 의 `{{content}}` 자리에 템플릿 본문이 들어간다 */
export interface MailLayoutView {
    id: string;
    name: string;
    builtin: boolean;
    audience: MailLayoutAudience;
    /** 맨 위에 사이트 로고를 보인다(로고는 사이트 설정 「일반」의 것) */
    showLogo: boolean;
    /** 맨 위에 사이트 이름을 보인다 */
    showSiteName: boolean;
    /** 머리말 띠 색(팔레트) — null 이면 띠 없음 */
    headerColor: MailLayoutColor | null;
    /** 언어별 꼬리말 — 평문(줄바꿈 가능) + 공통 변수. 본문 아래 가는 줄 밑에 작은 회색 글씨로 보인다 */
    footer: Record<TemplateLocale, string>;
    /** 언어별 틀 — 간단 서식 HTML, `{{content}}` 정확히 한 번 + 공통 변수 */
    frames: Record<TemplateLocale, string>;
    templateCount: number;
    /** 이 레이아웃을 쓰는 템플릿 수(받는 사람별) — 쓰는 대상을 바꿀 때 맞지 않는 받는 사람의 템플릿이 있으면 막는다(400 `fields.audience`) */
    templateCounts: Record<TemplateAudience, number>;
    updatedAt: string | null;
    updatedByName: string | null;
    /** 사이트 설정의 로고 주소(읽기 전용 — 없으면 null) */
    logoUrl: string | null;
    siteName: string;
    /** 틀·꼬리말에서 쓸 수 있는 변수 — 쓰는 대상별(그 대상의 공통 변수 + 본문 자리 `content` — `content` 는 틀에서만). 편집 중 대상을 바꾸면 화면이 그 대상의 목록으로 바꾼다 */
    variables: Record<MailLayoutAudience, TemplateVariableView[]>;
}

/** `POST·PUT /api/admin/mail-layouts[/{id}]` — 미리보기의 저장 전 값(`MailPreviewRequest.layout`)도 이 모양 */
export interface MailLayoutSaveRequest {
    name: string;
    /** 쓰는 대상 — 쓰는 템플릿의 받는 사람과 맞지 않으면 400 `fields.audience` */
    audience: MailLayoutAudience;
    showLogo: boolean;
    showSiteName: boolean;
    headerColor: MailLayoutColor | null;
    footer: Record<TemplateLocale, string>;
    frames: Record<TemplateLocale, string>;
}

/** 레이아웃 변경 이력 — 설정 이력과 같은 모양(바뀐 칸 `name|audience|showLogo|showSiteName|headerColor|footerKo|footerEn|frameKo|frameEn`, 틀은 글자 요약) */
export type MailLayoutHistoryItemView = SettingHistoryItem;
export type MailLayoutHistoryPage = SettingHistoryPage<MailLayoutHistoryItemView>;

/** `GET /api/admin/mail-layouts?audience=` 거르기(쓰는 대상 그대로) — 화면은 전부 받아 화면에서 거른다 */
export interface MailLayoutAudienceQuery {
    audience?: MailLayoutAudience;
}

/** 사용자 레이아웃 상한(가정 — 계약 「미확정」) */
export const MAIL_LAYOUT_LIMIT = 20;
