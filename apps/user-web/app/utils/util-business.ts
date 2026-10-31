import type { AuthUser, Role } from '~/composables/auth/api/useAuthApi.types';
import type { AdType, BizAmount, BizApplicationBucket, BizApplicationKind, BizApplicationStatus, BizHistoryEntry, BizSchedulePhase } from '~/composables/business/api/useBusinessApi.types';

/** 사용자 메뉴의 사업자 줄 — `CENTER` 사업자 센터(신청했거나 사업자 전용 계정) · `APPLY` 사업자 신청(일반 회원, 신청 전) */
export type BusinessEntry = 'CENTER' | 'APPLY';

/** 신청 건 상세의 단계 표시(신청 → 심사 → 결제 → 진행 → 종료) */
export type BizStage = 'SUBMIT' | 'REVIEW' | 'PAYMENT' | 'RUN' | 'END';

/** 상태 딱지의 톤(`PointShopTag`) */
export type BizTone = 'neutral' | 'primary' | 'success' | 'warn' | 'danger';

/**
 * 사업자 규칙 — 셸(헤더 메뉴·햄버거·글쓰기 버튼)과 사업자 센터 화면이 쓴다. 판정은 서버이고 여기는 미리 알려 주는 용도다.
 * 요구사항 정본 `docs/plans/biz-ad-hotdeal/00-overview.md` 「★ 확정」.
 */
export default class UtilBusiness {
    /** 사업자 센터 주소 */
    static readonly CENTER_PATH = '/business';
    static readonly APPLY_PATH = '/business/apply';
    static readonly AD_NEW_PATH = '/business/ads/new';
    static readonly DEAL_NEW_PATH = '/business/deals/new';
    static readonly APPLICATIONS_PATH = '/business/applications';

    /** 부가세율(★-6 가정 — 10%) */
    static readonly VAT_RATE = 0.1;
    /** 결제 기한 임박 경고 — 24시간 전(F9) */
    static readonly DUE_WARN_MS = 24 * 60 * 60 * 1000;
    /** 첨부 한 장 최대 크기(B2 — 10MB) */
    static readonly FILE_MAX_BYTES = 10 * 1024 * 1024;
    /** 사업자등록증 사본 형식(B2) */
    static readonly CERTIFICATE_ACCEPT = 'application/pdf,image/jpeg,image/png';
    /** 광고 이미지 형식 */
    static readonly IMAGE_ACCEPT = 'image/jpeg,image/png,image/webp,image/gif';

    /** 광고 종류 차례(카드 순서) */
    static readonly AD_TYPES: readonly AdType[] = ['WIDE', 'STRIP', 'SQUARE', 'SMALL', 'TEXT_LINE'];

    /** 목록 거르기 차례 */
    static readonly BUCKETS: readonly BizApplicationBucket[] = ['ALL', 'REVIEW', 'AWAITING_PAYMENT', 'AWAITING_DEPOSIT', 'UPCOMING', 'RUNNING', 'ENDED', 'CLOSED'];

    /**
     * 사업자 전용 계정인가(H-2) — 일반 게시판 글쓰기·소모임·쪽지 보내기·출석·포인트를 못 쓴다.
     * @param {Role | null | undefined} role - 계정의 역할.
     * @returns {boolean} `BUSINESS` 이면 true.
     */
    static isBusinessAccount(role: Role | null | undefined): boolean {
        return role === 'BUSINESS';
    }

    /**
     * 사용자 메뉴의 사업자 줄 — 사업자 전용 계정이거나 신청한 적이 있으면 "사업자 센터", 일반 회원이고 신청 전이면 "사업자 신청", 운영진·비회원은 줄이 없다.
     * @param {Pick<AuthUser, 'role' | 'businessStatus'> | null | undefined} user - 로그인한 사람.
     * @returns {BusinessEntry | null} 줄의 종류. 줄이 없으면 null.
     */
    static entryOf(user: Pick<AuthUser, 'role' | 'businessStatus'> | null | undefined): BusinessEntry | null {
        if (!user) return null;
        if (user.role === 'BUSINESS') return 'CENTER';
        const status = user.businessStatus ?? 'NONE';
        if (status !== 'NONE') return 'CENTER';
        return user.role === 'USER' ? 'APPLY' : null;
    }

    /** 신청 건 상세 주소 */
    static applicationPath(id: string): string {
        return `${UtilBusiness.APPLICATIONS_PATH}/${encodeURIComponent(id)}`;
    }

