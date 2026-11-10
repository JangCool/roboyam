import type { SiteOperatorProfile } from '~/composables/site/api/useSiteOperatorApi.types';

/**
 * 약관 본문의 자리표시(`{{…}}`)를 운영 주체 값으로 바꾸는 규칙 — 관리자 약관 화면의 [미리보기]가 쓴다.
 * ⚠ **게시할 때 서버가 같은 규칙으로 바꿔 저장한다**(게시본은 고치지 않으므로 지난 판 글자는 그대로 남는다). 서버 구현은 `TermsPlaceholders`(2026-11-07) — 이 파일은 미리보기용이다.
 *
 * 규칙
 *   1. 자리표시는 `{{이름}}`(중괄호 두 겹, 안쪽 앞뒤 공백 허용 — `{{ 운영자 }}` 도 같다). 아는 이름은 아래 10개뿐이다.
 *        `{{운영자}}`          → 개인·개인사업자 「운영자」, 법인 「회사」(호칭)
 *        `{{운영자이름}}`      → 개인은 운영자 이름, 개인사업자는 상호, 법인은 회사명(실제 이름 — 「{{운영자이름}}(이하 「{{운영자}}」)」 꼴로 쓴다)
 *        `{{서비스이름}}`      → 사이트 이름
 *        `{{운영자정보}}`      → 단계에 맞는 정보 목록(아래 3)
 *        `{{보호책임자}}`      → 개인정보 보호책임자 이름(「운영자와 같음」이면 운영자 이름)
 *        `{{보호책임자연락처}}` → 보호책임자 이메일(「같음」이면 연락 이메일, 전화가 있으면 「 · 전화」를 붙인다 — 「같음」일 때만 운영자 전화)
 *        `{{연락이메일}}`      → 연락 이메일
 *        `{{호스팅사업자}}`    → 호스팅 사업자(운영 주체 설정, 선택 칸)
 *        `{{메일발송사업자}}`  → 메일 발송 사업자(운영 주체 설정, 선택 칸)
 *        `{{시행일}}`          → 시행일 「YYYY년 M월 D일」(앞 0 없음). 미리보기에서는 게시 대화상자의 최소 시행일
 *   2. 모르는 이름은 **바꾸지 않고 그대로 두고** 목록(`unknown`, 처음 나온 차례, 중복 없음)으로 돌려준다 — 화면은 경고하고 서버는 게시를 거절한다.
 *      아는 이름인데 바꿀 값이 비었으면(운영 주체 설정에 그 칸을 적지 않음) 빈 글자로 바꾸고 목록(`empty`, 같은 차례)으로 돌려준다 — 화면은 경고하고 서버는 게시를 거절한다(빈칸 게시본 방지).
 *   3. `{{운영자정보}}` 목록(이름표: 값). 빈 값의 줄은 뺀다.
 *        개인       = 운영자 · 연락 이메일 · 전화
 *        개인사업자 = 상호 · 대표자 · 사업자등록번호 · 사업장 주소 · 전화 · 이메일 · 통신판매업 신고번호(「번호 (신고 기관)」)
 *        법인       = 회사명 · 대표자 · 사업자등록번호 · 법인등록번호 · 사업장 주소 · 전화 · 이메일 · 통신판매업 신고번호
 *      - 문단 하나를 통째로 차지하면(`<p>{{운영자정보}}</p>`, 앞뒤 공백 허용) 그 문단을 `<ul><li>이름표: 값</li>…</ul>` 로 바꾼다.
 *      - 글 중간에 있으면 「이름표 값, 이름표 값」 한 줄로 바꾼다.
 *   4. 바꿔 넣는 값은 HTML 이스케이프한다(`& < > " '`). 이름표·호칭은 고정 한국어(약관 본문이 한국어다).
 *   5. 자리표시가 서식 경계로 쪼개진 경우(`{{운영<strong>자</strong>}}`)는 자리표시로 보지 않는다(글자 그대로).
 */

