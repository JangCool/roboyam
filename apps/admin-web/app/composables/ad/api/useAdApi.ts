import type {
    AdAdvertiser,
    AdApplicationStats,
    AdAdvertiserCreateRequest,
    AdAdvertiserRevealed,
    AdAvailability,
    AdBookingDetail,
    AdBookingPage,
    AdBookingQuery,
    AdCalendar,
    AdImage,
    AdManualCreateRequest,
    AdPlacementHistoryPage,
    AdPlacementsRequest,
    AdPlacementsView,
    AdPriceAcceptingRequest,
    AdPriceBulkScheduleRequest,
    AdPriceProduct,
    AdPriceRuleHistoryPage,
    AdPriceRulesView,
    AdPriceScheduleRequest,
    AdRestoreRequest,
    AdStatRange,
    AdStatsBookingPage,
    AdStatsBookingQuery,
    AdStatsOverview,
    AdStatsQuery,
    AdTakedownRequest,
    AdType,
} from '~/composables/ad/api/useAdApi.types';
import type { BizContractFile, BizPeriod } from '~/composables/bizapplication/api/useBizApplicationApi.types';

/**
 * 광고 일정표 API(관리자) — 서버 주소(`/api/admin/ads/…`)가 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 `docs/features/ad-contract.md` 「서버 구현 결정 — `/ads` 백엔드, V115」(서버 `AdAdminController`). 응답은 no-store.
 * 권한: 조회 `ad.read`(핫딜 노출 자리 탭 포함), 등록·광고주·계약서·이미지 `ad.manage`, 긴급 내림 `ad.takedown`, 되돌리기 `ad.restore`, 광고주 연락처 평문 `ad.contact.reveal`.
 *  - `GET  /api/admin/ads/calendar?type=&month=YYYY-MM` → `AdCalendar`(유형 요약 + 그 달 날짜별 남은 칸 + 그 달에 걸친 장부 건)
 *  - `GET  /api/admin/ads?type=&status=&q=&page=&size=(≤100)` → `AdBookingPage`
 *  - `GET  /api/admin/ads/{id}` → `AdBookingDetail`(장부 유형 줄이 없는 건 404)
 *  - `GET  /api/admin/ads/availability?type=&startAt=&endAt=` → `AdAvailability`(저장 때 서버가 다시 판정 — 꽉 차면 409 `BIZ_SLOT_FULL`)
 *  - `GET  /api/admin/ads/advertisers?q=` · `POST /api/admin/ads/advertisers` → 광고주(사업자 + 계정 없는 광고주). 같은 이름 400 `fields.name`, 연결된 사업자 400 `fields.businessId`
 *  - `POST /api/admin/ads/advertisers/{id}/reveal` `{ reason }`(5~200자) → `AdAdvertiserRevealed`(`hideAt` 뒤 화면이 다시 가린다 — 열람 기록·감사)
 *  - `POST /api/admin/ads/contract-files` 멀티파트 `file` 한 장 → `BizContractFile`(PDF·JPG·PNG 10MB, 등록 전에 미리 올린 사본)
 *  - `POST /api/admin/ads/images` 멀티파트 `file` → `AdImage`(PNG·JPG·GIF·WEBP 2MB — 배너 소재 `creative.imageFileId`)
 *  - `POST /api/admin/ads/manual` → 심사 대기 `AdBookingDetail`(`SUBMITTED`, source=OPERATOR — 광고 심사 큐로 간다. 승인 → 계약서 사본 + 입금 확인 → 확정은 `/api/admin/biz-applications/**`, 04-ia-review §2).
 *    광고 종류(배너 넷·한 줄)만 — 핫딜 노출 자리는 400 `fields.type`
 *  - `POST /api/admin/ads/{id}/takedown` `{ reason, notifyMail }` · `POST /api/admin/ads/{id}/restore` `{ compensation, reason }`
 *  - `GET  /api/admin/ads/placements`(`ad.read`) · `PUT /api/admin/ads/placements`(`ad.settings`) `AdPlacementsRequest` → `AdPlacementsView`(글 보기·날개 배너·게시판 목록 한 줄 자리 설정, 감사 `AD_PLACEMENT_UPDATE`).
 *    400 `fields.postInline.adType`·`fields.postInline.position`·`fields.wings.screens`·`fields.boardList.after`(1~20), 빠진 묶음은 `fields.postInline` 등.
 *    구글 애드센스 묶음 `adsense`(shared V176 — PUT 에서 빠지면 그대로): 400 `fields.adsense.mode`·`fields.adsense.client`·`fields.adsense.units.<자리>`·`fields.adsense.units.BOARD_LIST.slot|layoutKey`.
 *  - `GET  /api/admin/ads/placements/history?page=&size=`(`ad.read`) → `AdPlacementHistoryPage`(메일 서버 설정 이력과 같은 모양 — 최신이 위, 바뀐 칸이 없는 저장은 줄이 없다)
 *  - 상품 가격(요금표 `biz_price_rules` — 계약 `docs/features/ad-price-rules-contract.md`, 서버 admin `AdPriceRuleAdminService`):
 *    `GET /api/admin/ads/price-rules`(`ad.read`) → `AdPriceRulesView`(지금 값 9개, 줄마다 예약들 `schedules[]`(적용 시각 차례 — 한 상품에 여러 건) · 최근 변경 `recentChanges[]`).
 *    `POST /api/admin/ads/price-rules/{product}/schedules` · `PUT …/{product}/schedules/{id}`(`ad.settings`) `AdPriceScheduleRequest` → `AdPriceRulesView`(감사 `AD_PRICE_SCHEDULE_CREATE|UPDATE` — 가정).
 *      400 `fields.effectiveAt`(지금보다 뒤 · 같은 상품 같은 시각 중복) · `fields.unitPrice|capacity|maxDays|maxLeadDays`.
 *    `POST /api/admin/ads/price-rules/schedules/bulk`(`ad.settings`) `AdPriceBulkScheduleRequest`(고른 상품마다 예약 1건 — 단가 비율·정액 조정) → `AdPriceRulesView`. 400 `fields.effectiveAt`·`fields.products`·`fields.adjust.value`.
 *    `DELETE /api/admin/ads/price-rules/{product}/schedules/{id}` · `DELETE /api/admin/ads/price-rules/schedules`(모두)(`ad.settings`) → `AdPriceRulesView`(감사 `AD_PRICE_SCHEDULE_CANCEL`).
 *      서버는 가격 기간표로 두고 적용 시각이 지나면 그 값을 지금 값으로 읽는다(배치 없음 — 지난 줄은 「최근 변경」).
 *    `PATCH /api/admin/ads/price-rules/{product}/accepting`(`ad.settings`) `{ accepting }` → `AdPriceRulesView`(바로 적용 — 긴급 스위치, 400 `fields.accepting`).
 *    `GET /api/admin/ads/price-rules/history?page=&size=`(`ad.read`) → `AdPriceRuleHistoryPage`(칸 코드 `schedules.<상품>.effectiveAt`·`schedules.<상품>.<칸>`·`rules.<상품>.<칸>`).
 *  상태가 이미 바뀌었으면 409 `BIZ_APPLICATION_STATE_CHANGED`.
 *  광고주 요청 일시 정지·중단(서버 연결 2026-11-02 — `biz-pause-contract.md`): 줄(달력·표) `takedownCause`, 상세 `takedownCause`·`pause`·`takedown.channel`·`takedown.memo`·`takedown.pause`(신청 건 상세와 같은 모양).
 *    정지·재개·중단 처리는 신청 건 API(`/api/admin/biz-applications/{id}/pause|resume|stop`)만 — 이 화면은 「광고 심사에서 열기」로 보낸다. [되돌리기]는 긴급 내림만.
 *  광고 성과(서버 연결 2026-11-02 — `ad-performance-contract.md`, DTO 정본 shared `ad/dto/AdStatsView`·admin `ad/dto/AdStatsAdminView`, 집계 원천 shared V118 `ad_daily_stats`):
 *  - `GET  /api/admin/ads/{id}/stats?from&to`(`ad.read`) → `AdApplicationStats`(범위를 빼면 시작 날짜 ~ min(끝 날짜, 오늘))
 *  - `GET  /api/admin/ads/stats?from&to&type&advertiserId`(`ad.read`) → `AdStatsOverview`
 *  - `GET  /api/admin/ads/stats/bookings?from&to&type&advertiserId&sort=impressions|clicks|ctr&dir=asc|desc&page&size(≤100)`(`ad.read`) → `AdStatsBookingPage`
 */
