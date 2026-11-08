<template>
    <!--
        공지 「상단 고정」 아래의 고정 범위 · 뺄 게시판 · 걸어 둘 기간(전체 공지, 사용자 확정 2026-11-08 — notice-admin-contract 「전체 공지」).
        공지 작성·수정 화면(`/notices/{id}`)이 고정을 켰을 때만 그린다. 값은 부르는 쪽의 폼(`v-model:*`)에 둔다.
        - 범위: 「공지 게시판에만」(기본) · 「모든 게시판 맨 위에도」 + 칩 「전체 공지」. 전체 공지가 한도(3)만큼 걸려 있고 이 글이 그중 하나가 아니면 둘째 칸은 꺼짐 + 안내.
        - 뺄 게시판: 일반 게시판 칩(누르면 빠짐 = 빨간 칩 + − 아이콘, 다시 누르면 넣음 · 많으면 접어 「… N개 더」) + 소모임 게시판 줄(「소모임 게시판 전체 (N)」 칩 하나, 또는 「하나씩 고르기」).
          공지 게시판 자신은 목록에 없다(서버가 빼서 준다).
        - 기간: 끝 없음(기본) · 7일 · 30일 · 날짜 고르기(그날 끝까지, 한국 시각). 지나면 다른 게시판에서만 내려간다.
    -->
    <div class="flex flex-col gap-3 pl-7" data-testid="notice-global-options">
        <div role="radiogroup" :aria-labelledby="scopeLabelId" class="flex flex-col gap-2">
            <span :id="scopeLabelId" class="text-sm font-medium">{{ t('notice.scopeLabel') }}</span>
            <label class="flex cursor-pointer items-start gap-2">
                <RadioButton v-model="scope" value="NOTICE_ONLY" :name="scopeName" :disabled="disabled" class="mt-0.5 shrink-0" />
                <span class="text-sm">{{ t('notice.scopeNoticeOnly') }}</span>
            </label>
            <label class="flex items-start gap-2" :class="allBlocked ? 'cursor-not-allowed' : 'cursor-pointer'">
                <RadioButton v-model="scope" value="ALL" :name="scopeName" :disabled="disabled || allBlocked" class="mt-0.5 shrink-0" :aria-describedby="allHelpId" />
                <span class="flex min-w-0 flex-col gap-0.5">
                    <span class="flex flex-wrap items-center gap-2 text-sm" :class="allBlocked ? 'text-muted-color' : ''">
                        {{ t('notice.scopeAll') }}
                        <Tag :value="t('notice.globalChip')" icon="pi pi-megaphone" severity="info" class="!px-1.5 !py-0 !text-[11px]" />
                    </span>
                    <span :id="allHelpId" class="text-xs leading-relaxed" :class="allBlocked ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color'">
                        {{ allBlocked ? t('notice.globalFullHint', { max: global.max }) : t('notice.scopeAllHelp', { max: global.max, n: global.count }) }}
                    </span>
                </span>
            </label>
            <p v-if="error('scope')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ error('scope') }}</p>
        </div>

        <template v-if="scope === 'ALL'">
            <!-- 뺄 게시판 -->
            <div class="flex flex-col gap-2 rounded-lg border border-(--line) p-3">
                <div class="flex flex-wrap items-baseline justify-between gap-2">
                    <span :id="excludeLabelId" class="text-sm font-medium">{{ t('notice.excludeLabel') }}</span>
                    <span class="text-xs" :class="excludedCount > 0 ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" role="status">
                        {{ excludedCount > 0 ? t('notice.excludeCount', { n: excludedCount }) : t('notice.excludeNone') }}
                    </span>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('notice.excludeHelp') }}</p>

                <div class="flex flex-col gap-1.5" role="group" :aria-labelledby="excludeLabelId">
                    <span class="text-xs font-medium text-muted-color">{{ t('notice.excludeGeneral') }}</span>
                    <p v-if="global.boards.length === 0" class="m-0 text-xs text-muted-color">{{ t('notice.excludeGeneralEmpty') }}</p>
                    <div v-else class="flex flex-wrap gap-1.5">
                        <button v-for="b in shownBoards" :key="b.id" type="button" :class="chipClass(isExcluded(b.id))" :aria-pressed="isExcluded(b.id)" :disabled="disabled" @click="toggle(b.id)">
                            <i v-if="isExcluded(b.id)" class="pi pi-minus text-[10px]" aria-hidden="true" />
                            {{ localizedName(b.name, b.nameEn) }}
                        </button>
                        <button v-if="hiddenBoardCount > 0" type="button" class="rounded-full px-2.5 py-1 text-xs text-primary hover:underline" @click="expanded = true">
                            {{ t('notice.excludeMore', { n: hiddenBoardCount }) }}
                        </button>
                        <button v-else-if="expanded && global.boards.length > COLLAPSED" type="button" class="rounded-full px-2.5 py-1 text-xs text-primary hover:underline" @click="expanded = false">
                            {{ t('notice.excludeLess') }}
                        </button>
                    </div>
                </div>

                <div v-if="global.groupBoards.length" class="flex flex-col gap-1.5 border-t border-(--line) pt-2">
                    <span class="text-xs font-medium text-muted-color">{{ t('notice.excludeGroup') }}</span>
                    <div class="flex flex-wrap items-center gap-1.5">
                        <button type="button" :class="chipClass(excludeAllGroups)" :aria-pressed="excludeAllGroups" :disabled="disabled" data-testid="notice-exclude-groups" @click="toggleAllGroups">
                            <i v-if="excludeAllGroups" class="pi pi-minus text-[10px]" aria-hidden="true" />
                            {{ t('notice.excludeGroupAll', { n: global.groupBoards.length }) }}
                        </button>
                        <button v-if="!excludeAllGroups" type="button" class="rounded-full px-2.5 py-1 text-xs text-primary hover:underline" :aria-expanded="groupOpen" @click="groupOpen = !groupOpen">
                            {{ groupOpen ? t('notice.excludeGroupClose') : t('notice.excludeGroupEach') }}
                        </button>
                    </div>
                    <div v-if="groupOpen && !excludeAllGroups" class="flex flex-wrap gap-1.5">
                        <button v-for="b in global.groupBoards" :key="b.id" type="button" :class="chipClass(isExcluded(b.id))" :aria-pressed="isExcluded(b.id)" :disabled="disabled" @click="toggle(b.id)">
                            <i v-if="isExcluded(b.id)" class="pi pi-minus text-[10px]" aria-hidden="true" />
                            {{ localizedName(b.name, b.nameEn) }}
                        </button>
                    </div>
                </div>
                <p v-if="error('excludedBoardIds')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ error('excludedBoardIds') }}</p>
            </div>

            <!-- 걸어 둘 기간 -->
            <div class="flex flex-col gap-1.5">
                <span :id="periodLabelId" class="text-sm font-medium">{{ t('notice.periodLabel') }}</span>
                <SelectButton v-model="period" :options="periodOptions" option-label="label" option-value="value" :allow-empty="false" :aria-labelledby="periodLabelId" :disabled="disabled" size="small" />
                <div v-if="period === 'DATE'" class="w-full sm:w-56">
                    <AppDatePicker v-model="periodDate" :input-id="dateId" :min="minDate" :max="maxDate" :disabled="disabled" :invalid="!!error('expiresAt')" :described-by="periodHelpId" size="small" />
                </div>
                <p :id="periodHelpId" class="m-0 text-xs" :class="error('expiresAt') ? 'text-red-600 dark:text-red-400' : expired ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color'">
                    {{ error('expiresAt') || (expired ? t('notice.periodExpired') : t('notice.periodHelp')) }}
                </p>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { NoticeGlobalContext, NoticePinScope } from '~/composables/notice/api/useNoticeApi.types';
