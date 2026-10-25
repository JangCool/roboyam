<template>
    <!--
        게시판 정책 칸 하나 — 기본 설정(`/boards/defaults/*`)과 게시판 설정(`/boards/[slug]`)이 **같은 칸 정의**(`UtilBoard.SPECS`)로 같은 모양을 그린다.
        모양: 라벨 줄(라벨 · [기본 설정] 이 칸을 따로 정한 게시판 수 단추 · [게시판 설정] 「기본값 따름」 체크) + 입력 + 안내/오류 한 줄.
          · 숫자 칸은 글자 입력(`InputText` + `inputmode=numeric`) — grades.vue 와 같은 이유(InputNumber 는 범위 밖 값을 말없이 바꾼다). 값은 글자로 주고받는다.
          · 켜고 끄기는 ToggleSwitch, 고르기는 3개 이하면 SelectButton·넘으면 Select, 글쓰기·댓글 최소 자격은 등급 목록(`grades`)에서 고르는 Select(등급은 `GradeBadge` 로 그린다).
          · 짧은 이름(`label`)은 글자 입력(빈 칸 = null — 기본 설정이면 화면 기본 이름, 게시판이면 기본 설정 따름, 자리표시 글자로 그 이름을 보인다), 여러 개 고르기(`multi`)는 체크 목록(늘 칸 정의 차례로 보낸다, 빈 목록 허용).
          · 「기본값 따름」(값 `null`)이면 입력 대신 지금 기본값을 흐리게 보인다(「기본값: 20개」). 끄면 기본값을 시작 값으로 채운다.
        값을 바꿀 권한이 없으면(`editable=false`) 숫자 칸은 readonly, 나머지는 disabled — 서버 값이 그대로 보인다. 서버 호출은 없다(값만 주고받는다).
    -->
    <div class="flex flex-col gap-1.5" role="group" :aria-labelledby="labelId" :data-field="field">
        <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
            <component :is="following || isBool || isMulti ? 'span' : 'label'" :id="labelId" :for="following || isBool || isMulti ? undefined : inputId" class="text-sm font-medium">{{ t(`board.field.${field}`) }}</component>

            <!-- 이 칸을 따로 정한 게시판 — 눌러서 이름 목록 -->
            <template v-if="overrides.length">
                <button type="button" class="inline-flex items-center gap-1 rounded-full border border-(--line) px-2 py-0.5 text-[11px] leading-none text-muted-color hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800" :aria-expanded="open" @click="toggleOverrides">
                    <i class="pi pi-sliders-h text-[10px]" aria-hidden="true" />
                    {{ t('board.overrideCount', { n: overrides.length }) }}
                </button>
                <Popover ref="panel" @show="open = true" @hide="open = false">
                    <div class="flex w-56 flex-col gap-1">
                        <p class="m-0 text-xs font-medium">{{ t('board.overrideListTitle') }}</p>
                        <ul class="m-0 flex list-none flex-col gap-0.5 p-0">
                            <li v-for="board in overrides" :key="board.id">
                                <NuxtLink :to="`/boards/${board.slug}`" class="block truncate rounded px-1.5 py-1 text-[13px] hover:bg-surface-100 dark:hover:bg-surface-800">{{ localizedName(board.name, board.nameEn) }}</NuxtLink>
                            </li>
                        </ul>
                    </div>
                </Popover>
            </template>

            <!-- 게시판 설정 — 기본값 따름 -->
            <div v-if="inheritable" class="ml-auto flex items-center gap-1.5">
                <Checkbox binary :model-value="following" :input-id="inheritId" :disabled="!editable" @update:model-value="setFollowing" />
                <label :for="inheritId" class="text-xs" :class="editable ? 'cursor-pointer' : 'text-muted-color'">{{ t('board.followDefault') }}</label>
            </div>
        </div>

        <!-- 기본값 따름 — 지금 기본값을 흐리게 -->
        <p v-if="following" class="m-0 flex min-h-8 items-center gap-1.5 text-sm text-muted-color">
            <i class="pi pi-link text-[11px]" aria-hidden="true" />
            {{ t('board.defaultIs', { value: textOf(defaultValue) }) }}
        </p>

        <template v-else>
            <div v-if="spec.kind === 'number'" class="flex items-center gap-2">
                <InputText
                    :id="inputId"
                    :model-value="String(modelValue ?? '')"
                    inputmode="numeric"
                    autocomplete="off"
                    size="small"
                    :readonly="!editable"
                    :disabled="inactive"
                    :invalid="Boolean(error)"
                    :aria-describedby="messageId"
                    class="w-28 text-right tabular-nums"
                    @update:model-value="(value: string | undefined) => emit('update:modelValue', value ?? '')"
                />
                <span v-if="spec.unit" class="text-sm text-muted-color">{{ t(`board.suffix.${spec.unit}`) }}</span>
            </div>

            <div v-else-if="spec.kind === 'label'" class="flex items-center gap-2">
                <InputText
                    :id="inputId"
                    :model-value="typeof modelValue === 'string' ? modelValue : ''"
                    autocomplete="off"
                    size="small"
                    :placeholder="labelPlaceholder"
                    :readonly="!editable"
                    :disabled="inactive"
                    :invalid="Boolean(error)"
                    :aria-describedby="messageId"
                    class="w-40"
                    @update:model-value="(value: string | undefined) => emit('update:modelValue', value ?? '')"
                />
                <span class="text-xs tabular-nums text-muted-color">{{ t('board.labelCounter', { n: labelLength, max: spec.max ?? 0 }) }}</span>
            </div>

            <ul v-else-if="isMulti" class="m-0 flex min-h-8 list-none flex-wrap items-center gap-x-5 gap-y-2 p-0" :aria-describedby="messageId">
                <li v-for="option in choiceOptions" :key="option.value" class="flex items-center gap-1.5">
                    <Checkbox :model-value="picked" :value="option.value" :input-id="`${inputId}-${option.value}`" :disabled="!editable || inactive" :invalid="Boolean(error)" @update:model-value="onPick" />
                    <label :for="`${inputId}-${option.value}`" class="text-[13px]">{{ option.label }}</label>
                </li>
            </ul>

            <div v-else-if="isBool" class="flex min-h-8 items-center gap-2">
                <ToggleSwitch :model-value="Boolean(modelValue)" :input-id="inputId" :aria-labelledby="labelId" :aria-describedby="messageId" :disabled="!editable || inactive" @update:model-value="(value: boolean) => emit('update:modelValue', value)" />
                <span class="text-sm" :class="modelValue ? '' : 'text-muted-color'">{{ t(modelValue ? 'common.on' : 'common.off') }}</span>
            </div>

            <SelectButton
                v-else-if="spec.kind === 'choice' && choiceOptions.length <= 3"
                :model-value="modelValue"
                :options="choiceOptions"
                option-label="label"
                option-value="value"
                :allow-empty="false"
                size="small"
                :disabled="!editable || inactive"
                :invalid="Boolean(error)"
                :aria-labelledby="labelId"
                class="self-start"
                @update:model-value="(value: string | number) => emit('update:modelValue', value)"
            />

            <Select
                v-else-if="spec.kind === 'choice'"
                :model-value="modelValue"
                :options="choiceOptions"
                option-label="label"
                option-value="value"
                :input-id="inputId"
                size="small"
                :disabled="!editable || inactive"
                :invalid="Boolean(error)"
                :aria-describedby="messageId"
                class="w-48"
                @update:model-value="(value: string | number) => emit('update:modelValue', value)"
            />

            <Select
                v-else
                :model-value="modelValue"
                :options="levelOptions"
                option-label="text"
                option-value="value"
                :input-id="inputId"
                size="small"
                :disabled="!editable || inactive"
                :invalid="Boolean(error)"
                :aria-describedby="messageId"
                class="w-56"
                @update:model-value="(value: string) => emit('update:modelValue', value)"
            >
                <!-- 최소 자격 한 줄 — 등급은 뱃지 + 「이상」, 운영진·끔은 글자 -->
                <template #value="{ value }">
                    <span v-if="value && levelOf(value).badge" class="inline-flex items-center gap-1.5">
                        <GradeBadge v-bind="levelOf(value).badge!" size="small" />
                        <span class="text-sm">{{ t('board.orAbove') }}</span>
                    </span>
                    <span v-else-if="value" class="inline-flex items-center gap-1.5 text-sm">
                        <i :class="iconOf(value)" class="text-xs text-muted-color" aria-hidden="true" />
                        {{ levelOf(value).text }}
                    </span>
                </template>
                <template #option="{ option }">
                    <span v-if="option.badge" class="inline-flex items-center gap-1.5">
                        <GradeBadge v-bind="option.badge" size="small" />
                        <span class="text-sm">{{ t('board.orAbove') }}</span>
                    </span>
                    <span v-else class="inline-flex items-center gap-1.5 text-sm">
                        <i :class="iconOf(option.value)" class="text-xs text-muted-color" aria-hidden="true" />
                        {{ option.text }}
                    </span>
                </template>
            </Select>
        </template>

        <div :id="messageId" aria-live="polite" class="min-h-0 text-xs leading-relaxed">
            <p v-if="error" role="alert" class="m-0 text-red-600 dark:text-red-400">{{ error }}</p>
            <p v-else-if="note" class="m-0 text-amber-700 dark:text-amber-400">{{ note }}</p>
            <p v-else-if="helpText" class="m-0 text-muted-color">{{ helpText }}</p>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { BoardRef } from '~/composables/board/api/useBoardApi.types';
