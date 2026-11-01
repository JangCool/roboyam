<template>
    <!--
        광고 › 광고 기본 설정(`/ads/placements`, 옛 이름 「광고 자리 설정」) — 공개 화면의 광고 자리 셋(02-ad.md 7-2·7-3, 계약 `docs/features/ad-contract.md` 「공개 노출 목업」).
          ① 글 보기 — 켜기 · 유형(한 줄·띠 배너·네모 배너) · 위치(글 카드와 댓글 카드 사이 / 본문 바로 아래)
          ② 날개 배너 — 왼쪽·오른쪽 켜기 · 보일 화면(홈·가젯 페이지·게시판 목록·글 보기)
          ③ 게시판 목록 한 줄 — 켜기 · 일반 글 몇 번째 뒤(1~20)
          ④ 19금 — 19금 게시판·19금 글에도 광고 보이기(기본 끔. 끄면 날개까지 모두 빠짐 · 게시판 「광고 숨김」·소모임은 늘 빠짐)
        짜임(사용자 결정 2026-11-01): 묶음 넷 = 탭 넷(주소 `?tab=post|wings|list|adult`, 모르는 값은 첫 탭). 세 칸 — 왼쪽 탭 메뉴 카드(세로, lg 이상 sticky) · 가운데 고른 탭의 폼 카드 + 저장 줄 · 오른쪽 미리보기 한 곳(lg 이상 sticky).
          좁은 화면(lg 미만)은 한 열 — 메뉴 카드(탭 가로 줄, 넘치면 가로 스크롤) → 폼 카드·저장 줄 → 미리보기.
          미리보기 도식(`AdPlacement*Preview` — 공개 화면 짜임을 회색 상자로 단순하게 그리고 광고 자리만 주 색상)은 고른 탭의 것으로 바뀌고, 저장 전 폼 값을 바로 따라 그린다(저장된 값과 다르면 「저장 전」).
          폼 값은 이 화면(`form`)에 있고 탭 패널은 숨기기만 해서 탭을 바꿔도 입력이 남는다. 탭 단추에 저장 전이면 주황 점, 서버가 거절한 칸이 있으면 빨간 점. 저장이 칸 오류로 실패하면 지금 탭에 오류가 없을 때 첫 오류 탭으로 옮긴다.
        화면 하나로 한 번에 저장한다(`PUT` 한 번 — 본문 열 맨 아래 떠 있는 저장 줄, 폭·위치는 폼 카드와 같다, `forms.md` 2-4). 홈·가젯 페이지·게시판 레이아웃의 광고는 가젯(가젯 편집기)이라 여기 없다.
        권한: 화면 `ad.read` · 저장 `ad.settings`(없으면 입력이 잠기고 저장 줄 대신 읽기 전용 안내). 막는 것은 서버다.
        서버가 거절한 칸(400 `fields.postInline.adType` 등)은 그 칸 아래, 빠진 묶음(`fields.postInline` 등)은 그 묶음 위, 나머지는 저장 줄에 서버 문구 그대로.
        공개 화면은 서버 사본(20초) 뒤에 따라온다. 바꾼 값은 서버가 감사(`AD_PLACEMENT_UPDATE`, 전→후)에 남긴다.
        변경 이력: 제목 옆 「변경 이력」 단추(`ad.read`)가 오른쪽 서랍(`SettingHistoryDrawer` + `SettingHistoryTable`, 메일 서버 설정과 같은 부품)을 연다 — 처음 열 때 받고, 저장에 성공하면 받은 적이 있을 때 첫 쪽부터 다시.
          칸 이름·값(켜짐/꺼짐, 유형·위치·화면 이름, 「5번째 글 뒤」)은 서버 `labelKey` 형식이 정해지기 전이라 칸 코드(`field`)로 화면이 고른다(`UtilAdMessages.placementHistoryItems`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('ad.placementsTitle')" :subtitle="t('ad.placementsSubtitle')">
            <template v-if="canRead" #actions>
                <Button :label="t('settingHistory.open')" icon="pi pi-history" severity="secondary" outlined size="small" data-testid="ad-placements-history-open" @click="openHistory" />
            </template>
        </PageTitle>

        <!-- 읽는 중 -->
        <AppGadget v-if="loading && !saved" :title="t('ad.postInlineSection')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 3" :key="n" height="3rem" />
            </div>
        </AppGadget>

        <!-- 읽기 실패 -->
        <AppGadget v-else-if="!saved || !form" :title="t('ad.postInlineSection')">
            <ErrorState :code="loadError?.status === 403 ? '403' : '!'" :title="t('ad.placementsLoadFailed')" :description="loadError?.message" :retry="loadError?.status !== 403" :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate data-testid="ad-placements-form" @submit.prevent="save">
            <Message v-if="!canEdit" severity="info" :closable="false" size="small">{{ t('ad.placementsReadOnly') }}</Message>

            <!--
                세 칸(lg 이상) — 왼쪽: 탭 메뉴 카드(세로, 스크롤을 따라 붙는다) · 가운데(본문 열): 고른 탭의 폼 카드 → 마지막 저장 시각 → 떠 있는 저장 줄 · 오른쪽: 고른 탭의 미리보기(붙는다).
                메뉴 카드 모양은 화면 안 좌측 서브메뉴(`AppSideNav`, 내 정보 `/me`)를 따른다 — 카드 + 고른 것은 알약 배경. 다만 그 부품은 링크 목록(`aria-current`)이고 좁으면 Select 로 바뀌어,
                여기는 탭(`role="tablist"` · `role="tab"` `aria-selected` `aria-controls` · 패널 `role="tabpanel"` `aria-labelledby`)으로 직접 그린다. 키: 위·아래(·왼·오른) 화살표, Home·End — 초점과 함께 탭이 바뀐다.
                저장 줄은 본문 열 안에 두어 폭·위치가 폼 카드와 같다(`StickySaveBar` 는 sticky 라 부모 폭을 따른다 — 메뉴·미리보기 열 위로 넓게 깔리지 않는다).
                좁은 화면(lg 미만)은 한 열 — 메뉴 카드가 폼 카드 위에서 탭을 가로로 줄 세우고(넘치면 가로 스크롤, 붙지 않는다), 미리보기는 본문 열(저장 줄 포함) 아래로.
            -->
            <div class="grid items-start gap-4 lg:grid-cols-[12rem_minmax(0,1fr)_minmax(18rem,24rem)]">
                <div class="min-w-0 rounded-xl border border-(--line) bg-(--p-content-background) p-2 lg:sticky lg:top-4 lg:p-3" data-testid="ad-placements-menu">
                    <div
                        role="tablist"
                        :aria-label="t('ad.placementsTabs')"
                        :aria-orientation="isWide ? 'vertical' : 'horizontal'"
                        class="flex gap-0.5 overflow-x-auto lg:flex-col lg:overflow-visible"
                        data-testid="ad-placements-tablist"
                        @keydown="onTabKeydown"
                    >
                        <button
                            v-for="item in TABS"
                            :id="tabIdOf(item.key)"
                            :key="item.key"
                            type="button"
                            role="tab"
                            :aria-selected="tab === item.key"
                            :aria-controls="panelIdOf(item.key)"
                            :tabindex="tab === item.key ? 0 : -1"
                            class="flex min-h-9 shrink-0 cursor-pointer items-center gap-2 rounded-lg border-0 bg-transparent px-2.5 text-left text-sm whitespace-nowrap focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary lg:w-full lg:whitespace-normal"
                            :class="tab === item.key ? 'bg-primary-50! font-medium text-primary-700 dark:bg-primary-950! dark:text-primary-300' : 'text-color hover:bg-(--p-content-hover-background)'"
                            :data-testid="`ad-placements-tab-${item.key}`"
                            @click="selectTab(item.key)"
                        >
                            <span class="min-w-0 lg:flex-1">{{ t(item.label) }}</span>
                            <template v-if="invalidOf(item.section)">
                                <span class="inline-block size-1.5 shrink-0 rounded-full bg-red-500" aria-hidden="true" data-testid="ad-placements-tab-invalid" />
                                <span class="sr-only">{{ t('ad.placementsTabInvalid', { n: invalidOf(item.section) }) }}</span>
                            </template>
                            <template v-else-if="changes[item.section] > 0">
                                <span class="inline-block size-1.5 shrink-0 rounded-full bg-amber-500" aria-hidden="true" data-testid="ad-placements-tab-unsaved" />
                                <span class="sr-only">{{ t('ad.placementsTabUnsaved') }}</span>
                            </template>
                        </button>
                    </div>
                </div>

                <div class="flex min-w-0 flex-col gap-3" data-testid="ad-placements-main">
                    <!--
                        설정 카드 — 머리(고른 탭 이름 + 한 줄 설명 + 저장 전 꼬리표, 아래 구분선) · 본문(고른 탭의 패널).
                        머리 글자는 `AppGadget` 제목(text-sm font-medium)을 따르고, 높이(min-h-16)·구분선은 오른쪽 미리보기 카드(`AdPlacementFrame`) 머리와 같아 두 카드의 선이 한 줄에 선다.
                        패널은 숨기기만 한다(v-show) — 탭을 바꿔도 입력이 남는다.
                    -->
                    <div class="min-w-0 rounded-xl border border-(--line) bg-(--p-content-background)" data-testid="ad-placements-tabs">
                        <div class="flex min-h-16 items-center gap-3 border-b border-(--line) px-5 py-3" data-testid="ad-placements-card-head">
                            <div class="flex min-w-0 flex-1 flex-col gap-0.5">
                                <h2 class="m-0 text-sm font-medium" data-testid="ad-placements-card-title">{{ t(current.label) }}</h2>
                                <p class="m-0 text-xs leading-snug text-muted-color">{{ t(current.desc) }}</p>
                            </div>
                            <Tag v-if="changes[current.section] > 0" severity="warn" :value="t('ad.previewUnsaved')" class="shrink-0" data-testid="ad-placements-card-unsaved" />
                        </div>
                        <div class="p-5">
                            <!-- ① 글 보기 -->
                            <div v-show="tab === 'post'" :id="panelIdOf('post')" role="tabpanel" :aria-labelledby="tabIdOf('post')" tabindex="0" class="focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary">
                                <div class="flex min-w-0 flex-col gap-4">
                                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('ad.postInlineLead') }}</p>
                                    <Message v-if="errorOf('postInline')" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ errorOf('postInline') }}</Message>
                                    <div class="flex items-center gap-2">
                                        <ToggleSwitch v-model="form.postInline.enabled" :input-id="`${idBase}-inline-on`" :disabled="locked" @update:model-value="clearServer('postInline.enabled')" />
                                        <label :for="`${idBase}-inline-on`" class="text-sm">{{ t('ad.postInlineEnabled') }}</label>
                                    </div>
                                    <div class="grid gap-x-8 gap-y-4 md:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2" :class="{ 'opacity-60': !form.postInline.enabled }">
                                        <LabeledField :label="t('ad.postInlineType')" :help="form.postInline.adType === 'STRIP_BANNER' ? t('ad.postInlineTypeHint') : ''" :error="errorOf('postInline.adType')">
                                            <template #default="{ id, describedBy, invalid }">
                                                <Select
                                                    v-model="form.postInline.adType"
                                                    :input-id="id"
                                                    :options="inlineTypeOptions"
                                                    option-label="label"
                                                    option-value="value"
                                                    :disabled="locked"
                                                    :invalid="invalid"
                                                    :aria-describedby="describedBy"
                                                    fluid
                                                    @change="clearServer('postInline.adType')"
                                                />
                                            </template>
                                        </LabeledField>
                                        <LabeledField :label="t('ad.postInlinePosition')" :error="errorOf('postInline.position')">
                                            <template #default="{ id, describedBy, invalid }">
                                                <Select
                                                    v-model="form.postInline.position"
                                                    :input-id="id"
                                                    :options="inlinePositionOptions"
                                                    option-label="label"
                                                    option-value="value"
                                                    :disabled="locked"
                                                    :invalid="invalid"
                                                    :aria-describedby="describedBy"
                                                    fluid
                                                    @change="clearServer('postInline.position')"
                                                />
                                            </template>
                                        </LabeledField>
                                    </div>
                                </div>
                            </div>

                            <!-- ② 날개 배너 -->
                            <div v-show="tab === 'wings'" :id="panelIdOf('wings')" role="tabpanel" :aria-labelledby="tabIdOf('wings')" tabindex="0" class="focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary">
                                <div class="flex min-w-0 flex-col gap-4">
                                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('ad.wingsLead') }}</p>
                                    <Message v-if="errorOf('wings')" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ errorOf('wings') }}</Message>
                                    <div class="flex flex-wrap items-center gap-x-6 gap-y-3">
                                        <div class="flex items-center gap-2">
                                            <ToggleSwitch v-model="form.wings.left" :input-id="`${idBase}-wing-left`" :disabled="locked" @update:model-value="clearServer('wings.left')" />
                                            <label :for="`${idBase}-wing-left`" class="text-sm">{{ t('ad.wingsLeft') }}</label>
                                        </div>
                                        <div class="flex items-center gap-2">
                                            <ToggleSwitch v-model="form.wings.right" :input-id="`${idBase}-wing-right`" :disabled="locked" @update:model-value="clearServer('wings.right')" />
                                            <label :for="`${idBase}-wing-right`" class="text-sm">{{ t('ad.wingsRight') }}</label>
                                        </div>
                                    </div>
                                    <fieldset class="m-0 flex flex-col gap-2 border-0 p-0" :class="{ 'opacity-60': !form.wings.left && !form.wings.right }" :aria-describedby="`${idBase}-screens-msg`">
                                        <legend class="mb-1 p-0 text-sm font-medium">{{ t('ad.wingsScreens') }}</legend>
                                        <div class="flex flex-wrap gap-x-5 gap-y-2">
                                            <div v-for="screen in UtilAd.WING_SCREENS" :key="screen" class="flex items-center gap-2">
                                                <Checkbox v-model="form.wings.screens" :value="screen" :input-id="`${idBase}-screen-${screen}`" :disabled="locked" :invalid="!!errorOf('wings.screens')" @update:model-value="clearServer('wings.screens')" />
                                                <label :for="`${idBase}-screen-${screen}`" class="text-sm">{{ t(`ad.wingScreen.${screen}`) }}</label>
                                            </div>
                                        </div>
                                        <p :id="`${idBase}-screens-msg`" class="m-0 text-xs leading-relaxed" :class="errorOf('wings.screens') ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" :role="errorOf('wings.screens') ? 'alert' : undefined">
                                            {{ errorOf('wings.screens') || t('ad.wingsScreensHint') }}
                                        </p>
                                    </fieldset>
                                </div>
                            </div>

                            <!-- ③ 게시판 목록 한 줄 -->
                            <div v-show="tab === 'list'" :id="panelIdOf('list')" role="tabpanel" :aria-labelledby="tabIdOf('list')" tabindex="0" class="focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary">
                                <div class="flex min-w-0 flex-col gap-4">
                                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('ad.boardListLead') }}</p>
                                    <Message v-if="errorOf('boardList')" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ errorOf('boardList') }}</Message>
                                    <div class="flex items-center gap-2">
                                        <ToggleSwitch v-model="form.boardList.enabled" :input-id="`${idBase}-feed-on`" :disabled="locked" @update:model-value="clearServer('boardList.enabled')" />
                                        <label :for="`${idBase}-feed-on`" class="text-sm">{{ t('ad.boardListEnabled') }}</label>
                                    </div>
                                    <div class="max-w-xs" :class="{ 'opacity-60': !form.boardList.enabled }">
                                        <LabeledField :label="t('ad.boardListAfter')" :help="t('ad.boardListAfterHelp', { min: UtilAd.FEED_AFTER.min, max: UtilAd.FEED_AFTER.max })" :error="errorOf('boardList.after')">
                                            <template #default="{ id, describedBy, invalid }">
                                                <InputGroup>
                                                    <InputNumber
                                                        v-model="afterModel"
                                                        :input-id="id"
                                                        :min="UtilAd.FEED_AFTER.min"
                                                        :max="UtilAd.FEED_AFTER.max"
                                                        :allow-empty="false"
                                                        show-buttons
                                                        :readonly="locked"
                                                        :invalid="invalid"
                                                        :aria-describedby="describedBy"
                                                        input-class="w-16 text-right tabular-nums"
                                                    />
                                                    <InputGroupAddon>{{ t('ad.boardListAfterSuffix') }}</InputGroupAddon>
                                                </InputGroup>
                                            </template>
                                        </LabeledField>
                                    </div>
                                </div>
                            </div>

                            <!-- ④ 19금 게시판 -->
                            <div v-show="tab === 'adult'" :id="panelIdOf('adult')" role="tabpanel" :aria-labelledby="tabIdOf('adult')" tabindex="0" class="focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary">
                                <div class="flex min-w-0 flex-col gap-4">
                                    <Message v-if="errorOf('adult')" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ errorOf('adult') }}</Message>
                                    <div class="flex items-start gap-2">
                                        <ToggleSwitch v-model="form.adult.enabled" :input-id="`${idBase}-adult-on`" :disabled="locked" class="shrink-0" :aria-describedby="`${idBase}-adult-help`" @update:model-value="clearServer('adult.enabled')" />
                                        <label :for="`${idBase}-adult-on`" class="flex flex-col gap-0.5">
                                            <span class="text-sm">{{ t('ad.adultEnabled') }}</span>
                                            <span :id="`${idBase}-adult-help`" class="text-xs leading-relaxed" :class="errorOf('adult.enabled') ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" :role="errorOf('adult.enabled') ? 'alert' : undefined">
                                                {{ errorOf('adult.enabled') || t('ad.adultHelp') }}
                                            </span>
                                        </label>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <p class="m-0 text-xs text-muted-color" data-testid="ad-placements-saved-at">
                        {{ saved.updatedAt ? t('ad.placementsLastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy?.name ?? '—' }) : t('ad.placementsNeverSaved') }}
                    </p>

                    <!-- 네 묶음 한 번에 저장(PUT 한 번) — 본문 열 안이라 폼 카드와 같은 폭·위치로 창 아래에 붙는다 -->
                    <StickySaveBar v-if="canEdit" submit revertable :dirty="dirty" :saving="saving" :count="changeCount" :error-count="errorCount" :error="formError" @revert="revert" />
                </div>

                <!-- 고른 탭의 미리보기 한 곳 — 셸의 위쪽 막대는 스크롤 영역(.app-content) 밖이라 작은 여유(top-4)만 두고 붙인다(메뉴 카드와 같은 top) -->
                <aside class="min-w-0 lg:sticky lg:top-4" :aria-label="t('ad.placementsPreviewOf', { section: t(current.label) })" data-testid="ad-placements-preview-pane">
                    <AdPlacementPostPreview v-if="tab === 'post'" :enabled="form.postInline.enabled" :ad-type="form.postInline.adType" :position="form.postInline.position" :unsaved="changes.postInline > 0" />
                    <AdPlacementWingsPreview v-else-if="tab === 'wings'" :left="form.wings.left" :right="form.wings.right" :screens="form.wings.screens" :unsaved="changes.wings > 0" />
                    <AdPlacementListPreview v-else-if="tab === 'list'" :enabled="form.boardList.enabled" :after="form.boardList.after" :unsaved="changes.boardList > 0" />
                    <AdPlacementAdultPreview v-else :form="form" :unsaved="changes.adult > 0" />
                </aside>
            </div>
        </form>

        <!-- 설정 이력 — 제목 옆 단추가 오른쪽 서랍으로 연다(최신이 위, 메일 서버 설정과 같은 부품) -->
        <SettingHistoryDrawer v-model:visible="historyOpen" :hint="t('ad.placementsHistoryHint')">
            <SettingHistoryTable
                :items="historyRows"
                :total="history.total.value"
                :first="history.first.value"
                :page-size="history.size.value"
                :loading="history.loading.value"
                :loaded="history.loaded.value"
                :error="history.error.value"
                :compact="prefs.density === 'compact'"
                @page="history.onPage"
                @retry="history.load"
            />
        </SettingHistoryDrawer>
    </div>
</template>

<script setup lang="ts">
import type { AdPlacementsRequest, AdPlacementsView } from '~/composables/ad/api/useAdApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilAd from '~/utils/util-ad';
import UtilAdMessages from '~/utils/util-ad-messages';

type Section = 'postInline' | 'wings' | 'boardList' | 'adult';
type PlacementTab = 'post' | 'wings' | 'list' | 'adult';

/** 탭 넷(주소 `?tab=` 의 값 · 묶음 · 이름 · 설정 카드 머리의 한 줄 설명) — 차례가 화면 차례이고, 저장 실패 때 첫 오류 탭을 고르는 차례다 */
const TABS: readonly { key: PlacementTab; section: Section; label: string; desc: string }[] = [
    { key: 'post', section: 'postInline', label: 'ad.postInlineSection', desc: 'ad.postInlineDesc' },
    { key: 'wings', section: 'wings', label: 'ad.wingsSection', desc: 'ad.wingsDesc' },
    { key: 'list', section: 'boardList', label: 'ad.boardListSection', desc: 'ad.boardListDesc' },
    { key: 'adult', section: 'adult', label: 'ad.adultSection', desc: 'ad.adultDesc' },
];
const DEFAULT_TAB = TABS[0]!;

const { t, te } = useI18n();
useSeoMeta({ title: () => t('ad.placementsTitle') });

const route = useRoute();
const router = useRouter();

/** 주소의 탭 값 — 모르는 값·없으면 첫 탭 */
function tabOf(value: unknown): PlacementTab {
    return TABS.find((item) => item.key === value)?.key ?? DEFAULT_TAB.key;
}

const tab = ref<PlacementTab>(tabOf(route.query.tab));
const current = computed(() => TABS.find((item) => item.key === tab.value) ?? DEFAULT_TAB);

/** 탭을 바꾼다 — 폼 값은 부모(`form`)에 있고 탭 패널은 숨기기만 해서 입력은 그대로 남는다. 고른 탭은 주소에 남겨 새로고침해도 유지한다 */
function selectTab(value: string): void {
    const next = tabOf(value);
    if (next === tab.value) return;
    tab.value = next;
    void router.replace({ query: { ...route.query, tab: next } });
}

const tabIdBase = useId();
const tabIdOf = (key: PlacementTab) => `${tabIdBase}-tab-${key}`;
const panelIdOf = (key: PlacementTab) => `${tabIdBase}-panel-${key}`;

/** 세 칸(lg = 1024px 이상)인가 — 메뉴가 세로로 서는지(`aria-orientation`). `true` 로 시작한다(SPA 라 데스크톱에서 한 번 깜빡이지 않게, `AppSideNav` 와 같다) */
const isWide = ref(true);
let wideQuery: MediaQueryList | undefined;
const syncWide = () => {
    if (wideQuery) isWide.value = wideQuery.matches;
};
onMounted(() => {
    wideQuery = window.matchMedia('(min-width: 1024px)');
    syncWide();
    wideQuery.addEventListener('change', syncWide);
});
onBeforeUnmount(() => wideQuery?.removeEventListener('change', syncWide));

/** 탭 목록의 키 — 위·왼 화살표는 앞 탭, 아래·오른 화살표는 다음 탭(끝에서 돌아간다), Home·End 는 처음·끝. 초점을 옮기면서 그 탭을 고른다 */
function onTabKeydown(event: KeyboardEvent): void {
    const index = TABS.findIndex((item) => item.key === tab.value);
    let next = -1;
    if (event.key === 'ArrowDown' || event.key === 'ArrowRight') next = (index + 1) % TABS.length;
    else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') next = (index - 1 + TABS.length) % TABS.length;
    else if (event.key === 'Home') next = 0;
    else if (event.key === 'End') next = TABS.length - 1;
    if (next < 0) return;
    event.preventDefault();
    const key = TABS[next]!.key;
    selectTab(key);
    document.getElementById(tabIdOf(key))?.focus();
}

const api = useAdApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();

const idBase = useId();

/** 저장할 수 있나(정의: util-permission-catalog.ts) */
const canEdit = computed(() => can('ad.settings'));
/** 변경 이력을 볼 수 있나 — 화면 조회 권한과 같다 */
const canRead = computed(() => can('ad.read'));
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다
const { prefs } = useLayoutPrefs();

const saved = ref<AdPlacementsView | null>(null);
const form = ref<AdPlacementsRequest | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<Record<string, string>>({});

const locked = computed(() => !canEdit.value || saving.value);

const inlineTypeOptions = computed(() => UtilAd.INLINE_TYPES.map((value) => ({ value, label: t(`ad.postInlineTypeOption.${value}`) })));
const inlinePositionOptions = computed(() => UtilAd.INLINE_POSITIONS.map((value) => ({ value, label: t(`ad.postInlinePositionOption.${value}`) })));

/** `InputNumber` 는 지우는 중에 null 을 줄 수 있다 — 칸 값은 늘 숫자로 둔다 */
const afterModel = computed<number | null>({
    get: () => form.value?.boardList.after ?? null,
    set: (value) => {
        if (!form.value || value === null) return;
        form.value.boardList.after = value;
        clearServer('boardList.after');
    },
});

const changes = computed(() => (form.value && saved.value ? UtilAd.placementChanges(form.value, saved.value) : { postInline: 0, wings: 0, boardList: 0, adult: 0 }));
const changeCount = computed(() => changes.value.postInline + changes.value.wings + changes.value.boardList + changes.value.adult);
const dirty = computed(() => changeCount.value > 0);
const errorCount = computed(() => Object.keys(serverErrors.value).length);

function errorOf(key: string): string {
    return serverErrors.value[key] ?? '';
}

/** 묶음의 오류 칸 수(묶음 자체 오류 포함) */
function invalidOf(section: Section): number {
    return Object.keys(serverErrors.value).filter((key) => key === section || key.startsWith(`${section}.`)).length;
}

function clearServer(key: string): void {
    if (!(key in serverErrors.value)) return;
    const { [key]: _removed, ...rest } = serverErrors.value;
    serverErrors.value = rest;
}

onMounted(() => {
    void load();
});

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.placements());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

function applySaved(view: AdPlacementsView): void {
    saved.value = view;
    form.value = UtilAd.placementsForm(view);
    serverErrors.value = {};
    formError.value = '';
}

function revert(): void {
    if (saved.value) applySaved(saved.value);
}

async function save(): Promise<void> {
    if (!canEdit.value || saving.value || !dirty.value || !form.value) return;
    // 반응형 객체를 그대로 보내지 않는다 — 새로 만든 평범한 객체
    const body = UtilAd.placementsForm(toRaw(form.value));
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        applySaved(await api.savePlacements(body));
        toast.success(t('ad.placementsSaved'));
        // 이력을 한 번이라도 받았으면 첫 쪽부터 다시(새 줄이 맨 위에 생긴다 — 메일 서버 설정과 같다)
        if (history.loaded.value) void history.reloadFirst();
    } catch (e) {
        const failure = toApiError(e);
        const fields: Record<string, string> = {};
        if (failure.code === 'VALIDATION') {
            for (const key of UtilAd.PLACEMENT_FIELDS) {
                const message = failure.field(key);
                if (message) fields[key] = message;
            }
        }
        serverErrors.value = fields;
        formError.value = Object.keys(fields).length ? t('ad.placementsFieldsRejected') : failure.message;
        // 거절된 칸이 지금 탭에 없으면 첫 오류 탭으로 옮긴다(다른 오류 탭은 탭 단추의 빨간 점)
        const firstInvalid = TABS.find((item) => invalidOf(item.section) > 0);
        if (firstInvalid && invalidOf(current.value.section) === 0) selectTab(firstInvalid.key);
    } finally {
        saving.value = false;
    }
}

/* ── 설정 이력 ── */

const history = useSettingHistory((page, size) => api.placementHistory(page, size));
/** 표에 넘길 줄 — 칸 이름·값을 사람이 읽는 글자로(언어를 바꾸면 따라 바뀌게 받은 원본에서 매번 만든다) */
const historyRows = computed(() => UtilAdMessages.placementHistoryItems(t, te, history.rows.value));
/** 이력 서랍 — 처음 열 때 받는다(화면을 열 때마다 이력을 부르지 않는다) */
const historyOpen = ref(false);
function openHistory(): void {
    historyOpen.value = true;
    if (!history.loaded.value && !history.loading.value) void history.load();
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('ad.placementsLeaveTitle'), message: t('ad.placementsLeaveMessage'), confirmLabel: t('ad.placementsLeaveConfirm'), cancelLabel: t('ad.placementsLeaveCancel'), tone: 'danger' });
});
</script>
