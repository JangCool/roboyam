<template>
    <!--
        회원 상세 — 본인 인증 구획(커머스 0-4, commerce.md 12-1, 읽기 전용). 인증 여부 · 인증 일시 · 만 19세 이상 여부 · 인증 수단(포트원/가짜)만 보인다.
        생년월일·CI·DI·이름·휴대폰 번호는 서버가 주지 않는다(애초에 이름·휴대폰 번호·CI·DI 원문은 저장하지 않는다). 탈퇴 회원에게는 이 구획이 없다.
        기본 정보 구획의 「성인 확인」(출생연도 자기 입력)은 본인 인증과 별개다.
        서버는 미인증도 `{ verified:false, … null }` 로 준다 — 인증됨·미인증을 모두 이 구획이 그린다(칸이 null 이면 빈 값 표시).
    -->
    <div class="flex flex-col gap-3">
        <dl class="m-0 flex flex-col">
            <MemberDetailItem :label="t('identity.verified')">
                <MemberDetailTag v-if="verified" :value="t('identity.verifiedYes')" icon="pi pi-verified" severity="success" />
                <MemberDetailTag v-else :value="t('identity.verifiedNo')" icon="pi pi-minus-circle" severity="secondary" />
            </MemberDetailItem>
            <MemberDetailItem :label="t('identity.verifiedAt')" :empty="!identity?.verifiedAt">
                <span class="tabular-nums">{{ time.full(identity?.verifiedAt) }}</span>
            </MemberDetailItem>
            <MemberDetailItem :label="t('identity.age')" :empty="identity?.adult == null">
                <MemberDetailTag :value="identity?.adult ? t('identity.adult') : t('identity.minor')" :icon="identity?.adult ? 'pi pi-check-circle' : 'pi pi-exclamation-circle'" :severity="identity?.adult ? 'success' : 'warn'" />
            </MemberDetailItem>
            <MemberDetailItem :label="t('identity.provider')" :empty="!identity?.provider">
                <MemberDetailTag v-if="identity?.provider" :value="t(`identity.providerName.${identity.provider}`)" :icon="identity.provider === 'PORTONE' ? 'pi pi-mobile' : 'pi pi-wrench'" :severity="identity.provider === 'PORTONE' ? 'secondary' : 'warn'" />
            </MemberDetailItem>
        </dl>
        <p class="m-0 flex items-start gap-1.5 text-xs leading-relaxed text-muted-color">
            <i class="pi pi-eye-slash mt-0.5" aria-hidden="true" />
            {{ t('identity.hiddenNote') }}
        </p>
    </div>
</template>

<script setup lang="ts">
import type { MemberDetail } from '~/composables/member/api/useMemberApi.types';

interface Props {
    detail: MemberDetail;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const time = useDisplayTime();

const identity = computed(() => props.detail.identity ?? null);
const verified = computed(() => identity.value?.verified === true);
</script>