import { BOARD_MIN_GUEST, BOARD_MIN_OFF, BOARD_MIN_STAFF } from '~/composables/board/api/useBoardApi.types';
import type { Grade } from '~/composables/grade/api/useGradeApi.types';
import type { BoardFieldValue } from '~/utils/util-board';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGrade from '~/utils/util-grade';

/** 최소 자격 선택지 — 등급이면 뱃지, 운영진·끔이면 글자 */
interface LevelOption {
    value: string;
    text: string;
    badge: { label: string; color: string; icon: string | null } | null;
}

interface Props {
    /** 칸 이름 — 정의는 `UtilBoard.spec`(일반 화면 `SPECS` + 하위 화면 칸) */
    field: string;
    /** 입력값 — 숫자 칸은 글자, `null` = 기본값 따름(게시판 설정) */
    modelValue: BoardFieldValue;
    /** 「기본값 따름」 체크를 보인다(게시판 설정) */
    inheritable?: boolean;
    /** 지금 기본값 — 기본값 따름일 때 흐리게 보이고, 따름을 끄면 시작 값이 된다 */
    defaultValue?: BoardFieldValue;
    /** 등급 목록(서열 순) — 최소 자격 칸이 쓴다 */
    grades?: Grade[];
    /** 값을 바꿀 수 있다 — false 면 readonly/disabled */
    editable?: boolean;
    /** 칸 아래 오류(화면 검사 또는 서버 문구) */
    error?: string;
    /** 다른 칸 때문에 지금은 쓰이지 않는다 — 입력을 끄고 `note` 를 보인다 */
    inactive?: boolean;
    /** 주의 한 줄(잠금 때문에 적용되지 않음 등) — 안내 대신 보인다 */
    note?: string;
    /** 이 칸을 따로 정한 게시판들(기본 설정) */
    overrides?: BoardRef[];
    /** 최소 자격 칸에 「비회원 포함 모두」를 보인다 — 게시판 설정의 일반·소모임 게시판만(기본 설정·핫딜·직접홍보는 false) */
    allowGuest?: boolean;
    /** 최소 자격 칸에서 뺄 등급 키(핫딜 글쓰기의 첫 등급) — 지금 값이면 서버 값 그대로 보이도록 남긴다 */
    excludeGrades?: string[];
    /** 칸 아래 안내를 바꾼다(비우면 `board.fieldHelp.<칸>`) — 게시판 설정의 이름 칸처럼 화면마다 뜻이 다를 때 */
    help?: string;
}

