import type { OssCollectedItem, OssNoticeCategory, OssNoticeEntry, OssNoticeEntrySaveRequest } from '~/composables/osslicense/api/useOssLicenseAdminApi.types';

/** 직접 추가 항목 입력 폼 — 글자 칸은 빈 글자로 둔다(보낼 때 null) */
export interface OssNoticeForm {
    name: string;
    version: string;
    license: string;
    copyright: string;
    licenseText: string;
    sourceUrl: string;
    category: OssNoticeCategory;
    hidden: boolean;
}

export type OssNoticeFormField = 'name' | 'version' | 'license' | 'copyright' | 'licenseText' | 'sourceUrl' | 'category';
export type OssNoticeFormProblem = 'REQUIRED' | 'TOO_LONG' | 'URL';

/**
 * 오픈소스 라이선스 고지 관리의 순수 규칙 — 서버 `OssNotices`(V180 제약)와 같은 한도. 화면 검사는 미리 알려 주는 것이고 판정은 서버다.
 */
export default class UtilOssLicense {
    static readonly INTRO_MAX = 1000;
    static readonly NAME_MAX = 100;
    static readonly VERSION_MAX = 50;
    static readonly LICENSE_MAX = 100;
    static readonly COPYRIGHT_MAX = 2000;
    static readonly LICENSE_TEXT_MAX = 100_000;
    static readonly SOURCE_URL_MAX = 500;
    static readonly CATEGORIES: readonly OssNoticeCategory[] = ['USER', 'ADMIN', 'SERVER', 'FONT_ICON', 'OTHER'];

    /** 글자 수(서버 char_length 와 같이 코드 포인트) */
    static length(text: string | null | undefined): number {
        return text ? [...text].length : 0;
    }

    static emptyForm(): OssNoticeForm {
        return { name: '', version: '', license: '', copyright: '', licenseText: '', sourceUrl: '', category: 'OTHER', hidden: false };
    }

    static formOf(entry: OssNoticeEntry): OssNoticeForm {
        return {
            name: entry.name,
            version: entry.version ?? '',
            license: entry.license,
            copyright: entry.copyright ?? '',
            licenseText: entry.licenseText ?? '',
            sourceUrl: entry.sourceUrl ?? '',
            category: entry.category,
            hidden: entry.hidden,
        };
    }

    /** 보낼 본문 — 빈 칸은 null */
    static toRequest(form: OssNoticeForm): OssNoticeEntrySaveRequest {
        const orNull = (value: string): string | null => (value.trim() ? value : null);
        return {
            name: form.name.trim(),
            version: orNull(form.version.trim()),
            license: form.license.trim(),
            copyright: orNull(form.copyright),
            licenseText: orNull(form.licenseText),
            sourceUrl: orNull(form.sourceUrl.trim()),
            category: form.category,
            hidden: form.hidden,
        };
    }

    /** 칸별 문제(없으면 그 칸이 없다) */
    static problems(form: OssNoticeForm): Partial<Record<OssNoticeFormField, OssNoticeFormProblem>> {
        const out: Partial<Record<OssNoticeFormField, OssNoticeFormProblem>> = {};
        const name = form.name.trim();
        if (!name) out.name = 'REQUIRED';
        else if (UtilOssLicense.length(name) > UtilOssLicense.NAME_MAX) out.name = 'TOO_LONG';
        if (UtilOssLicense.length(form.version.trim()) > UtilOssLicense.VERSION_MAX) out.version = 'TOO_LONG';
        const license = form.license.trim();
        if (!license) out.license = 'REQUIRED';
        else if (UtilOssLicense.length(license) > UtilOssLicense.LICENSE_MAX) out.license = 'TOO_LONG';
        if (UtilOssLicense.length(form.copyright.trim()) > UtilOssLicense.COPYRIGHT_MAX) out.copyright = 'TOO_LONG';
        if (UtilOssLicense.length(form.licenseText.trim()) > UtilOssLicense.LICENSE_TEXT_MAX) out.licenseText = 'TOO_LONG';
        const url = form.sourceUrl.trim();
        if (UtilOssLicense.length(url) > UtilOssLicense.SOURCE_URL_MAX) out.sourceUrl = 'TOO_LONG';
        else if (url && !/^https?:\/\/\S+$/i.test(url)) out.sourceUrl = 'URL';
        return out;
    }

    /** 칸의 한도(넘침 문구용) */
    static maxOf(field: OssNoticeFormField): number {
        const max: Record<OssNoticeFormField, number> = {
            name: UtilOssLicense.NAME_MAX,
            version: UtilOssLicense.VERSION_MAX,
            license: UtilOssLicense.LICENSE_MAX,
            copyright: UtilOssLicense.COPYRIGHT_MAX,
            licenseText: UtilOssLicense.LICENSE_TEXT_MAX,
            sourceUrl: UtilOssLicense.SOURCE_URL_MAX,
            category: 0,
        };
        return max[field];
    }

    /** 자동 수집 목록 찾기 — 이름·라이선스(대소문자 무시) */
    static searchCollected(items: OssCollectedItem[], query: string): OssCollectedItem[] {
        const q = query.trim().toLowerCase();
        if (!q) return items;
        return items.filter((item) => item.name.toLowerCase().includes(q) || item.license.toLowerCase().includes(q));
    }

    /** 바깥 링크로 써도 되는 주소인가(http·https 만) */
    static safeUrl(url: string | null): string | null {
        return url && /^https?:\/\//i.test(url) ? url : null;
    }
}
