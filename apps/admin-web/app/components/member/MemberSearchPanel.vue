<template>
    <!--
        회원 조회 조건 카드 — 그누보드 회원 관리의 필터 영역 구조를 따른다. 위에서 아래로:
        ① 맨 윗줄: 검색 대상 + 검색어 + 「상세검색」 토글(「컬럼 선택」은 표 위 툴바로 옮겼다)  ② 기본 조건(계정 상태 · 회원 역할 · 가입일)  ③ 구분선 + 「상세 검색 조건」(토글로 접힘/펼침)  ④ 맨 아래 줄: 「검색편집」 · 「검색」 · 「초기화」.
        조건은 화면 상태(`v-model`)로만 들고 있고 서버 조회는 「검색」(또는 Enter)을 누를 때만 일어난다 — 이 컴포넌트는 `search`·`reset` 을 올릴 뿐 서버를 모른다.
        「검색편집」은 상세 조건 중 어느 줄을 화면에 보일지 고른다(`v-model:detail-rows`). 줄을 끄면 그 값도 비운다 — 보이지 않는 조건이 검색에 남지 않게.
        줄·기간·선택 묶음·팝오버는 `components/common/` 의 부품(`FilterRow`·`DateRangeField`·`FilterOptionGroup`·`CheckListPopover`)이라 다른 조회 화면(운영자 관리 등)도 쓴다.
    -->
    <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('member.searchLabel')" @submit.prevent="emit('search')">
        <!-- ① 맨 윗줄 -->
        <div class="flex flex-wrap items-center gap-2">
            <Select v-model="filter.searchField" :options="searchFields" option-label="label" option-value="value" :aria-label="t('member.searchFieldLabel')" class="w-32 shrink-0" />
            <InputText v-model="filter.keyword" type="text" maxlength="100" autocomplete="off" :aria-label="t('member.keywordLabel')" :placeholder="t('member.keywordPlaceholder')" class="min-w-0 flex-1 basis-56" />
            <Button type="button" :label="t('member.detailToggle')" :icon="detailOpen ? 'pi pi-chevron-up' : 'pi pi-chevron-down'" icon-pos="right" size="small" severity="secondary" variant="outlined" :aria-expanded="detailOpen" :aria-controls="detailId" @click="detailOpen = !detailOpen" />
        </div>

        <p v-if="topError" role="alert" class="-mt-1 text-xs text-red-600 dark:text-red-400">{{ topError }}</p>

        <!-- ② 기본 조건 -->
        <FilterRow :label="t('member.filter.status')" :error="errorOf('statuses')">
            <template #default="{ labelId }">
                <FilterOptionGroup v-model="filter.statuses" multiple :all-label="t('common.all')" :options="statusOptions" :labelledby="labelId" />
            </template>
        </FilterRow>

        <FilterRow :label="t('member.filter.role')" :label-for="ids.role" :error="errorOf('roleIds')">
            <AppMultiSelect v-model="filter.roleIds" :options="roleOptions" option-label="roleName" option-value="roleId" :input-id="ids.role" filter :placeholder="t('member.filter.rolePlaceholder')" :filter-placeholder="t('member.filter.roleSearch')" :empty-filter-message="t('member.filter.roleEmpty')" :selected-items-label="t('member.filter.rolesSelected', { count: '{0}' })" :max-selected-labels="2" class="max-w-xl" />
        </FilterRow>

        <FilterRow :label="t('member.filter.joined')" :error="errorOf('joinedFrom', 'joinedTo')">
            <DateRangeField v-model:from="filter.joinedFrom" v-model:to="filter.joinedTo" :presets="presets" :presets-label="t('member.presetsLabel')" :from-label="t('member.filter.joinedFrom')" :to-label="t('member.filter.joinedTo')" />
        </FilterRow>

        <!-- ③ 상세 검색 조건 -->
        <div v-if="detailOpen" :id="detailId" class="flex flex-col gap-3 border-t border-(--line) pt-3">
            <h2 class="text-[13px] font-medium">{{ t('member.detailTitle') }}</h2>
            <p v-if="!detailRows.length" class="text-xs text-muted-color">{{ t('member.detailNone') }}</p>

            <FilterRow v-if="shown('lastLogin')" :label="t('member.filter.lastLogin')" :error="errorOf('lastLoginFrom', 'lastLoginTo', 'noLoginHistory')">
                <DateRangeField v-model:from="filter.lastLoginFrom" v-model:to="filter.lastLoginTo" :presets="presets" :presets-label="t('member.presetsLabel')" :from-label="t('member.filter.lastLoginFrom')" :to-label="t('member.filter.lastLoginTo')" :disabled="filter.noLoginHistory">
                    <div class="flex items-center gap-1.5">
                        <Checkbox binary :model-value="filter.noLoginHistory" :input-id="ids.noLogin" @update:model-value="onNoLogin" />
                        <label :for="ids.noLogin" class="cursor-pointer text-[13px]">{{ t('member.filter.noLoginHistory') }}</label>
                    </div>
                </DateRangeField>
            </FilterRow>

            <FilterRow v-if="shown('grade')" :label="t('member.filter.grade')" :error="errorOf('grades')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.grades" multiple :options="gradeOptions" :labelledby="labelId" />
                </template>
            </FilterRow>

            <FilterRow v-if="shown('memberType')" :label="t('member.filter.memberType')" :error="errorOf('memberType')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.memberType" :options="typeOptions" :labelledby="labelId" />
                </template>
            </FilterRow>

            <FilterRow v-if="shown('business')" :label="t('member.filter.business')" :error="errorOf('businessVerified')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.businessVerified" :options="businessOptions" :labelledby="labelId" />
                </template>
            </FilterRow>

            <FilterRow v-if="shown('emailVerified')" :label="t('member.filter.emailVerified')" :error="errorOf('emailVerified')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.emailVerified" :options="verifiedOptions" :labelledby="labelId" />
                </template>
            </FilterRow>

            <FilterRow v-if="shown('authMethod')" :label="t('member.filter.authMethod')" :error="errorOf('authMethods')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.authMethods" multiple :options="authOptions" :labelledby="labelId" />
                </template>
            </FilterRow>

            <FilterRow v-if="shown('dormant')" :label="t('member.filter.dormant')" :error="errorOf('dormantFrom', 'dormantTo')">
                <DateRangeField v-model:from="filter.dormantFrom" v-model:to="filter.dormantTo" :from-label="t('member.filter.dormantFrom')" :to-label="t('member.filter.dormantTo')" />
            </FilterRow>

            <FilterRow v-if="shown('statusChanged')" :label="t('member.filter.statusChanged')" :error="errorOf('statusChangedKind', 'statusChangedFrom', 'statusChangedTo')">
                <Select :model-value="filter.statusChangedKind || 'ALL'" :options="changeKindOptions" option-label="label" option-value="value" :aria-label="t('member.filter.statusChangedKind')" class="w-32" @update:model-value="(value: string) => (filter.statusChangedKind = value === 'ALL' ? '' : (value as MemberFilter['statusChangedKind']))" />
                <DateRangeField v-model:from="filter.statusChangedFrom" v-model:to="filter.statusChangedTo" :from-label="t('member.filter.statusChangedFrom')" :to-label="t('member.filter.statusChangedTo')" />
            </FilterRow>

            <FilterRow v-if="shown('point')" :label="t('member.filter.point')" :error="pointReversed ? '' : errorOf('pointMin', 'pointMax')">
                <!-- 기간 칸(`DateRangeField`)과 같은 모양 — 칸마다 고정폭 상자에 `fluid` 로 채운다(`fluid` 가 없으면 InputNumber 안쪽 입력이 자기 기본 너비로 상자 밖까지 늘어 옆 칸과 겹친다) -->
                <div class="flex flex-wrap items-center gap-x-2 gap-y-2">
                    <div class="w-42">
                        <label class="sr-only" :for="ids.pointMin">{{ t('member.filter.pointMin') }}</label>
                        <InputNumber v-model="filter.pointMin" :input-id="ids.pointMin" :min="0" :max-fraction-digits="0" :placeholder="t('member.filter.pointMin')" :invalid="pointReversed || Boolean(errorOf('pointMin', 'pointMax'))" fluid />
                    </div>
                    <span class="text-muted-color" aria-hidden="true">~</span>
                    <div class="w-42">
                        <label class="sr-only" :for="ids.pointMax">{{ t('member.filter.pointMax') }}</label>
                        <InputNumber v-model="filter.pointMax" :input-id="ids.pointMax" :min="0" :max-fraction-digits="0" :placeholder="t('member.filter.pointMax')" :invalid="pointReversed || Boolean(errorOf('pointMin', 'pointMax'))" fluid />
                    </div>
                    <span v-if="pointReversed" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ t('member.filter.pointReversed') }}</span>
                </div>
            </FilterRow>

            <FilterRow v-if="shown('boardRole')" :label="t('member.filter.boardRole')" :error="errorOf('boardRole')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.boardRole" :options="boardRoleOptions" :labelledby="labelId" />
                </template>
            </FilterRow>

            <FilterRow v-if="shown('sanction')" :label="t('member.filter.sanction')" :error="errorOf('hasSanction')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.hasSanction" :options="sanctionOptions" :labelledby="labelId" />
                </template>
            </FilterRow>
        </div>

        <!-- 어느 칸에도 붙지 않는 서버 오류(쪽 번호·크기·정렬 등) -->
        <p v-if="otherErrors" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ otherErrors }}</p>

        <!-- ④ 맨 아래 줄 -->
        <div class="flex flex-wrap items-center justify-between gap-2 border-t border-(--line) pt-3 md:grid md:grid-cols-[1fr_auto_1fr]">
            <CheckListPopover v-model="detailRows" :items="detailRowItems" :button-label="t('member.searchEdit')" icon="pi pi-sliders-h" :title="t('member.searchEditTitle')" :description="t('member.searchEditDesc')" :reset-label="t('member.searchEditReset')" @reset="detailRows = UtilMember.defaultDetailRows()" />
            <div class="flex items-center gap-2">
                <Button type="submit" :label="t('common.search')" size="small" :loading="busy" :disabled="pointReversed" />
                <Button type="button" :label="t('member.reset')" size="small" severity="secondary" variant="outlined" @click="emit('reset')" />
            </div>
        </div>

        <!-- 적용 중인 조건 요약 -->
        <div v-if="summary.length" class="flex flex-wrap items-center gap-1.5 border-t border-(--line) pt-3" role="status">
            <span class="text-xs text-muted-color">{{ t('member.applied') }}</span>
            <Tag v-for="item in summary" :key="item" :value="item" severity="secondary" class="text-[11px]!" />
        </div>
    </form>