    /**
     * 사업자등록번호 체크섬(국세청 공개 규칙 — 서버 `BusinessNumberValidator` 와 같은 규칙으로 둔다).
     * @param {string} value - 하이픈이 있어도 되는 번호.
     * @returns {boolean} 숫자 10자리이고 검증 자리가 맞으면 true.
     */
    static isValidBizNo(value: string): boolean {
        const digits = UtilBusiness.digits(value);
        if (digits.length !== 10) return false;
        const d = digits.split('').map(Number);
        const weights = [1, 3, 7, 1, 3, 7, 1, 3, 5];
        let sum = 0;
        for (let i = 0; i < 9; i++) sum += d[i]! * weights[i]!;
        sum += Math.floor((d[8]! * 5) / 10);
        return (10 - (sum % 10)) % 10 === d[9];
    }

    /** 숫자만 남긴다 */
    static digits(value: string | null | undefined): string {
        return (value ?? '').replace(/\D/g, '');
    }

    /**
     * 예상 금액 — 「하루 단가 × 일수 + 부가세 10%」(★-6 가정). 최종 금액은 운영자가 승인 때 확정한다.
     * @param {number} dailyPrice - 하루 단가(공급가).
     * @param {number} days - 일수.
     * @returns {BizAmount} 공급가·부가세·합계.
     */
    static estimate(dailyPrice: number, days: number): BizAmount {
        const supply = Math.max(0, dailyPrice) * Math.max(0, days);
        const vat = Math.floor(supply * UtilBusiness.VAT_RATE);
        return { supply, vat, total: supply + vat };
    }

    /** 두 달력 날짜(`YYYY-MM-DD`, 끝 포함) 사이의 일수 */
    static daysBetween(start: string, end: string): number {
        if (!start || !end) return 0;
        const a = Date.UTC(+start.slice(0, 4), +start.slice(5, 7) - 1, +start.slice(8, 10));
        const b = Date.UTC(+end.slice(0, 4), +end.slice(5, 7) - 1, +end.slice(8, 10));
        return Math.max(0, Math.round((b - a) / 86_400_000) + 1);
    }

    /** 할인율(정수 %) — 정상가가 없거나 딜가가 더 크면 null */
    static discountRate(listPrice: number | null, dealPrice: number | null): number | null {
        if (!listPrice || !dealPrice || dealPrice >= listPrice) return null;
        return Math.round(((listPrice - dealPrice) / listPrice) * 100);
    }

    /** 광고 종류인가(핫딜 아님) */
    static isAd(kind: BizApplicationKind): boolean {
        return kind === 'AD_BANNER' || kind === 'AD_TEXT_LINE';
    }

    /**
     * 목록 거르기 묶음 — 상태(+ 예약의 시각 단계)를 화면 묶음으로.
     * @returns {Exclude<BizApplicationBucket, 'ALL'>} 묶음.
     */
    static bucketOf(status: BizApplicationStatus, phase: BizSchedulePhase | null): Exclude<BizApplicationBucket, 'ALL'> {
        switch (status) {
            case 'SUBMITTED':
                return 'REVIEW';
            case 'AWAITING_PAYMENT':
                return 'AWAITING_PAYMENT';
            case 'AWAITING_DEPOSIT':
                return 'AWAITING_DEPOSIT';
            case 'SCHEDULED':
                return phase === 'RUNNING' ? 'RUNNING' : phase === 'ENDED' ? 'ENDED' : 'UPCOMING';
            case 'TAKEN_DOWN':
                return 'RUNNING';
            default:
                return 'CLOSED';
        }
    }

    /** 상태 딱지 글자 키의 끝 마디 — `SCHEDULED` 는 시각 단계로 */
    static statusKey(status: BizApplicationStatus, phase: BizSchedulePhase | null): string {
        return status === 'SCHEDULED' && phase ? `SCHEDULED_${phase}` : status;
    }

    /** 상태 딱지 톤 */
    static statusTone(status: BizApplicationStatus, phase: BizSchedulePhase | null): BizTone {
        switch (status) {
            case 'SUBMITTED':
                return 'primary';
            case 'AWAITING_PAYMENT':
            case 'AWAITING_DEPOSIT':
                return 'warn';
            case 'SCHEDULED':
                return phase === 'ENDED' ? 'neutral' : 'success';
            case 'REJECTED':
            case 'TAKEN_DOWN':
                return 'danger';
            default:
                return 'neutral';
        }
    }

