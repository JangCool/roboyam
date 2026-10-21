import UtilTime, { type DisplayTime } from '#layers/display-time/utils/util-time';

/**
 * 시각을 그리는 함수 모음 — 컴포넌트·페이지·컴포저블이 시각을 글자로 만들 때 쓴다(`UtilTime.xxx(iso)` 를 직접 부르지 않는다).
 *
 * ```vue
 * <script setup lang="ts">
 * const time = useDisplayTime();
 * </script>
 * <template>{{ time.listLabel(post.createdAt) }}</template>
 * ```
 *
 * 돌려주는 것은 계산값이다 — 표시 값이 바뀌면 그린 글자도 저절로 다시 그려진다. 스크립트 안에서는 `time.value.full(iso)` 로 쓴다.
 * 표시 값을 앱이 채우기 전에는 시스템 폴백(`UtilTime.FALLBACK_DISPLAY`)으로 그린다.
 *
 * @returns 표시 값에 묶인 시각 함수 모음(`UtilTime.of(values)`)의 계산값.
 */
export function useDisplayTime(): ComputedRef<DisplayTime> {
    const values = useDisplayValues();
    return computed(() => UtilTime.of(values.value));
}
