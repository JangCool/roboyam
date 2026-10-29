<template>
    <!--
        공동구매 남은 시간(1초마다) — 예정이면 시작까지, 모집 중이면 마감(연장 중이면 연장 끝)까지. 집계 중·종료면 그리지 않는다(부르는 쪽이 상태 글자를 보인다).
        `card`: 목록·홈 카드의 한 줄(「마감까지 2일 05:12:09」). `detail`: 상세 구매 영역의 상자(큰 숫자 + 끝나는 시각).
        하루 안이면 빨강, 한 시간 안이면 상자가 붉어지고 숫자가 굵어진다. 시계는 화면 전체가 하나를 같이 쓴다(`useNowTicker`).
        0 이 되면 `finished` 를 한 번 올린다 — 상세는 다시 읽어 집계 중·모집 시작으로 바꾼다. 읽기 프로그램에는 매초 읽히지 않게 끝나는 시각을 이름으로 준다.
    -->
    <p
        v-if="cd && variant === 'card'"
        class="m-0 flex items-center gap-1 text-xs font-semibold tabular-nums"
        :class="cd.urgent ? 'text-red-600 dark:text-red-400' : 'text-muted-color'"
        role="timer"
        :aria-label="label"
        data-testid="group-buy-countdown"
    >
        <i class="pi pi-clock text-[0.6875rem]" aria-hidden="true" /><span aria-hidden="true">{{ t(`groupBuy.countdown.to.${cd.kind}`) }} {{ digits }}</span>
    </p>
    <div
        v-else-if="cd"
        class="flex flex-col gap-1 rounded-lg border px-3.5 py-3"
        :class="cd.critical ? 'border-red-300 bg-red-50 dark:border-red-800 dark:bg-red-950/40' : cd.urgent ? 'border-red-200 bg-red-50/60 dark:border-red-900 dark:bg-red-950/25' : 'border-hairline bg-(--p-content-hover-background)'"
        role="timer"
        :aria-label="label"
        data-testid="group-buy-countdown"
    >
        <div class="flex flex-wrap items-center justify-between gap-x-3 gap-y-0.5" aria-hidden="true">
            <span class="flex items-center gap-1.5 text-sm font-semibold" :class="cd.urgent ? 'text-red-700 dark:text-red-300' : ''">
                <i class="pi pi-clock text-xs" />{{ t(`groupBuy.countdown.title.${cd.kind}`) }}
            </span>
            <span class="text-xs tabular-nums text-muted-color">{{ t(`groupBuy.countdown.at.${cd.kind}`, { at: time.shortDateTime(cd.target) }) }}</span>
        </div>
        <p class="m-0 flex items-baseline gap-1 tabular-nums leading-none" :class="cd.urgent ? 'text-red-600 dark:text-red-400' : 'text-color'" aria-hidden="true">
            <template v-if="cd.days > 0">
                <span class="text-[1.625rem] font-extrabold">{{ cd.days }}</span><span class="mr-1.5 text-sm font-semibold">{{ t('groupBuy.countdown.dayUnit') }}</span>
            </template>
            <span class="text-[1.625rem] font-extrabold" :class="cd.critical ? 'animate-pulse' : ''">{{ clock }}</span>
        </p>
        <p v-if="cd.kind === 'EXTENDED'" class="m-0 text-xs text-muted-color">{{ t('groupBuy.countdown.extendedHelp') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyListItem } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import UtilGroupBuy from '~/utils/util-group-buy';

interface Props {
    gb: Pick<GroupBuyListItem, 'phase' | 'status' | 'startAt' | 'endAt' | 'extendedUntil'>;
    variant?: 'card' | 'detail';
}

const props = withDefaults(defineProps<Props>(), { variant: 'card' });
const emit = defineEmits<{ finished: [] }>();

const { t } = useI18n();
const time = useDisplayTime();
const now = useNowTicker();

const cd = computed(() => UtilGroupBuy.countdown(props.gb, now.value));

const pad = (n: number) => String(n).padStart(2, '0');
const clock = computed(() => (cd.value ? `${pad(cd.value.hours)}:${pad(cd.value.minutes)}:${pad(cd.value.secs)}` : ''));
/** 카드 한 줄 — 하루가 넘으면 「2일 05:12:09」 */
const digits = computed(() => (cd.value && cd.value.days > 0 ? `${t('groupBuy.countdown.days', { n: cd.value.days })} ${clock.value}` : clock.value));
/** 읽기 프로그램용 — 매초 바뀌지 않게 끝나는 시각 */
const label = computed(() => (cd.value ? t(`groupBuy.countdown.at.${cd.value.kind}`, { at: time.value.shortDateTime(cd.value.target) }) : ''));

// 0 이 되는 순간 한 번 — 부르는 쪽이 상태를 다시 읽는다(시계가 계속 흘러도 같은 끝을 두 번 알리지 않는다)
let finishedFor: string | null = null;
watch(
    () => (cd.value && cd.value.seconds === 0 ? cd.value.target : null),
    (target) => {
        if (target && finishedFor !== target) {
            finishedFor = target;
            emit('finished');
        }
    },
);
</script>