/** 아는 자리표시 이름 — 칩 차례 */
export const TERMS_PLACEHOLDERS = ['운영자', '운영자이름', '서비스이름', '운영자정보', '보호책임자', '보호책임자연락처', '연락이메일', '호스팅사업자', '메일발송사업자', '시행일'] as const;

export type TermsPlaceholderName = (typeof TERMS_PLACEHOLDERS)[number];

/** 바꿀 값 — `UtilTermsPlaceholder.valuesOf` 가 만든다 */
export interface TermsPlaceholderValues {
    /** 「운영자」·「회사」 */
    operatorAlias: string;
    /** `{{운영자이름}}` — 개인은 운영자 이름, 개인사업자는 상호, 법인은 회사명 */
    operatorName: string;
    serviceName: string;
    /** `{{운영자정보}}` 목록(빈 값 줄은 이미 뺐다) */
    operatorInfo: Array<{ label: string; value: string }>;
    privacyOfficer: string;
    privacyOfficerContact: string;
    contactEmail: string;
    /** `{{호스팅사업자}}`(없으면 빈 글자) */
    hostingProvider: string;
    /** `{{메일발송사업자}}`(없으면 빈 글자) */
    mailProvider: string;
    /** 「YYYY년 M월 D일」로 이미 바꾼 글자 */
    effectiveDate: string;
}

/** 바꾼 결과 */
export interface TermsPlaceholderResult {
    html: string;
    /** 모르는 자리표시 이름(처음 나온 차례, 중복 없음) */
    unknown: string[];
    /** 아는 이름인데 바꿀 값이 빈 자리표시 이름(처음 나온 차례, 중복 없음) — 빈 글자로 바뀌었다 */
    empty: string[];
}

const TOKEN = /\{\{\s*([^{}<>]+?)\s*\}\}/g;
const INFO_PARAGRAPH = /<p(?:\s[^>]*)?>\s*\{\{\s*운영자정보\s*\}\}\s*<\/p>/g;

export default class UtilTermsPlaceholder {
    /**
     * 자리표시 글자.
     * @param {TermsPlaceholderName} name - 이름.
     * @returns {string} `{{운영자}}` 꼴.
     */
    static token(name: TermsPlaceholderName): string {
        return `{{${name}}}`;
    }

    /**
     * `YYYY-MM-DD` → 「YYYY년 M월 D일」.
     * @param {string} ymd - 날짜.
     * @returns {string} 바꾼 글자(모양이 다르면 그대로).
     */
    static koreanDate(ymd: string): string {
        const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(ymd);
        return m ? `${Number(m[1])}년 ${Number(m[2])}월 ${Number(m[3])}일` : ymd;
    }

