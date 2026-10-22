<template>
    <!-- 비회원 작성 칸 — 이름 · 비밀번호(수정·삭제용) · 자동입력 방지. 글쓰기와 댓글이 같이 쓴다. 비밀번호는 부르는 화면의 메모리에만 둔다(forms.md 2-1) -->
    <fieldset class="m-0 flex flex-col gap-3 border-0 p-0">
        <legend class="sr-only">{{ t('post.guestLegend') }}</legend>
        <div class="grid gap-3" :class="compact ? 'grid-cols-2' : 'md:grid-cols-2'">
            <LabeledField :label="t('post.guestName')" :hint="t('post.lengthHint', { min: rules.nameMin, max: rules.nameMax })" :error="nameError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText
                        :id="id"
                        :model-value="name"
                        :maxlength="rules.nameMax"
                        autocomplete="nickname"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        fluid
                        :size="compact ? 'small' : undefined"
                        @update:model-value="$emit('update:name', String($event ?? ''))"
                    />
                </template>
            </LabeledField>
            <PasswordField
                :model-value="password"
                :label="t('post.guestPassword')"
                :hint="t('post.lengthHint', { min: rules.passwordMin, max: rules.passwordMax })"
                :help="compact ? '' : t('post.guestPasswordHelp')"
                :error="passwordError"
                autocomplete="off"
                name="guest-password"
                @update:model-value="$emit('update:password', String($event ?? ''))"
            />
        </div>
        <!-- 자동입력 방지 자리 표시 — "로봇이 아닙니다" 체크. Cloudflare Turnstile 위젯(사이트 키를 공개 정보로 받는다)은 후속. 서버에 비밀키가 있으면 이 토큰은 거절되고 그 문구가 아래(captchaError = fields.guest.captchaToken)에 보인다 -->
        <div v-if="rules.captchaRequired">
            <label class="inline-flex cursor-pointer items-center gap-2 rounded-md border border-surface px-3 py-2 text-sm" :class="captchaError ? 'border-red-500' : ''">
                <Checkbox :model-value="captcha" binary :input-id="captchaId" :invalid="Boolean(captchaError)" @update:model-value="$emit('update:captcha', Boolean($event))" />
                <span>{{ t('post.captchaLabel') }}</span>
                <i class="pi pi-shield text-muted-color" aria-hidden="true" />
            </label>
            <p v-if="captchaError" role="alert" class="mt-1 text-xs text-red-600 dark:text-red-400">{{ captchaError }}</p>
        </div>
    </fieldset>
</template>

<script setup lang="ts">
import type { BoardGuestRules } from '~/composables/board/api/useBoardApi.types';

interface Props {
    rules: BoardGuestRules;
    name: string;
    password: string;
    captcha: boolean;
    nameError?: string;
    passwordError?: string;
    captchaError?: string;
    /** 댓글처럼 좁은 자리 — 두 칸을 늘 한 줄에, 비밀번호 안내를 뺀다 */
    compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    nameError: '',
    passwordError: '',
    captchaError: '',
    compact: false,
});

defineEmits<{
    'update:name': [value: string];
    'update:password': [value: string];
    'update:captcha': [value: boolean];
}>();

const { t } = useI18n();
const captchaId = useId();
</script>
