<template>
    <!--
        회원 상세 — 개인 설정 구획(회원이 직접 정한 값을 운영자가 읽기만 한다).
        표시 형식은 공유 레이어 `@roboyam/display-time` 의 표기(시간대·시간 형식·날짜 형식 이름과 견본)를 그대로 쓴다. 알림은 종류별 켜짐/꺼짐(계정 알림은 끌 수 없다)과 앱 푸시 구독,
        그누보드7 의 게시글 완료·답변 알림은 roboyam 에 대응 기능이 없어 「준비 중」. 차단·메모·알림함은 건수만 보이고 내용은 보이지 않는다.
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

        <section :aria-labelledby="notificationId">
            <h3 :id="notificationId" class="mb-1 text-[13px] font-semibold">{{ t('member.detail.settings.notificationTitle') }}</h3>
            <ul class="m-0 flex list-none flex-col p-0">
                <li v-for="n in notifications" :key="n.kind" class="flex items-center justify-between gap-3 border-b border-(--line) py-2.5 text-[13px] last:border-b-0">
                    <span class="min-w-0">{{ t(`member.detail.settings.notification.${n.kind}`) }}</span>
                    <MemberDetailTag v-if="n.pending" :value="t('member.detail.pending')" icon="pi pi-clock" severity="secondary" />
                    <MemberDetailTag v-else :value="n.enabled ? t('common.on') : t('common.off')" :icon="n.enabled ? 'pi pi-bell' : 'pi pi-bell-slash'" :severity="n.enabled ? 'success' : 'secondary'" />
                </li>
                <li class="flex items-center justify-between gap-3 py-2.5 text-[13px]">
                    <span class="min-w-0">{{ t('member.detail.settings.push') }}</span>
                    <MemberDetailTag :value="detail.settings.pushSubscribed ? t('member.detail.settings.pushOn') : t('member.detail.settings.pushOff')" :icon="detail.settings.pushSubscribed ? 'pi pi-mobile' : 'pi pi-minus-circle'" :severity="detail.settings.pushSubscribed ? 'success' : 'secondary'" />
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
                    <dd class="m-0 mt-0.5 text-base font-semibold tabular-nums">{{ item.value.toLocaleString(locale) }}</dd>
                </div>
            </dl>
            <p class="mt-2 text-xs text-muted-color">{{ t('member.detail.settings.countsNote') }}</p>
        </section>
    </div>
</template>

<script setup lang="ts">
import type { MemberDetail } from '~/composables/member/api/useMemberApi.types';
import UtilMember from '~/utils/util-member';
import UtilMemberMessages from '~/utils/util-member-messages';

interface Props {
    detail: MemberDetail;
}

const props = defineProps<Props>();

const { t, locale } = useI18n();
const displayId = useId();
const notificationId = useId();
const countsId = useId();
/** 시차·견본을 잴 순간 — 구획을 그릴 때 한 번 */
const now = Date.now();

/** 알림 종류 차례대로 — 대응 기능이 없는 종류는 `pending` */
const notifications = computed(() =>
    UtilMember.NOTIFICATION_KINDS.map((kind) => ({
        kind,
        enabled: props.detail.settings?.notifications.find((n) => n.kind === kind)?.enabled ?? null,
        pending: UtilMember.notificationPending(kind),
    })),
);

const counts = computed(() => {
    const s = props.detail.settings;
    return [
        { key: 'blockedUser', label: t('member.detail.settings.blockedUsers'), value: s?.blockedUserCount ?? 0 },
        { key: 'blockedWord', label: t('member.detail.settings.blockedWords'), value: s?.blockedWordCount ?? 0 },
        { key: 'memo', label: t('member.detail.settings.memos'), value: s?.memoCount ?? 0 },
    ];
});
</script>
