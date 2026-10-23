<template>
    <!--
        구획마다 따로 저장하는 소모임 화면(설정 네 구획 · 분류 패널)의 구획 맨 아래 저장 줄(`forms.md` 2-4 「구획마다 따로 저장」) — 왼쪽 상태 글(늘 보인다), 오른쪽 [되돌리기]·[저장].
        [저장]·[되돌리기]는 바꾼 값이 있을 때만 켠다. 입력 오류는 [저장]만 막는다(`disabled`). 권한이 없으면 부르는 쪽이 이 줄을 그리지 않는다.
    -->
    <div class="flex flex-wrap items-center justify-between gap-x-3 gap-y-2 border-t border-(--line) pt-3">
        <p class="m-0 min-w-0 flex-1 basis-48 text-[13px]" :class="dirty ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color'" role="status">
            {{ note }}<template v-if="errorCount > 0"> · <span class="text-red-600 dark:text-red-400">{{ t('common.saveBarErrors', { count: errorCount }) }}</span></template>
        </p>
        <div class="ml-auto flex flex-wrap items-center gap-2">
            <Button type="button" :label="t('common.reset')" size="small" severity="secondary" variant="outlined" :disabled="!dirty || saving" @click="emit('revert')" />
            <Button type="button" :label="t('common.save')" icon="pi pi-check" size="small" :loading="saving" :disabled="!dirty || saving || disabled" @click="emit('save')" />
        </div>
    </div>
</template>

<script setup lang="ts">
interface Props {
    note: string;
    dirty: boolean;
    saving?: boolean;
    /** 입력 오류 등으로 저장을 막는다 */
    disabled?: boolean;
    errorCount?: number;
}

withDefaults(defineProps<Props>(), { saving: false, disabled: false, errorCount: 0 });
const emit = defineEmits<{ save: []; revert: [] }>();

const { t } = useI18n();
</script>
