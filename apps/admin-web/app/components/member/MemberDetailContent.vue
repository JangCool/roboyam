<template>
    <!--
        회원 상세 서랍의 본문 — 탭이 아니라 **구획을 한 스크롤 영역에 모두 쌓고**, 오른쪽에 기존 구역 내비(`SectionNav`, 「이 화면의 구역」)를 붙인다.
        구획은 `AppGadget` 의 `anchor` 로 스스로 등록하고(`useSectionNav`), 항목을 누르면 그 구획으로 부드럽게 이동 + 제목 초점 + 테두리 강조, 스크롤하면 현재 구획 표시가 따라간다 — 셸의 화면들과 같은 부품·같은 동작이다.
        서랍은 자기 스크롤 영역(`.p-drawer-content`)이 있어서 셸과 따로 된 범위(`SECTION_NAV_SCOPE`, 서랍이 provide)로 쓴다 — 셸 오른쪽 내비에 섞이지 않는다.
        본문이 좁으면(컨테이너 48rem 미만) 오른쪽 목록 대신 맨 위 「구역 이동」 선택 상자.
        구역 내비는 구획 12개를 늘 모두 보인다 — 서버가 아직 데이터를 주지 않는 구획(null/빈 값)은 구획 안에서 「아직 연동되지 않았어요」 안내(EmptyState)로 보이고, 데이터가 오면 그려진다.
        탈퇴한 회원은 줄어든 표시라 서버가 비운 구획(연락처·프로필·등급·개인 설정·동의·공동구매·알림함·본인 인증)은 없다.
        탈퇴 회원의 기본 정보 아래에는 분리 보관한 연락용 이메일 블록(`MemberWithdrawnContactBlock`)이 선다.
        「기능 제한」(커머스 0-4)은 「제재 이력」 바로 앞 — 탈퇴 회원도 목록은 보인다. 기능 제한 목록·연락처는 부모(회원 관리 화면)가 받아 내려 주고, 걸기·해제·열람은 이벤트로 올린다(대화상자도 부모).
        「준비 중」 표시(`pending` — 구획 제목의 작은 뱃지, 내비 이름 뒤 「(준비 중)」)는 지금 쓰는 구획이 없다(본인 인증은 커머스 0-4 에서 채웠다).
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
                    <div v-if="section.key === 'basic'" class="flex flex-col gap-4">
                        <MemberDetailBasicSection :detail="detail" />
                        <MemberWithdrawnContactBlock v-if="detail.status === 'WITHDRAWN'" :view="withdrawnContact" @reveal="emit('reveal-contact')" @mask="emit('mask-contact')" @retry="emit('retry-contact')" />
                    </div>
                    <MemberDetailContactSection v-else-if="section.key === 'contact'" :detail="detail" />
                    <MemberDetailProfileSection v-else-if="section.key === 'profile'" :detail="detail" />
                    <MemberDetailRolesSection v-else-if="section.key === 'roles'" :detail="detail" />
                    <MemberDetailGradePointSection
                        v-else-if="section.key === 'gradePoint'"
                        :detail="detail"
                        :point-lots="pointLots"
                        @lots-query="(q) => emit('point-lots-query', q)"
                        @retry-lots="emit('retry-point-lots')"
                    />
                    <MemberDetailSettingsSection v-else-if="section.key === 'settings'" :detail="detail" />
                    <MemberDetailConsentSection v-else-if="section.key === 'consent'" :detail="detail" />
                    <MemberDetailIdentitySection v-else-if="section.key === 'identity'" :detail="detail" />
                    <MemberDetailGroupBuySection v-else-if="section.key === 'groupBuy'" :detail="detail" />
                    <MemberDetailInboxSection v-else-if="section.key === 'inbox'" :detail="detail" />
                    <MemberDetailRestrictionSection
                        v-else-if="section.key === 'restrictions'"
                        :view="restrictions"
                        :withdrawn="detail.status === 'WITHDRAWN'"
                        @restrict="emit('restrict')"
                        @lift="(r) => emit('lift', r)"
                        @retry="emit('retry-restrictions')"
                    />
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

import type { MemberDetailEmits, MemberPointLotsView, MemberRestrictionsView, WithdrawnContactView } from '~/components/member/MemberDetailContent.types';

interface Props {
    detail: MemberDetail;
    /** 「기능 제한」 구획 — 부모가 받은 목록. 없으면 뼈대 */
    restrictions?: MemberRestrictionsView | null;
    /** 탈퇴 회원의 연락용 이메일 블록 — 탈퇴 회원일 때만 쓴다 */
    withdrawnContact?: WithdrawnContactView | null;
    /** 「등급·포인트」 구획의 포인트 묶음(커머스 1-1) — 없으면(권한 없음·받지 않는 화면) 묶음 부분을 그리지 않는다 */
    pointLots?: MemberPointLotsView | null;
}

const props = withDefaults(defineProps<Props>(), { restrictions: null, withdrawnContact: null, pointLots: null });
const emit = defineEmits<MemberDetailEmits>();

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

/** 구획 차례 — 탈퇴 회원만 데이터가 없는 구획을 뺀다(정상 회원은 비어도 안내로 보인다). 구획 제목과 구역 내비 이름은 같다(준비 중인 구획만 내비에 「(준비 중)」) */
const sections = computed<Section[]>(() => {
    const d = props.detail;
    // 탈퇴한 회원은 줄어든 표시 — 서버가 비운 구획은 없다. 정상 회원은 데이터가 없어도 구획을 두고 안내를 보인다
    const shown = (data: unknown) => d.status !== 'WITHDRAWN' || data !== null;
    const all: { key: string; labelKey: string; show: boolean; pending?: boolean }[] = [
        { key: 'basic', labelKey: 'member.detail.sectionBasic', show: true },
        { key: 'contact', labelKey: 'member.detail.sectionContact', show: shown(d.contact) },
        { key: 'profile', labelKey: 'member.detail.sectionProfile', show: shown(d.profile) },
        { key: 'roles', labelKey: 'member.detail.sectionRoles', show: true },
        { key: 'gradePoint', labelKey: 'member.detail.sectionGradePoint', show: true },
        { key: 'settings', labelKey: 'member.detail.sectionSettings', show: shown(d.settings) },
        { key: 'consent', labelKey: 'member.detail.sectionConsent', show: shown(d.consents) },
        { key: 'identity', labelKey: 'member.detail.sectionIdentity', show: d.status !== 'WITHDRAWN' },
        { key: 'groupBuy', labelKey: 'member.detail.sectionGroupBuy', show: shown(d.groupBuy) },
        { key: 'inbox', labelKey: 'member.detail.sectionInbox', show: shown(d.settings) },
        { key: 'restrictions', labelKey: 'restriction.sectionTitle', show: true },
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
