<template>
    <!--
        시험 메일 보내기 대화상자 — 메일 서버 설정(`/settings/mail`)과 메일 템플릿(`/settings/mail-templates`)의 「시험 발송」이 같은 모양으로 쓴다.
        받는 주소 한 칸 + 결과(성공 · 실패 까닭) + 칸에 붙지 않는 오류 한 줄. 보내기는 부르는 쪽이 한다(`send` 이벤트) — 여기는 서버를 부르지 않는다.
    -->
    <Dialog v-model:visible="open" modal :header="t('mail.testTitle')" class="w-[28rem] max-w-[calc(100vw-2rem)]">
        <form class="flex flex-col gap-3" novalidate @submit.prevent="emit('send')">
            <LabeledField :label="t('mail.testToLabel')" :help="help || t('mail.testHelp')" :error="toError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="to" name="to" type="email" size="small" autocomplete="email" placeholder="me@example.com" :invalid="invalid" :aria-describedby="describedBy" :readonly="testing" fluid />
                </template>
            </LabeledField>

            <div v-if="result" class="flex items-start gap-2 rounded-lg p-3 text-sm" :class="result.succeeded ? 'bg-green-50 text-green-800 dark:bg-green-950 dark:text-green-300' : 'bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300'" :role="result.succeeded ? 'status' : 'alert'">
                <i :class="result.succeeded ? 'pi pi-check-circle' : 'pi pi-times-circle'" class="mt-0.5" aria-hidden="true" />
                <span class="min-w-0">
                    <b class="block">{{ result.succeeded ? t('mail.testSucceeded') : t('mail.testFailed') }}</b>
                    <span class="block">{{ result.succeeded ? t('mail.testSucceededHint', { to: result.to }) : result.text }}</span>
                </span>
            </div>
            <Message v-if="error" severity="error" variant="simple" size="small">{{ error }}</Message>

            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('common.close')" severity="secondary" variant="text" @click="open = false" />
                <Button type="submit" :label="t('mail.testSend')" icon="pi pi-send" :loading="testing" :disabled="!to.trim()" />
            </div>
        </form>
    </Dialog>
</template>

<script setup lang="ts">
/** 시험 발송 결과 — 성공이면 받는 주소, 실패면 까닭 한 줄(`UtilMailSettingsMessages.testFailure`) */
export interface MailTestDialogResult {
    succeeded: boolean;
    text: string;
    to: string;
}

interface Props {
    /** 받는 주소 칸 아래 안내 — 없으면 메일 서버 설정의 기본 안내 */
    help?: string;
    /** 받는 주소 칸의 오류(화면 검사 또는 서버 `fields.to`) */
    toError?: string;
    /** 칸에 붙지 않는 오류 한 줄(429 등) */
    error?: string;
    testing?: boolean;
    result?: MailTestDialogResult | null;
}

withDefaults(defineProps<Props>(), { help: '', toError: '', error: '', testing: false, result: null });

const open = defineModel<boolean>('visible', { required: true });
const to = defineModel<string>('to', { required: true });
const emit = defineEmits<{ send: [] }>();

const { t } = useI18n();
</script>
