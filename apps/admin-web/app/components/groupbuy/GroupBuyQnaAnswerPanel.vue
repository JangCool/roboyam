<template>
    <!--
        공구 문의의 답변 칸 — 답변이 없으면 바로 쓰는 칸, 있으면 답변 + 「답변 고치기」(고치면 지금 내용은 수정 이력으로 — 서버 몫).
        서버는 부르지 않는다(`submit` 으로 올린다). 부르는 서랍이 질문·답변 시각으로 `:key` 를 바꿔 저장 뒤 처음 모양으로 돌아온다.
    -->
    <div class="flex flex-col gap-2">
        <div v-if="answer && !editing" class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2">
            <p class="m-0 whitespace-pre-line text-[13px] leading-relaxed [overflow-wrap:anywhere]">{{ answer.body }}</p>
            <span class="text-[11px] text-muted-color">{{ t('groupBuyQna.answerBy', { name: answer.answeredBy.name, at: time.shortDateTime(answer.answeredAt) }) }}</span>
            <span v-if="answer.updatedAt && answer.updatedBy" class="text-[11px] text-muted-color">{{ t('groupBuyQna.answerUpdatedBy', { name: answer.updatedBy.name, at: time.shortDateTime(answer.updatedAt) }) }}</span>
            <Button v-if="canAnswer" type="button" size="small" severity="secondary" variant="outlined" icon="pi pi-pencil" :label="t('groupBuyQna.answerEdit')" class="mt-1 self-start" @click="startEdit" />
        </div>
        <p v-else-if="!answer && !canAnswer" class="m-0 text-[13px] text-muted-color">{{ blockedText || t('groupBuyQna.noAnswer') }}</p>

        <form v-if="canAnswer && (editing || !answer)" class="flex flex-col gap-2" novalidate data-testid="group-buy-qna-answer-form" @submit.prevent="submit">
            <LabeledField :label="t('groupBuyQna.answerLabel')" :error="fieldError || localError">
                <template #aside>
                    <small class="text-xs tabular-nums text-muted-color">{{ draft.length }} / {{ MAX }}</small>
                </template>
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="draft" rows="4" auto-resize :maxlength="MAX" :placeholder="t('groupBuyQna.answerPlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>
            <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color">
                <i class="pi text-xs" :class="answer ? 'pi-history' : 'pi-bell'" aria-hidden="true" />{{ answer ? t('groupBuyQna.answerEditNote') : t('groupBuyQna.answerNotifyNote') }}
            </p>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
            <div class="flex justify-end gap-2">
                <Button v-if="answer" type="button" :label="t('common.cancel')" severity="secondary" variant="text" size="small" :disabled="busy" @click="editing = false" />
                <Button type="submit" :label="answer ? t('groupBuyQna.answerUpdate') : t('groupBuyQna.answerSubmit')" size="small" :loading="busy" />
            </div>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyQnaDetail } from '~/composables/groupbuy/api/useGroupBuyQnaAdminApi.types';

interface Props {
    answer: GroupBuyQnaDetail['answer'];
    /** `commerce.order.manage` 이고 작성자가 지우지 않았다 */
    canAnswer: boolean;
    /** 답할 수 없는 까닭(작성자가 지움 등) — 답변이 없을 때 보인다 */
    blockedText?: string;
    busy?: boolean;
    error?: string;
    fieldError?: string;
}

const props = withDefaults(defineProps<Props>(), { blockedText: '', busy: false, error: '', fieldError: '' });
const emit = defineEmits<{ submit: [body: string] }>();

const MAX = 2000;
const { t } = useI18n();
const time = useDisplayTime();
const editing = ref(false);
const draft = ref('');
const localError = ref('');

function startEdit(): void {
    draft.value = props.answer?.body ?? '';
    localError.value = '';
    editing.value = true;
}

function submit(): void {
    const text = draft.value.trim();
    localError.value = text ? '' : t('groupBuyQna.answerRequired');
    if (text) emit('submit', text);
}
</script>
