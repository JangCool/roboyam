<template>
    <!--
        회원 닉네임 바꾸기(`member.nickname`) — 회원 상세 기본 정보의 닉네임 줄 [닉네임 변경]이 연다. 새 닉네임 + 사유(2~200자) + 안내(무료 횟수·변경권 안 씀 · 감사 · 예약어 가능).
        서버 호출은 하지 않는다 — `submit` 으로 본문을 올린다. 서버 칸 오류(`fieldErrors.nickname|reason` — 409 NICKNAME_TAKEN 은 부모가 `nickname` 칸에 넣는다)는 칸 아래,
        그 밖은 폼 아래에 서버 문구 그대로. 열 때마다 지금 닉네임으로 채우고 사유는 비운다. 형식(길이·글자) 판정은 서버가 한다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :header="t('member.nicknameEdit.title')" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-sm">{{ t('member.nicknameEdit.current', { name: currentNickname }) }}</p>

            <LabeledField :label="t('member.nicknameEdit.nicknameLabel')" :error="nicknameError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="nickname" name="nickname" fluid autocomplete="off" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" />
                </template>
            </LabeledField>

            <LabeledField :label="t('member.nicknameEdit.reasonLabel')" :hint="t('reason.hint', { min: REASON_MIN, max: REASON_MAX })" :error="reasonError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="reason" name="reason" rows="3" fluid :maxlength="REASON_MAX + 50" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" />
                </template>
            </LabeledField>
            <p class="-mt-3 text-right text-[11px] tabular-nums text-muted-color">{{ t('reason.count', { count: reason.trim().length, max: REASON_MAX }) }}</p>

            <p class="m-0 flex items-start gap-2 text-xs leading-relaxed text-muted-color">
                <i class="pi pi-info-circle mt-0.5" aria-hidden="true" />
                <span>{{ t('member.nicknameEdit.notice') }}</span>
            </p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('member.nicknameEdit.submit')" icon="pi pi-check" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { MemberNicknameUpdateRequest } from '~/composables/member/api/useMemberApi.types';

interface Props {
    visible: boolean;
    /** 지금 닉네임 — 칸의 처음 값이자 「같음」 판정 */
    currentNickname?: string;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 거절한 칸 문구 — 키 `nickname`·`reason` */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { currentNickname: '', busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: MemberNicknameUpdateRequest] }>();

/** 사유 길이 — 서버 `MemberNicknameAdminService.REASON_MIN·REASON_MAX` 와 같다 */
const REASON_MIN = 2;
const REASON_MAX = 200;

const { t } = useI18n();
const formId = useId();

const nickname = ref('');
const reason = ref('');
const submitted = ref(false);
/** 서버 칸 오류를 보일지 — 새 오류가 오면 켜고, 칸을 고치면 그 칸만 끈다 */
const hidden = reactive<Record<string, boolean>>({});

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        nickname.value = props.currentNickname;
        reason.value = '';
        submitted.value = false;
    },
    { immediate: true },
);
watch(
    () => props.fieldErrors,
    () => {
        for (const key of Object.keys(hidden)) delete hidden[key];
    },
);
watch(nickname, () => (hidden.nickname = true));
watch(reason, () => (hidden.reason = true));

function server(key: string): string {
    return hidden[key] ? '' : (props.fieldErrors[key] ?? '');
}

const nicknameError = computed(() => {
    if (server('nickname')) return server('nickname');
    if (!submitted.value) return '';
    if (!nickname.value.trim()) return t('member.nicknameEdit.nicknameRequired');
    if (nickname.value.trim() === props.currentNickname) return t('member.nicknameEdit.nicknameSame');
    return '';
});

const reasonError = computed(() => {
    if (server('reason')) return server('reason');
    if (!submitted.value) return '';
    const length = reason.value.trim().length;
    if (length === 0) return t('reason.required');
    if (length < REASON_MIN || length > REASON_MAX) return t('reason.length', { min: REASON_MIN, max: REASON_MAX });
    return '';
});

function submit(): void {
    submitted.value = true;
    for (const key of Object.keys(hidden)) hidden[key] = true;
    if (nicknameError.value || reasonError.value || props.busy) return;
    emit('submit', { nickname: nickname.value.trim(), reason: reason.value.trim() });
}
</script>
