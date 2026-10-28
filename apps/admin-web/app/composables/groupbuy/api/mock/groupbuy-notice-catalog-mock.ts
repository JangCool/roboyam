// MOCK: 상품정보제공고시 카탈로그(결정 87) — `GET /api/admin/group-buys/notice-catalog` 의 목업 응답.
// 옆의 `notice-catalog-2023-01-01.json` 은 **서버 원본 사본 — 서버 붙으면 지움**(원본 `server/shared/src/main/resources/groupbuy/notice-catalog-2023-01-01.json`, 2b-2 가 고시 원문에서 옮김).
// 서버가 그 파일을 그대로 돌려준다고 가정한다. 품목·항목 이름·원칙 원문은 서버 데이터라 번역 키로 옮기지 않는다(`i18n.md` 5절).

import type { NoticeCatalog } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import catalogJson from '~/composables/groupbuy/api/mock/notice-catalog-2023-01-01.json';

/** JSON 은 `kind` 가 넓은 `string` 으로 잡혀 서버 모양으로 한 번 맞춘다 */
export function mockNoticeCatalog(): NoticeCatalog {
    return structuredClone(catalogJson) as NoticeCatalog;
}
