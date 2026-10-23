<template>
    <!-- 대화방 ⋯ 시트 — 차단 · 대화 삭제(community 의 쪽지 신고는 로보얌 신고 대상에 쪽지가 없어 뺐다) -->
    <AppSheet v-model:visible="visible" :title="title">
        <ul class="m-0 mt-3 list-none p-0">
            <li v-if="showBlock">
                <button type="button" :class="ITEM" data-testid="dm-menu-block" @click="pick(blocked ? 'unblock' : 'block')">
                    {{ blocked ? t('dm.menuUnblock', { nickname }) : t('dm.menuBlock', { nickname }) }}
                    <small class="mt-0.5 block text-xs font-normal text-muted-color">{{ blocked ? t('dm.menuUnblockHint') : t('dm.menuBlockHint') }}</small>
                </button>
            </li>
            <li>
                <button type="button" :class="[ITEM, 'text-red-600 dark:text-red-400']" data-testid="dm-menu-delete" @click="pick('remove')">
                    {{ t('dm.menuDelete') }}
                    <small class="mt-0.5 block text-xs font-normal text-muted-color">{{ t('dm.menuDeleteHint') }}</small>
                </button>
            </li>
        </ul>
        <div class="px-5 pt-3">
            <Button type="button" :label="t('common.close')" severity="secondary" variant="outlined" fluid @click="visible = false" />
        </div>
    </AppSheet>
</template>

<script setup lang="ts">
/** 대화방 ⋯ 시트 — 무엇을 눌렀는지만 올린다(`block`·`unblock`·`remove`). 상대가 탈퇴했거나 운영진이면 `showBlock` 이 꺼진다 */
interface Props {
    title: string;
    nickname: string;
    showBlock?: boolean;
    blocked?: boolean;
}

withDefaults(defineProps<Props>(), { showBlock: true, blocked: false });

const emit = defineEmits<{ block: []; unblock: []; remove: [] }>();
const visible = defineModel<boolean>('visible', { required: true });
const { t } = useI18n();

function pick(kind: 'block' | 'unblock' | 'remove') {
    visible.value = false;
    if (kind === 'block') emit('block');
    else if (kind === 'unblock') emit('unblock');
    else emit('remove');
}

const ITEM = 'block w-full border-b border-surface px-5 py-3 text-left text-sm hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:hover:bg-surface-800';
</script>
