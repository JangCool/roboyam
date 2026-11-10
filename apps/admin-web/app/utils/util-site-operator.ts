import type { SiteFooterNoticeScope, SiteOperatorProfile, SiteOperatorRequest, SiteOperatorStage } from '~/composables/site/api/useSiteOperatorApi.types';

/** 입력칸 모양 — 모두 글자(빈 글자 = 없음). 사업자 칸은 개인 단계에서도 들고 있다(단계를 바꿔 볼 때 지운 듯 보이지 않게) */
export interface SiteOperatorForm {
    stage: SiteOperatorStage;
    operatorName: string;
    contactEmail: string;
    phone: string;
    privacyOfficerSameAsOperator: boolean;
    privacyOfficerName: string;
    privacyOfficerEmail: string;
    hostingProvider: string;
    businessName: string;
    representative: string;
    registrationNumber: string;
    address: string;
    mailOrderNumber: string;
    mailOrderAuthority: string;
    corporateNumber: string;
    /** 푸터 안내 문구 — 여러 줄 입력 그대로(한 줄에 한 문장, 빈 글자 = 숨김) */
    footerNotice: string;
    footerNoticeScope: SiteFooterNoticeScope;
}

/** 칸 이름 — 서버 `fields` 의 열쇠와 같다(사업자 칸은 `business.` 이 붙는다 — `UtilSiteOperator.fieldKey`) */
export type SiteOperatorField = Exclude<keyof SiteOperatorForm, 'stage' | 'privacyOfficerSameAsOperator' | 'footerNoticeScope'>;

/** 칸 문제 — 비었음 · 길이 · 이메일 모양 · 전화 글자 · 사업자등록번호 모양 · 푸터 안내 줄 수 · 푸터 안내 한 줄 길이 */
export type SiteOperatorProblem = 'REQUIRED' | 'LENGTH' | 'EMAIL' | 'PHONE' | 'BIZ_NUMBER' | 'NOTICE_LINES' | 'NOTICE_LINE_LENGTH';

export type SiteOperatorProblems = Partial<Record<SiteOperatorField, SiteOperatorProblem>>;

/** 사업자 칸 — 개인사업자·법인일 때만 보이고 보낸다 */
const BUSINESS_FIELDS: readonly SiteOperatorField[] = ['businessName', 'representative', 'registrationNumber', 'address', 'mailOrderNumber', 'mailOrderAuthority', 'corporateNumber'];

/**
 * 일반 설정 「운영 주체」 구획의 순수 규칙 — 입력 미리 검사·사업자등록번호 하이픈 맞춤·바뀐 칸 수·저장 요청 만들기.
 * 판정은 서버(`SiteOperatorService`)이고 여기 규칙(필수 여부·길이·글자 모양)은 서버와 같다 — 화면은 미리 알려 주기만 한다.
 */
export default class UtilSiteOperator {
    /** 글자 칸 전부(화면 차례) */
    static readonly FIELDS: readonly SiteOperatorField[] = [
        'operatorName',
        'contactEmail',
        'phone',
        'privacyOfficerName',
        'privacyOfficerEmail',
        'hostingProvider',
        ...BUSINESS_FIELDS,
        'footerNotice',
    ];

    /** 운영자 이름·보호책임자 이름 1~30자 */
    static readonly NAME_MAX = 30;
    /** 이메일 254자(RFC 5321) */
    static readonly EMAIL_MAX = 254;
    static readonly PHONE_MAX = 20;
    static readonly SHORT_MAX = 50;
    static readonly ADDRESS_MAX = 200;
    /** 법인등록번호 20자(서버 `CORPORATE_NUMBER_MAX`) */
    static readonly CORPORATE_NUMBER_MAX = 20;
    /** 아주 느슨한 이메일 모양 — 판정은 서버 */
    static readonly EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    /** 전화번호에 쓸 수 있는 글자(숫자·+·-·괄호·띄어쓰기) */
    static readonly PHONE_PATTERN = /^[0-9+\-() ]+$/;
    /** 사업자등록번호 `123-45-67890` */
    static readonly BIZ_NUMBER_PATTERN = /^\d{3}-\d{2}-\d{5}$/;
    /** 푸터 안내 문구 최대 줄 수(서버 `SiteFooterNotice.LINES_MAX`) */
    static readonly NOTICE_LINES_MAX = 3;
    /** 푸터 안내 문구 줄마다 글자 수(서버 `SiteFooterNotice.LINE_MAX`, 코드 포인트) */
    static readonly NOTICE_LINE_MAX = 120;

    /**
     * 푸터 안내 문구 → 줄 — 줄마다 앞뒤 공백을 떼고 빈 줄은 버린다(서버 `SiteFooterNotice.split` 과 같다).
     * @param {string} text - 여러 줄 입력.
     * @returns {string[]} 줄(없으면 빈 배열 = 숨김).
     */
    static noticeLines(text: string): string[] {
        return text
            .split(/\r\n|\r|\n/)
            .map((line) => line.trim())
            .filter((line) => line.length > 0);
    }

