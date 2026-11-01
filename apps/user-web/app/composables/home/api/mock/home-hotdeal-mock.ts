// MOCK: 홈 가젯 `HOT_DEALS`(회원 핫딜 1차) 목업 — 서버 `HomeGadgetKinds` 에 종류가 생기면 이 파일과 `useHomeGadgetApi` 의 `// MOCK:` 줄을 지운다.
// 공개 홈 응답에 `HOT_DEALS` 가 없으면 끝에 하나 더한다(배치에 자리가 없으니 격자 맨 아래 — `UtilHomeLayout.read`). `?mock=deal-gadget-off` 면 넣지 않는다.
// 줄은 핫딜 게시판 목업(`composables/board/api/mock/board-deal-mock.ts`)의 진행 중 글 최신 5개. 스위치는 `composables/hotdeal/api/mock/hotdeal-mock.ts` 머리 주석.

import { mockFlags } from '~/composables/common/mock/mock-util';
import { MOCK_DEAL_BOARD_SLUG, mockHotDealRows, type MockDealViewer } from '~/composables/board/api/mock/board-deal-mock';
import type { HomeGadgetsResponse } from '~/composables/home/api/useHomeGadgetApi.types';

export function mockWithHotDealsGadget(res: HomeGadgetsResponse, viewer: MockDealViewer): HomeGadgetsResponse {
    if (mockFlags().has('deal-gadget-off') || !Array.isArray(res.gadgets) || res.gadgets.some((g) => g.type === 'HOT_DEALS')) return res;
    const items = mockHotDealRows(viewer, 5);
    const gadget = {
        id: 'mock-gadget-hot-deals',
        type: 'HOT_DEALS',
        title: '핫딜',
        desktop: true,
        mobile: true,
        empty: items.length === 0,
        locked: false,
        // 꾸미기는 빠진 칸을 화면이 기본값으로 읽는다(`UtilHomeGadget.sanitize`)
        style: { icon: 'pi-tag' },
        config: { boards: [], sort: 'LATEST', count: 5 },
        more: { label: null, to: `/boards/${MOCK_DEAL_BOARD_SLUG}` },
        data: { items },
    };
    return { ...res, gadgets: [...res.gadgets, gadget as unknown as HomeGadgetsResponse['gadgets'][number]] };
}
