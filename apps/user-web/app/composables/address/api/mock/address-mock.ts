// MOCK: 주소 검색 API 목업 — 서버가 붙으면 이 폴더와 `useAddressApi.ts` 의 `// MOCK:` 줄을 지운다.
// 스위치(`?mock=`, 쉼표로 여럿):
//   address-internal     제공처 = 자체 주소 DB 만
//   address-fallback     제공처 = 다음 먼저, 못 불러오면 자체 주소 DB
//   daum-fail            다음 스크립트 주소를 열리지 않는 주소로 준다(못 불러오는 경우)
//   address-config-fail  설정을 못 받는다(화면은 다음 우편번호로 계속한다)
//   address-search-fail  검색이 500
//   address-db-empty     주소 DB 를 올린 적이 없다(503 ADDRESS_DB_NOT_LOADED)

import type { AddressSearchConfig, AddressSearchPage, AddressSearchResult } from '~/composables/address/api/useAddressApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';

const DEFAULT_SCRIPT_URL = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';

/** 이름 있는 건물 몇 개 + 「테헤란로」 연속 번지(더 보기 확인용) */
const NAMED: AddressSearchResult[] = [
    { postcode: '06236', roadAddress: '서울특별시 강남구 테헤란로 152', jibunAddress: '서울특별시 강남구 역삼동 737', buildingName: '강남파이낸스센터', roadAddressEn: '152 Teheran-ro, Gangnam-gu, Seoul' },
    { postcode: '04524', roadAddress: '서울특별시 중구 세종대로 110', jibunAddress: '서울특별시 중구 태평로1가 31', buildingName: '서울특별시청', roadAddressEn: '110 Sejong-daero, Jung-gu, Seoul' },
    { postcode: '13529', roadAddress: '경기도 성남시 분당구 판교역로 166', jibunAddress: '경기도 성남시 분당구 백현동 532', buildingName: '카카오 판교아지트', roadAddressEn: '166 Pangyoyeok-ro, Bundang-gu, Seongnam-si, Gyeonggi-do' },
    { postcode: '48058', roadAddress: '부산광역시 해운대구 센텀남대로 35', jibunAddress: '부산광역시 해운대구 우동 1505', buildingName: '벡스코', roadAddressEn: '35 Centum nam-daero, Haeundae-gu, Busan' },
    { postcode: '63309', roadAddress: '제주특별자치도 제주시 첨단로 242', jibunAddress: '제주특별자치도 제주시 영평동 2181', buildingName: '', roadAddressEn: null },
    { postcode: '34126', roadAddress: '대전광역시 유성구 대학로 99', jibunAddress: '대전광역시 유성구 궁동 220', buildingName: '충남대학교', roadAddressEn: '99 Daehak-ro, Yuseong-gu, Daejeon' },
];

const TEHERAN: AddressSearchResult[] = Array.from({ length: 28 }, (_, i) => {
    const no = 201 + i * 2;
    return {
        postcode: String(6130 + i).padStart(5, '0'),
        roadAddress: `서울특별시 강남구 테헤란로 ${no}`,
        jibunAddress: `서울특별시 강남구 역삼동 ${700 + i}-${i + 1}`,
        buildingName: i % 3 === 0 ? `테헤란 ${no}빌딩` : '',
        roadAddressEn: i % 4 === 0 ? null : `${no} Teheran-ro, Gangnam-gu, Seoul`,
    };
});

const ENTRIES: AddressSearchResult[] = [...NAMED, ...TEHERAN];

/** 띄어쓰기를 무시한 부분 일치(서버는 `search_text` 에 `pg_trgm` 부분 일치) */
function squash(value: string): string {
    return value.replace(/\s+/g, '').toLowerCase();
}

export async function mockAddressConfig(): Promise<AddressSearchConfig> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('address-config-fail')) throw MOCK_ERRORS.internal();
    const provider = flags.has('address-internal') ? 'INTERNAL' : flags.has('address-fallback') ? 'DAUM_FALLBACK_INTERNAL' : 'DAUM';
    // 열리지 않는 주소(예약된 최상위 도메인 `.invalid`) — 스크립트 실패를 실제로 일으킨다
    const scriptUrl = flags.has('daum-fail') ? 'https://postcode.invalid/postcode.v2.js' : DEFAULT_SCRIPT_URL;
    return { provider, scriptUrl };
}

export async function mockAddressSearch(keyword: string, page: number, size: number, signal?: AbortSignal): Promise<AddressSearchPage> {
    await mockDelay();
    // 진짜 통로(`useApiClient`)는 끊긴 요청을 `ABORTED` 로 바꿔 던진다
    if (signal?.aborted) throw new ApiError({ status: 0, code: 'ABORTED', message: 'aborted' });
    const flags = mockFlags();
    if (flags.has('address-search-fail')) throw MOCK_ERRORS.internal();
    if (flags.has('address-db-empty')) {
        throw new ApiError({ status: 503, code: 'ADDRESS_DB_NOT_LOADED', message: '주소 검색을 아직 쓸 수 없어요. 주소를 직접 입력해 주세요.' });
    }
    const q = squash(keyword);
    if (q.length < 2) throw MOCK_ERRORS.validation({ keyword: '검색어를 2자 이상 입력해 주세요.' });
    const hits = ENTRIES.filter((e) => squash([e.roadAddress, e.jibunAddress, e.buildingName, e.roadAddressEn ?? ''].join(' ')).includes(q));
    const items = hits.slice(page * size, page * size + size);
    return mockClone({ items, total: hits.length, page, size });
}
