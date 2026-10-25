<template>
    <!-- 오늘 남은 한도 두 칸 — 남은 쪽지 · 남은 새 상대. 한국 시간 0시에 다시 채워진다 -->
    <ul class="m-0 grid list-none grid-cols-2 gap-2 p-0" :aria-label="t('dm.quotaLabel')" data-testid="dm-quota">
        <li class="rounded-lg bg-surface-100 px-3 py-2.5 dark:bg-surface-800/60">
            <p class="m-0 text-lg font-bold leading-tight" data-testid="dm-quota-messages">{{ t('dm.quotaMessages', { n: quota.remainingToday }) }}</p>
            <p class="m-0 mt-0.5 text-xs text-muted-color">{{ t('dm.quotaMessagesLabel', { n: quota.dailyLimit }) }}</p>
        </li>
        <li class="rounded-lg bg-surface-100 px-3 py-2.5 dark:bg-surface-800/60">
            <p class="m-0 text-lg font-bold leading-tight" data-testid="dm-quota-partners">{{ t('dm.quotaPartners', { n: quota.remainingNewPartners }) }}</p>
            <p class="m-0 mt-0.5 text-xs text-muted-color">{{ t('dm.quotaPartnersLabel', { n: quota.newPartnerLimit }) }}</p>
        </li>
    </ul>
</template>

<script setup lang="ts">
import type { DmQuota } from '~/composables/dm/api/useDmApi.types';

interface Props {
    quota: DmQuota;
}

withDefaults(defineProps<Props>(), {});
const { t } = useI18n();
</script>
