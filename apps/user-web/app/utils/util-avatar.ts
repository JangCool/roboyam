import type { AvatarCategory, AvatarItem } from '~/composables/avatar/api/useAvatarApi.types';

/**
 * 프로필 아바타 규칙 — 기본 아바타 주소, 무료 카테고리, 고르기·상점 목록 거르기. 상태도 요청도 없다.
 * 카탈로그(328종 — 무료 22 · 유료 306)의 정본은 서버(`GET /api/avatars`, 배포 자산 `avatars/manifest.json`)다. 화면은 주소를 만들지 않고 응답의 `url` 을 쓴다 — 대체 이미지(`FALLBACK_URL`)만 예외.
 * 계약: `docs/features/commerce-stage1-contract.md` B절 「아바타 품목」·「고른 아바타」·결정 14.
 */
export default class UtilAvatar {
    /** 고르지 않았거나 카탈로그에 없는 아바타·그림을 못 불러왔을 때 그리는 것 */
    static readonly FALLBACK_ID = 'basic-default';

    /** 대체 이미지 주소(앱 정적 자원 `public/avatars/roboyam/`) */
    static readonly FALLBACK_URL = '/avatars/roboyam/basic-default.svg';

    /** 무료 카테고리(서버 `AvatarCatalog` 의 판정과 같다) — 품목 행이 없다 */
    static readonly FREE_CATEGORIES: readonly string[] = ['basic', 'hobby'];

    /**
     * 카테고리 이름의 문구 키 — 카탈로그 이름에는 「(남/여)」 꼬리표가 있어 화면은 쓰지 않는다
     * @param {string} id - 카테고리 ID(`basic`·`arts-driver` …).
     * @returns {string} `avatar.category.<id>`.
     */
    static categoryKey(id: string): string {
        return `avatar.category.${id}`;
    }

    /**
     * 프로필 설정에서 고를 수 있는 것 — 무료 + 내가 가진 유료
     * @param {AvatarItem[]} items - 카탈로그 줄.
     * @returns {AvatarItem[]} 고를 수 있는 줄(카탈로그 순서).
     */
    static choosable(items: AvatarItem[]): AvatarItem[] {
        return items.filter((item) => item.free || item.owned);
    }

    /**
     * 줄들이 실제로 있는 카테고리만, 수를 그 줄 수로 다시 센다(칩 줄)
     * @param {AvatarCategory[]} categories - 서버 카테고리(카탈로그 순서).
     * @param {AvatarItem[]} items - 보일 줄.
     * @returns {AvatarCategory[]} 보일 카테고리.
     */
    static categoriesOf(categories: AvatarCategory[], items: AvatarItem[]): AvatarCategory[] {
        const counts = new Map<string, number>();
        for (const item of items) counts.set(item.category, (counts.get(item.category) ?? 0) + 1);
        return categories.filter((c) => counts.has(c.id)).map((c) => ({ ...c, count: counts.get(c.id) ?? 0 }));
    }

    /**
     * 상점 아바타 탭에 보일 것 — 유료만(판매 중단이어도 보유 중이면 보인다, 아니면 「판매 중단」으로 흐리게)
     * @param {AvatarItem[]} items - 카탈로그 줄.
     * @returns {AvatarItem[]} 유료 줄.
     */
    static forShop(items: AvatarItem[]): AvatarItem[] {
        return items.filter((item) => !item.free);
    }
}
