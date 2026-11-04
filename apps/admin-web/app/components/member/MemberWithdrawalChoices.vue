<template>
    <!--
        회원이 탈퇴 화면에서 고른 것(배송완료 대기/지금 · 광고 기다리기/포기 · 충전 환불/포기 · 모임별 넘기기/해산/자동) — 탈퇴 관리 서랍과 회원 상세 블록이 같은 모양으로 쓴다.
        세 선택값은 서버가 정규화해 늘 값이 있다(해당 없는 상황이어도 기본값 — `WithdrawalChoicesView`). 모임장인 소모임이 없으면 「해당 없음」.
    -->
    <dl class="m-0 grid grid-cols-[8.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-[13px]">
        <dt class="text-muted-color">{{ t('memberWithdrawal.choice.deliveredLabel') }}</dt>
        <dd class="m-0">{{ t(`memberWithdrawal.choice.delivered.${choices.deliveredChoice}`) }}</dd>
        <dt class="text-muted-color">{{ t('memberWithdrawal.choice.bizLabel') }}</dt>
        <dd class="m-0">{{ t(`memberWithdrawal.choice.biz.${choices.bizChoice}`) }}</dd>
        <dt class="text-muted-color">{{ t('memberWithdrawal.choice.paidLabel') }}</dt>
        <dd class="m-0">{{ t(`memberWithdrawal.choice.paid.${choices.paidPointChoice}`) }}</dd>
        <dt class="text-muted-color">{{ t('memberWithdrawal.choice.groupsLabel') }}</dt>
        <dd class="m-0">
            <span v-if="!choices.groupChoices.length" class="text-muted-color">{{ t('memberWithdrawal.choice.notApplicable') }}</span>
            <ul v-else class="m-0 flex list-none flex-col gap-0.5 p-0">
                <li v-for="g in choices.groupChoices" :key="g.groupId" class="flex flex-wrap items-center gap-1.5">
                    <span class="font-medium">{{ g.groupName }}</span>
                    <MemberDetailTag :value="t(`memberWithdrawal.choice.group.${g.choice}`)" severity="secondary" />
                    <span v-if="g.choice === 'TRANSFER' && g.transferTo" class="text-xs text-muted-color">{{ t('memberWithdrawal.choice.transferTo', { name: g.transferTo }) }}</span>
                </li>
            </ul>
        </dd>
    </dl>
</template>

<script setup lang="ts">
import type { WithdrawalChoicesView } from '~/composables/member/api/useMemberWithdrawalApi.types';

interface Props {
    choices: WithdrawalChoicesView;
}

defineProps<Props>();

const { t } = useI18n();
</script>
