<template>
    <!--
        운영자 상세 서랍의 「사이트 계정」 탭 — 이 운영자가 사이트(user-web)에서 쓰는 회원 계정 하나를 잇고 끊는다(`docs/features/operator-member-account-contract.md`).
        ① 위쪽 짧은 설명 ② 연결됨(회원 카드 + 상태 딱지 + 멈춘 까닭 + [연결 해제]) /
           연결 안 됨 — 이메일이 달라도 잇는다(2026-11-24): 「추천」 운영자와 같은 이메일로 가입한 회원 카드(있을 때) → 회원 검색(닉네임·이메일·회원번호, 0.3초 디바운스·Enter 바로 →
           결과 목록: 닉네임·가린 이메일·회원번호·가입일·매니저/스텝 수, 이을 수 없으면 까닭 뱃지 + [연결] 꺼짐) → [새 계정 만들기]
        ③ 힘 목록 — 연결 여부와 상관없이 보인다(연결 전이면 「연결하면 이렇게 쓸 수 있어요」). 게시판 관리 힘은 이 운영자의 관리자 권한 거울이라 됨/안 됨과 필요한 권한 이름을 함께 보인다.
        값은 페이지가 받아 내려 주고(`account`·`search`), 연결·해제는 `link`(후보 통째)·`unlink`, 검색은 `search`(다듬지 않은 말)로 올린다 — 확인창·서버 호출은 페이지가 한다. 이 탭은 서랍 푸터를 쓰지 않는다.
        [새 계정 만들기]는 `create` 를 올린다(대화창은 페이지가 띄운다). 검색 칸은 운영자가 바뀌거나 연결되면 비운다.
        단추는 `canManage`(operator.manage)일 때만, 대상이 최고 관리자면 본인일 때만 — 아니면 읽기 전용(까닭 한 줄). 감추는 것은 편의이고 막는 것은 서버다.
    -->
    <section class="flex flex-col gap-4 pt-3" :aria-labelledby="titleId">
        <h3 :id="titleId" class="sr-only">{{ t('operatorMember.sectionTitle') }}</h3>
        <div class="flex flex-col gap-1 text-xs leading-relaxed text-muted-color">
            <p class="m-0">{{ t('operatorMember.intro') }} {{ t('operatorMember.introPowers') }}</p>
            <p class="m-0">{{ t('operatorMember.introStop') }}</p>
        </div>

        <div v-if="loading && !account" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <Skeleton height="6rem" />
            <Skeleton height="1rem" width="60%" />
        </div>
        <ErrorState v-else-if="!account" :title="t('operatorMember.loadFailed')" :description="error" retry :retrying="loading" @retry="emit('retry')" />

        <template v-else>
            <!-- 연결됨 -->
            <div v-if="account.linked" class="flex flex-col gap-3 rounded-lg border border-(--line) px-3 py-3">
                <div class="flex flex-wrap items-center gap-2">
                    <i class="pi pi-user text-sm text-muted-color" aria-hidden="true" />
                    <NuxtLink v-if="canReadMember" :to="{ path: '/members', query: { memberNo: account.linked.userId } }" class="min-w-0 truncate text-sm font-medium text-(--p-primary-color) hover:underline" :title="t('operatorMember.openMember')">
                        {{ account.linked.nickname }}
                    </NuxtLink>
                    <span v-else class="min-w-0 truncate text-sm font-medium">{{ account.linked.nickname }}</span>
                    <GradeBadge v-bind="gradeState.badgeOf(account.linked.grade)" />
                    <Tag v-if="account.linked.active" :value="t('operatorMember.stateActive')" icon="pi pi-check-circle" severity="success" />
                    <Tag v-else :value="t('operatorMember.statePaused')" icon="pi pi-pause-circle" severity="warn" />
                </div>
                <dl class="m-0 grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1.5 text-[13px]">
                    <dt class="text-muted-color">{{ t('operatorMember.fieldUserId') }}</dt>
                    <dd class="m-0"><code class="text-[12px] tabular-nums">{{ account.linked.userId }}</code></dd>
                    <dt class="text-muted-color">{{ t('operatorMember.fieldLinkedAt') }}</dt>
                    <dd class="m-0 tabular-nums">{{ time.full(account.linked.linkedAt) }}</dd>
                    <dt class="text-muted-color">{{ t('operatorMember.fieldLinkedBy') }}</dt>
                    <dd class="m-0">{{ account.linked.linkedBy?.operatorName ?? '—' }}</dd>
                </dl>
                <Message v-if="!account.linked.active && account.linked.pausedReasons.length" severity="warn" size="small" variant="simple" :closable="false">
                    <span class="font-medium">{{ t('operatorMember.pausedTitle') }}</span>
                    <ul class="m-0 mt-1 list-disc pl-4">
                        <li v-for="reason in account.linked.pausedReasons" :key="reason">{{ t(`operatorMember.paused.${reason}`) }}</li>
                    </ul>
                </Message>
                <div v-if="canEdit">
                    <Button type="button" :label="t('operatorMember.unlink')" icon="pi pi-times" size="small" severity="danger" variant="outlined" :loading="busy" @click="emit('unlink')" />
                </div>
            </div>

            <!-- 연결 안 됨 — ① 추천(운영자와 같은 이메일) ② 회원 검색(이메일이 달라도) ③ 새 계정 만들기 -->
            <template v-else>
                <div v-if="account.candidate" class="flex flex-col gap-3 rounded-lg border border-(--line) px-3 py-3">
                    <p class="m-0 flex flex-wrap items-center gap-1.5 text-xs text-muted-color">
                        <Tag :value="t('operatorMember.recommendTag')" icon="pi pi-star" severity="info" />
                        <span>{{ t('operatorMember.recommendTitle', { email: operator.email }) }}</span>
                    </p>
                    <div class="flex flex-wrap items-center gap-2">
                        <i class="pi pi-user text-sm text-muted-color" aria-hidden="true" />
                        <span class="min-w-0 truncate text-sm font-medium">{{ account.candidate.nickname }}</span>
                        <GradeBadge v-bind="gradeState.badgeOf(account.candidate.grade)" />
                    </div>
                    <dl class="m-0 grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1.5 text-[13px]">
                        <dt class="text-muted-color">{{ t('operatorMember.fieldJoinedAt') }}</dt>
                        <dd class="m-0 tabular-nums">{{ time.date(account.candidate.joinedAt) }}</dd>
                        <dt class="text-muted-color">{{ t('operatorMember.fieldBoardRoles') }}</dt>
                        <dd class="m-0 tabular-nums">{{ t('operatorMember.boardRoles', { manager: account.candidate.boardManagerCount, staff: account.candidate.boardStaffCount }) }}</dd>
                    </dl>
                    <Message v-if="!account.candidate.eligible" severity="secondary" size="small" variant="simple" :closable="false">
                        <span class="font-medium">{{ t('operatorMember.ineligibleTitle') }}</span>
                        <ul class="m-0 mt-1 list-disc pl-4">
                            <li v-for="reason in account.candidate.ineligibleReasons" :key="reason">{{ t(`operatorMember.ineligible.${reason}`, { name: account.candidate.linkedOperatorName ?? '' }) }}</li>
                        </ul>
                    </Message>
                    <div v-else-if="canEdit">
                        <Button
                            type="button"
                            :label="t('operatorMember.link')"
                            :aria-label="t('operatorMember.linkLabel', { nickname: account.candidate.nickname })"
                            icon="pi pi-link"
                            size="small"
                            :loading="busy && linkingId === account.candidate.userId"
                            :disabled="busy"
                            @click="link(account.candidate)"
                        />
                    </div>
                </div>
                <p v-else class="m-0 flex items-start gap-2 rounded-lg border border-dashed border-(--line) px-3 py-2.5 text-[13px] leading-relaxed text-muted-color">
                    <i class="pi pi-user mt-0.5" aria-hidden="true" />
                    <span>{{ canEdit ? t('operatorMember.unlinkedNone') : t('operatorMember.unlinkedNoneReadOnly') }}</span>
                </p>

                <!-- 회원 검색 — 닉네임·이메일·회원번호. 글자를 멈추고 0.3초 뒤에 찾는다(Enter 는 바로) -->
                <section v-if="canEdit" class="flex flex-col gap-1.5" :aria-labelledby="searchTitleId">
                    <label :id="searchTitleId" :for="searchInputId" class="text-sm font-medium">{{ t('operatorMember.search.label') }}</label>
                    <IconField>
                        <InputIcon class="pi pi-search" aria-hidden="true" />
                        <InputText
                            :id="searchInputId"
                            v-model="query"
                            type="search"
                            maxlength="100"
                            autocomplete="off"
                            :placeholder="t('operatorMember.search.placeholder')"
                            :aria-describedby="searchHelpId"
                            fluid
                            @keydown.enter.prevent="emitSearch(true)"
                        />
                    </IconField>
                    <p :id="searchHelpId" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('operatorMember.search.help') }}</p>

                    <div aria-live="polite" :aria-busy="search.loading">
                        <div v-if="search.loading && !search.items" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                            <Skeleton v-for="n in 2" :key="n" height="3rem" />
                        </div>
                        <p v-else-if="search.error" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ search.error }}</p>
                        <p v-else-if="tooShort" class="m-0 text-xs text-muted-color">{{ t('operatorMember.search.tooShort', { min: OPERATOR_MEMBER_SEARCH_MIN }) }}</p>
                        <p v-else-if="search.items && !search.items.length" class="m-0 rounded-lg border border-(--line) px-3 py-3 text-center text-[13px] text-muted-color">
                            {{ t('operatorMember.search.empty', { query: search.query }) }}
                        </p>
                        <template v-else-if="search.items">
                            <ul class="m-0 flex list-none flex-col rounded-lg border border-(--line) p-0" :aria-label="t('operatorMember.search.results')">
                                <li v-for="c in search.items" :key="c.userId" class="flex items-start justify-between gap-3 border-b border-(--line) px-3 py-2.5 last:border-b-0">
                                    <div class="flex min-w-0 flex-1 flex-col gap-1">
                                        <div class="flex min-w-0 flex-wrap items-center gap-2">
                                            <span class="min-w-0 truncate text-sm font-medium">{{ c.nickname }}</span>
                                            <GradeBadge v-bind="gradeState.badgeOf(c.grade)" />
                                        </div>
                                        <div class="flex flex-wrap gap-x-2 gap-y-0.5 text-[11px] text-muted-color">
                                            <span class="min-w-0 truncate" :title="c.maskedEmail ? undefined : t('operatorMember.search.noEmail')">{{ c.maskedEmail ?? '—' }}</span>
                                            <span class="tabular-nums">{{ t('operatorMember.search.memberNo', { no: c.memberNo }) }}</span>
                                            <span class="tabular-nums">{{ t('operatorMember.search.joinedAt', { date: time.date(c.joinedAt) }) }}</span>
                                            <span class="tabular-nums">{{ t('operatorMember.boardRoles', { manager: c.boardManagerCount, staff: c.boardStaffCount }) }}</span>
                                        </div>
                                        <div v-if="!c.eligible" class="flex flex-wrap gap-1">
                                            <Tag
                                                v-for="reason in c.ineligibleReasons"
                                                :key="reason"
                                                v-tooltip.top="t(`operatorMember.ineligible.${reason}`, { name: c.linkedOperatorName ?? '' })"
                                                :value="t(`operatorMember.ineligibleShort.${reason}`, { name: c.linkedOperatorName ?? '' })"
                                                :aria-label="t(`operatorMember.ineligible.${reason}`, { name: c.linkedOperatorName ?? '' })"
                                                severity="secondary"
                                                class="text-[11px]"
                                            />
                                        </div>
                                    </div>
                                    <Button
                                        type="button"
                                        :label="t('operatorMember.link')"
                                        :aria-label="t('operatorMember.linkLabel', { nickname: c.nickname })"
                                        icon="pi pi-link"
                                        size="small"
                                        :severity="c.eligible ? undefined : 'secondary'"
                                        :variant="c.eligible ? 'outlined' : 'text'"
                                        class="shrink-0"
                                        :loading="busy && linkingId === c.userId"
                                        :disabled="!c.eligible || busy"
                                        @click="link(c)"
                                    />
                                </li>
                            </ul>
                            <p v-if="search.items.length >= SEARCH_LIMIT" class="m-0 mt-1 text-xs text-muted-color">{{ t('operatorMember.search.limit', { count: SEARCH_LIMIT }) }}</p>
                        </template>
                    </div>
                </section>

                <!-- 새 계정 만들기 — 찾는 회원이 없을 때 -->
                <div v-if="canEdit" class="flex flex-wrap items-center gap-x-3 gap-y-1.5 border-t border-dashed border-(--line) pt-3">
                    <span class="text-xs text-muted-color">{{ t('operatorMember.createHint') }}</span>
                    <Button type="button" :label="t('operatorMember.createHere')" icon="pi pi-user-plus" size="small" severity="secondary" variant="outlined" :disabled="busy" @click="emit('create')" />
                </div>
            </template>

            <p v-if="readOnlyNote" class="m-0 flex items-center gap-1 text-xs text-muted-color">
                <i class="pi pi-lock text-[10px]" aria-hidden="true" />{{ readOnlyNote }}
            </p>

            <!-- 힘 목록 — 연결 여부와 상관없이 -->
            <section class="flex flex-col gap-2 border-t border-(--line) pt-4" :aria-labelledby="powersId">
                <h4 :id="powersId" class="m-0 text-sm font-medium">{{ account.linked ? t('operatorMember.powersTitle') : t('operatorMember.powersPreview') }}</h4>
                <p v-if="account.linked && !account.linked.active" class="m-0 text-xs text-muted-color">{{ t('operatorMember.powersPausedNote') }}</p>
                <ul class="m-0 flex list-none flex-col p-0">
                    <li v-for="power in powerRows" :key="power.key" class="flex items-start justify-between gap-3 border-b border-(--line) py-2 last:border-b-0">
                        <div class="min-w-0">
                            <div class="text-[13px]">{{ power.label }}</div>
                            <div class="mt-0.5 text-xs text-muted-color">{{ power.hint }}</div>
                        </div>
                        <Tag v-if="power.granted" :value="t('operatorMember.powerGranted')" icon="pi pi-check" severity="success" class="shrink-0" />
                        <Tag v-else :value="t('operatorMember.powerDenied')" icon="pi pi-minus" severity="secondary" class="shrink-0" />
                    </li>
                </ul>
                <p v-if="account.powers.dailyLimit === null" class="m-0 flex items-center gap-1 text-xs text-muted-color">
                    <i class="pi pi-info-circle text-[10px]" aria-hidden="true" />{{ t('operatorMember.dailyLimitNone') }}
                </p>
                <p class="m-0 text-xs text-muted-color">{{ t('operatorMember.powerScopeNote') }}</p>
            </section>
        </template>
    </section>