const PATH = {
    root: '/api/admin/ads',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    calendar: () => `${PATH.root}/calendar`,
    availability: () => `${PATH.root}/availability`,
    advertisers: () => `${PATH.root}/advertisers`,
    reveal: (id: string) => `${PATH.root}/advertisers/${encodeURIComponent(id)}/reveal`,
    contractFiles: () => `${PATH.root}/contract-files`,
    images: () => `${PATH.root}/images`,
    manual: () => `${PATH.root}/manual`,
    placements: () => `${PATH.root}/placements`,
    placementHistory: () => `${PATH.root}/placements/history`,
    priceRules: () => `${PATH.root}/price-rules`,
    priceSchedules: () => `${PATH.root}/price-rules/schedules`,
    priceBulkSchedules: () => `${PATH.root}/price-rules/schedules/bulk`,
    productSchedules: (product: string) => `${PATH.root}/price-rules/${encodeURIComponent(product)}/schedules`,
    productSchedule: (product: string, id: string) => `${PATH.root}/price-rules/${encodeURIComponent(product)}/schedules/${encodeURIComponent(id)}`,
    priceAccepting: (product: string) => `${PATH.root}/price-rules/${encodeURIComponent(product)}/accepting`,
    priceRuleHistory: () => `${PATH.root}/price-rules/history`,
    stats: (id: string) => `${PATH.one(id)}/stats`,
    statsOverview: () => `${PATH.root}/stats`,
    statsBookings: () => `${PATH.root}/stats/bookings`,
};

