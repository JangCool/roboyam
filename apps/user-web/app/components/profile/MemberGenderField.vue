<template>
    <!--
        회원 성별 칸(선택) — 남성 · 여성 · 밝히지 않음(기본). 가입 화면(이메일·소셜)과 프로필 수정이 같이 쓴다.
        다른 회원에게 보이지 않고 사이트 로고 표시에만 쓴다는 안내를 늘 붙인다. 값만 주고받는다(서버는 부르지 않는다 — 보내는 것은 가입 요청·프로필 저장의 `gender`).
    -->
    <div class="flex flex-col gap-1.5">
        <span :id="`${uid}-label`" class="text-sm font-medium">
            {{ t('profile.genderLabel') }}
            <small class="ml-1 text-xs font-normal text-muted-color">{{ t('profile.genderOptional') }}</small>
        </span>
        <SelectButton
            :model-value="modelValue"
            :options="options"
            option-label="label"
            option-value="value"
            :allow-empty="false"
            :disabled="disabled"
            :aria-labelledby="`${uid}-label`"
            :aria-describedby="`${uid}-help`"
            class="self-start"
            :data-testid="testid || undefined"
            @update:model-value="(value: MemberGender) => emit('update:modelValue', value)"
        />
        <p :id="`${uid}-help`" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('profile.genderHelp') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { MemberGender } from '~/composables/profile/api/useProfileApi.types';

interface Props {
    modelValue: MemberGender;
    disabled?: boolean;
    testid?: string;
}

withDefaults(defineProps<Props>(), { disabled: false, testid: '' });
const emit = defineEmits<{ 'update:modelValue': [value: MemberGender] }>();

const { t } = useI18n();
const uid = useId();

const options = computed<{ label: string; value: MemberGender }[]>(() => [
    { label: t('profile.genderMale'), value: 'MALE' },
    { label: t('profile.genderFemale'), value: 'FEMALE' },
    { label: t('profile.genderNone'), value: 'NONE' },
]);
</script>
