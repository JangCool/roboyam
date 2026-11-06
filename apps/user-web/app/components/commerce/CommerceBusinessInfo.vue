<template>
    <!--
        통신판매업자 정보(전자상거래법 제10조 ① — 계약 결정 18·56). 사이트 푸터(`AppFooter`, 모바일·데스크톱)와 공구 상세 하단이 같이 쓴다.
        값은 부르는 쪽이 `useCommerceInfo().shownBusiness`(판매 스위치 켜짐 + 칸 완비일 때만)로 넘긴다 — 여기서는 서버를 부르지 않는다.
        `footer`: 이름표·값 표 — 넓은 화면(lg)은 두 쌍씩(이름표 | 값 | 이름표 | 값), 그보다 좁으면 한 쌍씩. 긴 칸(주소·개인정보 관리책임자)은 한 줄을 다 쓴다 · `panel`: 정의 목록(공구 화면 하단).
    -->
    <section :aria-label="t('commerce.bizTitle')" :class="variant === 'footer' ? 'text-xs leading-relaxed text-muted-color' : 'panel-card p-4 text-sm'" data-testid="commerce-business-info">
        <template v-if="variant === 'footer'">
            <dl class="m-0 grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-0.5 lg:grid-cols-[auto_minmax(0,1fr)_auto_minmax(0,1fr)]">
                <template v-for="row in footerRows" :key="row.key">
                    <dt class="whitespace-nowrap" :class="{ 'col-start-1': FOOTER_WIDE.includes(row.key) }">{{ row.label }}</dt>
                    <dd class="m-0 min-w-0 text-surface-600 [overflow-wrap:anywhere] dark:text-surface-300" :class="{ 'lg:col-span-3': FOOTER_WIDE.includes(row.key) }">{{ row.value }}</dd>
                </template>
            </dl>
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

/** 푸터 표에서 한 줄을 다 쓰는 긴 칸 — 짧은 칸 다음, 맨 아래에 둔다 */
const FOOTER_WIDE = ['privacy', 'address'];
/** 푸터 표 차례 — 짧은 칸(두 쌍씩 짝) 먼저, 긴 칸은 뒤로 */
const footerRows = computed(() => [...rows.value.filter((row) => !FOOTER_WIDE.includes(row.key)), ...FOOTER_WIDE.flatMap((key) => rows.value.filter((row) => row.key === key))]);
</script>
