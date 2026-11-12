<template>
    <div>
        <!--
            사이트 소개(`/about`) — 기본 레이아웃, 비회원도 본다. 푸터 「사이트 소개」 링크(사이트 정보의 `about` 이 true 일 때)가 여기로 온다.
            흰 카드(`gadget-card` data-headless) 한 장에 차례로(사용자 2026-11-12 「텍스트 위주로 심심해, 이미지·로고 아이콘으로 꾸며 줘」):
              ① 머리 — 사이트 로고 크게(`SiteBrand size="lg"` — 운영자 로고·언어·밝은/어두운 바탕 고르기는 상단 바와 같은 규칙) + 한 줄 소개(사이트 정보의 슬로건, 없으면 줄 없음)
                 + 다루는 이야기 칩 + 마스코트 로보티콘 둘(파란 로봇 「인사」·분홍 리본 로봇 「기쁨」 — 움직이는 WebP `public/brand/mascot-*.webp`). 바탕은 주 색상을 옅게 섞어 라이트·다크 모두 따라간다.
              ② 「IROBIT」 세 단어 카드 — AI(로고의 로봇이 A + I) · ROB(Robot) · BIT(Our Bit), 화면 문구(i18n). 소개 본문이 없거나 못 불러와도 ①②는 보인다.
              ③ 관리자 본문 — 관리자 설정 › 일반 「사이트 소개」, 서버가 정화한 HTML 만 `v-html`(`GET /api/site/about`). 없으면(`html: null`) 빈 상태.
                 글 모양(`rich-text`) 위에 이 화면에서만 제목 막대·목록 카드 격자·강조 색을 더한다(아래 scoped 스타일 — 다른 본문 화면에 새지 않는다).
              ④ 행동 단추 — 비회원 「가입하기」·「로그인」, 회원 「글쓰기」(사업자 전용 계정 제외)·「커뮤니티 둘러보기」(하단 탭과 같은 커뮤니티 자리 `useBottomTabs`).
            ⚠ 기본 소개의 첫 제목·세 단어 문단은 ②와 겹쳐 서버 기본값에서 뺐다(shared V205) — 화면에서 따로 숨기지 않는다.
        -->
        <div class="gadget-card" data-headless data-testid="site-about">
            <!-- ① 머리 -->
            <section class="about-hero relative overflow-hidden rounded-2xl px-5 py-6 md:px-8 md:py-8" :aria-labelledby="titleId" data-testid="site-about-hero">
                <div class="relative flex flex-col items-center gap-6 text-center md:flex-row md:items-center md:justify-between md:text-left">
                    <div class="flex min-w-0 flex-col items-center md:items-start">
                        <h1 :id="titleId" class="m-0 inline-flex items-center gap-1.5 text-xs font-semibold uppercase tracking-[0.2em] text-primary">
                            <i class="pi pi-sparkles text-[0.8125rem]" aria-hidden="true" />{{ t('siteAbout.title') }}
                        </h1>
                        <div class="mt-3 max-w-full text-3xl font-extrabold tracking-wider md:text-4xl">
                            <SiteBrand size="lg" class="max-w-full" />
                        </div>
                        <p v-if="tagline" class="m-0 mt-3 max-w-[32rem] text-base font-medium leading-relaxed md:text-lg" data-testid="site-about-tagline">{{ tagline }}</p>
                        <ul class="m-0 mt-4 flex list-none flex-wrap justify-center gap-2 p-0 md:justify-start" :aria-label="t('siteAbout.heroTopicsLabel')">
                            <li v-for="topic in topics" :key="topic.key" class="about-chip inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm font-semibold leading-none">
                                <i :class="topic.icon" class="text-[0.8125rem]" aria-hidden="true" /><span class="relative -top-[0.09em]">{{ t(topic.labelKey) }}</span>
                            </li>
                        </ul>
                    </div>

                    <!-- 마스코트 — 장식(읽지 않는다). 말풍선은 파란 로봇 머리 위 -->
                    <div class="relative flex shrink-0 items-end gap-1 pt-7" aria-hidden="true">
                        <span class="about-bubble absolute left-1/2 top-0 -translate-x-1/2 whitespace-nowrap rounded-full px-3 py-1 text-xs font-bold leading-none md:left-6 md:translate-x-0">
                            {{ t('siteAbout.heroHello') }}
                        </span>
                        <!-- 로보티콘 GIF(`/emoticons/robotikon-m/17.gif`·`robotikon-f/01.gif`)를 화질 손실 없는 움직이는 WebP 로 바꾼 사본(픽셀·프레임 간격 같음, 180KB → 137KB — 2026-11-13) -->
                        <img src="/brand/mascot-robotikon-m-17.webp" alt="" width="200" height="200" class="about-mascot size-24 md:size-32" loading="eager" decoding="async" />
                        <img src="/brand/mascot-robotikon-f-01.webp" alt="" width="200" height="200" class="about-mascot size-24 md:size-32" loading="eager" decoding="async" />
                    </div>
                </div>
            </section>

            <!-- ② 「IROBIT」 세 단어 -->
            <section class="mt-8" :aria-labelledby="wordsId" data-testid="site-about-words">
                <div class="text-center">
                    <h2 :id="wordsId" class="m-0 text-lg font-bold md:text-xl">{{ t('siteAbout.wordsTitle') }}</h2>
                    <p class="m-0 mt-1 text-sm font-medium tracking-wide text-muted-color">{{ t('siteAbout.wordsLead') }}</p>
                </div>
                <ul class="m-0 mt-5 grid list-none grid-cols-1 gap-3 p-0 sm:grid-cols-3">
                    <li v-for="word in words" :key="word.key" class="panel-card relative overflow-hidden p-5" :data-testid="`site-about-word-${word.key}`">
                        <!-- 큰 머리글자 그림자(장식) -->
                        <span class="about-ghost pointer-events-none absolute -bottom-4 -right-1 select-none text-8xl font-black leading-none" aria-hidden="true">{{ t(word.letterKey) }}</span>
                        <div class="relative flex items-center gap-3">
                            <span class="grid size-11 shrink-0 place-items-center rounded-xl bg-primary-50 text-primary-700 dark:bg-primary-950 dark:text-primary-300" aria-hidden="true">
                                <i :class="word.icon" class="text-xl" />
                            </span>
                            <!-- 「AI」 카드는 A 자리에 로고의 로봇(안테나와 A자 다리가 A 를 그린다) + I — 다른 카드는 글자만 -->
                            <span v-if="word.key === 'ai'" class="inline-flex items-end text-3xl font-black leading-none tracking-tight text-primary" aria-hidden="true">
                                <img src="/brand/irobit-logo-ko-light.svg#symbol" alt="" class="site-logo-light -mb-0.5 h-10 w-12" />
                                <img src="/brand/irobit-logo-ko-dark.svg#symbol" alt="" class="site-logo-dark -mb-0.5 h-10 w-12" />
                                <span>I</span>
                            </span>
                            <span v-else class="text-3xl font-black leading-none tracking-tight text-primary" aria-hidden="true">{{ t(word.letterKey) }}</span>
                        </div>
                        <h3 class="relative m-0 mt-4 text-base font-bold">{{ t(word.titleKey) }}</h3>
                        <p class="relative m-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t(word.descKey) }}</p>
                    </li>
                </ul>
            </section>

            <!-- ③ 관리자 본문 -->
            <div class="mt-8">
                <div v-if="status === 'loading'" role="status" :aria-label="t('common.loading')" data-testid="site-about-loading">
                    <div class="h-4 w-1/2 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    <div class="mt-4 h-64 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
                </div>

                <ErrorState v-else-if="status === 'error'" :title="t('siteAbout.loadError')" :description="errorMessage" retry data-testid="site-about-error" @retry="load" />

                <!-- eslint-disable-next-line vue/no-v-html -->
                <article v-else-if="html" class="about-body rich-text text-[0.9375rem]" :aria-label="t('siteAbout.title')" data-testid="site-about-body" v-html="html" />

                <EmptyState v-else icon="pi pi-info-circle" :title="t('siteAbout.empty')" data-testid="site-about-empty" />
            </div>

            <!-- ④ 행동 단추 — 로그인 상태를 안 뒤에만 -->
            <section v-if="loaded" class="about-cta mt-8 rounded-2xl px-5 py-6 text-center md:px-8" :aria-labelledby="ctaId" data-testid="site-about-cta">
                <div class="flex justify-center -space-x-2" aria-hidden="true">
                    <img
                        v-for="avatar in AVATARS"
                        :key="avatar"
                        :src="`/avatars/irobit/${avatar}.svg`"
                        alt=""
                        width="40"
                        height="40"
                        class="about-avatar size-10 rounded-full p-0.5"
                        loading="lazy"
                        decoding="async"
                    />
                </div>
                <h2 :id="ctaId" class="m-0 mt-4 text-lg font-bold">{{ user ? t('siteAbout.ctaMemberTitle') : t('siteAbout.ctaGuestTitle') }}</h2>
                <p class="m-0 mt-1 text-sm text-muted-color">{{ user ? t('siteAbout.ctaMemberDesc') : t('siteAbout.ctaGuestDesc') }}</p>
                <div class="mt-5 flex flex-col justify-center gap-2 sm:flex-row">
                    <template v-if="user">
                        <Button v-if="canWrite" as="router-link" to="/write" icon="pi pi-pencil" :label="t('siteAbout.ctaWrite')" data-testid="site-about-write" />
                        <Button
                            as="router-link"
                            :to="community.to"
                            icon="pi pi-compass"
                            :label="t('siteAbout.ctaBrowse', { name: community.label })"
                            :severity="canWrite ? 'secondary' : undefined"
                            :variant="canWrite ? 'outlined' : undefined"
                            data-testid="site-about-browse"
                        />
                    </template>
                    <template v-else>
                        <Button as="router-link" to="/signup" icon="pi pi-user-plus" :label="t('common.signup')" data-testid="site-about-signup" />
                        <Button as="router-link" :to="loginTarget" :label="t('common.login')" severity="secondary" variant="outlined" data-testid="site-about-login" />
                    </template>
                </div>
            </section>
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilBusiness from '~/utils/util-business';
import UtilRedirect from '~/utils/util-redirect';

