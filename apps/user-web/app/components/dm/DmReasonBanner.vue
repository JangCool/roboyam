<template>
    <!-- 못 보내는 이유 배너 — 가입 7일 전 · 오늘 한도 · 새 상대 한도 · 수신 거부 · 차단 관계 · 정지 중 · 이메일 인증 전. 가입 7일 전이면 진행 막대가 붙는다 -->
    <div class="space-y-2" data-testid="dm-reason">
        <DmNotice :tone="copy.tone" :title="copy.title" :text="copy.text" :link="copy.link" />

        <div v-if="progress && progressCopy" class="rounded-lg bg-surface-100 px-3 py-2.5 dark:bg-surface-800/60" data-testid="dm-join-progress">
            <p class="m-0 text-sm font-semibold">{{ progressCopy.title }}</p>
            <p class="m-0 mt-0.5 text-xs text-muted-color">{{ progressCopy.text }}</p>
            <div
                class="mt-2 h-1.5 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700"
                role="progressbar"
                :aria-label="t('dm.joinProgressLabel')"
                aria-valuemin="0"
                aria-valuemax="100"
                :aria-valuenow="progress.percent"
            >
                <div class="h-full rounded-full bg-primary" :style="{ width: `${progress.percent}%` }" />
            </div>
        </div>

        <p v-if="copy.help" class="m-0 text-xs text-muted-color">{{ copy.help }}</p>
    </div>
</template>

<script setup lang="ts">
import type { DmQuota, DmReason } from '~/composables/dm/api/useDmApi.types';
import UtilDm from '~/utils/util-dm';
import UtilDmMessages from '~/utils/util-dm-messages';

/** 서버가 알려 준 못 보내는 이유 하나를 배너로 — 문구·색은 `UtilDmMessages.reason` */
interface Props {
    reason: DmReason;
    availableFrom?: string | null;
    quota?: DmQuota | null;
    /** 가입 후 보낼 수 있게 되는 일수(서버 `rules.minAccountAgeDays`) */
    minDays?: number;
}

const props = withDefaults(defineProps<Props>(), { availableFrom: null, quota: null, minDays: 7 });

const { t } = useI18n();
const time = useDisplayTime();

const copy = computed(() => UtilDmMessages.reason(t, props.reason, { quota: props.quota, availableFrom: props.availableFrom, minDays: props.minDays, time: time.value }));
const progress = computed(() => (props.reason === 'TOO_NEW' ? UtilDm.joinProgress(props.availableFrom, props.minDays) : null));
const progressCopy = computed(() => (progress.value ? UtilDmMessages.joinProgress(t, progress.value.day, progress.value.left, props.minDays) : null));
</script>