    /**
     * 운영 주체 값 → 바꿀 값.
     * @param {SiteOperatorProfile} op - 운영 주체.
     * @param {string} serviceName - 사이트 이름.
     * @param {string} effectiveDate - 시행일(`YYYY-MM-DD`).
     * @returns {TermsPlaceholderValues} 바꿀 값.
     */
    static valuesOf(op: SiteOperatorProfile, serviceName: string, effectiveDate: string): TermsPlaceholderValues {
        const b = op.business;
        const corp = op.stage === 'CORPORATION';
        const rows: Array<[string, string | null | undefined]> =
            op.stage === 'INDIVIDUAL' || !b
                ? [
                      ['운영자', op.operatorName],
                      ['연락 이메일', op.contactEmail],
                      ['전화', op.phone],
                  ]
                : [
                      [corp ? '회사명' : '상호', b.businessName],
                      ['대표자', b.representative],
                      ['사업자등록번호', b.registrationNumber],
                      ['법인등록번호', corp ? b.corporateNumber : null],
                      ['사업장 주소', b.address],
                      ['전화', op.phone],
                      ['이메일', op.contactEmail],
                      ['통신판매업 신고번호', b.mailOrderNumber ? (b.mailOrderAuthority ? `${b.mailOrderNumber} (${b.mailOrderAuthority})` : b.mailOrderNumber) : null],
                  ];
        const same = op.privacyOfficerSameAsOperator;
        const officerEmail = same ? op.contactEmail : (op.privacyOfficerEmail ?? '');
        const officerPhone = same ? op.phone : null;
        const name = op.stage === 'INDIVIDUAL' || !b ? op.operatorName : b.businessName;
        return {
            operatorAlias: corp ? '회사' : '운영자',
            operatorName: (name ?? '').trim(),
            serviceName,
            operatorInfo: rows.filter((row): row is [string, string] => Boolean(row[1]?.trim())).map(([label, value]) => ({ label, value: value.trim() })),
            privacyOfficer: same ? op.operatorName : (op.privacyOfficerName ?? ''),
            privacyOfficerContact: officerPhone ? `${officerEmail} · ${officerPhone}` : officerEmail,
            contactEmail: op.contactEmail,
            hostingProvider: (op.hostingProvider ?? '').trim(),
            mailProvider: (op.mailProvider ?? '').trim(),
            effectiveDate: UtilTermsPlaceholder.koreanDate(effectiveDate),
        };
    }

    /**
     * 본문의 자리표시를 바꾼다(규칙은 파일 머리 주석).
     * @param {string} html - 편집기 본문(서식 HTML).
     * @param {TermsPlaceholderValues} values - 바꿀 값.
     * @returns {TermsPlaceholderResult} 바꾼 본문과 모르는 자리표시 목록.
     */
    static apply(html: string, values: TermsPlaceholderValues): TermsPlaceholderResult {
        const esc = UtilTermsPlaceholder.escape;
        const list = values.operatorInfo.length ? `<ul>${values.operatorInfo.map((row) => `<li>${esc(row.label)}: ${esc(row.value)}</li>`).join('')}</ul>` : '';
        const inline = values.operatorInfo.map((row) => `${esc(row.label)} ${esc(row.value)}`).join(', ');
        const known: Record<TermsPlaceholderName, string> = {
            운영자: esc(values.operatorAlias),
            운영자이름: esc(values.operatorName),
            서비스이름: esc(values.serviceName),
            운영자정보: inline,
            보호책임자: esc(values.privacyOfficer),
            보호책임자연락처: esc(values.privacyOfficerContact),
            연락이메일: esc(values.contactEmail),
            호스팅사업자: esc(values.hostingProvider),
            메일발송사업자: esc(values.mailProvider),
            시행일: esc(values.effectiveDate),
        };
        // 값이 빈 자리표시 — 목록으로 바꾸기 전 본문에서 찾는다(문단 하나짜리 `{{운영자정보}}` 도 같은 차례에 든다)
        const empty: string[] = [];
        for (const match of html.matchAll(TOKEN)) {
            const name = (match[1] ?? '').trim();
            if (UtilTermsPlaceholder.isKnown(name) && !known[name].trim() && !empty.includes(name)) empty.push(name);
        }
        const unknown: string[] = [];
        const out = html.replace(INFO_PARAGRAPH, list).replace(TOKEN, (whole, raw: string) => {
            const name = raw.trim();
            if (UtilTermsPlaceholder.isKnown(name)) return known[name];
            if (!unknown.includes(name)) unknown.push(name);
            return whole;
        });
        return { html: out, unknown, empty };
    }

    /**
     * 아는 자리표시 이름인가.
     * @param {string} name - 이름.
     * @returns {boolean} 10개 중 하나면 true.
     */
    static isKnown(name: string): name is TermsPlaceholderName {
        return (TERMS_PLACEHOLDERS as readonly string[]).includes(name);
    }

    /**
     * HTML 이스케이프.
     * @param {string} text - 글자.
     * @returns {string} `& < > " '` 를 바꾼 글자.
     */
    static escape(text: string): string {
        return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }
}