const { t } = useI18n();
useSeoMeta({ title: () => t('siteAbout.title') });

const api = useSiteAboutApi();
const route = useRoute();
const { user, loaded } = useAuth();
const { tagline } = useSiteTagline();
const { tabs } = useBottomTabs();

const titleId = useId();
const wordsId = useId();
const ctaId = useId();

/** 머리의 「다루는 이야기」 칩 */
const topics = [
    { key: 'ai', icon: 'pi pi-microchip-ai', labelKey: 'siteAbout.heroTopicAi' },
    { key: 'robot', icon: 'pi pi-cog', labelKey: 'siteAbout.heroTopicRobot' },
    { key: 'tech', icon: 'pi pi-bolt', labelKey: 'siteAbout.heroTopicTech' },
] as const;

/** 「IROBIT」 세 단어 카드 — 머리글자 · 이름 · 한 줄 뜻 */
const words = [
    { key: 'ai', icon: 'pi pi-microchip-ai', letterKey: 'siteAbout.wordILetter', titleKey: 'siteAbout.wordITitle', descKey: 'siteAbout.wordIDesc' },
    { key: 'rob', icon: 'pi pi-cog', letterKey: 'siteAbout.wordRobLetter', titleKey: 'siteAbout.wordRobTitle', descKey: 'siteAbout.wordRobDesc' },
    { key: 'bit', icon: 'pi pi-sparkles', letterKey: 'siteAbout.wordBitLetter', titleKey: 'siteAbout.wordBitTitle', descKey: 'siteAbout.wordBitDesc' },
] as const;

