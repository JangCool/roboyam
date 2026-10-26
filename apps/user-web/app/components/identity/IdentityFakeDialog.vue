<template>
    <!--
        가짜 본인인증(개발용) 대화상자 — 레이아웃에 한 번 놓는다. 띄우는 것은 `useIdentity().verify()`(서버가 `provider: 'FAKE'` 를 줄 때만).
        local·시험 전용 구현이라 포트원 인증창 대신 생년월일과 「사람 번호」(같은 번호 = 같은 사람 — 1인 1계정 시험용)를 받는다.
        [인증 완료]는 서버 인증 완료 요청을 보내고, 입력이 틀리면(400 VALIDATION) 연 채로 칸 아래에 서버 문구를 보인다. [취소]·Esc·바깥 누름은 사용자가 인증창을 닫은 것과 같다.
        입력값은 이 대화상자의 메모리에만 있고 열 때마다 비운다.
    -->
    <Dialog
        :visible="fake.open"
        modal
        :draggable="false"
        :closable="!fake.busy"
        :close-on-escape="!fake.busy"
        :header="t('identity.fakeTitle')"
        :style="{ width: 'min(26rem, calc(100vw - 2rem))' }"
        data-testid="identity-fake-dialog"
        @update:visible="onVisible"
    >
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <Message severity="warn" size="small" :closable="false">{{ t('identity.fakeNotice') }}</Message>

            <LabeledField :label="t('identity.fakeBirthDate')" :help="t('identity.fakeBirthDateHelp')" :error="errors.birthDate">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="birthDate" type="date" name="birthDate" :max="today" :invalid="invalid" :aria-describedby="describedBy" :disabled="fake.busy" fluid data-testid="identity-fake-birth" />
                </template>
            </LabeledField>

            <LabeledField :label="t('identity.fakePersonKey')" :help="t('identity.fakePersonKeyHelp')" :error="errors.personKey">
                <template #default="{ id, describedBy, invalid }">
                    <InputText
                        :id="id"
                        v-model="personKey"
                        name="personKey"
                        autocomplete="off"
                        autocapitalize="off"
                        spellcheck="false"
                        :maxlength="IDENTITY_PERSON_KEY_MAX"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        :disabled="fake.busy"
                        fluid
                        data-testid="identity-fake-person"
                    />
                </template>
            </LabeledField>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="fake.busy" @click="identity.cancelFake()" />
            <Button type="submit" :form="formId" :label="t('identity.fakeSubmit')" :loading="fake.busy" data-testid="identity-fake-submit" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import { IDENTITY_PERSON_KEY_MAX } from '~/composables/identity/api/useIdentityVerificationApi.types';

const { t } = useI18n();
const identity = useIdentity();
const fake = identity.fake;
const formId = useId();

const birthDate = ref('');
const personKey = ref('');
/** 화면 검사 오류 — 서버 칸 오류(`fake.fieldErrors`)가 오면 그것이 앞선다 */
const localErrors = reactive<{ birthDate?: string; personKey?: string }>({});

/** 브라우저 날짜의 오늘 — 생년월일 고르개의 끝 */
const today = computed(() => {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
});

const errors = computed(() => ({
    birthDate: fake.value.fieldErrors.birthDate ?? localErrors.birthDate,
    personKey: fake.value.fieldErrors.personKey ?? localErrors.personKey,
}));

// 열 때마다 비운다 — 앞에 적던 값이 다음 인증에 딸려 가면 안 된다
watch(
    () => fake.value.open,
    (open) => {
        if (!open) return;
        birthDate.value = '';
        personKey.value = '';
        localErrors.birthDate = undefined;
        localErrors.personKey = undefined;
    },
);
/** 칸을 고치기 시작하면 그 칸의 오류(화면 검사·서버)를 걷는다 */
function clearError(key: 'birthDate' | 'personKey'): void {
    localErrors[key] = undefined;
    if (fake.value.fieldErrors[key]) fake.value = { ...fake.value, fieldErrors: { ...fake.value.fieldErrors, [key]: undefined } };
}
watch(birthDate, () => clearError('birthDate'));
watch(personKey, () => clearError('personKey'));

function onVisible(visible: boolean): void {
    if (!visible) identity.cancelFake();
}

function submit(): void {
    localErrors.birthDate = birthDate.value ? undefined : t('identity.fakeBirthDateRequired');
    localErrors.personKey = personKey.value.trim() ? undefined : t('identity.fakePersonKeyRequired');
    if (localErrors.birthDate || localErrors.personKey) return;
    void identity.submitFake({ birthDate: birthDate.value, personKey: personKey.value.trim() });
}
</script>
