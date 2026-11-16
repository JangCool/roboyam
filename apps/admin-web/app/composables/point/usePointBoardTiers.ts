import type { PointBoardTiers } from '~/composables/point/api/usePointApi.types';
import UtilPoint from '~/utils/util-point';

/** 게시판 등급 안내에 쓰는 정책 값 — 등급별 금액·조건 + 함께 쓰는 추천 문턱 */
export interface PointBoardTierInfo {
    tiers: PointBoardTiers;
    likeThreshold: number;
}

/**
 * 게시판 등급의 금액·조건 읽기(point-board-tier-contract) — 게시판 설정의 포인트 칸 안내 한 줄·글 관리의 우수 글 선정 확인이 쓴다.
 * 포인트 정책(`GET /api/admin/settings/points`, `point.read`)에서 읽고, 권한이 없거나 못 읽으면 **계약 기본값**으로 보인다(안내용이라 실패를 알리지 않는다).
 * 상태는 부른 화면 하나의 것이다(정책 화면에서 저장한 값은 그 화면을 다시 열 때 읽는다).
 */
export function usePointBoardTiers() {
    const api = usePointApi();
    const { can } = useAdminPermission();
    /** 받은 값 — `undefined` 아직 안 물어봄 · `null` 못 읽음(기본값을 쓴다) */
    const loaded = ref<PointBoardTierInfo | null | undefined>(undefined);
    let pending: Promise<void> | null = null;

    const info = computed<PointBoardTierInfo>(() => loaded.value ?? { tiers: UtilPoint.TIER_DEFAULTS, likeThreshold: UtilPoint.DEFAULTS.likeBonusThreshold });

    /** 아직 안 읽었으면 읽는다(동시에 불러도 한 번) */
    function ensure(): Promise<void> {
        if (loaded.value !== undefined) return Promise.resolve();
        if (!can('point.read')) {
            loaded.value = null;
            return Promise.resolve();
        }
        pending ??= api
            .loadSettings()
            .then((res) => {
                loaded.value = { tiers: res.boardTiers, likeThreshold: res.likeBonusThreshold };
            })
            .catch(() => {
                loaded.value = null;
            })
            .finally(() => {
                pending = null;
            });
        return pending;
    }

    return { info, ensure };
}
