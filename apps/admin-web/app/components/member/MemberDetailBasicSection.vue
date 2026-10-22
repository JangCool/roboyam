<template>
    <!--
        회원 상세 — 기본 정보 구획(읽기 전용). 이메일은 서버가 준 대로 그린다: `member.pii` 가 없으면 가려진 값이고 그렇다고 스크린리더에도 알린다.
        소셜 연동·성인 확인(출생연도는 보이지 않는다)·쪽지 수신·휴면 예정은 정상 회원에게만(서버가 아직 모르는 성인 확인·쪽지 수신은 `-`), 탈퇴 회원은 줄어든 표시(탈퇴 시각이 보이고 이메일·소개 등이 없다).
        아래에 닉네임 변경 이력 — `member.pii` 가 없으면 값 대신 잠금 문구(이메일 가림과 같은 결). 한 줄 소개는 「프로필·메모」 구획에서 한 곳에만 보인다.
    -->
    <div class="flex flex-col gap-5">
        <p v-if="detail.status === 'WITHDRAWN'" class="m-0 flex items-start gap-2 rounded-lg border border-(--line) px-3 py-2.5 text-[13px] text-muted-color">
            <i class="pi pi-sign-out mt-0.5" aria-hidden="true" />
            <span>{{ t('member.detail.withdrawnNotice') }}</span>
        </p>

        <dl class="m-0 flex flex-col">
            <div v-for="field in fields" :key="field.key" class="grid grid-cols-[7.5rem_minmax(0,1fr)] gap-x-3 border-b border-(--line) py-2.5 last:border-b-0">
                <dt class="text-[13px] text-muted-color">{{ field.label }}</dt>
                <dd class="m-0 min-w-0 text-[13px] break-words">
                    <code v-if="field.key === 'memberNo'" class="text-[12px] tabular-nums">{{ detail.memberNo }}</code>
                    <template v-else-if="field.key === 'email'">
                        <template v-if="detail.email">
                            <span>{{ detail.email }}</span>
                            <span v-if="detail.emailMasked" class="sr-only"> {{ t('member.detail.maskedSuffix') }}</span>
                            <span v-if="detail.emailMasked" class="ml-1.5 inline-flex items-center gap-1 text-[11px] text-muted-color" aria-hidden="true">
                                <i class="pi pi-eye-slash" />
                                {{ t('member.detail.maskedSuffix') }}
                            </span>
                        </template>
                        <span v-else class="text-muted-color">{{ t('member.none') }}</span>
                    </template>
                    <template v-else-if="field.key === 'nickname'">{{ detail.nickname }}</template>
                    <GradeBadge v-else-if="field.key === 'grade'" v-bind="gradeState.badgeOf(detail.grade)" />
                    <Tag v-else-if="field.key === 'memberType'" :value="t(`member.type.${detail.memberType}`)" :icon="UtilMember.typeIcon(detail.memberType)" :severity="detail.memberType === 'BUSINESS' ? 'contrast' : 'secondary'" />
                    <div v-else-if="field.key === 'status'" class="flex flex-col gap-1">
                        <span>
                            <Tag :value="t(`member.status.${detail.status}`)" :icon="UtilMember.statusIcon(detail.status)" :severity="UtilMember.statusSeverity(detail.status)" />
                        </span>
                        <span v-if="detail.statusChangedAt" class="text-xs text-muted-color">{{ t('member.detail.statusChangedOn', { at: time.full(detail.statusChangedAt) }) }}</span>
                        <span v-if="detail.statusReason" class="text-xs text-muted-color">{{ t('member.detail.statusReason', { reason: detail.statusReason }) }}</span>
                    </div>
                    <Tag v-else-if="field.key === 'authMethod'" :value="t(`member.authMethod.${detail.authMethod}`)" :icon="UtilMember.authIcon(detail.authMethod)" severity="secondary" />
                    <div v-else-if="field.key === 'social'" class="flex flex-wrap gap-1.5">
                        <MemberDetailTag
                            v-for="s in socials"
                            :key="s.provider"
                            :value="`${t(`member.authMethod.${s.provider}`)} · ${s.linked ? t('member.detail.linked') : t('member.detail.notLinked')}`"
                            :icon="UtilMember.authIcon(s.provider)"
                            :severity="s.linked ? 'success' : 'secondary'"
                        />
                    </div>
                    <span v-else-if="field.key === 'joinedAt'" class="tabular-nums">{{ time.full(detail.joinedAt) }}</span>
                    <span v-else-if="field.key === 'withdrawnAt'" class="tabular-nums">{{ detail.withdrawnAt ? time.full(detail.withdrawnAt) : t('member.none') }}</span>
                    <template v-else-if="field.key === 'emailVerifiedAt'">
                        <span v-if="detail.emailVerifiedAt" class="tabular-nums">{{ time.full(detail.emailVerifiedAt) }}</span>
                        <span v-else class="text-muted-color">{{ t('member.detail.notVerified') }}</span>
                    </template>
                    <span v-else-if="field.key === 'adult' && detail.adultConfirmed === null" class="text-muted-color">{{ t('member.detail.empty') }}</span>
                    <MemberDetailTag v-else-if="field.key === 'adult'" :value="detail.adultConfirmed ? t('member.detail.adultYes') : t('member.detail.adultNo')" :icon="detail.adultConfirmed ? 'pi pi-check-circle' : 'pi pi-minus-circle'" :severity="detail.adultConfirmed ? 'success' : 'secondary'" />
                    <span v-else-if="field.key === 'acceptDm' && detail.acceptDm === null" class="text-muted-color">{{ t('member.detail.empty') }}</span>
                    <MemberDetailTag v-else-if="field.key === 'acceptDm'" :value="detail.acceptDm ? t('member.detail.dmAccept') : t('member.detail.dmRefuse')" :icon="detail.acceptDm ? 'pi pi-envelope' : 'pi pi-ban'" :severity="detail.acceptDm ? 'success' : 'secondary'" />
                    <template v-else-if="field.key === 'lastLoginAt'">
                        <span v-if="detail.lastLoginAt" class="tabular-nums">{{ time.full(detail.lastLoginAt) }}</span>
                        <span v-else class="text-muted-color">{{ t('member.neverLoggedIn') }}</span>
                    </template>
                    <template v-else-if="field.key === 'dormantAt'">
                        <span v-if="detail.dormantAt" class="tabular-nums">{{ time.full(detail.dormantAt) }}</span>
                        <span v-else class="text-muted-color">{{ t('member.none') }}</span>
                    </template>
                    <div v-else-if="field.key === 'dormancy' && detail.dormancy" class="flex flex-col gap-1">
                        <span>
                            <MemberDetailTag :value="t('member.detail.dormancyPending')" icon="pi pi-moon" severity="info" />
                        </span>
                        <span class="text-xs text-muted-color tabular-nums">{{ t('member.detail.dormancyNoticedAt', { at: time.full(detail.dormancy.noticedAt) }) }}</span>
                        <span class="text-xs text-muted-color tabular-nums">{{ t('member.detail.dormancyEraseOn', { at: time.date(detail.dormancy.eraseOn) }) }}</span>
                    </div>
                    <span v-else-if="field.key === 'point'" class="text-muted-color">{{ t('member.detail.pending') }}</span>
                </dd>
            </div>
        </dl>

        <!-- 닉네임 변경 이력 — 탈퇴한 회원에게는 없다 -->
        <section v-if="detail.status !== 'WITHDRAWN'" :aria-labelledby="nicknameId">
            <h3 :id="nicknameId" class="mb-2 text-[13px] font-semibold">{{ t('member.detail.nicknameTitle') }}</h3>
            <p v-if="detail.nicknameHistory === null" class="m-0 flex items-start gap-2 rounded-lg border border-(--line) px-3 py-2.5 text-[13px] text-muted-color">
                <i class="pi pi-lock mt-0.5" aria-hidden="true" />
                <span>{{ t('member.detail.nicknameLocked') }}</span>
            </p>
            <p v-else-if="!detail.nicknameHistory.length" class="text-[13px] text-muted-color">{{ t('member.detail.historyEmpty') }}</p>
            <ul v-else class="m-0 flex list-none flex-col gap-1.5 p-0">
                <li v-for="(change, index) in detail.nicknameHistory" :key="`${change.changedAt}-${index}`" class="rounded-lg border border-(--line) px-3 py-2 text-[13px]">
                    <span class="text-muted-color">{{ change.oldNickname }}</span>
                    <span class="mx-1.5" aria-hidden="true">→</span>
                    <span class="sr-only">{{ t('member.detail.nicknameTo') }}</span>
                    <b class="font-semibold">{{ change.newNickname }}</b>
                    <small class="ml-2 text-xs text-muted-color tabular-nums">{{ time.full(change.changedAt) }}</small>
                </li>
            </ul>
        </section>
    </div>
