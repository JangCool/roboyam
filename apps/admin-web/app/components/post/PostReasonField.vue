<template>
    <!--
        조치 사유 칸(1~200자 필수) — 글 하나의 조치·이동·일괄 대화창이 같이 쓴다. 오류는 화면 검사 문구 또는 서버 `fields.reason` 문구 그대로.
        `presets`(자주 쓰는 사유의 문구 키 — `UtilPost.reasonPresetKeys`)를 주면 선택 상자가 먼저 보인다. 선택지 글자는 화면 언어, **사유로 들어가는 값은 늘 한국어 문장**
        (`t(key, {}, { locale: 'ko' })` — 작성자 알림에 그대로 가므로. 한국어 문구는 기본 언어라 화면 조각이 늘 들고 온다). 맨 끝 「직접 입력」을 고르면 아래에 입력 칸이 열린다(비운 채로, 바로 입력 칸으로).
        아무것도 고르지 않았으면 사유가 빈 글자라 부른 화면의 필수 검사에 걸린다(그때 문구는 「사유를 골라 주세요」). 대화창이 닫히면 이 칸도 사라져 고른 값은 다음에 열 때 비어 있다.
    -->
    <LabeledField :label="t('post.reasonLabel')" :hint="t('post.reasonCounter', { n: UtilPost.reasonLength(reason), max: UtilPost.REASON_MAX })" :help="t('post.reasonHelp')" :error="shownError">
        <template #default="{ id, describedBy, invalid }">
            <div v-if="presetMode" class="flex flex-col gap-2">
                <Select
                    :model-value="choice"
                    :input-id="id"
                    :options="choiceOptions"
                    option-label="label"
                    option-value="value"
                    :placeholder="t('post.reasonPresetPlaceholder')"
                    :disabled="disabled"
                    :invalid="invalid && !custom"
                    :aria-describedby="custom ? undefined : describedBy"
                    fluid
                    @update:model-value="pick"
                />
                <Textarea
                    v-if="custom"
                    ref="customInput"
                    v-model="reason"
                    name="reason"
                    rows="3"
                    auto-resize
                    fluid
                    :aria-label="t('post.reasonCustomLabel')"
                    :placeholder="t('post.reasonPlaceholder')"
                    :disabled="disabled"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                />
            </div>
            <Textarea v-else :id="id" v-model="reason" name="reason" rows="3" auto-resize fluid :placeholder="t('post.reasonPlaceholder')" :disabled="disabled" :invalid="invalid" :aria-describedby="describedBy" />
        </template>
    </LabeledField>
</template>

<script setup lang="ts">
import UtilPost from '~/utils/util-post';

const reason = defineModel<string>({ required: true });

interface Props {
    error?: string;
    disabled?: boolean;
    /** 자주 쓰는 사유의 문구 키(`UtilPost.reasonPresetKeys`) — 주면 선택 상자 + 「직접 입력」, 비면 입력 칸만(지금 모양) */
    presets?: readonly string[];
}

const props = withDefaults(defineProps<Props>(), { error: '', disabled: false, presets: () => [] });

const { t } = useI18n();

/** 「직접 입력」 선택지의 값 — 문장과 겹치지 않는 표식 */
const CUSTOM = '__custom__';

const presetMode = computed(() => props.presets.length > 0);
/** 사유로 저장하는 언어 — 화면 언어와 상관없이 한국어(작성자 알림에 그대로 간다) */
const SAVED_LOCALE = 'ko';
/** 고른 선택지 — 한국어 사유 문장 또는 `CUSTOM`, 아직 안 골랐으면 null */
const choice = ref<string | null>(null);
const custom = computed(() => choice.value === CUSTOM);
/** 선택지 — 글자는 화면 언어, 값은 한국어 문장(그대로 사유가 된다) */
const choiceOptions = computed(() => [
    ...props.presets.map((key) => ({ value: savedSentence(key), label: t(key) })),
    { value: CUSTOM, label: t('post.reasonPresetCustom') },
]);
/** 아직 고르지 않아 생긴 필수 오류는 「골라 주세요」로 */
const shownError = computed(() => (presetMode.value && choice.value === null && props.error ? t('post.reasonPickRequired') : props.error));

/** 저장할 한국어 문장 — 한국어 문구를 못 찾으면(키가 그대로 돌아오면) 키 글자가 사유로 가지 않게 화면 언어 문장으로 */
function savedSentence(key: string): string {
    const ko = t(key, {}, { locale: SAVED_LOCALE });
    return ko && ko !== key ? ko : t(key);
}

const customInput = ref<{ $el?: HTMLElement } | null>(null);

function pick(value: string | null): void {
    choice.value = value;
    if (value === CUSTOM) {
        reason.value = '';
        void nextTick(() => customInput.value?.$el?.focus());
        return;
    }
    reason.value = value ?? '';
}
</script>
