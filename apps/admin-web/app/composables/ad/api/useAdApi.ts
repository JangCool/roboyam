import {
    mockAdAdvertisers,
    mockAdAvailability,
    mockAdCalendar,
    mockAdCreateAdvertiser,
    mockAdCreateManual,
    mockAdDetail,
    mockAdList,
    mockAdRestore,
    mockAdTakedown,
    mockAdUploadContract,
} from '~/composables/ad/api/mock/ad-mock';
import type {
    AdAdvertiser,
    AdAdvertiserCreateRequest,
    AdAvailability,
    AdBookingDetail,
    AdBookingPage,
    AdBookingQuery,
    AdCalendar,
    AdManualCreateRequest,
    AdRestoreRequest,
    AdTakedownRequest,
    AdType,
} from '~/composables/ad/api/useAdApi.types';
import type { BizContractFile, BizPeriod } from '~/composables/bizapplication/api/useBizApplicationApi.types';

/**
 * 광고 일정표 API(관리자) — 서버 주소(`/api/admin/ads/…`)가 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 후보(화면 확정 뒤 `docs/features/ad-contract.md`). 권한: 조회 `ad.read`, 등록·소재 `ad.manage`, 긴급 내림 `ad.takedown`, 되돌리기 `ad.restore`(추가 예정), 계약서 열람 `ad.manage` 이상 + 열람 기록.
 *  - `GET  /api/admin/ads/calendar?type=&month=YYYY-MM` → `AdCalendar`(그 달 + 다음 달 날짜별 남은 칸, 유형 요약)
 *  - `GET  /api/admin/ads?type=&status=&q=&page=&size=` → `AdBookingPage`
 *  - `GET  /api/admin/ads/{id}` → `AdBookingDetail`
 *  - `GET  /api/admin/ads/availability?type=&startAt=&endAt=` → `AdAvailability`(저장 때 서버가 다시 판정 — 꽉 차면 409 `AD_SLOT_FULL`)
 *  - `GET  /api/admin/ads/advertisers?q=` · `POST /api/admin/ads/advertisers` → 광고주(사업자 + 계정 없는 광고주)
 *  - `POST /api/admin/ads/contract-files` 멀티파트 → `BizContractFile`(PDF·JPG·PNG 10MB, 원본 보존)
 *  - `POST /api/admin/ads/manual` → 예약 확정된 `AdBookingDetail`(계약서 1장 이상 + 입금 확인 둘 다 — 서버 CHECK)
 *  - `POST /api/admin/ads/{id}/takedown` `{ reason, notifyMail }` · `POST /api/admin/ads/{id}/restore` `{ compensation, reason }`
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/ad-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    root: '/api/admin/ads',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    calendar: () => `${PATH.root}/calendar`,
    availability: () => `${PATH.root}/availability`,
    advertisers: () => `${PATH.root}/advertisers`,
    contractFiles: () => `${PATH.root}/contract-files`,
    manual: () => `${PATH.root}/manual`,
};

export function useAdApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        calendar: (type: AdType, month: string): Promise<AdCalendar> =>
            // MOCK:
            mockAdCalendar(type, month),
        // REAL: client.get<AdCalendar>(PATH.calendar(), { query: { type, month } }),

        list: (query: AdBookingQuery): Promise<AdBookingPage> =>
            // MOCK:
            mockAdList(query),
        // REAL: client.get<AdBookingPage>(PATH.root, { query: { ...query } }),

        detail: (id: string): Promise<AdBookingDetail> =>
            // MOCK:
            mockAdDetail(id),
        // REAL: client.get<AdBookingDetail>(PATH.one(id)),

        availability: (type: AdType, period: BizPeriod): Promise<AdAvailability> =>
            // MOCK:
            mockAdAvailability(type, period),
        // REAL: client.get<AdAvailability>(PATH.availability(), { query: { type, ...period } }),

        advertisers: (q: string): Promise<AdAdvertiser[]> =>
            // MOCK:
            mockAdAdvertisers(q),
        // REAL: client.get<AdAdvertiser[]>(PATH.advertisers(), { query: { q } }),

        createAdvertiser: (body: AdAdvertiserCreateRequest): Promise<AdAdvertiser> =>
            // MOCK:
            mockAdCreateAdvertiser(body),
        // REAL: client.post<AdAdvertiser>(PATH.advertisers(), body),

        uploadContract: (file: File): Promise<BizContractFile> => {
            const form = new FormData();
            form.append('file', file);
            // MOCK:
            return mockAdUploadContract(file);
            // REAL: return client.post<BizContractFile>(PATH.contractFiles(), form);
        },

        createManual: (body: AdManualCreateRequest): Promise<AdBookingDetail> =>
            // MOCK:
            mockAdCreateManual(body),
        // REAL: client.post<AdBookingDetail>(PATH.manual(), body),

        takedown: (id: string, body: AdTakedownRequest): Promise<AdBookingDetail> =>
            // MOCK:
            mockAdTakedown(id, body),
        // REAL: client.post<AdBookingDetail>(`${PATH.one(id)}/takedown`, body),

        restore: (id: string, body: AdRestoreRequest): Promise<AdBookingDetail> =>
            // MOCK:
            mockAdRestore(id, body),
        // REAL: client.post<AdBookingDetail>(`${PATH.one(id)}/restore`, body),
    };
}