</template>

<script setup lang="ts">
import type { OperatorView } from '~/composables/operator/api/useOperatorApi.types';
import type { OperatorMemberAccountView, OperatorMemberCandidate } from '~/composables/operator/api/useOperatorMemberAccountApi.types';
import { OPERATOR_MEMBER_SEARCH_MIN, type OperatorMemberSearchView } from '~/composables/operator/useOperatorMemberSearch.types';
import UtilPermissionCatalog from '~/utils/util-permission-catalog';

/** 서버가 한 번에 주는 검색 결과 수 — 이만큼 오면 「더 자세히 찾아 보세요」 */
const SEARCH_LIMIT = 20;

interface Props {
    operator: OperatorView;
    /** 이 운영자의 사이트 계정 상태(페이지가 받는다). null = 받는 중이거나 못 받았다 */
    account?: OperatorMemberAccountView | null;
    loading?: boolean;
    /** 못 받았을 때 서버 문구 */
    error?: string;
    /** 운영자를 고칠 수 있는가(`operator.manage`) */
    canManage?: boolean;
    /** 보고 있는 운영자가 로그인한 본인인가 — 최고 관리자의 연결은 본인만 바꾼다 */
    isSelf?: boolean;
    /** 연결·해제 중 */
    busy?: boolean;
    /** 회원 검색 상태(페이지가 `useOperatorMemberSearch` 로 받는다). null = 아직 없음 */
    search?: OperatorMemberSearchView | null;
}