/** 행동 단추 위 아바타 줄(장식) — 아이로빗 기본 아바타 몇 개 */
const AVATARS = ['basic-default', 'job-developer-f', 'service-scientist-m', 'factory-robot-engineer-f', 'hobby-gamer'] as const;

/** 글쓰기 단추 — 사업자 전용 계정은 일반 게시판에 글을 쓸 수 없어 빼고 둘러보기만(`WriteFab` 과 같은 판정) */
const canWrite = computed(() => !UtilBusiness.isBusinessAccount(user.value?.role));
/** 커뮤니티 자리 — 하단 탭의 커뮤니티 칸과 같은 주소·이름(메뉴의 「커뮤니티」 묶음, 없으면 기본 가젯 페이지) */
const community = computed(() => {
    const tab = tabs.value.find((item) => item.key === 'community');
    return { to: tab?.to ?? '/p/community', label: tab?.label ?? t('shell.tabCommunity') };
});
/** 로그인 뒤 이 화면으로 돌아온다 */
const loginTarget = computed(() => UtilRedirect.toLogin(route.fullPath));

/**
 * 「할 수 있는 것」 같은 본문 글머리 목록 — 항목마다 뜻에 맞는 아이콘(사용자 2026-11-13 「각 항목에 맞는 아이콘 넣어」).
 * 본문은 관리자가 고치는 HTML 이라 차례가 아니라 항목 머리(첫 굵은 글자, 없으면 항목 글 전체)의 낱말로 고른다. 맞는 낱말이 없으면 반짝 아이콘.
 * 위에서부터 처음 맞는 줄을 쓴다(「이야기 나누기」 설명에 「게시판」이 있어도 머리 낱말이 먼저 맞게 머리를 먼저 본다).
 */
