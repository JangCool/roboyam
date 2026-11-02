import {
    mockAdvertiserArchive,
    mockAdvertiserCreate,
    mockAdvertiserDetail,
    mockAdvertiserList,
    mockAdvertiserRemove,
    mockAdvertiserReveal,
    mockAdvertiserStats,
    mockAdvertiserUpdate,
} from '~/composables/ad/api/mock/ad-advertiser-mock';
import type {
    AdAdvertiserCreateBody,
    AdAdvertiserDetail,
    AdAdvertiserPage,
    AdAdvertiserQuery,
    AdAdvertiserUpdateBody,
} from '~/composables/ad/api/useAdAdvertiserApi.types';
import type { AdAdvertiserRevealed, AdStatRange, AdStatsOverview } from '~/composables/ad/api/useAdApi.types';

/**
 * 광고주 관리 API(관리자 `/ad-advertisers`) — 서버 주소가 있는 **유일한 곳**이다. 상태는 없다.
 * ⚠ MOCK: 서버 없음 — 주소는 후보. 모든 메서드가 `./mock/ad-advertiser-mock.ts` 를 부른다(스위치 `?mock=adv-*` 는 그 파일 머리말).
 * 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 *  - `GET    /api/admin/ad-advertisers?kind&state&q&sort&dir&page&size(≤100)`(`ad.read`) → `AdAdvertiserPage`(직접 등록 + 사업자 — 사업자는 광고주 줄이 없어도 광고 건이나 광고 서비스가 있으면 나온다)
 *  - `GET    /api/admin/ad-advertisers/{id}`(`ad.read`) → `AdAdvertiserDetail`(`adv-…`·`biz-…`, 없으면 404 `AD_ADVERTISER_NOT_FOUND`)
 *  - `POST   /api/admin/ad-advertisers`(`ad.manage`) `AdAdvertiserCreateBody` → Detail(기존 `POST /api/admin/ads/advertisers` 와 같은 검사 — 같은 이름 400 `fields.name`, 연락처 9~11자리, 이메일 형식, 감사 `AD_ADVERTISER_CREATE`)
 *  - `PATCH  /api/admin/ad-advertisers/{id}`(`ad.manage`) `AdAdvertiserUpdateBody` → Detail(빠진 칸 그대로 · 빈 글자 지우기, 사업자 광고주 이름 400 `fields.name`, 감사 `AD_ADVERTISER_UPDATE` — 바뀐 칸 이름만)
 *  - `POST   /api/admin/ad-advertisers/{id}/archive` · `…/unarchive`(`ad.manage`) → Detail(직접 등록만, 끝나지 않은 건이 있으면 409 `AD_ADVERTISER_HAS_OPEN_BOOKINGS`)
 *  - `DELETE /api/admin/ad-advertisers/{id}`(`ad.manage`) → 204(직접 등록 + 광고 건 0 만, 아니면 409 `AD_ADVERTISER_HAS_BOOKINGS`)
 *  - 연락처 평문: 기존 `POST /api/admin/ads/advertisers/{id}/reveal` `{ reason }`(`ad.contact.reveal`) — `biz-…` 는 사업자 신원 연락처를 낸다(서버 단계에서 넓힘)
 *  - 성과: 기존 `GET /api/admin/ads/stats?from&to&advertiserId`(`ad.read`) 그대로
 */
const PATH = {
    root: '/api/admin/ad-advertisers',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    reveal: (id: string) => `/api/admin/ads/advertisers/${encodeURIComponent(id)}/reveal`,
    stats: () => '/api/admin/ads/stats',
};

export function useAdAdvertiserApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    // MOCK: 서버가 붙으면 아래 `client`·`PATH` 를 쓰는 REAL 줄로 바꾼다
    void client;
    void PATH;

    return {
        // REAL: client.get<AdAdvertiserPage>(PATH.root, { query: { ...query } })
        list: (query: AdAdvertiserQuery): Promise<AdAdvertiserPage> => mockAdvertiserList(query), // MOCK:

        // REAL: client.get<AdAdvertiserDetail>(PATH.one(id))
        detail: (id: string): Promise<AdAdvertiserDetail> => mockAdvertiserDetail(id), // MOCK:

        // REAL: client.post<AdAdvertiserDetail>(PATH.root, body)
        create: (body: AdAdvertiserCreateBody): Promise<AdAdvertiserDetail> => mockAdvertiserCreate(body), // MOCK:

        // REAL: client.patch<AdAdvertiserDetail>(PATH.one(id), body)
        update: (id: string, body: AdAdvertiserUpdateBody): Promise<AdAdvertiserDetail> => mockAdvertiserUpdate(id, body), // MOCK:

        // REAL: client.post<AdAdvertiserDetail>(`${PATH.one(id)}/${archived ? 'archive' : 'unarchive'}`)
        setArchived: (id: string, archived: boolean): Promise<AdAdvertiserDetail> => mockAdvertiserArchive(id, archived), // MOCK:

        // REAL: client.delete<void>(PATH.one(id))
        remove: (id: string): Promise<void> => mockAdvertiserRemove(id), // MOCK:

        /** 연락처 평문 — 사유 5~200자. 화면은 `hideAt` 이 지나면 다시 가린다 */
        // REAL: client.post<AdAdvertiserRevealed>(PATH.reveal(id), { reason })
        reveal: (id: string, reason: string): Promise<AdAdvertiserRevealed> => mockAdvertiserReveal(id, reason), // MOCK:

        /** 광고주 성과(합계·날짜별·유형별) — 광고 성과 화면과 같은 API */
        // REAL: client.get<AdStatsOverview>(PATH.stats(), { query: { ...range, advertiserId: id } })
        stats: (id: string, range: AdStatRange): Promise<AdStatsOverview> => mockAdvertiserStats(id, range), // MOCK:
    };
}