const props = withDefaults(defineProps<Props>(), { inheritable: false, defaultValue: null, grades: () => [], editable: true, error: '', inactive: false, note: '', overrides: () => [], allowGuest: false, excludeGrades: () => [], help: '' });
const emit = defineEmits<{ 'update:modelValue': [value: BoardFieldValue] }>();

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const labelId = useId();
const inputId = useId();
const inheritId = useId();
const messageId = useId();
const panel = ref<{ toggle: (event: Event) => void } | null>(null);
const open = ref(false);

const spec = computed(() => UtilBoard.spec(props.field));
const isBool = computed(() => spec.value.kind === 'bool');
const isMulti = computed(() => spec.value.kind === 'multi');
const following = computed(() => props.inheritable && props.modelValue === null);

/** 칸 아래 안내 — 칸 설명 + (숫자 칸이면) 범위 */
const helpText = computed(() => {
    const help = props.help || t(`board.fieldHelp.${props.field}`);
    return spec.value.kind === 'number' ? `${help} ${UtilBoardMessages.range(t, props.field)}` : help;
});

/** 이름 칸 — 비웠을 때 쓰일 이름(게시판 설정이면 기본 설정 값, 그것도 없거나 기본 설정 화면이면 화면 기본 이름) */
const labelPlaceholder = computed(() => UtilBoard.labelValue(props.defaultValue) ?? t(`board.labelDefault.${props.field}`));
/** 이름 칸 글자 수 — 앞뒤 공백을 뗀 유니코드 한 글자씩(서버와 같다) */
const labelLength = computed(() => [...(UtilBoard.labelValue(props.modelValue) ?? '')].length);

