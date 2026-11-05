<template>
    <!--
        운영자 상세 서랍의 「사이트 계정」 탭 — 이 운영자가 사이트(user-web)에서 쓰는 회원 계정 하나를 잇고 끊는다(`docs/features/operator-member-account-contract.md`).
        ① 위쪽 짧은 설명 ② 연결됨(회원 카드 + 상태 딱지 + 멈춘 까닭 + [연결 해제]) / 연결 안 됨(같은 이메일로 가입한 회원 후보 카드 + [연결] 또는 이을 수 없는 까닭) / 후보 없음(빈 상태)
        ③ 힘 목록 — 연결 여부와 상관없이 보인다(연결 전이면 「연결하면 이렇게 쓸 수 있어요」). 게시판 관리 힘은 이 운영자의 관리자 권한 거울이라 됨/안 됨과 필요한 권한 이름을 함께 보인다.
        값은 페이지가 받아 내려 주고(`account`), 연결·해제는 `link`·`unlink` 로 올린다 — 확인창도 페이지가 띄운다. 이 탭은 서랍 푸터를 쓰지 않는다.
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

            <!-- 연결 안 됨 — 같은 이메일로 가입한 회원 -->
            <div v-else-if="account.candidate" class="flex flex-col gap-3 rounded-lg border border-(--line) px-3 py-3">
                <p class="m-0 text-xs text-muted-color">{{ t('operatorMember.candidateTitle', { email: operator.email }) }}</p>
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
                    <Button type="button" :label="t('operatorMember.link')" :aria-label="t('operatorMember.linkLabel', { nickname: account.candidate.nickname })" icon="pi pi-link" size="small" :loading="busy" @click="emit('link', account.candidate.userId)" />
                </div>
            </div>

            <!-- 후보 없음 -->
            <EmptyState v-else icon="pi pi-user" :title="t('operatorMember.candidateNone')" :description="t('operatorMember.candidateNoneHint')" />

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
import type { OperatorMemberAccountView } from '~/composables/operator/api/useOperatorMemberAccountApi.types';
import UtilPermissionCatalog from '~/utils/util-permission-catalog';

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
}

const props = withDefaults(defineProps<Props>(), {
    account: null,
    loading: false,
    error: '',
    canManage: false,
    isSelf: false,
    busy: false,
});
const emit = defineEmits<{
    /** [연결] — 확인은 페이지가 묻는다 */
    link: [userId: string];
    /** [연결 해제] — 확인은 페이지가 묻는다 */
    unlink: [];
    retry: [];
}>();

const { t } = useI18n();
const time = useDisplayTime();
const { can } = useAdminPermission();
// 등급 뱃지 — 등급 설정의 이름·색·아이콘(목록은 페이지가 `ensureLoaded` 로 받는다. 못 받으면 중립 뱃지 + 키)
const gradeState = useGradeAppearance();
const titleId = useId();
const powersId = useId();

/** 회원 조회로 가는 링크는 회원 조회 권한이 있을 때만 */
const canReadMember = computed(() => can('member.read'));
/** 연결·해제 단추를 보일까 — 최고 관리자 대상이면 본인일 때만 */
const canEdit = computed(() => props.canManage && (!props.operator.isSuper || props.isSelf));
/** 읽기 전용 까닭 — 단추가 있어야 할 자리(이을 수 있는 후보·연결됨)에서만 */
const readOnlyNote = computed(() => {
    const a = props.account;
    if (canEdit.value || !a || (!a.linked && !a.candidate?.eligible)) return '';
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
