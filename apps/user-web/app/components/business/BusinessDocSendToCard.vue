<template>
    <!--
        사업자등록증 「우편·팩스로 보내기」 보낼 곳 안내 — 신청 2단계(우편·팩스를 골랐을 때)와 사업자 센터 홈 심사 중(우편·팩스로 낸 신청)이 같이 쓴다.
        우편 주소·받는 사람 · 팩스 번호(서버 설정값 — 빈 칸은 줄을 감춘다) · 「서류 여백에 상호와 등록번호」 · 「받은 날부터 심사」.
    -->
    <section class="flex flex-col gap-3 rounded-lg border border-hairline p-3.5" :aria-labelledby="titleId" data-testid="business-doc-send-to">
        <h3 :id="titleId" class="m-0 flex flex-wrap items-center gap-2 text-sm font-semibold">
            <i class="pi pi-envelope text-xs text-muted-color" aria-hidden="true" />{{ t('business.docSend.title') }}
        </h3>
        <div v-if="!sendTo" class="h-16 animate-pulse rounded-md bg-surface-200 dark:bg-surface-800" role="status" :aria-label="t('common.loading')" />
        <dl v-else class="m-0 grid grid-cols-[4.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
            <template v-if="sendTo.address">
                <dt class="text-muted-color">{{ t('business.docSend.mail') }}</dt>
                <dd class="m-0 break-words">
                    <template v-if="sendTo.postcode">({{ sendTo.postcode }}) </template>{{ sendTo.address }}
                    <span v-if="sendTo.recipient" class="block text-xs text-muted-color">{{ sendTo.recipient }}</span>
                </dd>
            </template>
            <template v-if="sendTo.fax">
                <dt class="text-muted-color">{{ t('business.docSend.fax') }}</dt>
                <dd class="m-0 tabular-nums">{{ sendTo.fax }}</dd>
            </template>
        </dl>
        <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-xs leading-relaxed">
            <li class="flex items-start gap-1.5">
                <i class="pi pi-pencil mt-0.5 text-[0.6875rem] text-muted-color" aria-hidden="true" />
                <span>
                    {{ t('business.docSend.markMargin') }}
                    <span v-if="companyName || bizNo" class="block text-muted-color">{{ t('business.docSend.markExample', { company: companyName || '—', bizNo: bizNo || '—' }) }}</span>
                </span>
            </li>
            <li class="flex items-start gap-1.5"><i class="pi pi-calendar mt-0.5 text-[0.6875rem] text-muted-color" aria-hidden="true" />{{ t('business.docSend.reviewFromReceipt') }}</li>
        </ul>
    </section>
</template>

<script setup lang="ts">
import type { BusinessDocSendTo } from '~/composables/business/api/useBusinessApi.types';

interface Props {
    /** 받는 중이면 null */
    sendTo: BusinessDocSendTo | null;
    /** 여백에 적을 상호·등록번호 보기(모르면 비움) */
    companyName?: string;
    bizNo?: string;
}

withDefaults(defineProps<Props>(), { companyName: '', bizNo: '' });

const { t } = useI18n();
const titleId = useId();
</script>
