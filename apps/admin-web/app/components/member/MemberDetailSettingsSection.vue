<template>
    <!--
        회원 상세 — 개인 설정 구획(회원이 직접 정한 값을 운영자가 읽기만 한다).
        표시 형식은 공유 레이어 `@roboyam/display-time` 의 표기(시간대·시간 형식·날짜 형식 이름과 견본)를 그대로 쓴다. 보기 설정(야간 모드·글자 크기·댓글 정렬)은 회원이 정한 적이 없으면 「기본값」.
        알림은 서버 회원 이벤트 카탈로그 차례로 서버가 준 이름 + 앱 알림·이메일 켜짐/꺼짐(회원이 끌 수 없는 것은 「끌 수 없음」)과 앱 푸시 구독. 차단·메모·알림함은 건수만 보이고 내용은 보이지 않는다.
        서버가 null 로 준 칸은 `-`.
    -->
    <div v-if="detail.settings" class="flex flex-col gap-5">
        <section :aria-labelledby="displayId">
            <h3 :id="displayId" class="mb-1 flex flex-wrap items-center gap-2 text-[13px] font-semibold">
                {{ t('member.detail.settings.displayTitle') }}
                <MemberDetailTag :value="detail.settings.displayOwn ? t('member.detail.settings.displayOwn') : t('member.detail.settings.displayDefault')" :icon="detail.settings.displayOwn ? 'pi pi-user' : 'pi pi-globe'" severity="secondary" />
            </h3>
            <dl class="m-0 flex flex-col">
                <MemberDetailItem :label="t('member.detail.contact.language')">{{ UtilMemberMessages.languageName(detail.settings.display) }}</MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.contact.timezone')">{{ UtilMemberMessages.zoneText(t, locale, detail.settings.display, now) }}</MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.settings.timeFormat')">{{ UtilMemberMessages.timeFormatText(t, locale, detail.settings.display, now) }}</MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.settings.dateFormat')">{{ UtilMemberMessages.dateFormatText(t, locale, detail.settings.display, now) }}</MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.settings.preview')">
                    <span class="tabular-nums">{{ UtilMemberMessages.displayPreview(detail.settings.display, now) }}</span>
                </MemberDetailItem>
            </dl>
        </section>

        <section :aria-labelledby="viewId">
            <h3 :id="viewId" class="mb-1 flex flex-wrap items-center gap-2 text-[13px] font-semibold">
                {{ t('member.detail.settings.viewTitle') }}
                <MemberDetailTag v-if="view" :value="view.own ? t('member.detail.settings.viewOwn') : t('member.detail.settings.viewDefault')" :icon="view.own ? 'pi pi-user' : 'pi pi-globe'" severity="secondary" />
            </h3>
            <dl class="m-0 flex flex-col">
                <MemberDetailItem :label="t('member.detail.settings.theme')" :empty="!view?.theme">{{ view?.theme ? t(`member.detail.settings.themeValue.${view.theme}`) : '' }}</MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.settings.textScale')" :empty="view?.textScale == null">
                    <span class="tabular-nums">{{ t('member.detail.settings.textScaleValue', { scale: view?.textScale ?? '' }) }}</span>
                </MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.settings.commentOrder')" :empty="!view?.commentOrder">{{ view?.commentOrder ? t(`member.detail.settings.commentOrderValue.${view.commentOrder}`) : '' }}</MemberDetailItem>
            </dl>
        </section>

        <section :aria-labelledby="notificationId">
            <h3 :id="notificationId" class="mb-1 text-[13px] font-semibold">{{ t('member.detail.settings.notificationTitle') }}</h3>
            <ul class="m-0 flex list-none flex-col p-0">
                <li v-if="!detail.settings.notifications.length" class="border-b border-(--line) py-2.5 text-[13px] text-muted-color">{{ t('member.detail.settings.notificationEmpty') }}</li>
                <li v-for="n in detail.settings.notifications" :key="n.code" class="flex flex-wrap items-center justify-between gap-x-3 gap-y-1.5 border-b border-(--line) py-2.5 text-[13px]">
                    <span class="flex min-w-0 flex-wrap items-center gap-1.5">
                        <span>{{ n.name }}</span>
                        <MemberDetailTag v-if="n.locked" :value="t('member.detail.settings.notificationLocked')" icon="pi pi-lock" severity="secondary" />
                    </span>
                    <span class="flex shrink-0 flex-wrap items-center gap-1.5">
                        <MemberDetailTag v-for="ch in channelsOf(n)" :key="ch.key" :value="ch.text" :icon="ch.icon" :severity="ch.severity" />
                    </span>
                </li>
                <li class="flex items-center justify-between gap-3 py-2.5 text-[13px]">
                    <span class="min-w-0">{{ t('member.detail.settings.push') }}</span>
                    <span v-if="detail.settings.pushSubscribed === null" class="text-muted-color">{{ t('member.detail.empty') }}</span>
                    <MemberDetailTag v-else :value="detail.settings.pushSubscribed ? t('member.detail.settings.pushOn') : t('member.detail.settings.pushOff')" :icon="detail.settings.pushSubscribed ? 'pi pi-mobile' : 'pi pi-minus-circle'" :severity="detail.settings.pushSubscribed ? 'success' : 'secondary'" />
                </li>
            </ul>
            <p class="mt-1 flex items-start gap-1.5 text-xs text-muted-color">
                <i class="pi pi-info-circle mt-0.5" aria-hidden="true" />
                <span>{{ t('member.detail.settings.accountNoticeLocked') }}</span>
            </p>
        </section>

        <section :aria-labelledby="countsId">
            <h3 :id="countsId" class="mb-2 text-[13px] font-semibold">{{ t('member.detail.settings.countsTitle') }}</h3>
            <dl class="m-0 grid grid-cols-3 gap-2">
                <div v-for="item in counts" :key="item.key" class="rounded-lg border border-(--line) px-3 py-2">
                    <dt class="text-xs text-muted-color">{{ item.label }}</dt>
                    <dd class="m-0 mt-0.5 text-base font-semibold tabular-nums" :class="item.value === null ? 'text-muted-color' : ''">{{ item.value === null ? t('member.detail.empty') : item.value.toLocaleString(locale) }}</dd>
                </div>
            </dl>
            <p class="mt-2 text-xs text-muted-color">{{ t('member.detail.settings.countsNote') }}</p>
        </section>
    </div>
    <EmptyState v-else icon="pi pi-cog" :title="t('member.detail.notReady.title')" :description="t('member.detail.notReady.settings')" />
