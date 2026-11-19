<template>
    <!--
        캐시 상태 — 갱신 대기(아직 따라가지 않은 서버가 있음 — 세대 번호나 켜기/끄기) · 꺼짐(이 캐시를 껐다) · 꺼짐(전체)(전체 스위치가 꺼졌다) · 정상.
        대기가 먼저다(끄는 중·켜는 중에도 대기로 보인다). 캐시 관리 목록·상세 서랍·미리보기가 같은 모양을 쓴다.
    -->
    <Tag class="cache-status-tag" :value="label" :icon="icon" :severity="severity" />
</template>

<script setup lang="ts">
import type { CacheStatus } from '~/composables/cache/api/useCacheAdminApi.types';
import type { CacheEnabledState } from '~/utils/util-cache';

interface Props {
    status: CacheStatus;
    /** 켜짐 표시(`UtilCache.enabledState`) — 서버별 줄처럼 켜짐을 따지지 않는 곳은 기본 ON */
    enabledState?: CacheEnabledState;
}

const props = withDefaults(defineProps<Props>(), { enabledState: 'ON' });

const { t } = useI18n();

const label = computed(() => {
    if (props.status === 'PENDING') return t('cache.statusPending');
    if (props.enabledState === 'OFF_GLOBAL') return t('cache.statusOffGlobal');
    if (props.enabledState === 'OFF') return t('cache.statusOff');
    return t('cache.statusOk');
});
const icon = computed(() => (props.status === 'PENDING' ? 'pi pi-hourglass' : props.enabledState === 'ON' ? 'pi pi-check' : 'pi pi-power-off'));
const severity = computed(() => (props.status === 'PENDING' ? 'warn' : props.enabledState === 'ON' ? 'success' : 'secondary'));
</script>

<style scoped>
/* 뱃지 글자 세로 가운데 — 글자 상자를 대문자 높이~기준선으로 깎고 대칭 여백을 준다(`--chip-label-pad`, main.css · components.md 2-4) */
.cache-status-tag :deep(.p-tag-label) {
    text-box: trim-both cap alphabetic;
    padding-block: var(--chip-label-pad);
    white-space: nowrap;
}
</style>
