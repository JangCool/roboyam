import type { BizAdType, BizAmount, BizApplicationKind, BizApplicationStatus, BizPeriod, BizQuote } from '~/composables/bizapplication/api/useBizApplicationApi.types';

type Severity = 'secondary' | 'info' | 'success' | 'warn' | 'danger' | 'contrast';

/** 입금 확인 입력 중인 값(화면 폼) */
export interface BizDepositDraft {
    amount: number | null;
    depositedOn: string;
    depositorName: string;
    mismatchReason: string;
}

const DAY_MS = 86_400_000;
const KST_OFFSET_MS = 9 * 3_600_000;

/**
 * 사업자 신청 건(광고·핫딜) 화면 규칙 — 입력만 보고 답하는 순수 함수.
 * 요금·기한·파일 규칙은 목업 가정(00-overview.md ★-6)이며 서버(`BizApplicationPolicy` 예정)가 다시 판정한다.
 */
export default class UtilBizApplication {
    /** 부가세율 10% */
    static readonly VAT_RATE = 0.1;
    /** 온라인 결제 기한(설정값 가정) */
    static readonly PAYMENT_DEADLINE_HOURS = 72;
    /** 수동 결제 입금 기한 기본(설정값 가정) */
    static readonly DEPOSIT_DEADLINE_DAYS = 7;
    /** 계약서 사본 — PDF·JPG·PNG, 한 장 10MB */
    static readonly CONTRACT_ACCEPT = 'application/pdf,image/jpeg,image/png';
    static readonly CONTRACT_MAX_BYTES = 10 * 1024 * 1024;
    /** 사유 글 길이 */
    static readonly REASON = { min: 5, max: 200 } as const;
    /** 심사 점검표 — 다 체크해야 [승인] */
    static readonly CHECKLIST = ['spec', 'link', 'policy', 'period', 'price'] as const;

    /** 하루 단가(공급가, 원) — 요금표 가정 */
    static readonly DAILY_PRICE: Record<BizAdType, number> = {
        WIDE_BANNER: 50_000,
        STRIP_BANNER: 30_000,
        SQUARE_BANNER: 25_000,
        SMALL_BANNER: 15_000,
        TEXT_LINE: 8_000,
        HOTDEAL_TOP: 20_000,
        HOTDEAL_CATEGORY: 10_000,
    };
    /** 사업자 핫딜 글 게시 — 건당 */
    static readonly POST_PRICE = 30_000;

    /** 기간의 날 수(반열림, 하루 미만은 하루로) */
    static days(period: BizPeriod): number {
        const ms = Date.parse(period.endAt) - Date.parse(period.startAt);
        return Math.max(1, Math.ceil(ms / DAY_MS));
    }

    static amountOf(supply: number): BizAmount {
        const s = Math.max(0, Math.round(supply));
        const vat = Math.floor(s * UtilBizApplication.VAT_RATE);
        return { supply: s, vat, total: s + vat };
    }

    /** 요금표 계산값 — 종류 × 하루 단가 × 일수(핫딜 글은 건당) + 부가세 */
    static quote(kind: BizApplicationKind, adType: BizAdType | null, period: BizPeriod): BizQuote {
        if (kind === 'HOTDEAL_POST' || !adType) {
            const a = UtilBizApplication.amountOf(UtilBizApplication.POST_PRICE);
            return { unitPrice: UtilBizApplication.POST_PRICE, units: 1, unit: 'POST', ...a };
        }
        const units = UtilBizApplication.days(period);
        const unitPrice = UtilBizApplication.DAILY_PRICE[adType];
        return { unitPrice, units, unit: 'DAY', ...UtilBizApplication.amountOf(unitPrice * units) };
    }

    /** 시작이 이미 지났나(F6 — 그대로는 승인 불가) */
    static started(period: BizPeriod, now = Date.now()): boolean {
        return Date.parse(period.startAt) <= now;
    }

    /** 같은 길이로 뒤로 옮긴 기간 — 시작은 다음 날 00:00(KST) */
    static shifted(period: BizPeriod, now = Date.now()): BizPeriod {
        const length = Date.parse(period.endAt) - Date.parse(period.startAt);
        const start = UtilBizApplication.kstMidnight(UtilBizApplication.addDays(UtilBizApplication.kstDate(new Date(now).toISOString()), 1));
        return { startAt: start, endAt: new Date(Date.parse(start) + length).toISOString() };
    }

    /** 일할 환불 제안 — 남은 날 / 전체 날 × 결제 금액(원 단위 내림) */
    static prorate(paid: number, usedDays: number, totalDays: number): number {
        if (totalDays <= 0) return 0;
        return Math.floor((paid * Math.max(0, totalDays - usedDays)) / totalDays);
    }

