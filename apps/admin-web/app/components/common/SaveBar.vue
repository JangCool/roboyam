<template>
    <!--
        구획마다 따로 저장하는 설정 화면의 구획 맨 아래 저장 줄(`forms.md` 2-4 「구획마다 따로 저장」 — `SaveBar`). 화면 하나로 저장하는 화면은 `StickySaveBar` 를 쓴다.
        왼쪽에 상태 글이 늘 보이고(「변경한 값이 없어요」/「N개 칸을 바꿨어요」 · 오류 N건), 오른쪽에 [되돌리기]·[저장]. 둘 다 고친 값이 있을 때만 켠다. 입력 오류는 [저장]만 막는다.
        저장 실패 한 줄(칸에 붙지 않는 오류)은 이 줄 안에 선다. 권한이 없으면 부르는 쪽이 이 줄을 그리지 않는다.
        (소모임 설정의 `GroupSaveRow` 와 같은 모양 — 그쪽은 소모임 범위라 고치지 않았다. 합치는 것은 후속 정리)
    -->
    <div class="flex flex-col gap-2 border-t border-(--line) pt-3">
        <Message v-if="error" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ error }}</Message>
        <div class="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
            <p class="m-0 min-w-0 flex-1 basis-48 text-[13px]" :class="dirty ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color'" role="status">
                {{ status }}<template v-if="errorCount > 0"> · <span class="text-red-600 dark:text-red-400">{{ t('common.saveBarErrors', { count: errorCount }) }}</span></template>
            </p>
            <div class="ml-auto flex flex-wrap items-center gap-2">
                <Button type="button" :label="t('common.reset')" size="small" severity="secondary" variant="outlined" :disabled="!dirty || saving" @click="emit('revert')" />
                <Button type="button" :label="t('common.save')" icon="pi pi-check" size="small" :loading="saving" :disabled="!dirty || saving || disabled" @click="emit('save')" />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
interface Props {
    dirty: boolean;
    saving?: boolean;
    /** 입력 오류 등으로 저장을 막는다 */
    disabled?: boolean;
    /** 바뀐 칸 수 — 0 이면 숫자 없이 「저장하지 않은 변경이 있어요」 */
    count?: number;
    errorCount?: number;
    /** 저장 실패 한 줄(칸에 붙지 않는 오류) */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { saving: false, disabled: false, count: 0, errorCount: 0, error: '' });
const emit = defineEmits<{ save: []; revert: [] }>();

const { t } = useI18n();

const status = computed(() => {
    if (!props.dirty) return t('common.saveBarClean');
    return props.count > 0 ? t('common.saveBarChanged', { count: props.count }) : t('common.saveBarDirty');
});
</script>
