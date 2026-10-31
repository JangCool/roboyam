import type {
    AdAdvertiser,
    AdAdvertiserCreateRequest,
    AdAdvertiserRevealed,
    AdAvailability,
    AdBookingDetail,
    AdBookingPage,
    AdBookingQuery,
    AdCalendar,
    AdImage,
    AdManualCreateRequest,
    AdRestoreRequest,
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
 *  상태가 이미 바뀌었으면 409 `BIZ_APPLICATION_STATE_CHANGED`.
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
    };
}
