<template>
    <div class="flex flex-col gap-3">
        <!-- 목록을 못 받았다 — 서버가 기본 구성으로 설치하므로 설치는 계속할 수 있다 -->
        <Message v-if="error" severity="warn" :closable="false" size="small" data-testid="install-boards-error">
            <span class="block">{{ error }}</span>
            <Button type="button" :label="t('common.reload')" size="small" variant="text" class="mt-1" :loading="busy" data-testid="install-boards-retry" @click="emit('retry')" />
        </Message>
        <p v-else-if="busy && !presets.length" class="text-sm text-muted-color" role="status" data-testid="install-boards-loading">{{ t('install.boardsLoading') }}</p>

        <fieldset v-if="presets.length" class="m-0 min-w-0 border-0 p-0">
            <legend class="sr-only">{{ t('install.stepBoardsTitle') }}</legend>
            <div class="flex flex-col gap-2" role="radiogroup" :aria-label="t('install.stepBoardsTitle')">
                <div
                    v-for="preset in presets"
                    :key="preset.id"
                    class="rounded-lg border px-3 py-2.5"
                    :class="preset.id === model ? 'border-primary bg-primary-50 dark:bg-primary-950/40' : 'border-surface bg-surface-0 dark:bg-surface-900'"
                    :data-preset="preset.id"
                    data-testid="install-boards-option"
                >
                    <label :for="`${uid}-${preset.id}`" class="flex cursor-pointer items-start gap-3">
                        <RadioButton v-model="model" :value="preset.id" :input-id="`${uid}-${preset.id}`" :name="uid" :disabled="disabled" class="mt-0.5" :data-testid="`install-boards-${preset.id}`" />
                        <span class="min-w-0 flex-1 text-sm leading-snug">
                            <b class="font-semibold">{{ preset.name }}</b>
                            <small v-if="preset.id === defaultId" class="ml-1 text-xs text-muted-color">{{ t('install.boardsDefaultMark') }}</small>
                            <span class="mt-0.5 block text-xs leading-relaxed text-muted-color">{{ preset.description }}</span>
                        </span>
                    </label>
                    <details class="mt-2 pl-8 text-xs">
                        <summary class="cursor-pointer rounded font-medium text-muted-color focus-visible:outline-2 focus-visible:outline-primary">
                            {{ t('install.boardsSummary', { count: preset.boards.length }) }}
                        </summary>
                        <ul class="m-0 mt-1.5 flex list-none flex-col gap-1 p-0">
                            <li v-for="board in preset.boards" :key="board.name" class="leading-relaxed">
                                <b class="font-semibold">{{ board.name }}</b>
                                <span class="ml-1 text-muted-color">{{ board.prefixes.join(' · ') }}</span>
                            </li>
                        </ul>
                    </details>
                </div>
            </div>
        </fieldset>

        <!-- 데이터 — 데모를 지원하는 구성(서버가 demoAvailable 로 알린다)에서만 고른다. 아니면 늘 구조만 -->
        <fieldset v-if="selected" class="m-0 min-w-0 border-0 p-0" data-testid="install-demo">
            <legend class="mb-1.5 text-sm font-semibold">{{ t('install.demoTitle') }}</legend>
            <div v-if="selected.demoAvailable" class="flex flex-col gap-2" role="radiogroup" :aria-label="t('install.demoTitle')">
                <label
                    v-for="option in demoOptions"
                    :key="option.key"
                    :for="`${uid}-demo-${option.key}`"
                    class="flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5"
                    :class="option.value === demo ? 'border-primary bg-primary-50 dark:bg-primary-950/40' : 'border-surface bg-surface-0 dark:bg-surface-900'"
                    :data-testid="`install-demo-${option.key}`"
                >
                    <RadioButton v-model="demo" :value="option.value" :input-id="`${uid}-demo-${option.key}`" :name="`${uid}-demo`" :disabled="disabled" class="mt-0.5" />
                    <span class="min-w-0 flex-1 text-sm leading-snug">
                        <b class="font-semibold">{{ option.label }}</b>
                        <span class="mt-0.5 block text-xs leading-relaxed text-muted-color">{{ option.hint }}</span>
                    </span>
                </label>
            </div>
            <p v-else class="text-xs leading-relaxed text-muted-color" data-testid="install-demo-unavailable">{{ t('install.demoUnavailable') }}</p>
            <small v-if="demoError" class="mt-1 block text-xs text-red-600 dark:text-red-400" data-testid="install-demo-error">{{ demoError }}</small>
        </fieldset>

        <p class="text-xs leading-relaxed text-muted-color" data-testid="install-boards-note">{{ t('install.boardsNote') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { InstallBoardPreset } from '~/composables/install/api/useInstallApi.types';

/**
 * 6단계 — 기본 게시판 구성. 서버가 주는 프리셋(`GET …/board-presets`)을 라디오 카드로 보여 주고 하나를 고른다 — 중립(최소 구성)이 기본이다.
 * 카드마다 접어 둔 "게시판 N개"에 게시판 이름과 말머리 이름을 보인다(설정 값은 서버가 주지 않는다). 서버는 부르지 않는다 — 목록은 부모가 받아 넘긴다.
 * 고른 아이디는 설치 요청의 `boardPreset` 이 된다. 목록을 못 받았으면 아무것도 보내지 않고 서버가 기본(중립)으로 설치한다.
 * 아래 「데이터」는 구조만(기본) / 데모 데이터 포함 — 고른 구성의 `demoAvailable` 이 true 일 때만 고를 수 있고(`v-model:demo`), 아니면 늘 구조만이다.
 */
const model = defineModel<string>({ required: true });
/** 「데모 데이터 포함」을 골랐는가(기본 false = 구조만) */
const demo = defineModel<boolean>('demo', { default: false });

interface Props {
    presets: readonly InstallBoardPreset[];
    /** 서버가 정한 기본 프리셋 아이디 — "기본" 표시 */
    defaultId: string;
    busy?: boolean;
    /** 목록을 못 받았다는 안내 */
    error?: string;
    disabled?: boolean;
    /** 서버가 `demoData` 를 거절한 문구 */
    demoError?: string;
}

const props = withDefaults(defineProps<Props>(), {
    busy: false,
    error: '',
    disabled: false,
    demoError: '',
});

const emit = defineEmits<{ retry: [] }>();

const { t } = useI18n();

const uid = useId();

const selected = computed(() => props.presets.find((p) => p.id === model.value) ?? null);
const demoOptions = computed(() => [
    { key: 'structure', value: false, label: t('install.demoStructure'), hint: t('install.demoStructureHint') },
    { key: 'include', value: true, label: t('install.demoInclude'), hint: t('install.demoIncludeHint') },
]);
</script>
