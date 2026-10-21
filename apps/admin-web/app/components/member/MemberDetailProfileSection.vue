<template>
    <!--
        회원 상세 — 프로필·메모 구획(읽기 전용). 한 줄 소개(기본 정보 구획에서 옮겨 온 한 곳)·서명·관리자 메모. 한 줄 소개(`bio`)만 서버에 있고, 서명·관리자 메모(`profile`)는 서버가 null 이면 「아직 연동되지 않았어요」 안내 한 줄로 대신한다.
        관리자 메모는 쓰기가 필요한 값인데, 권한 표(`util-permission-catalog.ts`)에 회원 정보를 수정하는 권한이 없다(`member.read`·`member.pii` 뿐). 새 권한 코드를 만들지 않고 읽기 전용으로 보인다 — 편집은 후속.
    -->
    <div class="flex flex-col gap-5">
        <section :aria-labelledby="profileId">
            <h3 :id="profileId" class="mb-1 text-[13px] font-semibold">{{ t('member.detail.profile.title') }}</h3>
            <dl class="m-0 flex flex-col">
                <MemberDetailItem :label="t('member.detail.profile.bio')" :empty="!detail.bio">
                    <span class="whitespace-pre-line">{{ detail.bio }}</span>
                </MemberDetailItem>
                <MemberDetailItem v-if="detail.profile" :label="t('member.detail.profile.signature')" :empty="!detail.profile.signature">
                    <span class="whitespace-pre-line">{{ detail.profile.signature }}</span>
                </MemberDetailItem>
            </dl>
        </section>

        <section v-if="detail.profile" :aria-labelledby="memoId">
            <h3 :id="memoId" class="mb-2 text-[13px] font-semibold">{{ t('member.detail.profile.memoTitle') }}</h3>
            <p v-if="detail.profile.adminMemo" class="m-0 whitespace-pre-line rounded-lg border border-(--line) px-3 py-2.5 text-[13px]">{{ detail.profile.adminMemo }}</p>
            <p v-else class="m-0 text-[13px] text-muted-color">{{ t('member.detail.profile.memoNone') }}</p>
            <p v-if="detail.profile.adminMemoUpdatedAt" class="mt-1.5 text-xs text-muted-color tabular-nums">
                {{ t('member.detail.profile.memoUpdated', { at: time.full(detail.profile.adminMemoUpdatedAt), by: detail.profile.adminMemoUpdatedBy ?? t('member.none') }) }}
            </p>
            <p class="mt-2 flex items-start gap-1.5 text-xs text-muted-color">
                <i class="pi pi-lock mt-0.5" aria-hidden="true" />
                <span>{{ t('member.detail.profile.memoReadonly') }}</span>
            </p>
        </section>

        <p v-else class="m-0 flex items-start gap-2 rounded-lg border border-(--line) px-3 py-2.5 text-[13px] text-muted-color">
            <i class="pi pi-clock mt-0.5" aria-hidden="true" />
            <span>{{ t('member.detail.notReady.profile') }}</span>
        </p>
    </div>
</template>

<script setup lang="ts">
import type { MemberDetail } from '~/composables/member/api/useMemberApi.types';

interface Props {
    detail: MemberDetail;
}

defineProps<Props>();

const { t } = useI18n();
const time = useDisplayTime();
const profileId = useId();
const memoId = useId();
</script>
