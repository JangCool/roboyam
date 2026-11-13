import type { AuthUser, Role } from '~/composables/auth/api/useAuthApi.types';
import type {
    AdType,
    BizAmount,
    BizApplicationBucket,
    BizApplicationDetail,
    BizApplicationKind,
    BizApplicationStatus,
    BizHistoryEntry,
    BizSchedulePhase,
    BizScheduledPriceChange,
    BizTakedownCause,
    BusinessDocSendTo,
    BusinessProfile,
    BusinessProfileService,
    BusinessServiceKind,
} from '~/composables/business/api/useBusinessApi.types';

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
    /** 배너 규격 비율(가로 ÷ 세로) — `business.adTypeSize`(1200×300 등)와 같은 값, 한 줄 광고는 없음 */
    static readonly BANNER_RATIO: Readonly<Record<AdType, number | null>> = { WIDE: 4, STRIP: 12, SQUARE: 6 / 5, SMALL: 3, WING: 4 / 15, TEXT_LINE: null };
    /** 배너 비율 허용 폭 ±3%(docs/plans/biz-ad-hotdeal/02-ad.md 미디어 업로드) — 공개 자리는 규격 틀에 맞춰 잘라 보이므로 크게 다르면 막는다 */
    static readonly BANNER_RATIO_TOLERANCE = 0.03;

    /** 광고 종류 차례(카드 순서) */
    static readonly AD_TYPES: readonly AdType[] = ['WIDE', 'STRIP', 'SQUARE', 'SMALL', 'WING', 'TEXT_LINE'];

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

    /**
     * 신원은 승인인데 그 서비스만 정지된 줄(2026-11-02 서비스별 정지 — `GET /api/business/me` 의 `services[]`). 신원이 승인이 아니면 빈 목록(신원 상태가 우선).
     * 신원 정지를 따라간 줄은 신원이 승인이 아니라 여기서 걸러진다. 서버 순서에 기대지 않고 핫딜·광고 차례로 놓는다.
     * @param {Pick<BusinessProfile, 'status' | 'services'>} profile - 내 사업자 신원.
     * @returns {BusinessProfileService[]} 서비스만 정지된 줄(핫딜·광고 차례).
     */
    static suspendedServices(profile: Pick<BusinessProfile, 'status' | 'services'>): BusinessProfileService[] {
        if (profile.status !== 'APPROVED') return [];
        const order: BusinessServiceKind[] = ['HOTDEAL', 'AD'];
        return profile.services.filter((s) => s.status === 'SUSPENDED').sort((a, b) => order.indexOf(a.service) - order.indexOf(b.service));
    }

    /** 그 서비스만 정지됐나 — 광고·핫딜 신청 화면의 막힘 안내 */
    static serviceSuspension(profile: Pick<BusinessProfile, 'status' | 'services'>, service: BusinessServiceKind): BusinessProfileService | null {
        return UtilBusiness.suspendedServices(profile).find((s) => s.service === service) ?? null;
    }

    /** 등록증을 우편·팩스로 보낼 곳이 있나 — 주소나 팩스 번호 중 하나라도(서버 `BusinessDocSendToProperties.available`). 없으면 「우편·팩스로 보내기」를 보이지 않는다 */
    static docSendToAvailable(sendTo: BusinessDocSendTo | null | undefined): boolean {
        return Boolean(sendTo?.address?.trim() || sendTo?.fax?.trim());
    }

    /** 심사 중인데 우편·팩스로 보낸다고 한 등록증을 아직 받지 않았나 — 「등록증 사본을 받으면 심사를 시작해요」(서버 `certificateReceived` 판정) */
    static awaitingCertificate(profile: Pick<BusinessProfile, 'status' | 'certificateDelivery' | 'certificateReceived'>): boolean {
        return profile.status === 'PENDING' && profile.certificateDelivery === 'MAIL_FAX' && !profile.certificateReceived;
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

    /**
     * 아직 오지 않은 요금표 예약 변경 — 적용 시각이 지났거나(화면을 오래 열어 둔 경우) 없으면 null. 지난 변경을 지금 값으로 바꿔 계산하지는 않는다(견적은 서버가 정한다).
     * `scheduledChange` 는 서버가 아직 적용 전인 가장 가까운 예약 하나를 준다(ad-price-rules-contract).
     * @param {BizScheduledPriceChange | null | undefined} change - 상품의 예약 변경.
     * @param {number} now - 지금(밀리초).
     * @returns {BizScheduledPriceChange | null} 아직 앞으로의 변경이면 그대로, 아니면 null.
     */
    static pendingPriceChange(change: BizScheduledPriceChange | null | undefined, now: number): BizScheduledPriceChange | null {
        if (!change) return null;
        const at = Date.parse(change.effectiveAt);
        return Number.isFinite(at) && at > now ? change : null;
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

    /**
     * 배너 이미지가 그 종류의 규격 비율과 맞는지(±3%).
     * @param {AdType} type 광고 종류.
     * @param {number} width 이미지 가로(px).
     * @param {number} height 이미지 세로(px).
     * @returns {boolean} 비율이 맞거나 비율이 없는 종류(한 줄)면 true.
     */
    static bannerRatioOk(type: AdType, width: number, height: number): boolean {
        const ratio = UtilBusiness.BANNER_RATIO[type];
        if (ratio === null || width <= 0 || height <= 0) return true;
        return Math.abs(width / height / ratio - 1) <= UtilBusiness.BANNER_RATIO_TOLERANCE + 1e-9; // 경계(딱 3%)는 부동소수 오차 없이 통과
    }

    /**
     * 고른 이미지 파일의 크기를 읽는다(올리기 전 비율 검사용).
     * @param {File} file 이미지 파일.
     * @returns {Promise<{ width: number; height: number } | null>} 크기, 읽지 못하면 null(검사를 건너뛴다).
     */
    static imageSizeOf(file: File): Promise<{ width: number; height: number } | null> {
        return new Promise((resolve) => {
            const url = URL.createObjectURL(file);
            const image = new Image();
            image.onload = () => {
                resolve({ width: image.naturalWidth, height: image.naturalHeight });
                URL.revokeObjectURL(url);
            };
            image.onerror = () => {
                resolve(null);
                URL.revokeObjectURL(url);
            };
            image.src = url;
        });
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

    /**
     * 상태 딱지 글자 키의 끝 마디 — `SCHEDULED` 는 시각 단계로, `TAKEN_DOWN` 은 내림 까닭(광고주 일시 정지 · 정지한 채 끝남 · 중단)으로. 까닭이 없거나 운영 내림이면 「내려짐」.
     * @param {boolean} pausedEnded - 정지한 채 끝 날짜가 지났다(`pauseEnded`).
     */
    static statusKey(status: BizApplicationStatus, phase: BizSchedulePhase | null, cause: BizTakedownCause | null = null, pausedEnded = false): string {
        if (status === 'TAKEN_DOWN' && cause === 'ADVERTISER_PAUSE') return pausedEnded ? 'TAKEN_DOWN_PAUSE_ENDED' : 'TAKEN_DOWN_PAUSE';
        if (status === 'TAKEN_DOWN' && cause === 'ADVERTISER_STOP') return 'TAKEN_DOWN_STOP';
        return status === 'SCHEDULED' && phase ? `SCHEDULED_${phase}` : status;
    }

    /** 상태 딱지 톤 — 광고주 일시 정지는 경고, 정지한 채 끝남·중단은 흐리게 */
    static statusTone(status: BizApplicationStatus, phase: BizSchedulePhase | null, cause: BizTakedownCause | null = null, pausedEnded = false): BizTone {
        if (status === 'TAKEN_DOWN' && cause === 'ADVERTISER_PAUSE') return pausedEnded ? 'neutral' : 'warn';
        if (status === 'TAKEN_DOWN' && cause === 'ADVERTISER_STOP') return 'neutral';
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

    /** 순간(ISO)의 한국 날짜(`YYYY-MM-DD`) — 일할 기준 날짜 */
    static kstYmdOf(iso: string): string {
        return new Date(Date.parse(iso) + 9 * 3_600_000).toISOString().slice(0, 10);
    }

    /** 끝 날짜(포함)가 지났나 — 끝 날짜 다음 날 00:00(KST)부터 지난 것으로 본다 */
    static periodOver(endDate: string, now: number): boolean {
        return now >= Date.parse(UtilBusiness.kstStartOf(UtilBusiness.addDays(endDate, 1)));
    }

    /** 정지한 채 기간이 끝났나 — 화면이 끝 날짜와 지금을 비교한다(서버는 상태를 `TAKEN_DOWN`·`ADVERTISER_PAUSE` 그대로 둔다 — biz-pause-contract) */
    static pauseEnded(status: BizApplicationStatus, cause: BizTakedownCause | null | undefined, endDate: string, now: number): boolean {
        return status === 'TAKEN_DOWN' && cause === 'ADVERTISER_PAUSE' && UtilBusiness.periodOver(endDate, now);
    }

    /**
     * 진행 중이라 [일시 정지]·[중단하고 환불 요청]을 보일 건인가(서버가 다시 판정) — 예약 상태의 진행 단계 · `pause` 있음(정지할 수 있는 종류·결제 뒤) · 환불 요청 없음 ·
     * 핫딜 글이면 게시되고 딜이 진행 중·의심일 때만(품절로 종료·기간 종료·게시 실패·운영 숨김 뒤에는 없음).
     */
    static runningPausable(a: Pick<BizApplicationDetail, 'status' | 'phase' | 'kind' | 'refund' | 'pause' | 'post'>): boolean {
        if (a.status !== 'SCHEDULED' || a.phase !== 'RUNNING' || !a.pause || a.refund) return false;
        if (a.kind !== 'HOTDEAL_POST') return true;
        return a.post?.status === 'POSTED' && (a.post.view === 'ACTIVE' || a.post.view === 'FLAGGED');
    }

    /**
     * 중단 예상 환불(서버 `BizPricing.stop` 과 같은 식 — biz-pause-contract 규칙 5, 법무 확정 전 가정): 일할 = floor(대상 × 남은 날 / 총 일수)(대상 = `pause.proratableTotal`,
     * 기준 날짜 = 기준 시각의 한국 날짜 — 그날까지 쓴 날로 친다) → 중도 해지 수수료 = floor(일할 × 비율 / 100) → 예상액 = 일할 − 수수료.
     * 서버의 `min(일할, 결제액 − 이미 환불)` 은 화면이 이미 환불한 금액을 몰라 생략한다. 금액은 운영팀 확인 뒤 확정한다.
     * @param {string} fromIso - 기준 시각(정지 중이면 멈춘 시각, 아니면 지금·중단 시각).
     * @param {number} feeRate - 그 건에 고정된 중도 해지 수수료율(% — `pause.feeRate`).
     * @returns 일할·수수료·예상액·총 일수·남은 일수.
     */
    static stopRefund(
        proratableTotal: number,
        startDate: string,
        endDate: string,
        fromIso: string,
        feeRate: number,
    ): { prorated: number; fee: number; amount: number; totalDays: number; leftDays: number } {
        const from = UtilBusiness.kstYmdOf(fromIso);
        const totalDays = UtilBusiness.daysBetween(startDate, endDate);
        const leftDays = Math.min(totalDays, Math.max(0, UtilBusiness.daysBetween(from, endDate) - 1));
        const prorated = UtilBusiness.proratedRefund(proratableTotal, startDate, endDate, from);
        const fee = Math.floor((prorated * Math.max(0, feeRate)) / 100);
        return { prorated, fee, amount: prorated - fee, totalDays, leftDays };
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
