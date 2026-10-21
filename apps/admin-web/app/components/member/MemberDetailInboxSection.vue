<template>
    <!-- 회원 상세 — 알림함 구획(읽기 전용, 그누보드7 알림함 대응 후보). 전체·안 읽은 알림 수만 보이고 알림 내용은 보이지 않는다 -->
    <div v-if="detail.settings" class="flex flex-col gap-2">
        <dl class="m-0 grid grid-cols-2 gap-2">
            <div v-for="item in items" :key="item.key" class="rounded-lg border border-(--line) px-3 py-2">
                <dt class="text-xs text-muted-color">{{ item.label }}</dt>
                <dd class="m-0 mt-0.5 text-base font-semibold tabular-nums">{{ item.value.toLocaleString(locale) }}</dd>
            </div>
        </dl>
        <p class="text-xs text-muted-color">{{ t('member.detail.inbox.note') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { MemberDetail } from '~/composables/member/api/useMemberApi.types';

interface Props {
    detail: MemberDetail;
}

const props = defineProps<Props>();

const { t, locale } = useI18n();

const items = computed(() => [
    { key: 'total', label: t('member.detail.inbox.total'), value: props.detail.settings?.inbox.totalCount ?? 0 },
    { key: 'unread', label: t('member.detail.inbox.unread'), value: props.detail.settings?.inbox.unreadCount ?? 0 },
]);
</script>
