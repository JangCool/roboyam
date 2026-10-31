<template>
    <!-- 필수 동의 묶음(H4 — 버전 저장) — 「모두 동의」 + 항목마다 체크와 펼치는 설명. 사업자 신청·광고 신청·핫딜 신청이 같이 쓴다 -->
    <fieldset class="m-0 flex flex-col gap-2 border-0 p-0" data-testid="business-agree-list">
        <label class="flex min-h-11 cursor-pointer items-center gap-2.5 rounded-lg border border-hairline px-3 py-2 text-sm font-semibold">
            <Checkbox :model-value="allChecked" binary :input-id="`${uid}-all`" @update:model-value="setAll" />
            {{ t('business.agree.all') }}
        </label>
        <div v-for="item in items" :key="item.key" class="flex flex-col gap-1 px-1">
            <label class="flex cursor-pointer items-start gap-2.5 text-sm">
                <Checkbox :model-value="Boolean(model[item.key])" binary :input-id="`${uid}-${item.key}`" class="mt-0.5" :invalid="Boolean(error) && !model[item.key]" @update:model-value="(v: boolean) => set(item.key, v)" />
                <span class="min-w-0">
                    {{ item.label }}
                    <span v-if="item.version" class="ml-1 text-xs text-muted-color">{{ t('business.agree.version', { version: item.version }) }}</span>
                </span>
            </label>
            <p class="m-0 ml-7 text-xs leading-relaxed text-muted-color">{{ item.detail }}</p>
        </div>
        <p v-if="error" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ error }}</p>
    </fieldset>
</template>

<script setup lang="ts">
import type { BusinessAgreeItem } from '~/components/business/BusinessAgreeList.types';

const model = defineModel<Record<string, boolean>>({ required: true });

interface Props {
    items: BusinessAgreeItem[];
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { error: '' });

const { t } = useI18n();
const uid = useId();

const allChecked = computed(() => props.items.every((i) => model.value[i.key]));

function set(key: string, value: boolean): void {
    model.value = { ...model.value, [key]: value };
}

function setAll(value: boolean): void {
    model.value = Object.fromEntries(props.items.map((i) => [i.key, value]));
}
</script>