</template>

<script setup lang="ts">
import type { MemberRoleOption } from '~/composables/member/api/useMemberApi.types';
import UtilDateRange from '~/utils/util-date-range';
import UtilMember, { type MemberDetailRow, type MemberFilter } from '~/utils/util-member';

/** 조회 조건(화면 값) — 부모가 들고 있고 이 컴포넌트가 고친다 */
const filter = defineModel<MemberFilter>({ required: true });
/** 화면에 보일 상세 검색 줄 */
const detailRows = defineModel<MemberDetailRow[]>('detailRows', { required: true });

interface Props {
    /** 역할 고르개 목록 */
    roleOptions: MemberRoleOption[];
    /** 조회 중 — 「검색」 단추에 스피너 */
    busy?: boolean;
    /** 마지막으로 검색한 조건의 요약 글자들(비면 칩 줄을 감춘다) */
    summary?: string[];
    /** 서버가 거절한 조건 문구(`400 VALIDATION`·`MEMBER_PERIOD_REVERSED`·`MEMBER_POINT_RANGE_REVERSED` 의 `fields` — 조회 조건 이름 → 문구). 서버 `message` 그대로 줄 아래에 붙는다 */
    errors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { busy: false, summary: () => [], errors: () => ({}) });
const emit = defineEmits<{ search: []; reset: [] }>();

