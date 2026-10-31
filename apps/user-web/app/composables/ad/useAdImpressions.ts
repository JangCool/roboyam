import type { Ref } from 'vue';
import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdPlacement } from '~/composables/ad/usePublicAds.types';

/**
 * 광고 자리의 노출 세기 — 자리 요소(`root`)가 화면에 **절반 이상** 보였을 때, 지금 보이는 광고(`shown`)마다 한 번 `usePublicAds().impression` 을 부른다.
 * 같은 광고·같은 소재 버전은 이 자리에서 다시 세지 않는다(새로 고른 광고만 센다). IntersectionObserver 가 없는 브라우저는 그리자마자 센다.
 * 자리 컴포넌트(`AdSlot`·`AdTextLines`·`AdHotdealPlacement`)가 setup 에서 부른다.
 * @param {Ref<HTMLElement | null>} root - 자리 요소.
 * @param {() => readonly PublicAd[]} shown - 지금 보이는 광고.
 * @param {() => AdPlacement} placement - 어디서.
 */
export function useAdImpressions(root: Ref<HTMLElement | null>, shown: () => readonly PublicAd[], placement: () => AdPlacement): void {
    const tracking = usePublicAds();
    const counted = new Set<string>();
    let observer: IntersectionObserver | null = null;

    function stop(): void {
        observer?.disconnect();
        observer = null;
    }

    function observe(): void {
        stop();
        const el = root.value;
        const fresh = shown().filter((ad) => !counted.has(`${ad.id}:${ad.version}`));
        if (!el || !fresh.length) return;
        const count = () => {
            for (const ad of fresh) {
                counted.add(`${ad.id}:${ad.version}`);
                tracking.impression(ad, placement());
            }
        };
        if (typeof IntersectionObserver === 'undefined') {
            count();
            return;
        }
        observer = new IntersectionObserver(
            (entries) => {
                if (!entries.some((e) => e.isIntersecting && e.intersectionRatio >= 0.5)) return;
                count();
                stop();
            },
            { threshold: 0.5 },
        );
        observer.observe(el);
    }

    watch([() => shown().map((ad) => `${ad.id}:${ad.version}`).join(','), root], observe, { flush: 'post' });
    onBeforeUnmount(stop);
}