import type { NoticePeriod } from '~/utils/util-notice';
import UtilNotice from '~/utils/util-notice';

interface Props {
    global: NoticeGlobalContext;
    /** 이 글이 지금 살아 있는 전체 공지인가(한도가 차도 고를 수 있다) */
    liveGlobal?: boolean;
    /** 저장된 기간이 이미 지났다(공지 게시판 고정만 남음) */
    expired?: boolean;
    disabled?: boolean;
    /** 칸 오류(서버 `fields.*`) — `scope` · `excludedBoardIds` · `expiresAt` */
    errors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { liveGlobal: false, expired: false, disabled: false, errors: () => ({}) });

const scope = defineModel<NoticePinScope>('scope', { required: true });
const excluded = defineModel<string[]>('excluded', { required: true });
const excludeAllGroups = defineModel<boolean>('excludeAllGroups', { required: true });
const period = defineModel<NoticePeriod>('period', { required: true });
const periodDate = defineModel<string>('periodDate', { required: true });

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const scopeName = useId();
const scopeLabelId = useId();
const allHelpId = useId();
const excludeLabelId = useId();
const periodLabelId = useId();
const periodHelpId = useId();
const dateId = useId();

/** 접었을 때 보일 일반 게시판 칩 수 */
const COLLAPSED = 12;
const expanded = ref(false);
/** 하나씩 고른 소모임 게시판이 있으면 펼쳐서 시작한다 */
const groupOpen = ref(props.global.groupBoards.some((b) => excluded.value.includes(b.id)));