const { t } = useI18n();
/** 줄에 문구를 붙이는 조회 조건 이름 — 이 밖의 이름은 맨 아래 한 줄에 모은다 */
const MAPPED_FIELDS = [
    'searchField', 'keyword', 'statuses', 'roleIds', 'joinedFrom', 'joinedTo', 'lastLoginFrom', 'lastLoginTo', 'noLoginHistory', 'grades', 'memberType', 'emailVerified', 'authMethods',
    'dormantFrom', 'dormantTo', 'statusChangedKind', 'statusChangedFrom', 'statusChangedTo', 'pointMin', 'pointMax', 'boardRole', 'hasSanction', 'businessVerified',
];

const detailId = useId();
const ids = { role: useId(), noLogin: useId(), pointMin: useId(), pointMax: useId() };

/** 상세검색 토글 — 처음에는 접혀 있다 */
const detailOpen = ref(false);

// ⚠ 번역 글자가 들어가는 목록은 `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const searchFields = computed(() => UtilMember.SEARCH_FIELDS.map((value) => ({ value, label: t(`member.searchField.${value}`) })));
const statusOptions = computed(() => UtilMember.STATUSES.map((value) => ({ value: value as string, label: t(`member.status.${value}`) })));
// 등급 옵션은 등급 목록(서열 순서)에서 읽는다 — 등급 설정에서 늘리거나 줄이면 그대로 따라간다
const gradeState = useGradeAppearance();
const gradeOptions = computed(() => gradeState.grades.value.map((grade) => ({ value: grade.gradeKey, label: grade.label })));
const typeOptions = computed(() => [{ value: '', label: t('common.all') }, ...UtilMember.TYPES.map((value) => ({ value: value as string, label: t(`member.type.${value}`) }))]);
const verifiedOptions = computed(() => [
    { value: '', label: t('common.all') },
    { value: 'DONE', label: t('member.emailVerified.DONE') },
    { value: 'NOT', label: t('member.emailVerified.NOT') },
]);
const authOptions = computed(() => UtilMember.AUTH_METHODS.map((value) => ({ value: value as string, label: t(`member.authMethod.${value}`) })));
const changeKindOptions = computed(() => [{ value: 'ALL', label: t('common.all') }, ...UtilMember.STATUS_CHANGE_KINDS.map((value) => ({ value: value as string, label: t(`member.statusChange.${value}`) }))]);
const boardRoleOptions = computed(() => [{ value: '', label: t('common.all') }, ...UtilMember.BOARD_ROLES.map((value) => ({ value: value as string, label: t(`member.boardRole.${value}`) }))]);
const businessOptions = computed(() => [
    { value: '', label: t('common.all') },
    { value: 'YES', label: t('member.businessVerified.YES') },
    { value: 'NO', label: t('member.businessVerified.NO') },
]);
const sanctionOptions = computed(() => [
    { value: '', label: t('common.all') },
    { value: 'YES', label: t('member.sanction.YES') },
    { value: 'NO', label: t('member.sanction.NO') },
]);
const presets = computed(() => UtilDateRange.PRESETS.map((kind) => ({ kind, label: t(`member.preset.${kind}`) })));

