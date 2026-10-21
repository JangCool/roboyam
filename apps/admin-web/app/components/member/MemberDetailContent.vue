<template>
    <!--
        회원 상세 서랍의 본문 — 탭이 아니라 **구획을 한 스크롤 영역에 모두 쌓고**, 오른쪽에 기존 구역 내비(`SectionNav`, 「이 화면의 구역」)를 붙인다.
        구획은 `AppGadget` 의 `anchor` 로 스스로 등록하고(`useSectionNav`), 항목을 누르면 그 구획으로 부드럽게 이동 + 제목 초점 + 테두리 강조, 스크롤하면 현재 구획 표시가 따라간다 — 셸의 화면들과 같은 부품·같은 동작이다.
        서랍은 자기 스크롤 영역(`.p-drawer-content`)이 있어서 셸과 따로 된 범위(`SECTION_NAV_SCOPE`, 서랍이 provide)로 쓴다 — 셸 오른쪽 내비에 섞이지 않는다.
        본문이 좁으면(컨테이너 48rem 미만) 오른쪽 목록 대신 맨 위 「구역 이동」 선택 상자. 탈퇴한 회원은 줄어든 표시라 데이터가 없는 구획(연락처·프로필·등급·개인 설정·동의·공동구매·알림함·본인 인증)은 없다.
        본인 인증은 도입 전이라 구획 제목에 작은 「준비 중」 뱃지, 구역 내비의 항목은 글자만 받으므로 이름 뒤에 「(준비 중)」을 붙인다.
    -->
    <div ref="rootEl" class="@container px-4 py-3" :style="{ '--section-nav-offset': '0.75rem' }">
        <div class="mb-3 @3xl:hidden">
            <SectionNav variant="select" />
        </div>
        <div class="grid gap-x-5 @3xl:grid-cols-[minmax(0,1fr)_10rem]">
            <div class="flex min-w-0 flex-col gap-4">
                <AppGadget v-for="section in sections" :key="section.key" :title="section.title" :anchor="section.anchor" :nav-label="section.navLabel">
                    <template v-if="section.pending" #title-extra>
                        <MemberDetailTag :value="t('member.detail.pending')" severity="secondary" />
                    </template>
                    <MemberDetailBasicSection v-if="section.key === 'basic'" :detail="detail" />
                    <MemberDetailContactSection v-else-if="section.key === 'contact'" :detail="detail" />
                    <MemberDetailProfileSection v-else-if="section.key === 'profile'" :detail="detail" />
                    <MemberDetailRolesSection v-else-if="section.key === 'roles'" :detail="detail" />
                    <MemberDetailGradePointSection v-else-if="section.key === 'gradePoint'" :detail="detail" />
                    <MemberDetailSettingsSection v-else-if="section.key === 'settings'" :detail="detail" />
                    <MemberDetailConsentSection v-else-if="section.key === 'consent'" :detail="detail" />
                    <MemberDetailIdentitySection v-else-if="section.key === 'identity'" />
                    <MemberDetailGroupBuySection v-else-if="section.key === 'groupBuy'" :detail="detail" />
                    <MemberDetailInboxSection v-else-if="section.key === 'inbox'" :detail="detail" />
                    <MemberDetailSanctionSection v-else :detail="detail" />
                </AppGadget>
            </div>
            <aside class="hidden @3xl:block">
                <SectionNav />
            </aside>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { MemberDetail } from '~/composables/member/api/useMemberApi.types';

interface Props {
    detail: MemberDetail;
}

const props = defineProps<Props>();

const { t } = useI18n();
// 서랍이 provide 한 범위의 구역 내비 — 이 범위의 목록·현재 구획 추적을 켜고 끈다
const sectionNav = useSectionNav();
const rootEl = ref<HTMLElement | null>(null);
/** 구획 주소가 서랍을 다시 열어도 겹치지 않게 붙이는 접두어 — 소문자·숫자·하이픈만(`UtilSectionNav.isValidAnchor`) */
const prefix = `mdd-${useId().toLowerCase().replace(/[^a-z0-9]+/g, '-')}`;

interface Section {
    key: string;
    anchor: string;
    title: string;
    navLabel: string;
    pending: boolean;
}

/** 구획 차례 — 데이터가 없는 구획은 뺀다. 구획 제목과 구역 내비 이름은 같다(본인 인증만 내비에 「(준비 중)」) */
const sections = computed<Section[]>(() => {
    const d = props.detail;
    const all: { key: string; labelKey: string; show: boolean; pending?: boolean }[] = [
        { key: 'basic', labelKey: 'member.detail.sectionBasic', show: true },
        { key: 'contact', labelKey: 'member.detail.sectionContact', show: d.contact !== null },
        { key: 'profile', labelKey: 'member.detail.sectionProfile', show: d.profile !== null },
        { key: 'roles', labelKey: 'member.detail.sectionRoles', show: true },
        { key: 'gradePoint', labelKey: 'member.detail.sectionGradePoint', show: d.gradePoint !== null },
        { key: 'settings', labelKey: 'member.detail.sectionSettings', show: d.settings !== null },
        { key: 'consent', labelKey: 'member.detail.sectionConsent', show: d.consents !== null },
        { key: 'identity', labelKey: 'member.detail.sectionIdentity', show: d.status !== 'WITHDRAWN', pending: true },
        { key: 'groupBuy', labelKey: 'member.detail.sectionGroupBuy', show: d.groupBuy !== null },
        { key: 'inbox', labelKey: 'member.detail.sectionInbox', show: d.settings !== null },
        { key: 'sanctions', labelKey: 'member.detail.sectionSanctions', show: true },
    ];
    return all
        .filter((item) => item.show)
        .map((item) => ({
            key: item.key,
            anchor: `${prefix}-${item.key.toLowerCase()}`,
            title: t(item.labelKey),
            navLabel: item.pending ? `${t(item.labelKey)} (${t('member.detail.pending')})` : t(item.labelKey),
            pending: item.pending === true,
        }));
});

// 서랍 본문(`.p-drawer-content`)이 스크롤 영역이다 — 다른 회원을 열 때마다 맨 위에서 시작한다
onMounted(() => {
    const scroller = rootEl.value?.closest<HTMLElement>('.p-drawer-content') ?? null;
    scroller?.scrollTo({ top: 0 });
    sectionNav.start(scroller);
});
onBeforeUnmount(() => sectionNav.stop());
</script>
