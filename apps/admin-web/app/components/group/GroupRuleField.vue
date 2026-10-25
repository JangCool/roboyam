<template>
    <!--
        소모임 설정의 칸 한 줄(community `SettingItem`) — 이름 · 범위(「0~90일 · 기본 7」) · 도움말/오류 + 입력. 저장된 값과 다르면 이름 옆 「바뀜」, 기본값과 다르면 오른쪽 [기본값으로](고칠 수 있을 때만).
        입력칸은 기본 슬롯에 넣고 슬롯이 주는 `id`·`describedBy`·`invalid` 를 잇는다(`LabeledField` 와 같다). 값은 부르는 쪽이 든다.
    -->
    <LabeledField :label="label" :hint="hint" :help="help" :error="error">
        <template #default="slot">
            <div class="flex flex-wrap items-center gap-2">
                <slot v-bind="slot" />
            </div>
        </template>
        <template #aside>
            <span class="flex items-center gap-2">
                <GroupTag v-if="changed" :value="t('group.ruleChanged')" severity="warn" />
                <Button v-if="editable && differs" type="button" :label="t('group.ruleResetDefault')" :aria-label="t('group.ruleResetDefaultLabel', { name: label })" size="small" variant="link" class="p-0! text-xs" @click="emit('reset')" />
            </span>
        </template>
    </LabeledField>
</template>

<script setup lang="ts">
interface Props {
    label: string;
    /** 범위·기본값 글 */
    hint?: string;
    help?: string;
    error?: string;
    /** 저장된 값과 다르다 */
    changed?: boolean;
    /** 기본값과 다르다 */
    differs?: boolean;
    /** 고칠 수 있다(권한·저장 중 아님) */
    editable?: boolean;
}

withDefaults(defineProps<Props>(), { hint: '', help: '', error: '', changed: false, differs: false, editable: false });
const emit = defineEmits<{ reset: [] }>();

const { t } = useI18n();
</script>