/** 여러 개 고르기 — 고른 값(칸 정의 차례) */
const picked = computed(() => UtilBoard.multiValue(props.field, props.modelValue) ?? []);
function onPick(value: unknown): void {
    emit('update:modelValue', UtilBoard.multiValue(props.field, value) ?? []);
}

const choiceOptions = computed(() => (spec.value.choices ?? []).map((value) => ({ value, label: UtilBoardMessages.choice(t, props.field, value) })));

function gradeLabel(key: string): string {
    return props.grades.find((g) => g.gradeKey === key)?.label ?? key;
}

/** 최소 자격 선택지 — 등급 목록 + 운영진만(+ 댓글은 끔). 지금 값이 목록에 없으면 키 글자 뱃지로 더한다(등급 목록을 못 읽은 경우) */
const levelOptions = computed<LevelOption[]>(() => {
    const grades: LevelOption[] = props.grades.filter((g) => !props.excludeGrades.includes(g.gradeKey) || g.gradeKey === props.modelValue).map((g) => ({ value: g.gradeKey, text: UtilBoardMessages.minLevel(t, g.gradeKey, gradeLabel), badge: { label: g.label, color: g.badgeColor, icon: g.badgeIcon } }));
    const current = typeof props.modelValue === 'string' ? props.modelValue : '';
    const known = new Set([...grades.map((o) => o.value), BOARD_MIN_GUEST, BOARD_MIN_STAFF, BOARD_MIN_OFF]);
    if (current && !known.has(current)) grades.push({ value: current, text: UtilBoardMessages.minLevel(t, current, gradeLabel), badge: { label: current, ...UtilGrade.FALLBACK_BADGE } });
    const extra: LevelOption[] = [{ value: BOARD_MIN_STAFF, text: t('board.minStaff'), badge: null }];
    if (spec.value.kind === 'grade') return grades;
    if (spec.value.kind === 'commentMin') extra.push({ value: BOARD_MIN_OFF, text: t('board.minOff'), badge: null });
    // 맨 앞 「비회원 포함 모두」 — 허용한 곳이거나, 지금 값이 이미 GUEST 면(서버 값 그대로 보이도록) 넣는다
    const guest: LevelOption[] = props.allowGuest || props.modelValue === BOARD_MIN_GUEST ? [{ value: BOARD_MIN_GUEST, text: t('board.minGuest'), badge: null }] : [];
    return [...guest, ...grades, ...extra];
});

/** 글자 선택지 아이콘 — 비회원 포함 · 끔 · 운영진 */
function iconOf(value: string): string {
    return value === BOARD_MIN_GUEST ? 'pi pi-globe' : value === BOARD_MIN_OFF ? 'pi pi-ban' : 'pi pi-shield';
}

function levelOf(value: string): LevelOption {
    return levelOptions.value.find((o) => o.value === value) ?? { value, text: value, badge: null };
}

function textOf(value: BoardFieldValue): string {
    return UtilBoardMessages.valueText(t, props.field, value, gradeLabel);
}

/** 기본값 따름을 켜면 `null`, 끄면 지금 기본값으로 채운다(숫자 칸은 글자, 이름 칸은 기본 이름이 없으면 빈 글자, 목록은 복사) */
function setFollowing(on: boolean): void {
    if (on) emit('update:modelValue', null);
    else if (UtilBoard.isNumber(props.field)) emit('update:modelValue', String(props.defaultValue ?? ''));
    else if (spec.value.kind === 'label') emit('update:modelValue', UtilBoard.labelValue(props.defaultValue) ?? '');
    else if (isMulti.value) emit('update:modelValue', UtilBoard.multiValue(props.field, props.defaultValue) ?? []);
    else emit('update:modelValue', props.defaultValue);
}

function toggleOverrides(event: Event): void {
    panel.value?.toggle(event);
}
</script>
