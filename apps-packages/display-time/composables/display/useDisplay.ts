import UtilDisplayValues from '#layers/display-time/utils/util-display-values';
import UtilTime, { type DisplayValues } from '#layers/display-time/utils/util-time';

/**
 * 표시 값(시간대·시간 형식·날짜 형식)의 **효과값** — 화면 전체가 공유하는 상태(`useState('display-effective')`).
 * 이 레이어는 서버를 부르지 않는다: 값을 **채우는 일은 쓰는 앱이 한다**(`useDisplay().setDisplay(values)`).
 * user-web 은 사용자 개인 값 → 사이트 기본값 → 시스템 폴백을 합쳐 넣고, admin-web 은 사이트 기본값을 넣는다. 시각을 그리는 컴포넌트는 이것이 아니라 `useDisplayTime()` 을 쓴다.
 *
 * 값을 받기 전(또는 받지 못했을 때)은 시스템 폴백(`UtilTime.FALLBACK_DISPLAY` — Asia/Seoul · 24시간 · `2026.10.02` 점 형식)이다 — 브라우저 시간대는 쓰지 않는다.
 * 상태가 바뀌면 이것을 읽는 모든 곳(시각 글자·DatePicker 의 형식과 시간대·PrimeVue 로캘)이 같이 바뀐다.
 *
 * ⚠ 컴포저블은 setup 에서 부른다(`await` 뒤에는 Nuxt 문맥이 없다) — 안에서 필요한 상태는 부를 때 미리 잡아 둔다.
 */

/** 효과값을 화면 전체가 공유하는 상태 키(`useState`) */
const STATE_KEY = 'display-effective';

/** 서비스 시간대를 담는 상태 키(`useState`) */
const SERVICE_ZONE_KEY = 'display-service-zone';

/**
 * 효과값만 읽는다(바꾸지 않는다) — `useDisplayTime()` 이 쓴다. 목록 줄·카드처럼 많이 놓이는 조각이 불러도 가볍다.
 * @returns 지금 쓸 표시 값(읽기 전용 계산값). 앱이 채우기 전에는 시스템 폴백.
 */
export function useDisplayValues(): ComputedRef<DisplayValues> {
    const effective = useState<DisplayValues>(STATE_KEY, () => UtilDisplayValues.copy(UtilTime.FALLBACK_DISPLAY));
    return computed(() => effective.value);
}

/**
 * 표시 값을 읽고 채운다 — 앱의 설정 컴포저블(서버에서 받은 값을 넣는 곳)이 쓴다.
 * @returns `values` 지금 쓸 값 · `serviceZoneId` 서비스 시간대(서비스 하루의 기준 — "한국 시간 기준" 덧말의 재료) ·
 *          `setDisplay(values)` 값을 바꾼다(쓸 수 없는 모양이면 무시하고 `false`) · `setServiceZone(zoneId)` 서비스 시간대를 바꾼다 · `resetDisplay()` 시스템 폴백으로 되돌린다.
 */
export function useDisplay() {
    const effective = useState<DisplayValues>(STATE_KEY, () => UtilDisplayValues.copy(UtilTime.FALLBACK_DISPLAY));
    const serviceZoneId = useState<string>(SERVICE_ZONE_KEY, () => UtilTime.SERVICE_ZONE);

    /** 표시 값을 바꾼다. 세 칸이 모두 있고 허용된 값일 때만 — 아니면 지금 값을 그대로 두고 false */
    function setDisplay(values: DisplayValues): boolean {
        const clean = UtilDisplayValues.sanitize(values);
        if (!clean) return false;
        effective.value = clean;
        return true;
    }

    /** 서비스 시간대를 바꾼다(서버가 준 값) */
    function setServiceZone(zoneId: string): void {
        const zone = zoneId.trim();
        if (zone !== '') serviceZoneId.value = zone;
    }

    /** 시스템 폴백으로 되돌린다 */
    function resetDisplay(): void {
        effective.value = UtilDisplayValues.copy(UtilTime.FALLBACK_DISPLAY);
        serviceZoneId.value = UtilTime.SERVICE_ZONE;
    }

    return { values: computed(() => effective.value), serviceZoneId: computed(() => serviceZoneId.value), setDisplay, setServiceZone, resetDisplay };
}
