<template>
    <!-- 회원 상세 — 역할·활동 구획(읽기 전용). 보유 회원 역할을 유형(등급·회원 유형·게시판 단위·사용자 정의)별로 묶어 보이고, 아래에 활동 요약 숫자를 둔다. 역할 이름·아이디는 역할 관리의 회원 역할과 같다. 활동 요약(`activity`)은 글·댓글 수와 최근 7일 수가 실제 집계이고, 추천·인기글·접속일은 서버가 아직 집계하지 않아 0 대신 「준비 중」으로 보인다(0 은 "활동 없음"으로 오해된다). 탈퇴 회원은 서버가 비워 전용 문구 -->
    <div class="flex flex-col gap-5">
        <section :aria-labelledby="rolesId">
            <h3 :id="rolesId" class="mb-2 text-[13px] font-semibold">{{ t('member.detail.rolesTitle') }}</h3>
            <p v-if="!detail.roles.length" class="text-[13px] text-muted-color">{{ t('member.detail.noRoles') }}</p>
            <div v-else class="flex flex-col gap-3">
                <div v-for="group in groups" :key="group.type">
                    <h4 class="mb-1 flex items-center gap-1.5 text-xs text-muted-color">
                        {{ t(`member.detail.roleType.${group.type}`) }}
                        <span aria-hidden="true">·</span>
                        <span>{{ t(`member.detail.grant.${group.roles[0]!.grantMode}`) }}</span>
                    </h4>
                    <ul class="m-0 flex list-none flex-col gap-1.5 p-0">
                        <li v-for="role in group.roles" :key="role.roleId" class="flex min-w-0 flex-col gap-0.5 rounded-lg border border-(--line) px-3 py-2">
                            <div class="flex min-w-0 flex-wrap items-center gap-2">
                                <Tag :value="role.roleName" :icon="UtilMember.roleIcon(role.typeId)" severity="secondary" />
                                <code class="text-[11px] text-muted-color">{{ role.roleId }}</code>
                            </div>
                            <p v-if="role.boardNames.length" class="text-xs text-muted-color">{{ t('member.detail.boards', { names: role.boardNames.join(', ') }) }}</p>
                        </li>
                    </ul>
                </div>
            </div>
        </section>

        <section :aria-labelledby="activityId">
            <h3 :id="activityId" class="mb-2 text-[13px] font-semibold">{{ t('member.detail.activityTitle') }}</h3>
            <p v-if="!activity" class="m-0 flex items-start gap-2 rounded-lg border border-(--line) px-3 py-2.5 text-[13px] text-muted-color">
                <i :class="withdrawn ? 'pi pi-user-minus' : 'pi pi-clock'" class="mt-0.5" aria-hidden="true" />
                <span>{{ withdrawn ? t('member.detail.withdrawnActivity') : t('member.detail.notReady.activity') }}</span>
            </p>
            <template v-else>
                <dl class="m-0 grid grid-cols-2 gap-2 sm:grid-cols-3">
                    <div v-for="item in activity" :key="item.key" class="rounded-lg border border-(--line) px-3 py-2">
                        <dt class="text-xs text-muted-color">{{ item.label }}</dt>
                        <dd v-if="item.pending" class="m-0 mt-0.5 text-[13px] text-muted-color">{{ t('member.detail.pending') }}</dd>
                        <dd v-else class="m-0 mt-0.5 text-base font-semibold tabular-nums">{{ item.value.toLocaleString(locale) }}</dd>
                    </div>
                </dl>
                <p class="mt-2 text-xs text-muted-color">{{ t('member.detail.activityNote') }}</p>
            </template>
        </section>
    </div>
</template>

<script setup lang="ts">
import type { MemberDetail, MemberRoleDetail } from '~/composables/member/api/useMemberApi.types';
import UtilMember from '~/utils/util-member';

interface Props {
    detail: MemberDetail;
}

const props = defineProps<Props>();

const { t, locale } = useI18n();
const rolesId = useId();
const activityId = useId();

/** 유형별 묶음 — 역할이 없는 유형은 뺀다 */
const groups = computed(() => UtilMember.ROLE_TYPES.map((type) => ({ type, roles: props.detail.roles.filter((r: MemberRoleDetail) => r.typeId === type) })).filter((g) => g.roles.length > 0));

const withdrawn = computed(() => props.detail.status === 'WITHDRAWN');

/** 활동 요약 — 서버가 비우면(탈퇴 회원) null. 서버가 아직 집계하지 않는 칸(`UtilMember.activityPending`)은 값 대신 「준비 중」 */
const activity = computed(() => {
    const a = props.detail.activity;
    if (!a) return null;
    return UtilMember.ACTIVITY_KEYS.map((key) => ({ key, label: t(`member.detail.activity.${key}`), value: a[key], pending: UtilMember.activityPending(key) }));
});
</script>
