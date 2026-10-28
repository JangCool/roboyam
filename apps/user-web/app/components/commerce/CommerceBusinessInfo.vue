<template>
    <!--
        통신판매업자 정보(전자상거래법 제10조 ① — 계약 결정 18·56). 사이트 푸터(`AppFooter`, 데스크톱)와 공구 상세 하단이 같이 쓴다.
        값은 부르는 쪽이 `useCommerceInfo().shownBusiness`(판매 스위치 켜짐 + 칸 완비일 때만)로 넘긴다 — 여기서는 서버를 부르지 않는다.
        `footer`: 작은 글씨 한 덩어리(구분점으로 잇는다) · `panel`: 정의 목록(공구 화면 하단).
    -->
    <section :aria-label="t('commerce.bizTitle')" :class="variant === 'footer' ? 'text-xs leading-relaxed text-muted-color' : 'rounded-xl border border-surface bg-surface-0 p-4 text-sm dark:bg-surface-900'" data-testid="commerce-business-info">
        <template v-if="variant === 'footer'">
            <p class="m-0">
                <span v-for="(row, index) in rows" :key="row.key">
                    <span v-if="index > 0" aria-hidden="true"> · </span>
                    <span>{{ row.label }} {{ row.value }}</span>
                </span>
            </p>
        </template>
        <template v-else>
            <h2 class="m-0 mb-2 text-sm font-semibold">{{ t('commerce.bizTitle') }}</h2>
            <dl class="m-0 grid grid-cols-[minmax(0,8rem)_minmax(0,1fr)] gap-x-3 gap-y-1 text-xs">
                <template v-for="row in rows" :key="row.key">
                    <dt class="text-muted-color">{{ row.label }}</dt>
                    <dd class="m-0 min-w-0 [overflow-wrap:anywhere]">{{ row.value }}</dd>
                </template>
            </dl>
            <p v-if="business.policyText" class="m-0 mt-3 whitespace-pre-line text-xs leading-relaxed text-muted-color">{{ business.policyText }}</p>
            <p class="m-0 mt-2 text-xs text-muted-color">{{ t('commerce.bizDispute') }}</p>
        </template>
    </section>
</template>

<script setup lang="ts">
import type { CommerceBusiness } from '~/composables/commerce/api/useCommerceInfoApi.types';

interface Props {
    business: CommerceBusiness;
    variant?: 'footer' | 'panel';
}

const props = withDefaults(defineProps<Props>(), { variant: 'panel' });
const { t } = useI18n();

/** 보일 칸 — 비어 있는 선택 칸(운영시간·개인정보 책임자·호스팅)은 뺀다 */
const rows = computed(() => {
    const b = props.business;
    const all: { key: string; label: string; value: string | null }[] = [
        { key: 'company', label: t('commerce.bizCompany'), value: b.companyName },
        { key: 'ceo', label: t('commerce.bizCeo'), value: b.ceoName },
        { key: 'businessNo', label: t('commerce.bizNo'), value: b.businessNo },
        { key: 'mailOrder', label: t('commerce.bizMailOrder'), value: b.mailOrderAuthority ? `${b.mailOrderNo} (${b.mailOrderAuthority})` : b.mailOrderNo },
        { key: 'address', label: t('commerce.bizAddress'), value: b.address },
        { key: 'phone', label: t('commerce.bizPhone'), value: b.phone },
        { key: 'email', label: t('commerce.bizEmail'), value: b.email },
        { key: 'hours', label: t('commerce.bizHours'), value: b.hours },
        { key: 'privacy', label: t('commerce.bizPrivacyOfficer'), value: b.privacyOfficer },
        { key: 'hosting', label: t('commerce.bizHosting'), value: b.hostingProvider },
    ];
    return all.filter((row): row is { key: string; label: string; value: string } => !!row.value);
});
</script>