    /**
     * 사업자 단계인가(개인사업자·법인).
     * @param {SiteOperatorStage} stage - 단계.
     * @returns {boolean} 개인이 아니면 true.
     */
    static isBusiness(stage: SiteOperatorStage): boolean {
        return stage !== 'INDIVIDUAL';
    }

    /**
     * 서버 값 → 입력칸.
     * @param {SiteOperatorProfile} profile - 저장된 값.
     * @returns {SiteOperatorForm} 입력칸 값.
     */
    static formOf(profile: SiteOperatorProfile): SiteOperatorForm {
        const b = profile.business;
        return {
            stage: profile.stage,
            operatorName: profile.operatorName,
            contactEmail: profile.contactEmail,
            phone: profile.phone ?? '',
            privacyOfficerSameAsOperator: profile.privacyOfficerSameAsOperator,
            privacyOfficerName: profile.privacyOfficerName ?? '',
            privacyOfficerEmail: profile.privacyOfficerEmail ?? '',
            hostingProvider: profile.hostingProvider ?? '',
            businessName: b?.businessName ?? '',
            representative: b?.representative ?? '',
            registrationNumber: b?.registrationNumber ?? '',
            address: b?.address ?? '',
            mailOrderNumber: b?.mailOrderNumber ?? '',
            mailOrderAuthority: b?.mailOrderAuthority ?? '',
            corporateNumber: b?.corporateNumber ?? '',
            footerNotice: profile.footerNotice ?? '',
            footerNoticeScope: profile.footerNoticeScope === 'ALWAYS' ? 'ALWAYS' : 'INDIVIDUAL_ONLY',
        };
    }

    /**
     * 입력칸 → 저장 요청 — 앞뒤 공백을 떼고 빈 글자는 null. 개인은 `business` null, 보호책임자가 같음이면 두 칸 null, 법인이 아니면 법인등록번호 null.
     * @param {SiteOperatorForm} form - 입력칸 값.
     * @returns {SiteOperatorRequest} 서버에 보낼 값(평범한 객체).
     */
    static request(form: SiteOperatorForm): SiteOperatorRequest {
        const opt = (value: string): string | null => value.trim() || null;
        const same = form.privacyOfficerSameAsOperator;
        return {
            stage: form.stage,
            operatorName: form.operatorName.trim(),
            contactEmail: form.contactEmail.trim(),
            phone: opt(form.phone),
            privacyOfficerSameAsOperator: same,
            privacyOfficerName: same ? null : opt(form.privacyOfficerName),
            privacyOfficerEmail: same ? null : opt(form.privacyOfficerEmail),
            hostingProvider: opt(form.hostingProvider),
            business: UtilSiteOperator.isBusiness(form.stage)
                ? {
                      businessName: form.businessName.trim(),
                      representative: form.representative.trim(),
                      registrationNumber: form.registrationNumber.trim(),
                      address: form.address.trim(),
                      mailOrderNumber: opt(form.mailOrderNumber),
                      mailOrderAuthority: opt(form.mailOrderAuthority),
                      corporateNumber: form.stage === 'CORPORATION' ? opt(form.corporateNumber) : null,
                  }
                : null,
            // 푸터 안내 — 줄마다 앞뒤 공백을 떼고 빈 줄을 버려 `\n` 으로 잇는다. 다 비면 빈 글자(= 숨김, null 은 서버가 「그대로 둠」으로 읽는다)
            footerNotice: UtilSiteOperator.noticeLines(form.footerNotice).join('\n'),
            footerNoticeScope: form.footerNoticeScope,
        };
    }

    /**
     * 저장된 값과 다른 칸 수 — 양쪽 모두 보내는 값(`request`) 모양으로 맞춰 비교하므로 숨은 칸(개인 단계의 사업자 칸 등)·응답에만 있는 칸(`updatedAt`·`history`)은 세지 않는다.
     * @param {SiteOperatorProfile} saved - 저장된 값.
     * @param {SiteOperatorForm} form - 입력칸 값.
     * @returns {number} 바뀐 칸 수(단계 포함).
     */
    static changes(saved: SiteOperatorProfile, form: SiteOperatorForm): number {
        const flat = (p: SiteOperatorRequest): Record<string, unknown> => {
            const { business, ...rest } = p;
            return { ...rest, ...(business ? Object.fromEntries(Object.entries(business).map(([k, v]) => [`business.${k}`, v])) : {}) };
        };
        const a = flat(UtilSiteOperator.request(UtilSiteOperator.formOf(saved)));
        const b = flat(UtilSiteOperator.request(form));
        const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
        let count = 0;
        for (const key of keys) if ((a[key] ?? null) !== (b[key] ?? null)) count += 1;
        return count;
    }

