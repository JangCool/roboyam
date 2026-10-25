<template>
    <!--
        글 상세의 「토론 설정」·「찬반 토론 열기」 대화창 — 설정 세 칸(`DebateSettingsFields`)을 고쳐 [저장]하면 `save` 로 값을 넘긴다(서버 호출·오류 판정은 페이지).
        열 때마다 그 글의 지금 토론 값으로 채운다(`initial`). 바뀐 것이 없으면 [저장]을 막는다. 닫을 수 없는 토론(`closeLocked`)은 열기 칸을 끄지 못한다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="opening ? t('comment.debateOpenAction') : t('comment.debateSettings')" :style="{ width: 'min(28rem, calc(100vw - 2rem))' }" @update:visible="close">
        <form class="flex flex-col gap-3" novalidate @submit.prevent="submit">
            <DebateSettingsFields v-model="values" :legend="false" :close-locked="closeLocked" :disabled="loading" :error="error" />
            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="loading" @click="close(false)" />
                <Button type="submit" :label="t('common.save')" :loading="loading" :disabled="!changed" data-testid="debate-settings-save" />
            </div>
        </form>
    </Dialog>
</template>

<script setup lang="ts">
import { DEFAULT_DEBATE_SETTINGS, type DebateSettingsValues } from '~/components/board/DebateSettingsFields.types';
import UtilDebate from '~/utils/util-debate';

interface Props {
    visible: boolean;
    /** 지금 값 — 토론이 없으면 열기 끔 + 두 설정 켬 */
    initial?: DebateSettingsValues;
    /** 열린 토론을 닫을 수 없다(서버 `canClose false`) */
    closeLocked?: boolean;
    loading?: boolean;
    /** 서버 문구 */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { initial: () => ({ ...DEFAULT_DEBATE_SETTINGS }), closeLocked: false, loading: false, error: '' });
const emit = defineEmits<{ 'update:visible': [value: boolean]; save: [value: DebateSettingsValues] }>();

const { t } = useI18n();
const values = ref<DebateSettingsValues>({ ...props.initial });
/** 토론이 없는 글에서 열었다 — 머리를 「찬반 토론 열기」로 */
const opening = computed(() => !props.initial.enabled);
const changed = computed(() => !UtilDebate.sameSettings(values.value, props.initial));

// 열 때마다 지금 값으로 — 토론이 없는 글은 「열기」를 켠 채로 시작한다(그러려고 연 대화창이다)
watch(
    () => props.visible,
    (open) => {
        if (open) values.value = props.initial.enabled ? { ...props.initial } : { ...props.initial, enabled: true };
    },
    { immediate: true },
);

function close(next: boolean): void {
    if (!next && props.loading) return;
    emit('update:visible', next);
}

function submit(): void {
    if (props.loading || !changed.value) return;
    emit('save', { ...values.value });
}
</script>