const detailRowItems = computed(() => UtilMember.DETAIL_ROWS.map((row) => ({ key: row as string, label: t(`member.filter.${row}`) })));

const pointReversed = computed(() => UtilMember.pointReversed(filter.value));

/** 줄 이름들 중 서버가 거절한 첫 문구 */
function errorOf(...names: string[]): string {
    for (const name of names) {
        const message = props.errors[name];
        if (message) return message;
    }
    return '';
}

/** 맨 윗줄(검색 대상·검색어)의 서버 오류 */
const topError = computed(() => errorOf('searchField', 'keyword'));

/** 줄에 붙은 이름이 아닌 서버 오류(알 수 없는 이름·쪽 번호·크기·정렬) — 한 줄로 모은다 */
const otherErrors = computed(() => {
    const mapped = new Set(MAPPED_FIELDS);
    return Object.entries(props.errors)
        .filter(([name]) => !mapped.has(name))
        .map(([, message]) => message)
        .join(' ');
});

function shown(row: MemberDetailRow): boolean {
    return detailRows.value.includes(row);
}

/** 「로그인 이력 없음」을 켜면 최근 로그인 기간은 의미가 없어 비운다 */
function onNoLogin(on: boolean): void {
    filter.value.noLoginHistory = on;
    if (on) {
        filter.value.lastLoginFrom = '';
        filter.value.lastLoginTo = '';
    }
}

/** 검색편집에서 줄을 끄면 그 줄의 값도 비운다 */
watch(detailRows, (rows, before) => {
    for (const row of before) if (!rows.includes(row)) UtilMember.clearRow(filter.value, row);
});
</script>
