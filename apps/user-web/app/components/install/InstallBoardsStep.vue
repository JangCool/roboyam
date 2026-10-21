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

        <p class="text-xs leading-relaxed text-muted-color" data-testid="install-boards-note">{{ t('install.boardsNote') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { InstallBoardPreset } from '~/composables/install/api/useInstallApi.types';

/**
 * 6단계 — 기본 게시판 구성. 서버가 주는 프리셋(`GET …/board-presets`)을 라디오 카드로 보여 주고 하나를 고른다 — 중립(최소 구성)이 기본이다.
 * 카드마다 접어 둔 "게시판 N개"에 게시판 이름과 말머리 이름을 보인다(설정 값은 서버가 주지 않는다). 서버는 부르지 않는다 — 목록은 부모가 받아 넘긴다.
 * 고른 아이디는 설치 요청의 `boardPreset` 이 된다. 목록을 못 받았으면 아무것도 보내지 않고 서버가 기본(중립)으로 설치한다.
 */
const model = defineModel<string>({ required: true });

interface Props {
    presets: readonly InstallBoardPreset[];
    /** 서버가 정한 기본 프리셋 아이디 — "기본" 표시 */
    defaultId: string;
    busy?: boolean;
    /** 목록을 못 받았다는 안내 */
    error?: string;
    disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    busy: false,
    error: '',
    disabled: false,
});

const emit = defineEmits<{ retry: [] }>();

const { t } = useI18n();

const uid = useId();
</script>