</template>

<script setup lang="ts">
import type { MemberDetail } from '~/composables/member/api/useMemberApi.types';
import UtilMember from '~/utils/util-member';

interface Props {
    detail: MemberDetail;
}

const props = defineProps<Props>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const nicknameId = useId();
// 등급 뱃지 — 등급 설정의 이름·색·아이콘(앱 전체가 공유하는 등급 목록)
const gradeState = useGradeAppearance();

type FieldKey = 'memberNo' | 'email' | 'nickname' | 'grade' | 'memberType' | 'status' | 'authMethod' | 'social' | 'joinedAt' | 'withdrawnAt' | 'emailVerifiedAt' | 'adult' | 'acceptDm' | 'lastLoginAt' | 'dormantAt' | 'dormancy' | 'point';
const KEYS: readonly FieldKey[] = ['memberNo', 'email', 'nickname', 'grade', 'memberType', 'status', 'authMethod', 'social', 'joinedAt', 'withdrawnAt', 'emailVerifiedAt', 'adult', 'acceptDm', 'lastLoginAt', 'dormantAt', 'dormancy', 'point'];
/** 표에 없는 칸의 이름 — 목록 표 칸 이름(`member.col.*`)에 없는 것은 상세 전용 키 */
const LABELS: Partial<Record<FieldKey, string>> = {
    social: 'member.detail.social',
    withdrawnAt: 'member.detail.withdrawnAt',
    adult: 'member.detail.adult',
    acceptDm: 'member.detail.acceptDm',
    dormancy: 'member.detail.dormancy',
};

/** 이 회원에게 보일 칸 — 탈퇴한 회원은 줄어든 표시(탈퇴 시각이 보이고 소셜·성인·쪽지·휴면 예정은 없다), 휴면 예정은 있을 때만 */
const fields = computed(() => {
    const withdrawn = props.detail.status === 'WITHDRAWN';
    return KEYS.filter((key) => {
        if (key === 'withdrawnAt') return withdrawn;
        if (key === 'social' || key === 'adult' || key === 'acceptDm') return !withdrawn;
        if (key === 'dormancy') return props.detail.dormancy !== null;
        return true;
    }).map((key) => ({ key, label: t(LABELS[key] ?? `member.col.${key}`) }));
});

const socials = computed(() => UtilMember.SOCIAL_PROVIDERS.map((provider) => ({ provider, linked: props.detail.social.some((s) => s.provider === provider && s.linked) })));
</script>
