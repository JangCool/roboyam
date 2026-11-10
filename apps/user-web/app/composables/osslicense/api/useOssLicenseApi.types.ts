/**
 * 오픈소스 라이선스 고지가 주고받는 모양 — 계약 `docs/features/oss-licenses-contract.md`.
 *   - 자동 수집 목록: 정적 파일 `/oss-licenses.json`(빌드 때 `scripts/collect-licenses.mjs` 가 만든다 — 관리자 화면도 같은 파일)
 *   - 안내 문구 + 직접 추가 항목: `GET /api/site/oss-licenses`
 */

/** 쓰는 곳 — 공개 화면 · 관리자 화면 · 서버 */
export type OssUsedIn = 'user' | 'admin' | 'server';

/** 자동 수집 항목 하나 */
/** 묶음 줄에 든 패키지 한 개(같은 프로젝트를 한 줄로 묶었을 때 — 사용자 2026-11-10) */
export interface OssCollectedPackage {
    name: string;
    version: string;
    license: string;
}

export interface OssCollectedItem {
    /** `npm:<이름>@<버전>` · `maven:<그룹>:<이름>@<버전>` */
    id: string;
    name: string;
    version: string;
    /** SPDX(또는 패키지가 밝힌 이름) — `A OR B` 꼴도 있다 */
    license: string;
    usedIn: OssUsedIn[];
    /** 글꼴·아이콘 묶음이면 `fontIcon` */
    category: 'fontIcon' | null;
    /** 저작권 고지(여러 줄은 `\n`) */
    copyright: string | null;
    /** 라이선스 전문 — `texts` 의 키(같은 전문은 한 번만 실린다) */
    textId: string | null;
    /** `package` 패키지의 라이선스 파일 · `template` 파일이 없어 표준 문구로 대신 · `none` 전문 없음(`licenseUrl` 만) */
    textSource: 'package' | 'template' | 'none';
    /** NOTICE 파일 — `texts` 의 키 */
    noticeId: string | null;
    /** 원본 저장소 */
    repository: string | null;
    /** 같은 프로젝트를 한 줄로 묶었으면 든 패키지(이름은 프로젝트 이름, 버전은 빈 글자) — 아니면 null(옛 파일은 칸 없음) */
    packages?: OssCollectedPackage[] | null;
    /** 라이선스 안내 주소(전문이 없을 때 등) */
    licenseUrl: string | null;
}

/** `/oss-licenses.json` */
export interface OssCollectedFile {
    schema: number;
    /** 마지막으로 모은 때(ISO) */
    collectedAt: string;
    items: OssCollectedItem[];
    texts: Record<string, string>;
}

/** 직접 추가 항목의 묶음 */
export type OssManualCategory = 'USER' | 'ADMIN' | 'SERVER' | 'FONT_ICON' | 'OTHER';

/** 직접 추가 항목(숨기지 않은 것) */
export interface OssManualEntry {
    id: string;
    name: string;
    version: string | null;
    license: string;
    copyright: string | null;
    licenseText: string | null;
    sourceUrl: string | null;
    category: OssManualCategory;
}

/** `GET /api/site/oss-licenses` 의 `data` */
export interface OssNoticePublic {
    /** 안내 문구 — null 이면 화면 기본 문구. `{사이트}` 는 화면이 사이트 이름으로 바꾼다 */
    intro: string | null;
    /** 이름순 */
    entries: OssManualEntry[];
}
