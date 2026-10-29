<template>
    <!--
        공구 문의 쓰기·고치기 칸 — 본문(1~1,000자) · 「연락처·주소는 쓰지 마세요」 상시 안내 · 비밀글. 서버는 부르지 않는다(`submit` 으로 올린다).
        개인정보 판정·자동 비밀글은 서버 몫이다(계약 「Q&A 규칙」).
    -->
    <form class="flex flex-col gap-2" novalidate data-testid="group-buy-qna-form" @submit.prevent="submit">
        <LabeledField :label="t('groupBuyQna.bodyLabel')" :error="fieldError || localError">
            <template #aside>
                <span class="text-xs tabular-nums text-muted-color">{{ body.length }} / {{ MAX }}</span>
            </template>
            <template #default="{ id, describedBy, invalid }">
                <Textarea :id="id" v-model="body" rows="3" :maxlength="MAX" :placeholder="t('groupBuyQna.bodyPlaceholder')" :invalid="invalid" :aria-describedby="describedBy" fluid auto-resize />
            </template>
        </LabeledField>
        <p class="m-0 flex items-start gap-1.5 text-xs text-orange-700 dark:text-orange-400"><i class="pi pi-shield mt-0.5 text-xs" aria-hidden="true" />{{ t('groupBuyQna.privacyHint') }}</p>
        <div class="flex flex-wrap items-center justify-between gap-2">
            <div class="flex items-center gap-1.5">
                <Checkbox v-model="secret" binary :input-id="secretId" />
                <label :for="secretId" class="text-sm">{{ t('groupBuyQna.secretLabel') }}</label>
            </div>
            <div class="flex gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" size="small" :disabled="busy" @click="emit('cancel')" />
                <Button type="submit" :label="submitLabel" size="small" :loading="busy" />
            </div>
        </div>
        <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
    </form>
</template>

<script setup lang="ts">
import type { GroupBuyQuestionRequest } from '~/composables/groupbuy/api/useGroupBuyQnaApi.types';

interface Props {
    /** 처음 값(고치기) */
    initialBody?: string;
    initialSecret?: boolean;
    submitLabel: string;
    busy?: boolean;
    /** 서버 문구(칸 밖) */
    error?: string;
    /** 서버 `fields.body` */
    fieldError?: string;
}

const props = withDefaults(defineProps<Props>(), { initialBody: '', initialSecret: false, busy: false, error: '', fieldError: '' });
const emit = defineEmits<{ submit: [value: GroupBuyQuestionRequest]; cancel: [] }>();

const MAX = 1000;
const { t } = useI18n();
const secretId = useId();
const body = ref(props.initialBody);
const secret = ref(props.initialSecret);
const localError = ref('');

function submit(): void {
    const text = body.value.trim();
    localError.value = text ? '' : t('groupBuyQna.bodyRequired');
    if (text) emit('submit', { body: text, secret: secret.value });
}
</script>
