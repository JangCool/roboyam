import type { AdSensePlace, AdSenseUnitSpec } from '~/utils/util-adsense';
import UtilAdSense from '~/utils/util-adsense';

/**
 * 구글 애드센스 공유 상태 — 장부 광고가 없는 자리를 채울 단위를 고른다(서버 `adsense` 묶음 — shared V176).
 * 설정은 공개 자리 설정(`usePublicAds().placements` — `GET /api/ads/placements`)에 실려 오고, 같은 20초 사본을 쓴다.
 *
 * 스크립트 전제: `adsbygoogle.js` 는 **서버가 index.html 머리에 넣는다**(api `SiteHeadFilter`/`IndexHtmlRenderer`, 「사이트 확인만」·「광고 표시」 단계).
 * 화면은 스크립트를 넣지 않고 `<ins class="adsbygoogle">` 를 그린 뒤 `window.adsbygoogle` 큐에 push 만 한다(`AdSenseUnit`) — 스크립트가 늦게 와도 큐로 동작한다.
 *
 * 그리지 않는 곳: 「광고 표시」가 아닌 단계 · 관리자 미리보기(iframe `adminPreview=1`·`cachePreview=1`) · 게시판 맥락의 19금·광고 숨김·소모임·비밀글(`contextAllows`).
 * 애드센스 자리는 우리 노출·클릭 집계(`/api/ads/impressions`·`clicks`)를 보내지 않는다.
 */
export function useAdSense() {
    const ads = usePublicAds();
    const preview = useAdminPreview();
    void ads.ensurePlacements();

    /** 접힌 자리(광고가 오지 않음 — `data-ad-status="unfilled"`) — 가젯은 이 표시로 칸을 접는다. 키는 부르는 쪽이 정한다 */
    const unfilled = useState<Record<string, true>>('adsense-unfilled', () => ({}));

    const settings = computed(() => UtilAdSense.read(ads.placements.value?.adsense));
    /** 이 창에서 애드센스 단위를 그리는가 — 「광고 표시」 단계 · 관리자 미리보기 아님 */
    const enabled = computed(() => UtilAdSense.showing(settings.value) && !preview.active.value && !preview.cacheMode.value);

    /** 그 자리의 단위 — 그리지 않는 단계·단위 ID 없음이면 null */
    function unit(place: AdSensePlace): AdSenseUnitSpec | null {
        return enabled.value ? UtilAdSense.unit(settings.value, place) : null;
    }

    /**
     * 게시판 화면·글 보기 맥락이 애드센스를 허락하는가 — 맥락(`setWingContext`)을 아직 못 받았으면 false(받기 전에 19금·소모임에 잠깐 비치지 않게).
     * 19금은 자리 설정 「19금에도 광고 보이기」와 상관없이 늘 뺀다.
     */
    function contextAllows(path: string): boolean {
        const ctx = ads.wingContextOf(path);
        return ctx !== null && !UtilAdSense.contextBlocked(ctx);
    }

    function isUnfilled(key: string): boolean {
        return unfilled.value[key] === true;
    }

    function markUnfilled(key: string): void {
        if (unfilled.value[key]) return;
        unfilled.value = { ...unfilled.value, [key]: true };
    }

    return { settings, enabled, unit, contextAllows, isUnfilled, markUnfilled };
}