const LIST_ICONS: readonly { icon: string; words: RegExp }[] = [
    { icon: 'pi pi-comments', words: /이야기|대화|수다|나누|게시판|talk|chat|share|discuss/i },
    { icon: 'pi pi-graduation-cap', words: /배우|알려|강좌|팁|사용기|노하우|learn|tip|guide|review/i },
    { icon: 'pi pi-users', words: /모이|모임|소모임|함께|meet|group|together/i },
    { icon: 'pi pi-shopping-bag', words: /사기|쇼핑|핫딜|공동구매|장터|거래|shop|deal|buy|market/i },
    { icon: 'pi pi-face-smile', words: /표현|로보티콘|이모티콘|감정|express|emoticon|emoji/i },
];
const LIST_ICON_FALLBACK = 'pi pi-sparkles';

/** 정화된 본문 HTML 의 맨 위 글머리 목록 항목 앞에 아이콘 칩(장식, 읽지 않음)을 넣는다 — 화면이 SPA(`ssr: false`)라 브라우저 DOMParser 를 쓴다 */
function withListIcons(source: string): string {
    const doc = new DOMParser().parseFromString(`<div>${source}</div>`, 'text/html');
    const root = doc.body.firstElementChild;
    if (!root) return source;
    for (const li of root.querySelectorAll(':scope > ul > li')) {
        const head = li.querySelector('strong')?.textContent?.trim() || '';
        const text = li.textContent ?? '';
        const icon = LIST_ICONS.find((item) => head && item.words.test(head))?.icon ?? LIST_ICONS.find((item) => item.words.test(text))?.icon ?? LIST_ICON_FALLBACK;
        const chip = doc.createElement('span');
        chip.className = 'about-li-icon';
        chip.setAttribute('aria-hidden', 'true');
        const i = doc.createElement('i');
        i.className = icon;
        chip.appendChild(i);
        li.prepend(chip);
    }
    return root.innerHTML;
}

const html = ref<string | null>(null);
const status = ref<'loading' | 'error' | 'ready'>('loading');
const errorMessage = ref('');

async function load(): Promise<void> {
    status.value = 'loading';
    errorMessage.value = '';
    try {
        const res = await api.about();
        html.value = res.html?.trim() ? withListIcons(res.html) : null;
        status.value = 'ready';
    } catch (e) {
        html.value = null;
        errorMessage.value = UtilBoardMessages.of(t, e);
        status.value = 'error';
    }
}

onMounted(() => void load());
</script>

<style scoped>
/*
 * 사이트 소개 화면만의 꾸밈 — 색은 PrimeVue 주 색상·글자 색 토큰(`--p-primary-color`·`--p-text-color`)에 섞어 다크 모드(`html[data-color-scheme='dark']`)를 따라간다.
 * 본문(`v-html`)은 `:deep` 으로만 닿고, 선택자가 `.about-body[data-v-…]` 라 공용 `rich-text.css`(층 밖 `.rich-text …`)를 이 화면에서만 이긴다.
 */

