<template>
    <!--
        회원 상세 — 동의 이력 구획(읽기 전용). 가입 때 받는 필수 동의 3개(약관·개인정보처리방침·만 14세 — 시각과 버전), 선택 동의(마케팅·광고성 이메일·제3자 제공·정보 공개 — 동의 여부와 동의일),
        동의 변경·철회 이력 표(일시 · 항목 · 동의/철회 · 경로). 서버는 이용약관·만 14세 확인만 주고(개인정보처리방침·선택 동의·이력은 null/빈 목록) `consents` 가 null 이면 구획 전체를 안내로 대신한다. 이력 표는 community 에 없어 후보 계약(확정 전)이다. 광고성 이메일은 마케팅 동의의 하위 항목이다.
    -->
    <div v-if="detail.consents" class="flex flex-col gap-5">
        <section :aria-labelledby="requiredId">
            <h3 :id="requiredId" class="mb-1 text-[13px] font-semibold">{{ t('member.detail.consent.requiredTitle') }}</h3>
            <dl class="m-0 flex flex-col">
                <MemberDetailItem :label="t('member.detail.consent.item.TERMS')">
                    <span class="tabular-nums">{{ time.full(detail.consents.terms.agreedAt) }}</span>
                    <span class="ml-1.5 text-xs text-muted-color">{{ t('member.detail.consent.version', { version: detail.consents.terms.version }) }}</span>
                </MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.consent.item.PRIVACY')" :empty="!detail.consents.privacy">
                    <template v-if="detail.consents.privacy">
                        <span class="tabular-nums">{{ time.full(detail.consents.privacy.agreedAt) }}</span>
                        <span class="ml-1.5 text-xs text-muted-color">{{ t('member.detail.consent.version', { version: detail.consents.privacy.version }) }}</span>
                    </template>
                </MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.consent.item.AGE')">
                    <span class="tabular-nums">{{ time.full(detail.consents.ageConfirmedAt) }}</span>
                </MemberDetailItem>
            </dl>
        </section>

        <section :aria-labelledby="optionalId">
            <h3 :id="optionalId" class="mb-1 text-[13px] font-semibold">{{ t('member.detail.consent.optionalTitle') }}</h3>
            <p v-if="!optional.length" class="text-[13px] text-muted-color">{{ t('member.detail.consent.optionalNone') }}</p>
            <ul v-else class="m-0 flex list-none flex-col p-0">
                <li v-for="c in optional" :key="c.key" class="flex items-start justify-between gap-3 border-b border-(--line) py-2.5 last:border-b-0" :class="UtilMember.consentIsChild(c.key) ? 'pl-4' : ''">
                    <div class="min-w-0">
                        <p class="text-[13px]">
                            <i v-if="UtilMember.consentIsChild(c.key)" class="pi pi-arrow-right mr-1.5 text-[10px] text-muted-color" aria-hidden="true" />
                            {{ t(`member.detail.consent.item.${c.key}`) }}
                        </p>
                        <p class="text-xs text-muted-color">{{ t(`member.detail.consent.desc.${c.key}`) }}</p>
                    </div>
                    <div class="flex shrink-0 flex-col items-end gap-1">
                        <MemberDetailTag :value="c.agreed ? t('member.detail.consent.agreed') : t('member.detail.consent.notAgreed')" :icon="c.agreed ? 'pi pi-check' : 'pi pi-minus'" :severity="c.agreed ? 'success' : 'secondary'" />
                        <span v-if="c.agreedAt" class="text-xs text-muted-color tabular-nums">{{ time.date(c.agreedAt) }}</span>
                    </div>
                </li>
            </ul>
        </section>

        <section :aria-labelledby="historyId">
            <h3 :id="historyId" class="mb-1 flex flex-wrap items-center gap-2 text-[13px] font-semibold">
                {{ t('member.detail.consent.historyTitle') }}
                <MemberDetailTag :value="t('member.detail.consent.candidate')" icon="pi pi-info-circle" severity="secondary" />
            </h3>
            <p v-if="!detail.consents.history.length" class="text-[13px] text-muted-color">{{ t('member.detail.consent.historyNone') }}</p>
            <div v-else class="overflow-x-auto">
                <table class="w-full border-collapse text-left text-[13px]">
                    <caption class="sr-only">{{ t('member.detail.consent.historyTitle') }}</caption>
                    <thead>
                        <tr class="text-xs text-muted-color">
                            <th scope="col" class="border-b border-(--line) py-2 pr-3 font-normal">{{ t('member.detail.consent.colAt') }}</th>
                            <th scope="col" class="border-b border-(--line) py-2 pr-3 font-normal">{{ t('member.detail.consent.colItem') }}</th>
                            <th scope="col" class="border-b border-(--line) py-2 pr-3 font-normal">{{ t('member.detail.consent.colAction') }}</th>
                            <th scope="col" class="border-b border-(--line) py-2 font-normal">{{ t('member.detail.consent.colChannel') }}</th>
                        </tr>
                    </thead>
                    <tbody>
                        <tr v-for="h in detail.consents.history" :key="h.changeId">
                            <td class="whitespace-nowrap border-b border-(--line) py-2 pr-3 tabular-nums">{{ time.full(h.changedAt) }}</td>
                            <td class="border-b border-(--line) py-2 pr-3">{{ t(`member.detail.consent.item.${h.item}`) }}</td>
                            <td class="border-b border-(--line) py-2 pr-3">
                                <MemberDetailTag :value="t(`member.detail.consent.action.${h.action}`)" :icon="UtilMember.consentActionIcon(h.action)" :severity="UtilMember.consentActionSeverity(h.action)" />
                            </td>
                            <td class="border-b border-(--line) py-2">{{ t(`member.detail.consent.channel.${h.channel}`) }}</td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </section>
    </div>
    <EmptyState v-else icon="pi pi-check-square" :title="t('member.detail.notReady.title')" :description="t('member.detail.notReady.consent')" />
</template>

<script setup lang="ts">
import type { MemberDetail } from '~/composables/member/api/useMemberApi.types';
import UtilMember from '~/utils/util-member';

interface Props {
    detail: MemberDetail;
}

const props = defineProps<Props>();

const { t } = useI18n();
const time = useDisplayTime();
const requiredId = useId();
const optionalId = useId();
const historyId = useId();

/** 선택 동의를 정해진 차례로 */
const optional = computed(() => UtilMember.OPTIONAL_CONSENTS.flatMap((key) => props.detail.consents?.optional.find((c) => c.key === key) ?? []));
</script>
