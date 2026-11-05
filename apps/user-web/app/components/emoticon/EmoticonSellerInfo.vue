<template>
    <!--
        유료 로보티콘 팩의 판매자 표시 — 카카오 방식이라 **판매자는 사이트**(사이트 사업자 정보)이고 작가는 따로 「작가」로만 보인다(계약 「가격과 결제」).
        팩 상세와 구매 확인창이 같이 쓴다. 사업자 정보는 부르는 쪽이 `useCommerceInfo` 에서 받아 넘긴다(칸이 다 찼을 때만 — 없으면 판매자 이름만 보이고 안내 한 줄).
        `compact`: 구매 확인창용 두 줄(판매자 · 작가).
    -->
    <section :aria-label="t('emoticon.sellerTitle')" :class="compact ? 'text-xs leading-relaxed' : 'panel-card p-4 text-sm'" data-testid="emoticon-seller-info">
        <template v-if="compact">
            <p class="m-0"><span class="text-muted-color">{{ t('emoticon.sellerLabel') }}</span> {{ sellerName }}</p>
            <p v-if="creatorName" class="m-0"><span class="text-muted-color">{{ t('emoticon.creatorLabel') }}</span> {{ creatorName }}</p>
        </template>
        <template v-else>
            <h2 class="m-0 mb-2 text-sm font-semibold">{{ t('emoticon.sellerTitle') }}</h2>
            <dl class="m-0 grid grid-cols-[minmax(0,7rem)_minmax(0,1fr)] gap-x-3 gap-y-1 text-xs">
                <dt class="text-muted-color">{{ t('emoticon.sellerLabel') }}</dt>
                <dd class="m-0 min-w-0 font-semibold">{{ sellerName }}</dd>
                <template v-for="row in rows" :key="row.key">
                    <dt class="text-muted-color">{{ row.label }}</dt>
                    <dd class="m-0 min-w-0 [overflow-wrap:anywhere]">{{ row.value }}</dd>
                </template>
                <template v-if="creatorName">
                    <dt class="text-muted-color">{{ t('emoticon.creatorLabel') }}</dt>
                    <dd class="m-0 min-w-0">{{ creatorName }}</dd>
                </template>
            </dl>
            <p v-if="!business" class="m-0 mt-2 text-xs text-muted-color">{{ t('emoticon.sellerInfoPending') }}</p>
            <p class="m-0 mt-2 text-xs leading-relaxed text-muted-color">{{ t('emoticon.sellerNote', { seller: sellerName }) }}</p>
        </template>
    </section>
</template>

<script setup lang="ts">
import type { CommerceBusiness } from '~/composables/commerce/api/useCommerceInfoApi.types';

interface Props {
    /** 사이트 사업자 정보 — 칸이 다 차지 않았으면 null */
    business?: CommerceBusiness | null;
    /** 사업자 정보가 없을 때 쓰는 판매자 이름(사이트 이름) */
    siteName: string;
    /** 작가 닉네임(운영 팩은 빈 글자) */
    creatorName?: string;
    compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), { business: null, creatorName: '', compact: false });
const { t } = useI18n();

const sellerName = computed(() => props.business?.companyName || props.siteName);

/** 판매자 칸 — 대표·사업자등록번호·통신판매업·주소·연락처(문구는 공구 화면과 같은 `commerce.biz*` 키) */
const rows = computed(() => {
    const b = props.business;
    if (!b) return [];
    const all: { key: string; label: string; value: string | null }[] = [
        { key: 'ceo', label: t('commerce.bizCeo'), value: b.ceoName },
        { key: 'businessNo', label: t('commerce.bizNo'), value: b.businessNo },
        { key: 'mailOrder', label: t('commerce.bizMailOrder'), value: b.mailOrderAuthority ? `${b.mailOrderNo} (${b.mailOrderAuthority})` : b.mailOrderNo },
        { key: 'address', label: t('commerce.bizAddress'), value: b.address },
        { key: 'phone', label: t('commerce.bizPhone'), value: b.phone },
        { key: 'email', label: t('commerce.bizEmail'), value: b.email },
    ];
    return all.filter((row): row is { key: string; label: string; value: string } => !!row.value);
});
</script>
