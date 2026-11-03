<template>
    <!--
        대시보드 숫자 카드 — 라벨 · 큰 숫자 · 설명(note) · 링크. 처리할 일·커머스·가젯들의 숫자 칸이 모두 이 모양이다.
        · 링크가 하나(`links` 한 개 또는 `to`)면 카드 전체가 링크, 둘 이상(도메인을 합친 칸)이면 카드 아래쪽에 도메인별 링크 줄.
        · 숫자 위치가 칸마다 같게: 라벨은 한 줄(넘치면 말줄임), 설명은 늘 두 줄 자리를 잡고(넘치면 말줄임), 링크 줄은 카드 **맨 아래에 고정**(`mt-auto`) — 설명 길이와 무관하게 같은 높이에 선다.
        · `row`(처리할 일·커머스·매출 요약·회원 현황 등 — 사용자 결정 2026-11-03 「영역이 너무 크고 넓다」): 한 줄 행(높이 약 34px — py-1.5 + 숫자 15px·줄 높이 20px) — 아이콘·라벨(말줄임) 왼쪽, 도메인별 링크·숫자 오른쪽. 설명은 마우스를 올리면 보인다.
        · 톤: `warn` 주황 · `danger` 빨강(숫자가 0 이면 숫자 색은 기본).
    -->
    <NuxtLink v-if="row && single" :to="single" class="flex h-full min-w-0 items-center gap-2 rounded-lg border px-3 py-1.5 transition-colors hover:bg-surface-50 dark:hover:bg-surface-900" :class="border" :title="hint">
        <i v-if="icon" :class="icon" class="shrink-0 text-xs text-muted-color" aria-hidden="true" />
        <span class="min-w-0 flex-1 truncate text-[13px] leading-5 text-muted-color">{{ label }}</span>
        <span class="shrink-0 text-[15px] leading-5 font-semibold whitespace-nowrap tabular-nums" :class="text">{{ countText }}</span>
    </NuxtLink>
    <div v-else-if="row" class="flex h-full min-w-0 items-center gap-2 rounded-lg border px-3 py-1.5" :class="border" :title="hint">
        <i v-if="icon" :class="icon" class="shrink-0 text-xs text-muted-color" aria-hidden="true" />
        <span class="min-w-0 flex-1 truncate text-[13px] leading-5 text-muted-color">{{ label }}</span>
        <span v-if="links.length" class="flex shrink-0 gap-2 text-[11px]">
            <NuxtLink v-for="link in links" :key="link.to" :to="link.to" class="inline-flex items-center gap-0.5 text-primary hover:underline">
                {{ link.label }}<i class="pi pi-angle-right text-[9px]" aria-hidden="true" />
            </NuxtLink>
        </span>
        <span class="shrink-0 text-[15px] leading-5 font-semibold whitespace-nowrap tabular-nums" :class="text">{{ countText }}</span>
    </div>
    <NuxtLink v-else-if="single" :to="single" class="flex h-full min-w-0 flex-col gap-1 rounded-xl border px-4 transition-colors hover:bg-surface-50 dark:hover:bg-surface-900" :class="[border, pad]" :title="hint">
        <span class="flex min-w-0 items-center gap-2 text-[13px] text-muted-color"><i v-if="icon" :class="icon" class="shrink-0 text-xs" aria-hidden="true" /><span class="truncate" :title="label">{{ label }}</span></span>
        <span class="text-2xl font-semibold tabular-nums" :class="text">{{ countText }}</span>
        <span v-if="!compact" class="line-clamp-2 min-h-[2lh] text-[11px] leading-snug text-muted-color" :title="note || undefined">{{ note }}</span>
    </NuxtLink>
    <div v-else class="flex h-full min-w-0 flex-col gap-1 rounded-xl border px-4" :class="[border, pad]" :title="hint">
        <span class="flex min-w-0 items-center gap-2 text-[13px] text-muted-color"><i v-if="icon" :class="icon" class="shrink-0 text-xs" aria-hidden="true" /><span class="truncate" :title="label">{{ label }}</span></span>
        <span class="text-2xl font-semibold tabular-nums" :class="text">{{ countText }}</span>
        <span v-if="!compact" class="line-clamp-2 min-h-[2lh] text-[11px] leading-snug text-muted-color" :title="note || undefined">{{ note }}</span>
        <span v-if="links.length" class="mt-auto flex flex-wrap gap-x-3 gap-y-1 pt-0.5 text-[12px]">
            <NuxtLink v-for="link in links" :key="link.to" :to="link.to" class="inline-flex items-center gap-1 text-primary hover:underline">
                {{ link.label }}<i class="pi pi-angle-right text-[10px]" aria-hidden="true" />
            </NuxtLink>
        </span>
    </div>
</template>

<script setup lang="ts">
import type { DashboardStatLink, DashboardStatTone } from '~/components/dashboard/DashboardStatCard.types';

interface Props {
    label: string;
    /** 큰 숫자(이미 글자로 만든 값도 된다 — 금액 등) */
    count: number | string;
    note?: string;
    /** PrimeIcons 클래스 */
    icon?: string;
    tone?: DashboardStatTone;
    /** 카드 전체 링크 — `links` 보다 먼저 */
    to?: string;
    /** 링크들 — 하나면 카드 전체가 링크, 둘 이상이면 아래 링크 줄 */
    links?: DashboardStatLink[];
    /** 설명 자리를 잡지 않는다(설명 없는 작은 숫자 칸) */
    compact?: boolean;
    /** 한 줄 행 모양(라벨 왼쪽 · 숫자 오른쪽) — 설명은 그리지 않는다 */
    row?: boolean;
}

const props = withDefaults(defineProps<Props>(), { note: '', icon: '', tone: 'normal', to: '', links: () => [], compact: false, row: false });

const { locale } = useI18n();

const single = computed(() => props.to || (props.links.length === 1 ? props.links[0]!.to : ''));
/** 작은 칸은 위아래 여백도 줄인다 */
const pad = computed(() => (props.compact ? 'py-2' : 'py-3'));
/** 설명을 그리지 않는 모양(작은 칸·행)은 마우스를 올리면 라벨과 설명을 보인다 */
const hint = computed(() => ((props.compact || props.row) && props.note ? `${props.label} — ${props.note}` : props.row ? props.label : undefined));
const countText = computed(() => (typeof props.count === 'number' ? props.count.toLocaleString(locale.value) : props.count));
const positive = computed(() => (typeof props.count === 'number' ? props.count > 0 : true));

const border = computed(() => (props.tone === 'danger' ? 'border-red-300 dark:border-red-800' : props.tone === 'warn' ? 'border-amber-300 dark:border-amber-800' : 'border-(--line)'));
const text = computed(() => {
    if (!positive.value) return '';
    return props.tone === 'danger' ? 'text-red-600 dark:text-red-400' : props.tone === 'warn' ? 'text-amber-700 dark:text-amber-400' : '';
});
</script>