    /** 계약서 파일 문제 — 형식·크기 */
    static contractProblem(file: File): 'TYPE' | 'SIZE' | null {
        if (!UtilBizApplication.CONTRACT_ACCEPT.split(',').includes(file.type)) return 'TYPE';
        if (file.size > UtilBizApplication.CONTRACT_MAX_BYTES) return 'SIZE';
        return null;
    }

    static reasonProblem(text: string, min: number = UtilBizApplication.REASON.min): 'REQUIRED' | 'TOO_SHORT' | 'TOO_LONG' | null {
        const v = text.trim();
        if (!v) return 'REQUIRED';
        if (v.length < min) return 'TOO_SHORT';
        if (v.length > UtilBizApplication.REASON.max) return 'TOO_LONG';
        return null;
    }

    /**
     * 입금 확인 칸 문제 — 비면 해당 칸 키, 입금액이 계약 금액과 다른데 사유가 없으면 `mismatchReason`.
     * (서버 `BizDepositConfirm` 검사와 같은 규칙 — 다르면 저장은 되지만 [예약 확정] 전에 사유가 필요하다)
     */
    static depositProblems(draft: BizDepositDraft, contractTotal: number): Partial<Record<keyof BizDepositDraft, 'REQUIRED'>> {
        const out: Partial<Record<keyof BizDepositDraft, 'REQUIRED'>> = {};
        if (!draft.amount || draft.amount <= 0) out.amount = 'REQUIRED';
        if (!draft.depositedOn) out.depositedOn = 'REQUIRED';
        if (!draft.depositorName.trim()) out.depositorName = 'REQUIRED';
        if (draft.amount && draft.amount !== contractTotal && !draft.mismatchReason.trim()) out.mismatchReason = 'REQUIRED';
        return out;
    }

    static emptyDeposit(): BizDepositDraft {
        return { amount: null, depositedOn: '', depositorName: '', mismatchReason: '' };
    }

    static statusSeverity(status: BizApplicationStatus): Severity {
        switch (status) {
            case 'SUBMITTED':
                return 'info';
            case 'AWAITING_PAYMENT':
            case 'AWAITING_DEPOSIT':
                return 'warn';
            case 'SCHEDULED':
                return 'contrast';
            case 'RUNNING':
                return 'success';
            case 'TAKEN_DOWN':
            case 'REJECTED':
                return 'danger';
            default:
                return 'secondary';
        }
    }

    /** 금액 글자(원, 단위 없이 자릿수만) */
    static won(amount: number, locale: string): string {
        return new Intl.NumberFormat(locale === 'en' ? 'en-US' : 'ko-KR').format(amount);
    }

    static fileSize(bytes: number): string {
        if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
        return `${Math.max(1, Math.round(bytes / 1024))} KB`;
    }

    /** 남은 시간(초) → 「2일 3시간」 같은 짧은 글자에 쓸 조각 */
    static remainParts(deadlineAt: string, now = Date.now()): { days: number; hours: number; minutes: number; overdue: boolean } {
        const diff = Date.parse(deadlineAt) - now;
        const abs = Math.abs(diff);
        return { days: Math.floor(abs / DAY_MS), hours: Math.floor((abs % DAY_MS) / 3_600_000), minutes: Math.floor((abs % 3_600_000) / 60_000), overdue: diff < 0 };
    }

    // ── 날짜(KST 달력 날짜 글자 `YYYY-MM-DD`) — 목업·달력이 같이 쓴다 ──

    static kstDate(iso: string): string {
        return new Date(Date.parse(iso) + KST_OFFSET_MS).toISOString().slice(0, 10);
    }

    static kstToday(now = Date.now()): string {
        return UtilBizApplication.kstDate(new Date(now).toISOString());
    }

    static kstMidnight(ymd: string): string {
        const [y, m, d] = ymd.split('-').map(Number);
        return new Date(Date.UTC(y!, m! - 1, d!) - KST_OFFSET_MS).toISOString();
    }

    static addDays(ymd: string, n: number): string {
        const [y, m, d] = ymd.split('-').map(Number);
        return new Date(Date.UTC(y!, m! - 1, d! + n)).toISOString().slice(0, 10);
    }

    /** 기간이 그날(KST 00:00~24:00)과 겹치나 */
    static overlapsDay(period: BizPeriod, ymd: string): boolean {
        const dayStart = Date.parse(UtilBizApplication.kstMidnight(ymd));
        return Date.parse(period.startAt) < dayStart + DAY_MS && Date.parse(period.endAt) > dayStart;
    }
}
