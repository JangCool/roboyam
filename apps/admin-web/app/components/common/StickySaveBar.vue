<template>
    <!--
        화면 하나로 저장하는 설정 화면의 저장 줄 — 구획 밖, 모든 구획 아래(`<form>` 맨 끝)에 둔다(`forms.md` 2-4).
        데스크톱(md 이상)에서는 창 아래에 붙어 따라다니고, 모바일은 고정하지 않고 맨 아래 일반 줄이다(`stickyOnMobile` 을 켠 화면만 모바일도 붙는다).
        `fixed` 가 아니라 `sticky` 라서 줄이 본문 흐름 안에 자리를 차지하므로 페이지 끝에서 마지막 칸을 가리지 않는다.
    -->
    <div class="flex flex-col gap-2 rounded-lg border border-(--line) bg-surface-0 px-3 py-2.5 shadow-md dark:bg-surface-900" :class="stickyOnMobile ? 'sticky bottom-3 z-20' : 'md:sticky md:bottom-3 md:z-20'" data-testid="sticky-save-bar">
        <!-- 저장 실패(칸에 붙지 않는 오류) — 칸마다의 문구는 그 칸 아래에 따로 선다 -->
        <Message v-if="error" severity="error" variant="simple" size="small" :closable="false" role="alert" data-testid="sticky-save-bar-error">{{ error }}</Message>
        <div class="flex flex-wrap items-center justify-between gap-x-3 gap-y-2">
            <p class="m-0 min-w-0 flex-1 basis-48 text-sm" :class="dirty ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color'" role="status" data-testid="sticky-save-bar-status">
                {{ status }}<template v-if="errorCount > 0"> · <span class="text-red-600 dark:text-red-400">{{ t('common.saveBarErrors', { count: errorCount }) }}</span></template>
            </p>
            <div class="ml-auto flex flex-wrap items-center justify-end gap-2">
                <slot name="extra" />
                <Button v-if="revertable" type="button" :label="t('common.reset')" size="small" severity="secondary" variant="text" :disabled="!dirty || saving" data-testid="sticky-save-bar-revert" @click="emit('revert')" />
                <Button :type="submit ? 'submit' : 'button'" :label="t('common.save')" size="small" :loading="saving" :disabled="!dirty || saving || disabled" data-testid="sticky-save-bar-save" @click="onSave" />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
interface Props {
    /** 고친 값이 있다 */
    dirty: boolean;
    /** 저장 요청 중 */
    saving?: boolean;
    /** 그 밖의 이유로 저장을 막는다(입력 오류 …) — 권한이 없으면 이 줄을 그리지 않는다 */
    disabled?: boolean;
    /** 바뀐 칸 수 — 0 이면 숫자 없이 "저장하지 않은 변경이 있어요" */
    count?: number;
    /** 맞지 않는 칸 수 */
    errorCount?: number;
    /** 저장 실패 한 줄(칸에 붙지 않는 오류) */
    error?: string;
    /** [되돌리기]를 보인다 */
    revertable?: boolean;
    /** [저장]을 제출 단추로 둔다 — 폼의 submit 이 받고 `save` 이벤트는 내지 않는다 */
    submit?: boolean;
    /** 모바일(md 미만)에서도 창 아래에 붙인다 — 기본은 데스크톱만. 칸 목록이 길어 저장 줄이 한참 아래인 화면에서만 켠다 */
    stickyOnMobile?: boolean;
}

const props = withDefaults(defineProps<Props>(), { saving: false, disabled: false, count: 0, errorCount: 0, error: undefined, revertable: false, submit: false, stickyOnMobile: false });
const emit = defineEmits<{ (e: 'save'): void; (e: 'revert'): void }>();

const { t } = useI18n();

/** 왼쪽 상태 글(오류 건수는 템플릿이 붙인다) — 고친 게 없으면 "변경한 값이 없어요", 있으면 "N개 칸을 바꿨어요" */
const status = computed(() => {
    if (!props.dirty) return t('common.saveBarClean');
    return props.count > 0 ? t('common.saveBarChanged', { count: props.count }) : t('common.saveBarDirty');
});

function onSave() {
    if (!props.submit) emit('save');
}
</script>