    /**
     * 보이는 칸의 문제 — 숨은 칸(개인 단계의 사업자 칸, 「같음」일 때의 보호책임자 칸)은 보지 않는다.
     * @param {SiteOperatorForm} form - 입력칸 값.
     * @returns {SiteOperatorProblems} 칸 → 문제(없으면 빈 객체).
     */
    static validate(form: SiteOperatorForm): SiteOperatorProblems {
        const problems: SiteOperatorProblems = {};
        const business = UtilSiteOperator.isBusiness(form.stage);
        const need = (field: SiteOperatorField, required: boolean, shape?: (v: string) => SiteOperatorProblem | null): void => {
            const value = form[field].trim();
            const max = UtilSiteOperator.maxOf(field);
            if (!value) {
                if (required) problems[field] = 'REQUIRED';
                return;
            }
            // 서버는 코드 포인트로 센다(이모지 한 개 = 1자)
            if (Array.from(value).length > max) problems[field] = 'LENGTH';
            else if (shape) {
                const problem = shape(value);
                if (problem) problems[field] = problem;
            }
        };
        const email = (v: string): SiteOperatorProblem | null => (UtilSiteOperator.EMAIL_PATTERN.test(v) ? null : 'EMAIL');
        const phone = (v: string): SiteOperatorProblem | null => (UtilSiteOperator.PHONE_PATTERN.test(v) ? null : 'PHONE');

        need('operatorName', true);
        need('contactEmail', true, email);
        need('phone', business, phone);
        if (!form.privacyOfficerSameAsOperator) {
            need('privacyOfficerName', true);
            need('privacyOfficerEmail', true, email);
        }
        need('hostingProvider', false);
        if (business) {
            need('businessName', true);
            need('representative', true);
            need('registrationNumber', true, (v) => (UtilSiteOperator.BIZ_NUMBER_PATTERN.test(v) ? null : 'BIZ_NUMBER'));
            need('address', true);
            need('mailOrderNumber', false);
            need('mailOrderAuthority', false);
            if (form.stage === 'CORPORATION') need('corporateNumber', false);
        }
        // 푸터 안내 — 선택(비우면 숨김). 줄 수 → 한 줄 길이 차례
        const notice = UtilSiteOperator.noticeLines(form.footerNotice);
        if (notice.length > UtilSiteOperator.NOTICE_LINES_MAX) problems.footerNotice = 'NOTICE_LINES';
        else if (notice.some((line) => Array.from(line).length > UtilSiteOperator.NOTICE_LINE_MAX)) problems.footerNotice = 'NOTICE_LINE_LENGTH';
        return problems;
    }

    /**
     * 칸의 글자 수 상한(문구의 `{max}`).
     * @param {SiteOperatorField} field - 칸.
     * @returns {number} 상한.
     */
    static maxOf(field: SiteOperatorField): number {
        switch (field) {
            case 'operatorName':
            case 'privacyOfficerName':
            case 'representative':
                return UtilSiteOperator.NAME_MAX;
            case 'contactEmail':
            case 'privacyOfficerEmail':
                return UtilSiteOperator.EMAIL_MAX;
            case 'phone':
                return UtilSiteOperator.PHONE_MAX;
            case 'corporateNumber':
                return UtilSiteOperator.CORPORATE_NUMBER_MAX;
            case 'registrationNumber':
                return 12;
            case 'address':
                return UtilSiteOperator.ADDRESS_MAX;
            case 'footerNotice':
                return UtilSiteOperator.NOTICE_LINE_MAX;
            default:
                return UtilSiteOperator.SHORT_MAX;
        }
    }

    /**
     * 칸의 서버 `fields` 열쇠 — 사업자 칸은 `business.` 이 붙는다.
     * @param {SiteOperatorField} field - 칸.
     * @returns {string} `operatorName` · `business.registrationNumber` 꼴.
     */
    static fieldKey(field: SiteOperatorField): string {
        return BUSINESS_FIELDS.includes(field) ? `business.${field}` : field;
    }

    /**
     * 사업자등록번호 하이픈 맞춤 — 숫자만 남겨 3-2-5 로 끊는다(10자리 넘는 숫자는 버린다).
     * @param {string} input - 입력한 글자.
     * @returns {string} `123-45-67890` 꼴(덜 쓴 동안은 `123-4` 처럼 쓴 데까지).
     */
    static formatBizNumber(input: string): string {
        const digits = input.replace(/\D/g, '').slice(0, 10);
        if (digits.length <= 3) return digits;
        if (digits.length <= 5) return `${digits.slice(0, 3)}-${digits.slice(3)}`;
        return `${digits.slice(0, 3)}-${digits.slice(3, 5)}-${digits.slice(5)}`;
    }
}