const props = withDefaults(defineProps<Props>(), {
    account: null,
    loading: false,
    error: '',
    canManage: false,
    isSelf: false,
    busy: false,
    search: null,
});
const emit = defineEmits<{
    /** [연결] — 추천 카드나 검색 결과의 회원 통째(확인창의 닉네임·게시판 수에 쓴다). 확인은 페이지가 묻는다 */
    link: [candidate: OperatorMemberCandidate];
    /** 회원 검색 — 다듬지 않은 말. 2자 미만이면 페이지가 결과를 비운다 */
    search: [query: string];
    /** [연결 해제] — 확인은 페이지가 묻는다 */
    unlink: [];
    /** [새 계정 만들기] — 대화창(`OperatorMemberCreateDialog`)은 페이지가 띄운다 */
    create: [];
    retry: [];
}>();

const { t } = useI18n();
const time = useDisplayTime();
const { can } = useAdminPermission();
// 등급 뱃지 — 등급 설정의 이름·색·아이콘(목록은 페이지가 `ensureLoaded` 로 받는다. 못 받으면 중립 뱃지 + 키)
const gradeState = useGradeAppearance();
const titleId = useId();
const powersId = useId();
const searchTitleId = useId();
const searchInputId = useId();
const searchHelpId = useId();

/* ── 회원 검색 ── */
const EMPTY_SEARCH: OperatorMemberSearchView = { query: '', items: null, loading: false, error: '' };
const search = computed<OperatorMemberSearchView>(() => props.search ?? EMPTY_SEARCH);
const query = ref('');
/** 누른 [연결]의 회원 — 그 단추에만 도는 표시 */
const linkingId = ref('');
/** 한 글자만 넣었다 — 부르지 않고 안내만 */
const tooShort = computed(() => {
    const length = query.value.trim().length;
    return length > 0 && length < OPERATOR_MEMBER_SEARCH_MIN;
});

