import type { SettingHistoryItem, SettingHistoryPage } from '~/composables/common/useSettingHistory.types';
import type { MailLocale, MailVariableView } from '~/composables/template/api/useMailTemplateApi.types';

/**
 * 메일 레이아웃 API 의 모양 — `docs/features/message-template-contract.md` 「개정」(레이아웃 여러 개, 템플릿마다 고른다). 화면 목업 단계 — 서버 단계에서 확정한다.
 * 기본 레이아웃(코드가 준다, `builtin=true`)은 읽기 전용이고 운영자가 복사해서 더 만든다. 쓰는 템플릿이 있는 레이아웃은 지울 수 없다(409 `LAYOUT_IN_USE`).
 */

/** 머리말 띠 색 — 팔레트(가정 8색). null 이면 띠 없이 흰 머리말 */
export type MailLayoutColor = 'BLUE' | 'INDIGO' | 'TEAL' | 'GREEN' | 'AMBER' | 'ORANGE' | 'RED' | 'SLATE';

/** `GET /api/admin/mail-layouts` 의 한 줄 */
export interface MailLayoutListItemView {
    id: string;
    name: string;
    builtin: boolean;
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
    /** 맨 위에 사이트 로고를 보인다(로고는 사이트 설정 「일반」의 것) */
    showLogo: boolean;
    /** 맨 위에 사이트 이름을 보인다 */
    showSiteName: boolean;
    /** 머리말 띠 색(팔레트) — null 이면 띠 없음 */
    headerColor: MailLayoutColor | null;
    /** 언어별 꼬리말 — 평문(줄바꿈 가능) + 공통 변수. 본문 아래 가는 줄 밑에 작은 회색 글씨로 보인다 */
    footer: Record<MailLocale, string>;
    /** 언어별 틀 — 간단 서식 HTML, `{{content}}` 정확히 한 번 + 공통 변수 */
    frames: Record<MailLocale, string>;
    templateCount: number;
    updatedAt: string | null;
    updatedByName: string | null;
    /** 사이트 설정의 로고 주소(읽기 전용 — 없으면 null) */
    logoUrl: string | null;
    siteName: string;
    /** 틀·꼬리말에서 쓸 수 있는 변수(공통 변수 + 본문 자리 `content` — `content` 는 틀에서만) */
    variables: MailVariableView[];
}

/** `POST·PUT /api/admin/mail-layouts[/{id}]` — 미리보기의 저장 전 값(`MailPreviewRequest.layout`)도 이 모양 */
export interface MailLayoutSaveRequest {
    name: string;
    showLogo: boolean;
    showSiteName: boolean;
    headerColor: MailLayoutColor | null;
    footer: Record<MailLocale, string>;
    frames: Record<MailLocale, string>;
}

/** 레이아웃 변경 이력 — 설정 이력과 같은 모양(바뀐 칸 `name|showLogo|showSiteName|headerColor|footerKo|footerEn|frameKo|frameEn`, 틀은 글자 요약) */
export type MailLayoutHistoryItemView = SettingHistoryItem;
export type MailLayoutHistoryPage = SettingHistoryPage<MailLayoutHistoryItemView>;

/** 사용자 레이아웃 상한(가정 — 계약 「미확정」) */
export const MAIL_LAYOUT_LIMIT = 20;