    /**
     * 단계 표시의 지금 단계 — 반려·취소·기한 지남은 멈춘 자리(`stopped`)로.
     * @returns {{ current: BizStage; stopped: boolean }} 지금 단계와 멈춤 여부.
     */
    static stageOf(status: BizApplicationStatus, phase: BizSchedulePhase | null, paid: boolean): { current: BizStage; stopped: boolean } {
        switch (status) {
            case 'SUBMITTED':
                return { current: 'REVIEW', stopped: false };
            case 'REJECTED':
                return { current: 'REVIEW', stopped: true };
            case 'AWAITING_PAYMENT':
            case 'AWAITING_DEPOSIT':
                return { current: 'PAYMENT', stopped: false };
            case 'EXPIRED':
                return { current: 'PAYMENT', stopped: true };
            case 'SCHEDULED':
                return phase === 'ENDED' ? { current: 'END', stopped: false } : phase === 'RUNNING' ? { current: 'RUN', stopped: false } : { current: 'PAYMENT', stopped: false };
            case 'TAKEN_DOWN':
                return { current: 'RUN', stopped: true };
            case 'CANCELED':
                return { current: paid ? 'RUN' : 'REVIEW', stopped: true };
        }
    }

    /**
     * 진행 중 건의 남은 기간 일할 금액(제안값 — 운영팀이 확인한다, H1).
     * @param {number} total - 결제 합계.
     * @param {string} start - 시작 날짜.
     * @param {string} end - 끝 날짜(포함).
     * @param {string} today - 오늘(서비스 날짜).
     * @returns {number} 남은 날(오늘 제외) 비율만큼의 금액(원, 버림).
     */
    static proratedRefund(total: number, start: string, end: string, today: string): number {
        const all = UtilBusiness.daysBetween(start, end);
        if (all <= 0) return 0;
        const left = Math.max(0, UtilBusiness.daysBetween(today, end) - 1);
        return Math.floor((total * Math.min(left, all)) / all);
    }

    /** 오늘(브라우저 기준 달력 날짜 `YYYY-MM-DD`) — 목업·미리보기용 */
    static todayYmd(now: number = Date.now()): string {
        const d = new Date(now);
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    }

    /** 달력 날짜에 일수를 더한다 */
    static addDays(ymd: string, days: number): string {
        const d = new Date(+ymd.slice(0, 4), +ymd.slice(5, 7) - 1, +ymd.slice(8, 10) + days);
        return UtilBusiness.todayYmd(d.getTime());
    }

    /** 한국 날짜(`YYYY-MM-DD`)의 00:00(KST) 순간 */
    static kstStartOf(ymd: string): string {
        return new Date(`${ymd}T00:00:00+09:00`).toISOString();
    }

    /**
     * 처리 기록 + 시각으로 계산한 「노출 시작」·「노출 종료」 — 서버는 이 둘을 기록하지 않는다(`phase` 로 계산). 시각순(오래된 것 먼저)으로 돌려준다.
     * 시작 = 시작 날짜 00:00(KST), 종료 = 끝 날짜 다음 날 00:00(KST — 끝 날짜 포함).
     */
    static withPhaseHistory(history: BizHistoryEntry[], status: BizApplicationStatus, phase: BizSchedulePhase | null, startDate: string, endDate: string): BizHistoryEntry[] {
        const out = [...history];
        if (status === 'SCHEDULED' && (phase === 'RUNNING' || phase === 'ENDED')) out.push({ at: UtilBusiness.kstStartOf(startDate), actor: 'SYSTEM', action: 'STARTED', note: null });
        if (status === 'SCHEDULED' && phase === 'ENDED') out.push({ at: UtilBusiness.kstStartOf(UtilBusiness.addDays(endDate, 1)), actor: 'SYSTEM', action: 'ENDED', note: null });
        return out.sort((a, b) => Date.parse(a.at) - Date.parse(b.at));
    }

    /** 단축 URL·형식 검사 — 링크 칸 미리 알림(서버가 다시 판정) */
    static isHttpUrl(value: string): boolean {
        try {
            const url = new URL(value.trim());
            return url.protocol === 'http:' || url.protocol === 'https:';
        } catch {
            return false;
        }
    }
}
