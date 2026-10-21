<template>
    <!--
        회원 상세 — 연락처·위치 구획(읽기 전용, 그누보드7 연락처정보·위치정보에 대응하는 후보). 이메일은 기본 정보 구획과 같은 값을 다시 입력받지 않고 그대로 보인다.
        휴대폰·전화·주소는 `member.pii` 가 없으면 서버가 가려서 주고(`piiMasked`) 이메일과 같은 방식으로 가려졌다는 표시를 한다. 언어·시간대는 개인 설정의 표시 형식과 같은 값이다. 빈 값은 `-`.
    -->
    <div v-if="detail.contact && detail.settings" class="flex flex-col gap-5">
        <section :aria-labelledby="contactId">
            <h3 :id="contactId" class="mb-1 text-[13px] font-semibold">{{ t('member.detail.contact.title') }}</h3>
            <dl class="m-0 flex flex-col">
                <MemberDetailItem :label="t('member.col.email')" :empty="!detail.email" :masked="detail.emailMasked">{{ detail.email }}</MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.contact.homepage')" :empty="!detail.contact.homepage">{{ detail.contact.homepage }}</MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.contact.mobile')" :empty="!detail.contact.mobile" :masked="detail.piiMasked">
                    <span class="tabular-nums">{{ detail.contact.mobile }}</span>
                </MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.contact.phone')" :empty="!detail.contact.phone" :masked="detail.piiMasked">
                    <span class="tabular-nums">{{ detail.contact.phone }}</span>
                </MemberDetailItem>
            </dl>
        </section>

        <section :aria-labelledby="locationId">
            <h3 :id="locationId" class="mb-1 text-[13px] font-semibold">{{ t('member.detail.contact.locationTitle') }}</h3>
            <dl class="m-0 flex flex-col">
                <MemberDetailItem :label="t('member.detail.contact.country')" :empty="!detail.contact.country">{{ detail.contact.country ? UtilMember.countryName(detail.contact.country, locale) : '' }}</MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.contact.language')">{{ UtilMemberMessages.languageName(detail.settings.display) }}</MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.contact.timezone')">{{ UtilMemberMessages.zoneText(t, locale, detail.settings.display, now) }}</MemberDetailItem>
                <MemberDetailItem :label="t('member.detail.contact.address')" :empty="!detail.contact.address" :masked="detail.piiMasked">{{ detail.contact.address }}</MemberDetailItem>
            </dl>
            <p class="mt-2 text-xs text-muted-color">{{ t('member.detail.contact.displayNote') }}</p>
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

defineProps<Props>();

const { t, locale } = useI18n();
const contactId = useId();
const locationId = useId();
/** 시차를 잴 순간 — 구획을 그릴 때 한 번 */
const now = Date.now();
</script>