/** 한도가 찼고 이 글은 그중 하나가 아니다 — 이미 ALL 로 고른 상태(저장 전)면 풀 수 있게 둔다 */
const allBlocked = computed(() => !props.liveGlobal && scope.value !== 'ALL' && props.global.count >= props.global.max);

const shownBoards = computed(() => {
    if (expanded.value || props.global.boards.length <= COLLAPSED) return props.global.boards;
    // 접어도 뺀 게시판은 보인다
    const head = props.global.boards.slice(0, COLLAPSED);
    const extra = props.global.boards.slice(COLLAPSED).filter((b) => excluded.value.includes(b.id));
    return [...head, ...extra];
});
const hiddenBoardCount = computed(() => props.global.boards.length - shownBoards.value.length);

const groupIds = computed(() => new Set(props.global.groupBoards.map((b) => b.id)));
/** 「뺄 게시판 N개」 — 일반 게시판 + (소모임 전체면 소모임 게시판 수, 아니면 하나씩 고른 수) */
const excludedCount = computed(() => {
    const general = excluded.value.filter((id) => !groupIds.value.has(id)).length;
    const group = excludeAllGroups.value ? props.global.groupBoards.length : excluded.value.filter((id) => groupIds.value.has(id)).length;
    return general + group;
});

function isExcluded(id: string): boolean {
    return excluded.value.includes(id);
}
function toggle(id: string): void {
    excluded.value = isExcluded(id) ? excluded.value.filter((x) => x !== id) : [...excluded.value, id];
}
/** 소모임 전체 빼기 — 켜면 하나씩 고른 소모임 게시판은 지운다(전체에 들어간다) */
function toggleAllGroups(): void {
    const next = !excludeAllGroups.value;
    excludeAllGroups.value = next;
    if (next) {
        excluded.value = excluded.value.filter((id) => !groupIds.value.has(id));
        groupOpen.value = false;
    }
}

function chipClass(on: boolean): string {
    return [
        'inline-flex items-center gap-1 rounded-full border px-2.5 py-1 text-xs focus-visible:outline-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-60',
        on
            ? 'border-red-300 bg-red-50 text-red-700 dark:border-red-800 dark:bg-red-950 dark:text-red-300'
            : 'border-(--line) bg-surface-0 hover:bg-surface-100 dark:bg-surface-900 dark:hover:bg-surface-800',
    ].join(' ');
}

const periodOptions = computed(() => UtilNotice.PERIODS.map((value) => ({ value, label: t(`notice.period.${value}`) })));
const minDate = computed(() => UtilNotice.kstToday());
const maxDate = computed(() => UtilNotice.kstAddDays(UtilNotice.kstToday(), UtilNotice.PERIOD_MAX_DAYS - 1));

function error(field: string): string {
    return props.errors[field] ?? '';
}
</script>
