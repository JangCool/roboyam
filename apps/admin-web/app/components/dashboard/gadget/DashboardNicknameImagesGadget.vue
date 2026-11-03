<template>
    <!-- 닉네임 이미지 심사 대기 — 건수 + 오래된 차례 썸네일 몇 장. 누르면 닉네임 이미지 심사 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/members/nickname-images" @refresh="state.reload">
        <div v-if="v" class="flex flex-col gap-3">
            <DashboardStatCard :label="t('dashboard.nicknameImagesPending')" :count="v.pending" icon="pi pi-image" :tone="v.pending > 0 ? 'warn' : 'normal'" to="/members/nickname-images" compact />
            <ul v-if="v.recent.length" class="m-0 flex list-none flex-wrap gap-2 p-0">
                <li v-for="img in v.recent" :key="img.id">
                    <img :src="img.imageUrl" :alt="t('dashboard.nicknameImageOf', { nickname: img.nickname })" :title="img.nickname" class="size-10 rounded-md border border-(--line) object-cover" loading="lazy" />
                </li>
            </ul>
        </div>
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const state = useDashboardKindData('NICKNAME_IMAGES', () => props.gadget);
const v = computed(() => state.data.value);
</script>
