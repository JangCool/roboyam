import type { Ref } from 'vue';
import type { PublicAd } from '~/composables/ad/api/usePublicAdApi.types';
import type { AdPlacement } from '~/composables/ad/usePublicAds.types';

/** 자리가 절반 이상 보이면 보이는 광고마다 한 번 노출을 센다 — 같은 광고·소재 버전은 다시 세지 않는다 */
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
