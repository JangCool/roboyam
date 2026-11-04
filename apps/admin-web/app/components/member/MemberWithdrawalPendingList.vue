<template>
    <!--
        탈퇴의 남은 일 목록 — 탈퇴 관리 서랍과 강제 탈퇴 확인 대화상자가 같은 모양으로 쓴다. 건마다 종류 딱지 · 식별 글(서버 글자 그대로) · 기다리는 끝 시각 · 「관리 화면에서 보기」(기존 관리자 화면의 서랍 쿼리 — `UtilMemberWithdrawal.pendingLink`).
        충전 환불처럼 아직 화면이 없는 종류나 대상 ID 가 없는 건은 링크 대신 흐린 글. 서버 호출은 하지 않는다.
    -->
    <p v-if="!items.length" class="m-0 text-[13px] text-muted-color">{{ emptyText || t('memberWithdrawal.pendingEmpty') }}</p>
    <ul v-else class="m-0 flex list-none flex-col gap-1.5 p-0">
        <li v-for="(item, index) in items" :key="`${item.code}-${item.refId ?? index}`" class="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-(--line) px-3 py-2 text-[13px]">
            <MemberDetailTag :value="t(`memberWithdrawal.pending.${item.code}`)" :icon="UtilMemberWithdrawal.pendingIcon(item.code)" severity="warn" />
            <code class="text-[12px] tabular-nums break-all">{{ item.label }}</code>
            <span v-if="item.waitUntil" class="text-xs text-muted-color tabular-nums">{{ t('memberWithdrawal.pendingWaitUntil', { at: time.full(item.waitUntil) }) }}</span>
            <span class="ml-auto inline-flex items-center">
                <NuxtLink v-if="linkOf(item)" :to="linkOf(item)!" class="inline-flex items-center gap-1 text-xs font-medium text-(--p-primary-color) hover:underline">
                    <i class="pi pi-external-link text-[10px]" aria-hidden="true" />
                    {{ t('memberWithdrawal.pendingOpen') }}
                </NuxtLink>
                <span v-else class="text-xs text-muted-color">{{ t('memberWithdrawal.pendingNoLink') }}</span>
            </span>
        </li>
    </ul>
</template>

<script setup lang="ts">
import type { WithdrawalPendingItem } from '~/composables/member/api/useMemberWithdrawalApi.types';
import UtilMemberWithdrawal from '~/utils/util-member-withdrawal';

interface Props {
    items: WithdrawalPendingItem[];
    /** 비었을 때 보일 글 — 없으면 기본 문구 */
    emptyText?: string;
}

withDefaults(defineProps<Props>(), { emptyText: '' });

const { t } = useI18n();
const time = useDisplayTime();

function linkOf(item: WithdrawalPendingItem) {
    return UtilMemberWithdrawal.pendingLink(item.code, item.refId);
}
</script>
