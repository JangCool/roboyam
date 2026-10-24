<template>
    <!--
        찬반 토론 설정 세 칸(post-debate-contract 「화면」) — 「찬반 토론 열기」 켬/끔, 켜면 「중립도 받기」·「입장 바꾸기 허용」(기본 켬, 끄면 「한 번 고른 입장은 바꿀 수 없어요」).
        글쓰기·고치기 폼(`PostEditorForm` 의 `extra` 자리)과 글 상세 「토론 설정」 대화창(`DebateSettingsDialog`)이 같이 쓴다. 그리기만 하고 저장은 부르는 화면이 한다.
        `readonly`(운영자·매니저가 연 토론을 글쓴이가 볼 때)면 칸 대신 안내 한 줄, `closeLocked`(글쓴이 + 투표 있음 — 서버 `canClose false`)면 열린 토론을 끄지 못하게 막고 안내한다.
    -->
    <fieldset class="m-0 flex min-w-0 flex-col gap-3 rounded-lg border border-surface p-3.5" data-testid="debate-settings">
        <legend v-if="legend" class="px-1 text-sm font-semibold">{{ t('comment.debateSettingsLegend') }}</legend>

        <p v-if="readonly" class="m-0 flex items-start gap-1.5 text-xs leading-relaxed text-muted-color" data-testid="debate-settings-readonly">
            <i class="pi pi-lock mt-0.5 text-[0.6875rem]" aria-hidden="true" />{{ t('comment.debateOperatorOpened') }}
        </p>

        <template v-else>
            <div class="flex flex-col gap-1">
                <label class="flex items-center gap-2 text-sm font-medium">
                    <ToggleSwitch
                        :model-value="modelValue.enabled"
                        :input-id="`${uid}-enabled`"
                        :disabled="disabled || openLocked"
                        :aria-describedby="`${uid}-enabled-help`"
                        data-testid="debate-settings-enabled"
                        @update:model-value="patch({ enabled: Boolean($event) })"
                    />
                    <span>{{ t('comment.debateOpenLabel') }}</span>
                </label>
                <p :id="`${uid}-enabled-help`" class="m-0 text-xs leading-relaxed text-muted-color">
                    <template v-if="openLocked"><i class="pi pi-lock mr-1 text-[0.6875rem]" aria-hidden="true" />{{ t('comment.debateCloseLocked') }}</template>
                    <template v-else>{{ t('comment.debateOpenHelp') }}</template>
                </p>
            </div>

            <div v-if="modelValue.enabled" class="flex flex-col gap-3 border-l-2 border-surface pl-3.5">
                <div class="flex flex-col gap-1">
                    <label class="flex items-center gap-2 text-sm">
                        <ToggleSwitch
                            :model-value="modelValue.neutralEnabled"
                            :input-id="`${uid}-neutral`"
                            :disabled="disabled"
                            :aria-describedby="`${uid}-neutral-help`"
                            data-testid="debate-settings-neutral"
                            @update:model-value="patch({ neutralEnabled: Boolean($event) })"
                        />
                        <span>{{ t('comment.debateNeutralLabel') }}</span>
                    </label>
                    <p :id="`${uid}-neutral-help`" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('comment.debateNeutralHelp') }}</p>
                </div>
                <div class="flex flex-col gap-1">
                    <label class="flex items-center gap-2 text-sm">
                        <ToggleSwitch
                            :model-value="modelValue.stanceChangeAllowed"
                            :input-id="`${uid}-change`"
                            :disabled="disabled"
                            :aria-describedby="`${uid}-change-help`"
                            data-testid="debate-settings-change"
                            @update:model-value="patch({ stanceChangeAllowed: Boolean($event) })"
                        />
                        <span>{{ t('comment.debateStanceChangeLabel') }}</span>
                    </label>
                    <p :id="`${uid}-change-help`" class="m-0 text-xs leading-relaxed" :class="modelValue.stanceChangeAllowed ? 'text-muted-color' : 'font-medium text-amber-700 dark:text-amber-300'">
                        {{ modelValue.stanceChangeAllowed ? t('comment.debateStanceChangeHelp') : t('comment.debateStanceChangeOff') }}
                    </p>
                </div>
            </div>
        </template>

        <p v-if="error" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ error }}</p>
    </fieldset>
</template>

<script setup lang="ts">
import type { DebateSettingsValues } from '~/components/board/DebateSettingsFields.types';

interface Props {
    modelValue: DebateSettingsValues;
    /** 묶음 제목 「찬반 토론」을 보인다(대화창은 머리에 제목이 있어 끈다) */
    legend?: boolean;
    /** 운영자·매니저가 연 토론 — 글쓴이는 볼 수만 있다 */
    readonly?: boolean;
    /** 열린 토론을 닫을 수 없다(서버 `canClose false` — 글쓴이 + 투표 있음). 두 설정은 바꿀 수 있다 */
    closeLocked?: boolean;
    disabled?: boolean;
    /** 서버 문구(`fields.debate`·`DEBATE_*` 거절) */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { legend: true, readonly: false, closeLocked: false, disabled: false, error: '' });
const emit = defineEmits<{ 'update:modelValue': [value: DebateSettingsValues] }>();

const { t } = useI18n();
const uid = useId();

/** 켜진 토론을 끌 수 없다 — 끈 채로 왔으면(다시 열기) 막지 않는다 */
const openLocked = computed(() => props.closeLocked && props.modelValue.enabled);

function patch(change: Partial<DebateSettingsValues>): void {
    emit('update:modelValue', { ...props.modelValue, ...change });
}
</script>
