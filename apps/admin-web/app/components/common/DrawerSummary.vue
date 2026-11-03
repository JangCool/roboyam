<template>
    <!--
        서랍 요약 영역 — 서랍 본문 맨 위(탭이 있으면 탭 위, 어느 탭에서나 보인다). 머리에는 이름(+ 종류·상태 하나)과 앞뒤 넘기기 같은 조작만 두고,
        상태 꼬리표·ID(복사)·바깥 링크·메타 값은 여기에 모은다(광고주 관리 서랍에서 시작한 모양 — 2026-11-03).
        칸 = 라벨 + 값. 값의 강조는 부모가 `valueClass` 로 주고(`UtilBizApplication.EMPH`), 꼬리표·복사 단추처럼 글자가 아닌 값은 칸 슬롯 `#item-<key>`.
        `links` 슬롯 = 아래 링크 줄, `note` 슬롯 = 맨 아래 흐린 안내 한 줄.
    -->
    <section class="mb-3 flex flex-col gap-2 rounded-xl border border-(--line) bg-(--p-content-hover-background) px-3 py-2.5" :aria-label="label">
        <dl v-if="items.length" class="m-0 grid grid-cols-2 gap-x-4 gap-y-2" :class="columns === 3 ? 'sm:grid-cols-3' : ''">
            <div v-for="item in items" :key="item.key" class="flex min-w-0 flex-col gap-0.5" :class="item.wide ? 'col-span-full' : ''">
                <dt class="text-[11px] text-muted-color">{{ item.label }}</dt>
                <dd class="m-0 min-w-0 text-[13px] tabular-nums" :class="[item.wide || item.value === undefined ? '[overflow-wrap:anywhere]' : 'truncate', item.valueClass]" :title="item.title">
                    <slot :name="`item-${item.key}`" :item="item">
                        <code v-if="item.code" class="text-[12px]">{{ item.value }}</code>
                        <template v-else>{{ item.value }}</template>
                    </slot>
                </dd>
            </div>
        </dl>
        <div v-if="$slots.links" class="flex flex-wrap items-center gap-x-4 gap-y-1 text-[12px]" :class="items.length ? 'border-t border-(--line) pt-2' : ''">
            <slot name="links" />
        </div>
        <p v-if="$slots.note" class="m-0 text-[11px] text-muted-color">
            <slot name="note" />
        </p>
    </section>
</template>

<script setup lang="ts">
import type { DrawerSummaryItem } from '~/components/common/DrawerSummary.types';

interface Props {
    /** 영역 이름(화면 읽기용) */
    label: string;
    items?: DrawerSummaryItem[];
    /** 넓은 화면의 열 수 — 기본 3, 2 면 늘 두 단(좁은 화면은 어느 쪽이든 두 단) */
    columns?: 2 | 3;
}

withDefaults(defineProps<Props>(), { items: () => [], columns: 3 });
</script>