export function useAdApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        calendar: (type: AdType, month: string): Promise<AdCalendar> => client.get<AdCalendar>(PATH.calendar(), { query: { type, month } }),

        list: (query: AdBookingQuery): Promise<AdBookingPage> => client.get<AdBookingPage>(PATH.root, { query: { ...query } }),

        detail: (id: string): Promise<AdBookingDetail> => client.get<AdBookingDetail>(PATH.one(id)),

        availability: (type: AdType, period: BizPeriod): Promise<AdAvailability> => client.get<AdAvailability>(PATH.availability(), { query: { type, ...period } }),

        advertisers: (q: string): Promise<AdAdvertiser[]> => client.get<AdAdvertiser[]>(PATH.advertisers(), { query: { q } }),

        createAdvertiser: (body: AdAdvertiserCreateRequest): Promise<AdAdvertiser> => client.post<AdAdvertiser>(PATH.advertisers(), body),

        /** 광고주 연락처 평문 — 사유 5~200자(400 `fields.reason`). 화면은 `hideAt` 이 지나면 다시 가린다 */
        revealAdvertiser: (id: string, reason: string): Promise<AdAdvertiserRevealed> => client.post<AdAdvertiserRevealed>(PATH.reveal(id), { reason }),

        /** 계약서 사본 미리 올리기(등록 전) — 한 장씩 */
        uploadContract: (file: File): Promise<BizContractFile> => {
            const form = new FormData();
            form.append('file', file);
            return client.post<BizContractFile>(PATH.contractFiles(), form);
        },

        /** 배너 이미지 올리기 — 돌아온 `id` 를 `creative.imageFileId` 로, `url` 을 미리보기로 쓴다 */
        uploadImage: (file: File): Promise<AdImage> => {
            const form = new FormData();
            form.append('file', file);
            return client.post<AdImage>(PATH.images(), form);
        },

        createManual: (body: AdManualCreateRequest): Promise<AdBookingDetail> => client.post<AdBookingDetail>(PATH.manual(), body),

        takedown: (id: string, body: AdTakedownRequest): Promise<AdBookingDetail> => client.post<AdBookingDetail>(`${PATH.one(id)}/takedown`, body),

        restore: (id: string, body: AdRestoreRequest): Promise<AdBookingDetail> => client.post<AdBookingDetail>(`${PATH.one(id)}/restore`, body),

        /** 광고 기본 설정 — 구글 애드센스 묶음(`adsense`) 포함 */
        placements: (): Promise<AdPlacementsView> => client.get<AdPlacementsView>(PATH.placements()),

        savePlacements: (body: AdPlacementsRequest): Promise<AdPlacementsView> => client.put<AdPlacementsView>(PATH.placements(), body),

        /** 광고 자리 설정 변경 이력 한 쪽 — `page` 0 부터 */
        placementHistory: (page: number, size: number): Promise<AdPlacementHistoryPage> => client.get<AdPlacementHistoryPage>(PATH.placementHistory(), { query: { page, size } }),

        /** 상품 가격(요금표) 9개의 지금 값(단가·동시 한도·최대 일수·미리 신청 일수·받기 + 앞날 겹침 분포) + 상품마다 걸린 예약 */
        priceRules: (): Promise<AdPriceRulesView> => client.get<AdPriceRulesView>(PATH.priceRules()),

        /** 상품 하나에 가격 변경 예약 만들기 — 적용 시각 뒤에 낸 신청부터 새 값 */
        createPriceSchedule: (product: AdPriceProduct, body: AdPriceScheduleRequest): Promise<AdPriceRulesView> => client.post<AdPriceRulesView>(PATH.productSchedules(product), body),

        /** 상품 하나의 예약 한 건 고치기(값·적용 시각) */
        updatePriceSchedule: (product: AdPriceProduct, id: string, body: AdPriceScheduleRequest): Promise<AdPriceRulesView> => client.put<AdPriceRulesView>(PATH.productSchedule(product, id), body),

        /** 상품 하나의 예약 한 건 취소 */
        cancelPriceSchedule: (product: AdPriceProduct, id: string): Promise<AdPriceRulesView> => client.del<AdPriceRulesView>(PATH.productSchedule(product, id)),

        /** 여러 상품 한꺼번에 — 고른 상품마다 예약 1건(단가 비율·정액 조정, 같은 적용 시각) */
        bulkPriceSchedules: (body: AdPriceBulkScheduleRequest): Promise<AdPriceRulesView> => client.post<AdPriceRulesView>(PATH.priceBulkSchedules(), body),

        /** 걸린 가격 변경 예약 모두 취소 */
        cancelAllPriceSchedules: (): Promise<AdPriceRulesView> => client.del<AdPriceRulesView>(PATH.priceSchedules()),

        /** 신청 받기 켬/끔 — 예약 없이 바로 적용(긴급 스위치) */
        setPriceAccepting: (product: AdPriceProduct, accepting: boolean): Promise<AdPriceRulesView> => client.patch<AdPriceRulesView>(PATH.priceAccepting(product), { accepting } satisfies AdPriceAcceptingRequest),

        /** 상품 가격 변경 이력 한 쪽 — `page` 0 부터 */
        priceRuleHistory: (page: number, size: number): Promise<AdPriceRuleHistoryPage> => client.get<AdPriceRuleHistoryPage>(PATH.priceRuleHistory(), { query: { page, size } }),

        /** 한 건의 성과 — 범위를 빼면 건 기간 전체(시작 날짜 ~ min(끝 날짜, 오늘)) */
        stats: (id: string, range?: AdStatRange): Promise<AdApplicationStats> =>
            client.get<AdApplicationStats>(PATH.stats(id), { query: { ...range } }),

        /** 운영자 성과 — 합계·날짜별·유형별·자리별·광고주 상위 10 */
        statsOverview: (query: AdStatsQuery): Promise<AdStatsOverview> =>
            client.get<AdStatsOverview>(PATH.statsOverview(), { query: { ...query } }),

        /** 건별 성과 한 쪽 — 정렬·쪽 나눔은 서버 */
        statsBookings: (query: AdStatsBookingQuery): Promise<AdStatsBookingPage> =>
            client.get<AdStatsBookingPage>(PATH.statsBookings(), { query: { ...query } }),
    };
}
