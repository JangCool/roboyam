<template>
    <!--
        단품 이름 한 칸(결정 88) — 주문 줄·요청 줄·환불·수량 화면이 같이 쓴다. 축 없는 단품 하나(빈 이름)는 「단품 하나」, 추가 구성상품은 「추가 구성」 딱지를 붙인다.
    -->
    <span class="inline-flex min-w-0 flex-wrap items-center gap-1">
        <span :class="name ? '' : 'text-muted-color'">{{ name || t('groupBuy.skuSingle') }}</span>
        <Tag v-if="kind === 'ADDON'" :value="t('groupBuy.skuAddonTag')" severity="info" class="px-1.5! py-0! text-[10px]!" />
    </span>
</template>

<script setup lang="ts">
import type { GroupBuySkuKind } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';

interface Props {
    /** 단품 이름(빈 글자 = 축 없는 단품 하나) */
    name?: string | null;
    kind?: GroupBuySkuKind | null;
}

withDefaults(defineProps<Props>(), { name: '', kind: 'MAIN' });

const { t } = useI18n();
</script>
