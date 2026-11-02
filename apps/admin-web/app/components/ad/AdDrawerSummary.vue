<template>
    <!--
        광고 서랍 요약 영역 — 서랍 본문 맨 위(탭 위에 고정, 어느 탭에서나 보인다). 머리에는 이름·종류·상태만 두고 나머지 요약 값은 여기에 모은다.
        광고주 관리 서랍(`AdAdvertiserDrawer` — ID·등록번호·등록일·누적 결제·최근 광고일·사업자 링크) · 광고 일정표 서랍(`AdBookingDrawer` — 유형·기간·결제 방식·심사 링크)이 같이 쓴다.
        칸 값의 강조는 부모가 `valueClass` 로 준다(`UtilBizApplication.EMPH`). 링크는 `links` 슬롯.
    -->
    <section class="mb-3 flex flex-col gap-2 rounded-xl border border-(--line) bg-(--p-content-hover-background) px-3 py-2.5" :aria-label="label">
        <dl class="m-0 grid grid-cols-2 gap-x-4 gap-y-2 sm:grid-cols-3">
            <div v-for="item in items" :key="item.key" class="flex min-w-0 flex-col gap-0.5">
                <dt class="text-[11px] text-muted-color">{{ item.label }}</dt>
                <dd class="m-0 min-w-0 truncate text-[13px] tabular-nums" :class="item.valueClass" :title="item.title">
                    <code v-if="item.code" class="text-[12px]">{{ item.value }}</code>
                    <template v-else>{{ item.value }}</template>
                </dd>
            </div>
        </dl>
        <div v-if="$slots.links" class="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-(--line) pt-2 text-[12px]">
            <slot name="links" />
        </div>
    </section>
</template>

<script setup lang="ts">
import type { AdDrawerSummaryItem } from '~/components/ad/AdDrawerSummary.types';

interface Props {
    /** 영역 이름(화면 읽기용) */
    label: string;
    items: AdDrawerSummaryItem[];
}

defineProps<Props>();
</script>