/* ① 머리 바탕 — 옅은 주 색상 + 오른쪽 위 빛 + 점 무늬(아래로 흐려짐) */
.about-hero {
    background:
        radial-gradient(120% 90% at 100% 0%, color-mix(in srgb, var(--p-primary-color) 20%, transparent), transparent 62%),
        color-mix(in srgb, var(--p-primary-color) 7%, transparent);
}
.about-hero::before {
    content: '';
    position: absolute;
    inset: 0;
    background-image: radial-gradient(color-mix(in srgb, var(--p-primary-color) 28%, transparent) 1px, transparent 1.6px);
    background-size: 18px 18px;
    mask-image: linear-gradient(to bottom, #000, transparent 75%);
    pointer-events: none;
}
.about-chip {
    background: color-mix(in srgb, var(--p-primary-color) 12%, transparent);
    color: color-mix(in srgb, var(--p-primary-color) 75%, var(--p-text-color));
}
.about-bubble {
    background: var(--p-primary-color);
    color: var(--p-primary-contrast-color);
    box-shadow: 0 4px 12px color-mix(in srgb, var(--p-primary-color) 30%, transparent);
}
.about-mascot {
    filter: drop-shadow(0 6px 10px color-mix(in srgb, var(--p-text-color) 14%, transparent));
}

/* ② 카드 뒤 큰 머리글자 */
.about-ghost {
    color: color-mix(in srgb, var(--p-primary-color) 8%, transparent);
}

/* ③ 관리자 본문 */
.about-body :deep(strong) {
    color: color-mix(in srgb, var(--p-primary-color) 75%, var(--p-text-color));
    font-weight: 700;
}
/* 제목 — 앞에 주 색상 막대(글자가 여러 조각이어도 흐름이 깨지지 않게 절대 위치) */
.about-body :deep(h2),
.about-body :deep(h3) {
    position: relative;
    margin: 1.75em 0 0.75em;
    padding-left: 0.875rem;
}
.about-body :deep(h2)::before,
.about-body :deep(h3)::before {
    content: '';
    position: absolute;
    left: 0;
    top: 0.2em;
    bottom: 0.2em;
    width: 0.3rem;
    border-radius: 9999px;
    background: var(--p-primary-color);
}
/* 글머리 목록 — 한 줄에 한 항목(여러 열로 나누면 설명이 좁아져 안 읽힌다 — 사용자 2026-11-13), 항목마다 뜻에 맞는 아이콘 칩(`withListIcons` 가 넣는 `.about-li-icon`) */
.about-body > :deep(ul) {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    gap: 0.75rem;
    margin: 1em 0 1.5em;
    padding: 0;
    list-style: none;
}
.about-body > :deep(ul > li) {
    position: relative;
    margin: 0;
    padding: 0.875rem 1rem 0.875rem 3.75rem;
    border: 1px solid color-mix(in srgb, var(--p-primary-color) 18%, transparent);
    border-radius: 0.875rem;
    background: color-mix(in srgb, var(--p-primary-color) 5%, transparent);
    line-height: 1.6;
}
.about-body > :deep(ul > li > .about-li-icon) {
    position: absolute;
    left: 0.875rem;
    top: 0.75rem;
    display: grid;
    place-items: center;
    width: 2.25rem;
    height: 2.25rem;
    border-radius: 0.75rem;
    background: var(--p-primary-color);
    color: var(--p-primary-contrast-color);
    box-shadow: 0 3px 8px color-mix(in srgb, var(--p-primary-color) 28%, transparent);
}
.about-body > :deep(ul > li > .about-li-icon > i) {
    font-size: 1.05rem;
    line-height: 1;
}
/* ④ 행동 단추 상자 */
.about-cta {
    background: color-mix(in srgb, var(--p-primary-color) 6%, transparent);
    border: 1px dashed color-mix(in srgb, var(--p-primary-color) 30%, transparent);
}
.about-avatar {
    background: var(--card-bg, var(--p-content-background));
    box-shadow: 0 0 0 2px var(--card-bg, var(--p-content-background));
}
</style>