let searchTimer: ReturnType<typeof setTimeout> | null = null;
function clearSearchTimer(): void {
    if (searchTimer) clearTimeout(searchTimer);
    searchTimer = null;
}
/** 글자를 멈추고 0.3초 뒤에 올린다(Enter 는 바로) — 포인트 조정 창의 회원 찾기와 같은 관례 */
function emitSearch(now: boolean): void {
    clearSearchTimer();
    if (now) emit('search', query.value);
    else searchTimer = setTimeout(() => emit('search', query.value), 300);
}
watch(query, () => emitSearch(false));
/** 다른 운영자로 바뀌거나 연결되면 검색 칸을 비운다(결과는 페이지가 비운다) */
watch([() => props.operator.operatorId, () => !!props.account?.linked], () => {
    clearSearchTimer();
    query.value = '';
    linkingId.value = '';
});
onBeforeUnmount(clearSearchTimer);

function link(candidate: OperatorMemberCandidate): void {
    if (!candidate.eligible || props.busy) return;
    linkingId.value = candidate.userId;
    emit('link', candidate);
}

/** 회원 조회로 가는 링크는 회원 조회 권한이 있을 때만 */
const canReadMember = computed(() => can('member.read'));
/** 연결·해제 단추를 보일까 — 최고 관리자 대상이면 본인일 때만 */
const canEdit = computed(() => props.canManage && (!props.operator.isSuper || props.isSelf));
/** 읽기 전용 까닭 — 단추·검색이 있어야 할 자리(연결됨이면 해제, 연결 안 됨이면 검색·연결)에서 */
const readOnlyNote = computed(() => {
    const a = props.account;
    if (canEdit.value || !a) return '';
    return props.canManage ? t('operatorMember.superOnlySelf') : t('operatorMember.readOnly');
});

/** 힘 한 줄 — 이름 · 필요한 권한(권한 카탈로그의 이름) · 됨/안 됨 */
const powerRows = computed(() =>
    (props.account?.powers.items ?? []).map((power) => {
        const def = power.permission ? UtilPermissionCatalog.permissionOf(power.permission) : undefined;
        const permissionName = def ? t(def.nameKey) : (power.permission ?? '');
        return {
            key: power.key,
            label: t(`operatorMember.power.${power.key}`),
            hint: power.permission ? t('operatorMember.powerNeeds', { permission: permissionName }) : t('operatorMember.powerAlways'),
            granted: power.granted,
        };
    }),
);
</script>