</template>

<script setup lang="ts">
import type { MemberDetail, MemberNotificationSetting } from '~/composables/member/api/useMemberApi.types';
import type { TagSeverity } from '~/utils/util-member';
import UtilMemberMessages from '~/utils/util-member-messages';

interface Props {
    detail: MemberDetail;
}

const props = defineProps<Props>();

const { t, locale } = useI18n();
const displayId = useId();
const viewId = useId();
const notificationId = useId();
const countsId = useId();
/** 시차·견본을 잴 순간 — 구획을 그릴 때 한 번 */
const now = Date.now();

const view = computed(() => props.detail.settings?.view ?? null);

/** 알림 한 줄의 받기 뱃지(앱 알림·이메일) — 값을 모르면(null) `-` */
function channelsOf(n: MemberNotificationSetting): { key: string; text: string; icon: string | undefined; severity: TagSeverity }[] {
    return [
        { key: 'inApp', label: t('member.detail.settings.notificationInApp'), on: n.inApp },
        { key: 'email', label: t('member.detail.settings.notificationEmail'), on: n.email },
    ].map((ch) => ({
        key: ch.key,
        text: `${ch.label} · ${ch.on === null ? t('member.detail.empty') : ch.on ? t('common.on') : t('common.off')}`,
        icon: ch.on === null ? undefined : ch.on ? 'pi pi-bell' : 'pi pi-bell-slash',
        severity: ch.on ? 'success' : 'secondary',
    }));
}

const counts = computed(() => {
    const s = props.detail.settings;
    return [
        { key: 'blockedUser', label: t('member.detail.settings.blockedUsers'), value: s?.blockedUserCount ?? null },
        { key: 'blockedWord', label: t('member.detail.settings.blockedWords'), value: s?.blockedWordCount ?? null },
        { key: 'memo', label: t('member.detail.settings.memos'), value: s?.memoCount ?? null },
    ];
});
</script>
