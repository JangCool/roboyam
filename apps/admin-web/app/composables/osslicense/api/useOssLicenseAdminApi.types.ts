/**
 * 오픈소스 라이선스 고지 관리가 주고받는 모양 — 서버 `/api/admin/oss-licenses`(계약 `docs/features/oss-licenses-contract.md`) + 정적 파일 `/oss-licenses.json`(자동 수집 목록, 읽기 전용).
 */

/** 직접 추가 항목의 묶음 — 공개 화면 · 관리자 화면 · 서버 · 글꼴·아이콘 · 기타 */
export type OssNoticeCategory = 'USER' | 'ADMIN' | 'SERVER' | 'FONT_ICON' | 'OTHER';

/** 직접 추가 항목 한 줄 */
export interface OssNoticeEntry {
    id: string;
    name: string;
    version: string | null;
    license: string;
    copyright: string | null;
    licenseText: string | null;
    sourceUrl: string | null;
    category: OssNoticeCategory;
    hidden: boolean;
    updatedAt: string;
}

/** `GET /api/admin/oss-licenses` */
export interface OssNoticeAdminView {
    /** 안내 문구 — null 이면 공개 화면 기본 문구 */
    intro: string | null;
    introUpdatedAt: string | null;
    /** 이름순 */
    entries: OssNoticeEntry[];
}

/** 만들기·고치기 본문 — 빈 선택 칸은 null(서버가 앞뒤 공백을 떼고 비면 null) */
export interface OssNoticeEntrySaveRequest {
    name: string;
    version: string | null;
    license: string;
    copyright: string | null;
    licenseText: string | null;
    sourceUrl: string | null;
    category: OssNoticeCategory;
    hidden: boolean;
}

/** 자동 수집 항목(정적 파일 — user-web 과 같은 모양) */
export interface OssCollectedItem {
    id: string;
    name: string;
    version: string;
    license: string;
    usedIn: ('user' | 'admin' | 'server')[];
    category: 'fontIcon' | null;
    copyright: string | null;
    textId: string | null;
    textSource: 'package' | 'template' | 'none';
    noticeId: string | null;
    repository: string | null;
    licenseUrl: string | null;
}

/** `/oss-licenses.json` */
export interface OssCollectedFile {
    schema: number;
    collectedAt: string;
    items: OssCollectedItem[];
    texts: Record<string, string>;
}
