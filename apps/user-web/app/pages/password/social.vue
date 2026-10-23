<template>
    <StatusPanel icon="pi pi-info-circle" :title="title" data-testid="password-social-guide">
        {{ t('password.socialNoPassword') }}<br />
        {{ known ? t('password.socialLoginKnown', UtilProvider.params(providerName)) : t('password.socialLoginUnknown') }}
        <template #actions>
            <SocialLoginButtons v-if="providerId" :only="providerId" mode="login" solid />
            <NuxtLink to="/login" class="text-sm text-primary hover:underline max-md:py-1.5">{{ t('signup.toLogin') }}</NuxtLink>
            <small class="text-xs leading-relaxed text-muted-color">{{ t('password.socialMailOnly') }}</small>
        </template>
    </StatusPanel>
</template>

<script setup lang="ts">
import UtilProvider from '~/utils/util-provider';

/**
 * 소셜 가입자 안내 `/password/social?provider=` — 원본(community) `pages/password/social.vue` 를 옮겼다.
 * 비밀번호 찾기를 요청한 사람이 **소셜로만 가입한 회원**이면 서버가 재설정 링크 대신 "○○ 로그인으로 가입했어요" 메일을 보내고, 그 메일의 링크가 이 화면이다(community `docs/api-auth.md` 「password/forgot」).
 * `?provider=kakao` 의 공급자로 로그인하도록 안내한다(서버가 그 공급자를 켜 둔 경우에만 단추가 보인다 — 실제 `GET /api/auth/providers`). 서버 호출은 없다.
 * 원본처럼 비밀번호를 **만드는** 화면이 아니다 — 소셜 전용 회원은 비밀번호가 없고, 원본에도 비밀번호 만들기 API 가 없다.
 */
definePageMeta({ layout: 'auth' });
const { t } = useI18n();
useSeoMeta({ title: () => t('password.socialSeoTitle'), description: () => t('password.socialSeoDescription'), robots: 'noindex' });

const route = useRoute();

const providerId = computed(() => (typeof route.query.provider === 'string' ? route.query.provider.toLowerCase() : ''));
const providerName = computed(() => UtilProvider.name(t, providerId.value));
/** 이름을 아는 공급자인가 (모르면 일반 문구) */
const known = computed(() => providerName.value !== UtilProvider.name(t, null));
const title = computed(() => (known.value ? t('password.socialTitleKnown', UtilProvider.params(providerName.value)) : t('password.socialTitleUnknown')));
</script>
