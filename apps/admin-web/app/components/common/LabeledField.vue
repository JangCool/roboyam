<template>
    <!--
        라벨 + 입력칸 + 안내/오류 한 묶음. 입력칸은 기본 슬롯에 넣고 슬롯이 주는 값을 연결한다.

            <LabeledField label="이메일" :error="err" v-slot="{ id, describedBy, invalid }">
                <InputText :id="id" v-model="email" :invalid="invalid" :aria-describedby="describedBy" />
            </LabeledField>

        - 라벨은 `for`/`id` 로 입력칸과 이어진다(`useId()` — 한 화면에 여러 개가 있어도 겹치지 않는다).
        - 안내·오류는 `aria-live` 영역에 그려 스크린리더가 바뀔 때 읽는다. 오류는 `role="alert"`.
    -->
    <div class="flex flex-col gap-1.5">
        <div class="flex items-baseline justify-between gap-2">
            <label :for="id" class="text-sm font-medium">
                {{ label }}
                <small v-if="hint" class="ml-1 text-xs font-normal text-muted-color">{{ hint }}</small>
            </label>
            <slot name="aside" />
        </div>

        <slot :id="id" :describedBy="describedBy" :invalid="Boolean(error)" />

        <div :id="messageId" aria-live="polite" class="min-h-0 text-xs leading-relaxed">
            <p v-if="error" role="alert" class="text-red-600 dark:text-red-400">
                <slot name="error">{{ error }}</slot>
            </p>
            <p v-else-if="success" class="text-green-700 dark:text-green-400">{{ success }}</p>
            <p v-else-if="help" class="text-muted-color">{{ help }}</p>
        </div>
    </div>
</template>

<script setup lang="ts">
interface Props {
    label: string;
    /** 라벨 옆 작은 글씨 (예: "2~12자") */
    hint?: string;
    /** 평소 안내 */
    help?: string;
    /** 사용할 수 있다는 확인 */
    success?: string;
    /** 오류. 있으면 안내·확인 대신 이것이 보인다 */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), {
    hint: '',
    help: '',
    success: '',
    error: '',
});

const id = useId();
const messageId = `${id}-msg`;
const describedBy = computed(() => (props.error || props.success || props.help ? messageId : undefined));
</script>
