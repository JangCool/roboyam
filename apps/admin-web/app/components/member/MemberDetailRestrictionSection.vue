<template>
    <!--
        회원 상세 — 기능 제한 구획(커머스 0-4, commerce.md 12-3). 계정 전체가 아니라 기능 하나(포인트 동결·공동구매 참여 제한·충전 제한)를 막는 제재다 — 계정 정지(기본 정보의 상태)와 따로 걸린다.
        위: 한 줄 설명 + [제한 걸기](`sanction.manage` 없으면 단추 없음, 탈퇴 회원은 없음 — 서버도 409 `MEMBER_WITHDRAWN`). 아래: 진행 중 → 지난 제한(기간 끝·해제됨) 차례의 목록.
        한 줄: 종류(아이콘+글자) · 상태 · 출처(자동 규칙 / 운영자 이름) · 기간(시작 ~ 끝 또는 무기한) · 공개 사유(「회원에게 보임」) · 운영 메모(「운영자만」) · 해제한 사람·시각·사유. 진행 중이면 [해제](`sanction.manage`).
        서버 호출은 하지 않는다 — 부모가 받아 내려 주고(`view`), 걸기·해제는 이벤트로 올려 부모가 대화상자를 띄운다.
    -->
    <div class="flex flex-col gap-4">
        <div class="flex flex-wrap items-start gap-3">
            <p class="m-0 min-w-0 flex-1 text-xs leading-relaxed text-muted-color">{{ t('restriction.intro') }}</p>
            <Button v-if="canManage && !withdrawn" type="button" :label="t('restriction.create')" icon="pi pi-ban" size="small" severity="danger" variant="outlined" :disabled="!view?.items" @click="emit('restrict')" />
        </div>

        <ErrorState v-if="view?.error" :title="t('restriction.loadFailed')" :description="view.error.message" retry :retrying="view.loading" @retry="emit('retry')" />

        <div v-else-if="!view?.items" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 2" :key="n" height="4.5rem" />
        </div>

        <p v-else-if="!view.items.length" class="m-0 text-[13px] text-muted-color">{{ t('restriction.empty') }}</p>

        <template v-else>
            <section v-for="group in groups" :key="group.key" :aria-labelledby="`${uid}-${group.key}`">
                <h3 :id="`${uid}-${group.key}`" class="mb-2 text-[13px] font-semibold">{{ group.title }} <span class="font-normal text-muted-color tabular-nums">{{ group.items.length }}</span></h3>
                <ul class="m-0 flex list-none flex-col gap-2 p-0">
                    <li v-for="r in group.items" :key="r.id" class="flex flex-col gap-2 rounded-lg border px-3 py-2.5" :class="r.status === 'ACTIVE' ? 'border-red-300 dark:border-red-900' : 'border-(--line)'" :data-restriction-status="r.status">
                        <div class="flex flex-wrap items-center gap-1.5">
                            <MemberDetailTag :value="t(`restriction.type.${r.type}`)" :icon="UtilMemberRestriction.typeIcon(r.type)" :severity="UtilMemberRestriction.typeSeverity(r.type)" />
                            <MemberDetailTag :value="t(`restriction.status.${r.status}`)" :icon="UtilMemberRestriction.statusIcon(r.status)" :severity="UtilMemberRestriction.statusSeverity(r.status)" />
                            <span class="text-xs text-muted-color">{{ sourceText(r) }}</span>
                            <Button v-if="r.status === 'ACTIVE' && canManage" type="button" class="ml-auto" :label="t('restriction.lift')" icon="pi pi-unlock" size="small" severity="secondary" variant="outlined" @click="emit('lift', r)" />
                        </div>

                        <dl class="m-0 flex flex-col gap-1 text-[13px]">
                            <div class="flex flex-wrap gap-x-2">
                                <dt class="text-muted-color">{{ t('restriction.period') }}</dt>
                                <dd class="m-0 tabular-nums">{{ periodText(r) }}</dd>
                            </div>
                            <div class="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                                <dt class="inline-flex items-center gap-1 text-muted-color">
                                    {{ t('restriction.reason') }}
                                    <span class="inline-flex items-center gap-0.5 text-[11px]"><i class="pi pi-eye text-[10px]" aria-hidden="true" />{{ t('restriction.reasonPublic') }}</span>
                                </dt>
                                <dd class="m-0 basis-full break-words">{{ r.reason }}</dd>
                            </div>
                            <div v-if="r.adminMemo" class="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                                <dt class="inline-flex items-center gap-1 text-muted-color">
                                    {{ t('restriction.memo') }}
                                    <span class="inline-flex items-center gap-0.5 text-[11px]"><i class="pi pi-lock text-[10px]" aria-hidden="true" />{{ t('restriction.memoPrivate') }}</span>
                                </dt>
                                <dd class="m-0 basis-full whitespace-pre-line break-words text-muted-color">{{ r.adminMemo }}</dd>
                            </div>
                            <div v-if="r.status === 'LIFTED'" class="mt-1 flex flex-col gap-0.5 rounded-md bg-surface-100 px-2.5 py-2 dark:bg-surface-800">
                                <dt class="text-xs text-muted-color">{{ t('restriction.liftedTitle') }}</dt>
                                <dd class="m-0 text-xs tabular-nums">{{ t('restriction.liftedBy', { name: r.liftedBy?.name ?? t('member.detail.empty'), at: time.full(r.liftedAt) }) }}</dd>
                                <dd v-if="r.liftReason" class="m-0 break-words text-xs">{{ t('restriction.liftReason', { reason: r.liftReason }) }}</dd>
                            </div>
                        </dl>
                    </li>
                </ul>
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { MemberRestriction } from '~/composables/member/api/useMemberRestrictionApi.types';
import type { MemberRestrictionsView } from '~/components/member/MemberDetailContent.types';
import UtilMemberRestriction from '~/utils/util-member-restriction';

interface Props {
    view: MemberRestrictionsView | null;
    /** 탈퇴한 회원 — 목록은 보이고 [제한 걸기]는 없다(서버도 409 `MEMBER_WITHDRAWN`) */
    withdrawn?: boolean;
}

const props = withDefaults(defineProps<Props>(), { withdrawn: false });
const emit = defineEmits<{ restrict: []; lift: [restriction: MemberRestriction]; retry: [] }>();

const { t, te } = useI18n();
const time = useDisplayTime();
const uid = useId();
const { can } = useAdminPermission();

/** 걸기·해제 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('sanction.manage'));

const groups = computed(() => {
    const items = props.view?.items ?? [];
    const active = items.filter((r) => r.status === 'ACTIVE');
    const past = items.filter((r) => r.status !== 'ACTIVE');
    return [
        { key: 'active', title: t('restriction.groupActive'), items: active },
        { key: 'past', title: t('restriction.groupPast'), items: past },
    ].filter((g) => g.items.length > 0);
});

function sourceText(r: MemberRestriction): string {
    // 자동 규칙 코드는 서버 문자열 — 화면이 모르는 새 코드면 코드 그대로 보인다
    const rule = r.autoRule ? (te(`restriction.autoRule.${r.autoRule}`) ? t(`restriction.autoRule.${r.autoRule}`) : r.autoRule) : '';
    if (r.source === 'AUTO') return t('restriction.sourceAuto', { rule });
    return t('restriction.sourceOperator', { name: r.createdBy?.name ?? t('member.detail.empty') });
}

function periodText(r: MemberRestriction): string {
    return r.until ? t('restriction.periodRange', { from: time.value.full(r.startsAt), to: time.value.full(r.until) }) : t('restriction.periodForever', { from: time.value.full(r.startsAt) });
}
</script>
