<template>
    <!-- 사이트 푸터 — 약관·개인정보처리방침·운영정책 링크 + 판매 스위치가 켜져 있으면 통신판매업자 정보(전자상거래법 제10조 ① — commerce-groupbuy 결정 56). 약관 본문 페이지는 새 탭 링크다.
         「고객 지원」 링크 묶음(버그 신고·기능 요청 …) — 메뉴 그룹 `footer`(`useFooterMenu`, 메인 메뉴와 따로). 같은 탭 이동, 항목이 `newTab` 이면 새 탭. 못 받았거나 비면 묶음을 숨긴다.
         데스크톱(md 이상): 두 단 — 왼쪽 = 사이트 이름 · 링크 · 고객 지원 · ©, 오른쪽 = 사업자 정보 표(`CommerceBusinessInfo` footer). 사업자 정보가 없으면 왼쪽 단만.
         사업자 정보가 없을 때(운영 주체 개인 단계 — 2026-11-06) 왼쪽 단에 「문의 help@…」 한 줄(운영 주체 연락 이메일 — `GET /api/site/info` 의 `operator.contactEmail`, null 이면 그 줄을 숨긴다).
         모바일(사용자 요청 2026-11-06 — 모바일에도 보인다): 한 단으로 쌓는다 — 사이트 이름 · 링크 한 줄, 그 아래 사업자 정보 표(이름표 | 값), 맨 아래 ©. 하단 탭 바로 위에 놓인다 -->
    <footer class="border-t border-surface bg-surface-100 dark:bg-surface-900">
        <div class="mx-auto grid max-w-[var(--content-max)] grid-cols-1 gap-y-3 px-4 py-5 text-xs text-muted-color md:gap-x-8 md:py-6" :class="personal ? '' : 'md:grid-cols-[10rem_minmax(0,1fr)]'">
            <!-- 사업자 정보가 없을 때(운영 주체 개인 단계) 데스크톱은 가로 3열(사용자 2026-11-10 시안): 1열 = 사이트 이름 · 문의 · © / 2열 = 「약관·정책」 + 약관 링크(세로) /
                 3열 = 「고객 지원」 + footer 메뉴 링크(세로, 없으면 열 없음). 모바일·사업자 정보가 있을 때는 예전 모양 그대로 -->
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1" :class="personal ? 'md:grid md:grid-cols-3 md:items-start md:gap-x-8 md:gap-y-1' : 'md:flex-col md:items-start md:gap-1'" data-testid="footer-main">
                <b class="text-sm font-semibold text-surface-700 md:col-start-1 md:mb-1 dark:text-surface-200">{{ siteLabel }}</b>
                <!-- 약관 링크 — 캐시 관리 미리보기의 영역 표시(`data-cache-area`, `contents` 라 줄 배치는 그대로 — 데스크톱 3열에서는 2열 한 칸) -->
                <span class="contents" :class="personal ? 'md:col-start-2 md:row-span-3 md:row-start-1 md:flex md:flex-col md:items-start md:gap-1' : ''" data-cache-area="terms.public">
                    <span v-if="personal" class="hidden font-semibold text-surface-700 md:block dark:text-surface-200">{{ t('shell.footerTerms') }}</span>
                    <a href="/terms" target="_blank" rel="noopener" class="hover:underline">{{ t('common.terms') }}</a>
                    <a href="/privacy" target="_blank" rel="noopener" class="font-semibold text-surface-700 hover:underline dark:text-surface-200">{{ t('common.privacy') }}</a>
                    <a href="/policy" target="_blank" rel="noopener" class="hover:underline">{{ t('terms.operationPolicy') }}</a>
                </span>
                <!-- 고객 지원 — 메뉴 그룹 `footer`(운영자가 정하는 링크, 폴더면 그 아래 항목을 평평하게). 못 받았거나 비면 숨긴다. 모바일은 약관 줄 다음 한 줄, 데스크톱은 왼쪽 단 약관 아래 -->
                <nav
                    v-if="supportLinks.length"
                    class="flex flex-wrap items-center gap-x-3 gap-y-1 max-md:basis-full md:flex-col md:items-start md:gap-1"
                    :class="personal ? 'md:col-start-3 md:row-span-3 md:row-start-1' : 'md:mt-2'"
                    :aria-label="t('shell.footerSupport')"
                    data-testid="footer-support"
                >
                    <span class="font-semibold text-surface-700 dark:text-surface-200">{{ t('shell.footerSupport') }}</span>
                    <NuxtLink v-for="link in supportLinks" :key="link.id" :to="link.path" v-bind="UtilMenu.linkAttrs(link)" class="hover:underline">
                        {{ localizedName(link.label, link.nameEn) }}<i v-if="link.newTab" class="pi pi-external-link ml-1 text-[0.5625rem]" aria-hidden="true" />
                    </NuxtLink>
                </nav>
                <a
                    v-if="contactEmail && commerceInfo !== undefined && !shownBusiness"
                    :href="`mailto:${contactEmail}`"
                    class="break-all hover:underline max-md:basis-full"
                    :class="personal ? 'md:col-start-1' : 'md:mt-2'"
                    data-testid="footer-contact"
                    >{{ t('shell.footerContact', { email: contactEmail }) }}</a
                >
                <span class="max-md:hidden md:mt-2" :class="personal ? 'md:col-start-1' : ''">{{ copyright }}</span>
            </div>
            <CommerceBusinessInfo v-if="shownBusiness" :business="shownBusiness" variant="footer" class="max-md:border-t max-md:border-surface max-md:pt-3" />
            <span class="md:hidden">{{ copyright }}</span>
        </div>
    </footer>
</template>

<script setup lang="ts">
import UtilMenu from '~/utils/util-menu';

const { t } = useI18n();
const { siteLabel } = useSiteName();
const commerce = useCommerceInfo();
// 커머스 정보를 받기 전(`undefined`)에는 문의 줄을 숨긴다 — 사업자 정보가 있는 사이트에서 한 번 비치지 않게
const { shownBusiness, info: commerceInfo } = commerce;
/** 운영 주체 개인 단계(보일 사업자 정보 없음) — 데스크톱 가로 3열 */
const personal = computed(() => !shownBusiness.value);
/** © 줄 — 데스크톱은 왼쪽 단 맨 아래, 모바일은 푸터 맨 아래 */
const copyright = computed(() => `© ${new Date().getFullYear()} ${siteLabel.value}`);
// 운영 주체 연락 이메일(사이트 정보) — 없으면 문의 줄을 숨긴다
const siteInfo = useSiteInfoState();
const contactEmail = computed(() => siteInfo.value?.operator?.contactEmail ?? null);
onMounted(() => void commerce.ensureLoaded());
// 고객 지원 링크(메뉴 그룹 `footer`) — 보는 사람마다 다를 수 있어 로그인·로그아웃으로 사람이 바뀌면 다시 받는다
const { localizedName } = useLocalizedName();
const footerMenu = useFooterMenu();
const supportLinks = footerMenu.items;
const { user } = useAuth();
watch(
    () => user.value?.id ?? '',
    () => void footerMenu.ensureLoaded(),
);
onMounted(() => void footerMenu.ensureLoaded());
</script>
