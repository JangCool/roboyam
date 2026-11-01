// MOCK: 사업자 핫딜 노출 자리(2차 — `HOTDEAL_TOP`·`HOTDEAL_CATEGORY`) 목업 — `usePublicAdApi` 의 `// MOCK:` 분기가 부른다. 서버가 붙으면 이 파일과 분기를 지운다.
//  - `?mock=bizdeal-placement`: 핫딜 게시판 첫 쪽 위 자리에 목업 딜 2건. 말머리를 고르면(`HOTDEAL_CATEGORY`) 그 말머리 딜로 친다(말머리 이름은 화면이 넘긴 이름 — 목업 딜은 `prefixName` 없음).
//    링크는 목업 사업자 글 보기(`/posts/mock-bizdeal*` — 글 목업이 이 게시판으로 서도록 창 저장소에 적어 둔다). 목업 광고(`mock-` ID)의 노출·클릭은 서버로 보내지 않는다.

import { mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { AdImpressionsRequest, PublicAd, PublicAdsQuery, PublicAdsResponse } from '~/composables/ad/api/usePublicAdApi.types';

const MOCK_AD_PREFIX = 'mock-';
/** 목업 사업자 글 보기가 설 게시판(글 목업 `board/api/mock/bizdeal-board-mock.ts` 와 같은 키) */
const MOCK_BOARD_KEY = 'roboyam-mock-bizdeal-board';

/** 핫딜 노출 자리를 묻는 요청이고 스위치가 켜졌나 */
export function bizdealPlacementMockOn(query: PublicAdsQuery): boolean {
    return mockFlags().has('bizdeal-placement') && query.types.some((t) => t === 'HOTDEAL_TOP' || t === 'HOTDEAL_CATEGORY');
}

/** 목업 광고 ID 인가 — 노출·클릭을 서버로 보내지 않는다 */
export function isMockAdId(id: string): boolean {
    return id.startsWith(MOCK_AD_PREFIX);
}

/** 목업 광고의 노출은 뺀다 — 남은 것만 서버로 */
export function withoutMockImpressions(request: AdImpressionsRequest): AdImpressionsRequest {
    return { items: request.items.filter((i) => !isMockAdId(i.adId)) };
}

/** `GET /api/ads?types=HOTDEAL_TOP|HOTDEAL_CATEGORY&board=&prefix=` */
export async function mockBizdealPlacementAds(query: PublicAdsQuery): Promise<PublicAdsResponse> {
    await mockDelay();
    const category = query.types.includes('HOTDEAL_CATEGORY');
    const type = category ? 'HOTDEAL_CATEGORY' : 'HOTDEAL_TOP';
    // 공개 광고 링크는 `/posts/{id}` 꼴만 받는다(쿼리 없음) — 목업 글 보기가 설 게시판을 창 저장소에 적어 둔다(글 목업이 읽는다)
    try {
        if (query.board) sessionStorage.setItem(MOCK_BOARD_KEY, query.board);
    } catch {
        // 저장소를 못 쓰면 글 목업이 기본 게시판(`hotdeal`)으로 선다
    }
    const items: PublicAd[] = [
        {
            id: `${MOCK_AD_PREFIX}bizdeal-ad-1`,
            type,
            version: 1,
            imageUrl: null,
            text: null,
            linkUrl: '/posts/mock-bizdeal',
            altText: 'MG996R 서보모터 10개 묶음 특가',
            weight: 5,
            deal: { postId: 'mock-bizdeal', title: 'MG996R 서보모터 10개 묶음 특가', shopName: '네이버 스마트스토어', dealPrice: 59_000, listPrice: 89_000, thumbnailUrl: null, prefixName: category ? null : '부품' },
            advertiserName: '로보파츠상회',
        },
        {
            id: `${MOCK_AD_PREFIX}bizdeal-ad-2`,
            type,
            version: 1,
            imageUrl: null,
            text: null,
            linkUrl: '/posts/mock-bizdeal-2',
            altText: 'ESP32-S3 개발보드 5개 세트',
            weight: 3,
            deal: { postId: 'mock-bizdeal-2', title: 'ESP32-S3 개발보드 5개 세트', shopName: '메이커랩몰', dealPrice: 32_000, listPrice: 45_000, thumbnailUrl: null, prefixName: category ? null : 'AI 기기' },
            advertiserName: '메이커랩',
        },
    ];
    return mockClone({ items });
}
