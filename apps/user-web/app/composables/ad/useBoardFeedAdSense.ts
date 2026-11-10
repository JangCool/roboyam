import type { MaybeRef, Ref } from 'vue';
import type { BoardFeedAd, BoardFeedAdSense } from '~/components/board/BoardPostList.types';
import type { BoardFeedAdSenseTarget } from '~/composables/ad/useBoardFeedAd.types';
import UtilAd from '~/utils/util-ad';
import UtilAdSense from '~/utils/util-adsense';

/**
 * 게시판 목록 안 애드센스 인피드 — 장부 한 줄 광고(`useBoardFeedAd`)가 채우지 못한 자리를 채운다.
 * 자리·개수는 자리 설정 그대로(`boardList.after`·`count`·`gap` — `UtilAd.feedIndexes`): 장부 광고가 앞 자리부터 차지하고 남은 자리가 애드센스다(2개 자리에 장부 1건이면 둘째 자리만).
 * 넣는 조건: 「광고 표시」 단계 + 인피드 단위 ID·레이아웃 키 · 목록형(LIST)이고 핫딜 게시판 아님 · 자리 켜짐 · 첫 쪽 · 검색 아님 · 일반 글 있음 ·
 *   게시판 상세를 받아 맥락(`setWingContext`)이 19금·광고 숨김·소모임이 아님(19금은 「19금에도 광고 보이기」와 상관없이 뺀다) · 장부 후보를 받은 뒤(받는 중엔 애드센스를 먼저 그리지 않는다).
 * 관리자 미리보기에서는 그리지 않는다(`useAdSense`).
 */
export function useBoardFeedAdSense(target: MaybeRef<BoardFeedAdSenseTarget | null>, ledger: Readonly<Ref<BoardFeedAd[] | null>>) {
    const ads = usePublicAds();
    const adsense = useAdSense();
    const route = useRoute();

    return computed<BoardFeedAdSense[] | null>(() => {
        const t = toValue(target);
        if (!t || !t.listStyle || !t.firstPage || t.search || t.rows <= 0) return null;
        const spec = adsense.unit('BOARD_LIST');
        if (!spec || !adsense.contextAllows(route.path)) return null;
        // 소모임 게시판은 공개 게시판 API 가 404 로 막아 이 화면에 오지 않지만, 게시판 종류(`kind`)로 한 번 더 막는다
        if (t.board?.adsHidden === true || t.board?.allowAdult === true || t.board?.kind === 'GROUP') return null;
        const placement = UtilAd.feedPlacement(ads.placements.value?.boardList);
        if (!placement.enabled) return null;
        // 장부 후보를 아직 못 받았으면 기다린다 — 애드센스가 먼저 그려졌다가 장부 광고로 바뀌지 않게
        if (ads.candidates('TEXT_LINE', t.slug) === undefined) return null;
        const rest = UtilAdSense.feedRest(UtilAd.feedIndexes(t.rows, placement), ledger.value?.length ?? 0);
        return rest.length ? rest.map((afterIndex) => ({ spec, afterIndex })) : null;
    });
}
