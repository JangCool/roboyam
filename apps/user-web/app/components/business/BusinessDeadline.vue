<template>
    <!--
        결제·입금 기한 남은 시간(1초마다, 화면 전체가 시계 하나 — `useNowTicker`). 24시간 안이면 빨강(F9 — 결제 기한 24시간 전 경고).
        `inline`: 카드 한 줄(「결제 기한까지 1일 05:12:09」). `box`: 상세의 상자(큰 숫자 + 끝나는 시각).
        읽기 프로그램에는 매초 읽히지 않게 끝나는 시각을 이름으로 준다.
    -->
    <p
        v-if="variant === 'inline'"
        class="m-0 flex items-center gap-1 text-xs font-semibold tabular-nums"
        :class="urgent ? 'text-red-600 dark:text-red-400' : 'text-muted-color'"
        role="timer"
        :aria-label="label"
        data-testid="business-deadline"
    >
        <i class="pi pi-clock text-[0.6875rem]" aria-hidden="true" /><span aria-hidden="true">{{ line }}</span>
    </p>
    <div
        v-else
        class="flex flex-col gap-1 rounded-lg border px-3.5 py-3"
        :class="urgent ? 'border-red-300 bg-red-50 dark:border-red-800 dark:bg-red-950/40' : 'border-hairline bg-(--p-content-hover-background)'"
        role="timer"
        :aria-label="label"
        data-testid="business-deadline"
    >
        <div class="flex flex-wrap items-center justify-between gap-x-3 gap-y-0.5" aria-hidden="true">
            <span class="flex items-center gap-1.5 text-sm font-semibold" :class="urgent ? 'text-red-700 dark:text-red-300' : ''">
                <i class="pi pi-clock text-xs" />{{ t(kind === 'PAYMENT' ? 'business.detail.pay' : 'business.detail.deposit') }}
            </span>
            <span class="text-xs tabular-nums text-muted-color">{{ t('business.deadline.dueAt', { at: time.full(dueAt) }) }}</span>
        </div>
        <p class="m-0 text-[1.5rem] font-extrabold leading-none tabular-nums" :class="urgent ? 'text-red-600 dark:text-red-400' : 'text-color'" aria-hidden="true">{{ left }}</p>
    </div>
</template>

<script setup lang="ts">
import UtilBusiness from '~/utils/util-business';
import UtilBusinessMessages from '~/utils/util-business-messages';

interface Props {
    /** 기한(ISO) */
    dueAt: string;
    kind?: 'PAYMENT' | 'DEPOSIT';
    variant?: 'inline' | 'box';
}

const props = withDefaults(defineProps<Props>(), { kind: 'PAYMENT', variant: 'inline' });

const { t } = useI18n();
const time = useDisplayTime();
const now = useNowTicker();

const ms = computed(() => Date.parse(props.dueAt) - now.value);
const urgent = computed(() => ms.value < UtilBusiness.DUE_WARN_MS);
const left = computed(() => UtilBusinessMessages.remaining(t, ms.value));
const line = computed(() => t(props.kind === 'PAYMENT' ? 'business.deadline.payLeft' : 'business.deadline.depositLeft', { left: left.value }));
const label = computed(() => `${line.value} · ${t('business.deadline.dueAt', { at: time.value.full(props.dueAt) })}`);
</script>
