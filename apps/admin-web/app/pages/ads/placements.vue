<template>
    <!--
        광고 › 광고 기본 설정(`/ads/placements`, 옛 이름 「광고 자리 설정」) — 공개 화면의 광고 자리 셋(02-ad.md 7-2·7-3, 계약 `docs/features/ad-contract.md` 「공개 노출 목업」).
          ① 글 보기 — 켜기 · 유형(한 줄·띠 배너·네모 배너) · 위치(글 카드와 댓글 카드 사이 / 본문 바로 아래)
          ② 날개 배너 — 왼쪽·오른쪽 켜기 · 보일 화면(홈·가젯 페이지·게시판 목록·글 보기)
          ③ 게시판 목록 한 줄 — 켜기 · 일반 글 몇 번째 뒤(1~20)
          ④ 19금 — 19금 게시판·19금 글에도 광고 보이기(기본 끔. 끄면 날개까지 모두 빠짐 · 게시판 「광고 숨김」·소모임은 늘 빠짐)
          ⑤ 중도 해지 수수료(biz-pause-contract 규칙 7 — V127) — 0~30 정수 %(기본 10), 광고·사업자 핫딜 공통, 신청 건을 낼 때 그 건에 고정. 미리보기는 예시 계산(남은 일할 100,000원 → 수수료 → 환불)
          ⑥ 구글 애드센스(shared V176 — 계약 `docs/features/adsense-contract.md`) — 사용 단계(끔·사이트 확인만·광고 표시) · 게시자 ID · 시험 표시 · 자리별 광고 단위 ID.
             장부 광고(사업자 신청·운영자 등록)가 없는 자리만 애드센스로 채운다. 미리보기는 단계·자리별 채우기 순서·서버가 낼 머리 스크립트와 `/ads.txt` 한 줄.
          ⑦ 상품 가격(`?tab=prices` — 2026-11-13. 요금표 `biz_price_rules`, 계약 `docs/features/ad-price-rules-contract.md` · 서버 shared V208) — 사업자 광고·핫딜 상품 9개를
             묶음 둘(광고: 배너 넷·날개·한 줄 / 핫딜: 글 게시·게시판 상단·말머리 상단)의 상품 카드(`AdPriceRuleRow`)로 보인다. 짜임(사용자 결정 2026-11-13 — 상품마다 보고 관리):
             · 상품 카드 = 보기 전용 지금 값(단가 · 동시 한도(건당은 「한도 없음」) · 최대 일수 · 시작일 선택 범위 · 단위 + 가격 영역 안 사업자 화면 미리보기(때 고르기 Select)) + 신청 받기 스위치(바로 적용 PATCH — 끌 때 확인 창) + [가격 변경].
               예약이 있으면 머리에 「예약 2건 · 다음 ○월 ○일 ○○:○○」, 아래에 그 상품의 예약 목록(적용 시각 차례 · 「○부터 · 단가 50,000원 → 60,000원」 — 전→후는 바로 앞 값(앞 예약이 적용된 뒤) 기준
               · [예약 수정]·[예약 취소](확인 창) — 예약자·예약한 때는 카드에 보이지 않고 변경 이력에만), [변경 내역](최근 적용된 변경 몇 건 — 적용 시각이 지난 예약 · 받기 켬/끔, 접어 둠).
             · [가격 변경]·[고치기] → `AdPriceScheduleDialog`: 적용 시각(그 상품 것 — 기본 내일 00:00 한국 시각, 지난 시각·같은 상품 같은 시각 중복은 칸 오류) · 칸마다 「지금」 옆 「새 값」
               (처음 값 = 그 시각 바로 앞 값, 바꾸지 않는 칸은 그대로) · 사업자 예상 금액(지금 → 새 값, 7일/1건 + 부가세 10% floor) · 안내(신청 낸 시각 기준 · 한도 줄이면 겹침 경고 · 0원 경고 · 뒤에 걸린 예약) → [예약하기].
               한 상품에 예약 여러 건(서로 다른 시각 — 예: 12/1 할인가, 12/31 원래 가격).
             · 오른쪽 열(미리보기 자리, 좁으면 상품 카드 위): 다가오는 예약 전부(가까운 순 — 누르면 그 상품 카드로 옮겨 잠깐 강조 · [예약 모두 취소](확인 창)) · 탭 위: [여러 상품 가격 변경] → `AdPriceBulkDialog`
               (상품 체크(묶음별 전체) · 단가 비율 %/정액 원(기준 = 각 상품 지금 단가, 10원 단위 버림, 0원 아래 금지) · 적용 시각 하나 · 결과 표 → 고른 상품마다 예약 1건).
             · 이 탭은 저장 줄·저장 전 상태가 없고(모든 동작이 바로 서버 — 다이얼로그 예약 · 즉시 스위치 · 취소), 오른쪽 미리보기 열도 없이 넓게 쓴다(예상 금액은 다이얼로그 안). 실패는 다이얼로그 칸·한 줄, 바로 부른 동작은 토스트.
             · 정책 가정(화면 안내): 어느 가격인가 = 사업자가 신청을 낸 시각(신청 건에 견적 고정 — 승인·결제가 뒤여도 그대로) · 한도를 줄여도 잡힌 예약은 그대로(이미 넘어 있는 앞날 수는 서버가 준 겹침 분포로 계산)
               · 받기 끔은 새 신청만 409 `BIZ_PRODUCT_CLOSED` · 운영자 직접 등록에는 최대 일수·시작일 선택 범위·받기 꺼짐이 적용되지 않음(ad-contract 37줄). 서버는 예약을 가격 기간표로 두고 적용 시각이 지나면 읽기에서 바로 지금 값이 된다(목록에서 빠져 「최근 변경」으로, 배치 없음).
             · 요금표를 읽지 못해도 나머지 탭은 쓸 수 있다(이 탭에만 `ErrorState` + 다시 시도). 권한: 보기 `ad.read` · 단추·스위치 `ad.settings`(없으면 숨기고 보기만).
        짜임(사용자 결정 2026-11-01): 묶음 다섯 = 탭 다섯(주소 `?tab=post|wings|list|adult|refund`, 모르는 값은 첫 탭) — 2026-11-10 여섯째 `adsense`, 2026-11-13 일곱째 `prices` 를 더했다. 세 칸 — 왼쪽 탭 메뉴 카드(세로, lg 이상 sticky) · 가운데 고른 탭의 폼 카드 + 저장 줄 · 오른쪽 미리보기 한 곳(lg 이상 sticky — 「상품 가격」 탭은 이 자리에 다가오는 예약).
          좁은 화면(lg 미만)은 한 열 — 메뉴 카드(탭 가로 줄, 넘치면 가로 스크롤) → 폼 카드·저장 줄 → 미리보기.
          미리보기 도식(`AdPlacement*Preview` — 공개 화면 짜임을 회색 상자로 단순하게 그리고 광고 자리만 주 색상)은 고른 탭의 것으로 바뀌고, 저장 전 폼 값을 바로 따라 그린다(저장된 값과 다르면 「저장 전」).
          폼 값은 이 화면(`form`)에 있고 탭 패널은 숨기기만 해서 탭을 바꿔도 입력이 남는다. 탭 단추에 저장 전이면 주황 점, 서버가 거절한 칸이 있으면 빨간 점. 저장이 칸 오류로 실패하면 지금 탭에 오류가 없을 때 첫 오류 탭으로 옮긴다.
        광고 자리 설정(①~⑥)은 화면 하나로 한 번에 저장한다(`PUT` 한 번 — 본문 열 맨 아래 떠 있는 저장 줄, 폭·위치는 폼 카드와 같다, `forms.md` 2-4). 홈·가젯 페이지·게시판 레이아웃의 광고는 가젯(가젯 편집기)이라 여기 없다.
        권한: 화면 `ad.read` · 저장 `ad.settings`(없으면 입력이 잠기고 저장 줄 대신 읽기 전용 안내). 막는 것은 서버다.
        서버가 거절한 칸(400 `fields.postInline.adType`·`fields.refund.stopFeeRate` 등)은 그 칸 아래, 빠진 묶음(`fields.postInline` 등)은 그 묶음 위, 나머지는 저장 줄에 서버 문구 그대로.
        공개 화면은 서버 사본(20초) 뒤에 따라온다. 바꾼 값은 서버가 감사(`AD_PLACEMENT_UPDATE` · 상품 가격은 `AD_PRICE_SCHEDULE_CREATE|UPDATE|CANCEL`·적용은 시스템 행위자 · 받기 `AD_PRICE_ACCEPTING_UPDATE` — 가정, 전→후)에 남긴다.
        변경 이력: 제목 옆 「변경 이력」 단추(`ad.read`)가 오른쪽 서랍(`SettingHistoryDrawer` + `SettingHistoryTable`, 메일 서버 설정과 같은 부품)을 연다 — 처음 열 때 받고, 저장에 성공하면 받은 적이 있을 때 첫 쪽부터 다시.
          서랍 위 고르기(광고 자리 설정 / 상품 가격 — 이력 API 가 따로)로 바꿔 보고, 「상품 가격」 탭에서 열면 상품 가격 이력이 먼저 보인다(예약 만들기·고치기·취소·적용됨·받기).
          칸 이름·값(켜짐/꺼짐, 유형·위치·화면 이름, 「5번째 글 뒤」)은 서버 `labelKey` 형식이 정해지기 전이라 칸 코드(`field`)로 화면이 고른다(`UtilAdMessages.placementHistoryItems`,
          상품 가격은 `schedules.<상품>.effectiveAt`·`schedules.<상품>.<칸>`·`rules.<상품>.<칸>` → 「넓은 배너 · 예약 적용 시각」·「넓은 배너 · 단가(예약)」·「50,000원」 — `UtilAdMessages.priceRuleHistoryItems`).
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
            <div class="grid items-start gap-4" :class="tab === 'prices' ? 'lg:grid-cols-[12rem_minmax(0,1fr)_minmax(14rem,17rem)]' : 'lg:grid-cols-[12rem_minmax(0,1fr)_minmax(18rem,24rem)]'">
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

                <div class="flex min-w-0 flex-col gap-3" :class="{ 'max-lg:order-2': tab === 'prices' }" data-testid="ad-placements-main">
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
                                    <!-- 한 쪽 광고 수 · 두 광고 사이 글 수(V155 — 2개까지, 사용자 2026-11-06) -->
                                    <div class="flex flex-col gap-1" :class="{ 'opacity-60': !form.boardList.enabled }">
                                        <span :id="`${idBase}-feed-count`" class="text-sm font-medium">{{ t('ad.boardListCount') }}</span>
                                        <SelectButton
                                            :model-value="form.boardList.count"
                                            :options="feedCountOptions"
                                            option-label="label"
                                            option-value="value"
                                            :allow-empty="false"
                                            :disabled="locked"
                                            size="small"
                                            :aria-labelledby="`${idBase}-feed-count`"
                                            @update:model-value="onFeedCount"
                                        />
                                        <small v-if="errorOf('boardList.count')" class="text-xs text-red-600 dark:text-red-400">{{ errorOf('boardList.count') }}</small>
                                    </div>
                                    <div v-if="form.boardList.count > 1" class="max-w-xs" :class="{ 'opacity-60': !form.boardList.enabled }">
                                        <LabeledField :label="t('ad.boardListGap')" :help="t('ad.boardListGapHelp', { min: UtilAd.FEED_GAP.min, max: UtilAd.FEED_GAP.max })" :error="errorOf('boardList.gap')">
                                            <template #default="{ id, describedBy, invalid }">
                                                <InputGroup>
                                                    <InputNumber
                                                        v-model="gapModel"
                                                        :input-id="id"
                                                        :min="UtilAd.FEED_GAP.min"
                                                        :max="UtilAd.FEED_GAP.max"
                                                        :allow-empty="false"
                                                        show-buttons
                                                        :readonly="locked"
                                                        :invalid="invalid"
                                                        :aria-describedby="describedBy"
                                                        input-class="w-16 text-right tabular-nums"
                                                    />
                                                    <InputGroupAddon>{{ t('ad.boardListGapSuffix') }}</InputGroupAddon>
                                                </InputGroup>
                                            </template>
                                        </LabeledField>
                                    </div>
                                </div>
                            </div>

                            <!--
                                ⑦ 상품 가격 — 안내 상자(접어 둠) → [여러 상품 가격 변경]
                                → 묶음 둘(광고·핫딜)의 상품 카드(`AdPriceRuleRow` — 보기 전용 지금 값 · 받기 스위치 · [가격 변경] · 그 상품의 예약 목록 · [변경 내역]).
                                요금표는 따로 읽어(`api.priceRules`) 이 탭만 읽는 중·실패를 그린다. 이 탭은 저장 줄을 쓰지 않는다(예약 다이얼로그 · 즉시 스위치 · 취소). 다가오는 예약은 오른쪽 열(미리보기 자리).
                            -->
                            <div v-show="tab === 'prices'" :id="panelIdOf('prices')" role="tabpanel" :aria-labelledby="tabIdOf('prices')" tabindex="0" class="focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary">
                                <div class="flex min-w-0 flex-col gap-5" data-testid="ad-placements-prices">
                                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('ad.pricesLead') }}</p>

                                    <div v-if="priceLoading && !priceSaved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                                        <Skeleton v-for="n in 3" :key="n" height="7rem" />
                                    </div>
                                    <ErrorState
                                        v-else-if="!priceSaved"
                                        :code="priceLoadError?.status === 403 ? '403' : '!'"
                                        :title="t('ad.pricesLoadFailed')"
                                        :description="priceLoadError?.message"
                                        :retry="priceLoadError?.status !== 403"
                                        :retrying="priceLoading"
                                        @retry="loadPrices"
                                    />
                                    <template v-else>
                                        <!-- 알아 둘 것(정책 가정) — 접어 둔다 -->
                                        <details class="rounded-lg bg-surface-100 px-4 py-3 dark:bg-surface-950" data-testid="ad-placements-prices-guide">
                                            <summary class="cursor-pointer text-xs font-semibold">
                                                <i class="pi pi-info-circle mr-1.5 text-[0.6875rem]" aria-hidden="true" />{{ t('ad.pricesGuideTitle') }}
                                            </summary>
                                            <ul class="m-0 mt-2 flex flex-col gap-1.5 pl-4 text-xs leading-relaxed text-muted-color">
                                                <li>{{ t('ad.pricesGuideSchedule') }}</li>
                                                <li>{{ t('ad.pricesGuideWhichPrice') }}</li>
                                                <li>{{ t('ad.pricesGuideAcceptingNow') }}</li>
                                                <li>{{ t('ad.pricesGuideCapacity') }}</li>
                                                <li>{{ t('ad.pricesGuideOperator') }}</li>
                                                <li>{{ t('ad.pricesGuideHistory') }}</li>
                                            </ul>
                                        </details>

                                        <div v-if="canEdit" class="flex flex-wrap items-center gap-2">
                                            <Button :label="t('ad.priceBulkOpen')" icon="pi pi-percentage" size="small" severity="secondary" outlined data-testid="ad-placements-price-bulk-open" @click="openBulk" />
                                            <span class="text-xs text-muted-color">{{ t('ad.priceBulkOpenHelp') }}</span>
                                        </div>

                                        <section v-for="group in priceGroups" :key="group.key" class="flex min-w-0 flex-col gap-3" :aria-labelledby="`${idBase}-price-group-${group.key}`" :data-testid="`ad-placements-price-group-${group.key}`">
                                            <div class="flex flex-col gap-0.5">
                                                <h3 :id="`${idBase}-price-group-${group.key}`" class="m-0 text-sm font-medium">{{ t(`ad.priceGroup.${group.key}`) }}</h3>
                                                <p class="m-0 text-xs text-muted-color">{{ t(`ad.priceGroupDesc.${group.key}`) }}</p>
                                            </div>
                                            <AdPriceRuleRow
                                                v-for="rule in group.rules"
                                                :id="priceRowId(rule.product)"
                                                :key="rule.product"
                                                :rule="rule"
                                                :manageable="canEdit"
                                                :busy="priceBusy !== null"
                                                :highlighted="highlightedProduct === rule.product"
                                                class="scroll-mt-4"
                                                @change-price="openSchedule(rule.product, null)"
                                                @edit-schedule="(s) => openSchedule(rule.product, s)"
                                                @cancel-schedule="(s) => cancelSchedule(rule.product, s)"
                                                @set-accepting="(v) => setAccepting(rule.product, v)"
                                            />
                                        </section>
                                    </template>
                                </div>
                            </div>

                            <!-- ⑤ 중도 해지 수수료 -->
                            <div v-show="tab === 'refund'" :id="panelIdOf('refund')" role="tabpanel" :aria-labelledby="tabIdOf('refund')" tabindex="0" class="focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary">
                                <div class="flex min-w-0 flex-col gap-4">
                                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('ad.refundLead') }}</p>
                                    <Message v-if="errorOf('refund')" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ errorOf('refund') }}</Message>
                                    <div class="max-w-xs">
                                        <LabeledField
                                            :label="t('ad.refundRate')"
                                            :help="t('ad.refundRateHelp', { min: UtilAd.STOP_FEE_RATE.min, max: UtilAd.STOP_FEE_RATE.max, def: UtilAd.STOP_FEE_RATE.def })"
                                            :error="errorOf('refund.stopFeeRate')"
                                        >
                                            <template #default="{ id, describedBy, invalid }">
                                                <InputGroup>
                                                    <InputNumber
                                                        v-model="feeRateModel"
                                                        :input-id="id"
                                                        :min="UtilAd.STOP_FEE_RATE.min"
                                                        :max="UtilAd.STOP_FEE_RATE.max"
                                                        :max-fraction-digits="0"
                                                        :allow-empty="false"
                                                        show-buttons
                                                        :readonly="locked"
                                                        :invalid="invalid"
                                                        :aria-describedby="describedBy"
                                                        input-class="w-16 text-right tabular-nums"
                                                        data-testid="ad-placements-stop-fee-rate"
                                                    />
                                                    <InputGroupAddon>{{ t('ad.refundRateSuffix') }}</InputGroupAddon>
                                                </InputGroup>
                                            </template>
                                        </LabeledField>
                                    </div>
                                </div>
                            </div>

                            <!--
                                ⑥ 구글 애드센스. 장부 광고(사업자 신청·운영자 등록)가 없는 자리만 채운다.
                                사용 단계 · 게시자 ID(「사이트 확인만」·「광고 표시」면 필수) · 시험 표시 · 자리별 광고 단위 ID(비우면 그 자리는 애드센스 안 씀) · 준비 안내.
                                칸 검사는 화면이 미리 알려 주고(`UtilAdSense.problems` — 형식 오류는 칸을 떠난 뒤·저장을 누른 뒤) 판정은 서버다(400 `fields.adsense.*` 는 그 칸 아래).
                                붙여 넣은 값은 칸을 떠날 때 다듬는다 — `pub-…` → `ca-pub-…`, 광고 코드 통째면 그 안의 게시자 ID·단위 ID·레이아웃 키만.
                            -->
                            <div v-show="tab === 'adsense'" :id="panelIdOf('adsense')" role="tabpanel" :aria-labelledby="tabIdOf('adsense')" tabindex="0" class="focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary">
                                <div class="flex min-w-0 flex-col gap-5" data-testid="ad-placements-adsense">
                                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('ad.adsenseLead') }}</p>
                                    <Message v-if="errorOf('adsense')" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ errorOf('adsense') }}</Message>

                                    <!-- 사용 단계 -->
                                    <fieldset class="m-0 flex flex-col gap-2.5 border-0 p-0">
                                        <legend class="mb-1 p-0 text-sm font-medium">{{ t('ad.adsenseMode') }}</legend>
                                        <div v-for="mode in UtilAdSense.MODES" :key="mode" class="flex items-start gap-2">
                                            <RadioButton
                                                v-model="form.adsense.mode"
                                                :value="mode"
                                                :input-id="`${idBase}-adsense-mode-${mode}`"
                                                :disabled="locked"
                                                class="mt-0.5 shrink-0"
                                                :data-testid="`ad-placements-adsense-mode-${mode}`"
                                                @update:model-value="clearServer('adsense.mode')"
                                            />
                                            <label :for="`${idBase}-adsense-mode-${mode}`" class="flex min-w-0 flex-col gap-0.5">
                                                <span class="text-sm">{{ t(`ad.adsenseModeOption.${mode}`) }}</span>
                                                <span class="text-xs leading-relaxed text-muted-color">{{ t(`ad.adsenseModeHelp.${mode}`) }}</span>
                                            </label>
                                        </div>
                                        <small v-if="errorOf('adsense.mode')" class="text-xs text-red-600 dark:text-red-400">{{ errorOf('adsense.mode') }}</small>
                                    </fieldset>

                                    <!-- 게시자 ID · 시험 표시 -->
                                    <div class="grid gap-x-8 gap-y-4 md:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
                                        <LabeledField
                                            :label="t('ad.adsenseClient')"
                                            :hint="form.adsense.mode !== 'OFF' ? t('ad.adsenseRequired') : ''"
                                            :help="t('ad.adsenseClientHelp')"
                                            :error="adsenseError('adsense.client')"
                                        >
                                            <template #default="{ id, describedBy, invalid }">
                                                <InputText
                                                    :id="id"
                                                    v-model="form.adsense.client"
                                                    :readonly="locked"
                                                    :invalid="invalid"
                                                    :aria-describedby="describedBy"
                                                    placeholder="ca-pub-0000000000000000"
                                                    autocomplete="off"
                                                    spellcheck="false"
                                                    class="font-mono text-sm"
                                                    fluid
                                                    data-testid="ad-placements-adsense-client"
                                                    @update:model-value="clearServer('adsense.client')"
                                                    @blur="onAdsenseBlur('adsense.client')"
                                                />
                                            </template>
                                        </LabeledField>
                                        <div class="flex items-start gap-2 md:pt-7 lg:pt-0 xl:pt-7">
                                            <ToggleSwitch v-model="form.adsense.adtest" :input-id="`${idBase}-adsense-adtest`" :disabled="locked" class="shrink-0" :aria-describedby="`${idBase}-adsense-adtest-help`" @update:model-value="clearServer('adsense.adtest')" />
                                            <label :for="`${idBase}-adsense-adtest`" class="flex flex-col gap-0.5">
                                                <span class="text-sm">{{ t('ad.adsenseAdtest') }}</span>
                                                <span :id="`${idBase}-adsense-adtest-help`" class="text-xs leading-relaxed" :class="errorOf('adsense.adtest') ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">
                                                    {{ errorOf('adsense.adtest') || t('ad.adsenseAdtestHelp') }}
                                                </span>
                                            </label>
                                        </div>
                                    </div>

                                    <!-- 자리별 광고 단위 ID -->
                                    <fieldset class="m-0 flex flex-col gap-3 border-0 p-0" :class="{ 'opacity-60': form.adsense.mode !== 'SHOW' }" :aria-describedby="`${idBase}-adsense-units-help`">
                                        <legend class="mb-1 p-0 text-sm font-medium">{{ t('ad.adsenseUnits') }}</legend>
                                        <p :id="`${idBase}-adsense-units-help`" class="m-0 -mt-1 text-xs leading-relaxed text-muted-color">{{ t('ad.adsenseUnitsHelp') }}</p>
                                        <Message v-if="errorOf('adsense.units')" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ errorOf('adsense.units') }}</Message>
                                        <div class="grid gap-x-6 gap-y-4 md:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
                                            <LabeledField v-for="slot in UtilAdSense.DISPLAY_SLOTS" :key="slot" :label="t(`ad.adsenseUnit.${slot}`)" :hint="t(`ad.adsenseUnitHint.${slot}`)" :error="adsenseError(`adsense.units.${slot}`)">
                                                <template #default="{ id, describedBy, invalid }">
                                                    <InputText
                                                        :id="id"
                                                        v-model="form.adsense.units[slot]"
                                                        :readonly="locked"
                                                        :invalid="invalid"
                                                        :aria-describedby="describedBy"
                                                        inputmode="numeric"
                                                        placeholder="1234567890"
                                                        autocomplete="off"
                                                        spellcheck="false"
                                                        class="font-mono text-sm tabular-nums"
                                                        fluid
                                                        :data-testid="`ad-placements-adsense-unit-${slot}`"
                                                        @update:model-value="clearServer(`adsense.units.${slot}`)"
                                                        @blur="onAdsenseBlur(`adsense.units.${slot}`)"
                                                    />
                                                </template>
                                            </LabeledField>
                                        </div>
                                        <!-- 게시판 목록 한 줄(인피드) — 단위 ID + 레이아웃 키 -->
                                        <div class="flex flex-col gap-2 rounded-lg border border-(--line) p-3">
                                            <div class="flex flex-col gap-0.5">
                                                <span class="text-sm font-medium">{{ t('ad.adsenseUnit.BOARD_LIST') }}</span>
                                                <span class="text-xs leading-relaxed text-muted-color">{{ t('ad.adsenseUnitHint.BOARD_LIST') }}</span>
                                            </div>
                                            <div class="grid gap-x-6 gap-y-3 md:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
                                                <LabeledField :label="t('ad.adsenseFeedSlot')" :error="adsenseError('adsense.units.BOARD_LIST.slot')">
                                                    <template #default="{ id, describedBy, invalid }">
                                                        <InputText
                                                            :id="id"
                                                            v-model="form.adsense.units.BOARD_LIST.slot"
                                                            :readonly="locked"
                                                            :invalid="invalid"
                                                            :aria-describedby="describedBy"
                                                            inputmode="numeric"
                                                            placeholder="1234567890"
                                                            autocomplete="off"
                                                            spellcheck="false"
                                                            class="font-mono text-sm tabular-nums"
                                                            fluid
                                                            data-testid="ad-placements-adsense-unit-BOARD_LIST"
                                                            @update:model-value="clearServer('adsense.units.BOARD_LIST.slot')"
                                                            @blur="onAdsenseBlur('adsense.units.BOARD_LIST.slot')"
                                                        />
                                                    </template>
                                                </LabeledField>
                                                <LabeledField :label="t('ad.adsenseFeedLayoutKey')" :help="t('ad.adsenseFeedLayoutKeyHelp')" :error="adsenseError('adsense.units.BOARD_LIST.layoutKey')">
                                                    <template #default="{ id, describedBy, invalid }">
                                                        <InputText
                                                            :id="id"
                                                            v-model="form.adsense.units.BOARD_LIST.layoutKey"
                                                            :readonly="locked"
                                                            :invalid="invalid"
                                                            :aria-describedby="describedBy"
                                                            placeholder="-fb+5w+4e-db+86"
                                                            autocomplete="off"
                                                            spellcheck="false"
                                                            class="font-mono text-sm"
                                                            fluid
                                                            data-testid="ad-placements-adsense-layout-key"
                                                            @update:model-value="clearServer('adsense.units.BOARD_LIST.layoutKey')"
                                                            @blur="onAdsenseBlur('adsense.units.BOARD_LIST.layoutKey')"
                                                        />
                                                    </template>
                                                </LabeledField>
                                            </div>
                                        </div>
                                    </fieldset>

                                    <!-- 준비 안내 -->
                                    <section class="flex flex-col gap-2 rounded-lg bg-surface-100 px-4 py-3 dark:bg-surface-950" :aria-labelledby="`${idBase}-adsense-guide`" data-testid="ad-placements-adsense-guide">
                                        <h3 :id="`${idBase}-adsense-guide`" class="m-0 inline-flex items-center gap-1.5 text-xs font-semibold">
                                            <i class="pi pi-info-circle text-[0.6875rem]" aria-hidden="true" />{{ t('ad.adsenseGuideTitle') }}
                                        </h3>
                                        <ol class="m-0 flex flex-col gap-1.5 pl-4 text-xs leading-relaxed text-muted-color">
                                            <li>{{ t('ad.adsenseGuideApproval') }}</li>
                                            <li>{{ t('ad.adsenseGuideAdsTxt') }}</li>
                                            <li>{{ t('ad.adsenseGuideConsent') }}</li>
                                            <li>{{ t('ad.adsenseGuidePrivacy') }}</li>
                                            <li>{{ t('ad.adsenseGuideExcluded') }}</li>
                                        </ol>
                                    </section>
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

                    <!-- 마지막 저장 — 상품 가격 탭은 요금표의 것(자원이 따로라 시각도 따로) -->
                    <p v-if="savedAtText" class="m-0 text-xs text-muted-color" data-testid="ad-placements-saved-at">{{ savedAtText }}</p>

                    <!-- 저장 줄 — 광고 자리 설정 여섯 묶음 PUT 한 번. 「상품 가격」 탭은 저장 줄을 쓰지 않는다(예약 다이얼로그 · 즉시 스위치 · 취소). 본문 열 안이라 폼 카드와 같은 폭·위치로 창 아래에 붙는다 -->
                    <StickySaveBar v-if="canEdit && tab !== 'prices'" submit revertable :dirty="dirty" :saving="saving" :disabled="localErrorCount > 0" :count="changeCount" :error-count="errorCount" :error="formError" @revert="revert" />
                </div>

                <!-- 고른 탭의 미리보기 한 곳 — 셸의 위쪽 막대는 스크롤 영역(.app-content) 밖이라 작은 여유(top-4)만 두고 붙인다(메뉴 카드와 같은 top). 「상품 가격」 탭은 이 자리에 다가오는 예약(상품 미리보기는 상품 카드의 가격 영역 안) -->
                <!--
                    「상품 가격」 탭의 오른쪽 열 = 다가오는 예약 카드 한 장(모든 상품, 가까운 순) — 머리(제목 · [예약 모두 취소], 아래 구분선) · 본문에 예약마다 작은 카드
                    (적용 시각 · 상품 이름 · 바뀌는 값 줄마다, 누르면 그 상품 카드로 옮겨 강조) · 빈 안내.
                    lg 이상은 붙어 있고(창보다 길면 열 안에서 스크롤), 좁은 화면은 탭 메뉴 바로 아래·상품 카드 위로 온다(order).
                -->
                <aside v-if="tab === 'prices'" class="min-w-0 max-lg:order-1 lg:sticky lg:top-4" :aria-labelledby="`${idBase}-price-upcoming`" data-testid="ad-placements-price-upcoming">
                    <section class="flex min-w-0 flex-col rounded-xl border border-(--line) bg-(--p-content-background) lg:max-h-[calc(100vh-2rem)]">
                        <div class="flex min-h-16 shrink-0 items-center gap-2 border-b border-(--line) px-4 py-3">
                            <h3 :id="`${idBase}-price-upcoming`" class="m-0 inline-flex min-w-0 flex-1 items-center gap-1.5 text-sm font-medium">
                                <i class="pi pi-clock text-xs text-amber-600 dark:text-amber-400" aria-hidden="true" />{{ t('ad.priceUpcomingTitle', { n: upcoming.length }) }}
                            </h3>
                            <Button
                                v-if="canEdit && upcoming.length"
                                :label="t('ad.priceSchedulesCancelAll')"
                                icon="pi pi-times"
                                size="small"
                                severity="danger"
                                text
                                :disabled="priceBusy !== null"
                                data-testid="ad-placements-price-cancel-all"
                                @click="cancelAllSchedules"
                            />
                        </div>
                        <div class="flex min-h-0 flex-col gap-2.5 overflow-y-auto p-3">
                            <div v-if="priceLoading && !priceSaved" class="flex flex-col gap-2.5" role="status" :aria-label="t('common.loading')">
                                <Skeleton v-for="n in 3" :key="n" height="5.5rem" border-radius="0.5rem" />
                            </div>
                            <p v-else-if="!priceSaved" class="m-0 px-1 py-5 text-center text-xs text-muted-color">{{ t('ad.pricesLoadFailed') }}</p>
                            <p v-else-if="!upcoming.length" class="m-0 px-1 py-5 text-center text-xs leading-relaxed text-muted-color" data-testid="ad-placements-price-upcoming-empty">
                                {{ t('ad.priceUpcomingEmpty') }}
                            </p>
                            <template v-else>
                                <p class="m-0 px-1 text-xs text-muted-color">{{ t('ad.priceUpcomingHint') }}</p>
                                <ul class="m-0 flex list-none flex-col gap-2 p-0">
                                    <li v-for="item in upcoming" :key="item.schedule.id">
                                        <button
                                            type="button"
                                            class="flex w-full cursor-pointer flex-col gap-2 rounded-lg border bg-surface-50 p-3 text-left text-color transition-colors hover:border-amber-400 focus-visible:outline-2 focus-visible:outline-primary dark:bg-surface-950 dark:hover:border-amber-600"
                                            :class="highlightedProduct === item.product ? 'border-amber-400 dark:border-amber-600' : 'border-(--line)'"
                                            data-testid="ad-placements-price-upcoming-card"
                                            @click="focusProduct(item.product)"
                                        >
                                            <span class="flex flex-wrap items-center justify-between gap-x-2 gap-y-1">
                                                <span class="text-sm font-semibold">{{ t(UtilAdPrice.nameKey(item.product)) }}</span>
                                                <span class="rounded-md bg-amber-100 px-1.5 py-0.5 text-xs font-semibold tabular-nums text-amber-800 dark:bg-amber-950/60 dark:text-amber-300">{{ t('ad.priceFrom', { at: time.full(item.schedule.effectiveAt) }) }}</span>
                                            </span>
                                            <span v-if="!item.diff.length" class="text-xs text-muted-color">{{ t('ad.priceScheduleNoDiff') }}</span>
                                            <span v-else class="flex flex-col gap-1 border-t border-(--line) pt-2">
                                                <span v-for="d in item.diff" :key="d.field" class="flex flex-wrap items-baseline justify-between gap-x-2 text-xs">
                                                    <span class="text-muted-color">{{ t(`ad.priceField.${d.field}`) }}</span>
                                                    <span class="tabular-nums [overflow-wrap:anywhere]">{{ diffValue(d.field, d.before) }} → <b class="font-semibold">{{ diffValue(d.field, d.after) }}</b></span>
                                                </span>
                                            </span>
                                        </button>
                                    </li>
                                </ul>
                            </template>
                        </div>
                    </section>
                </aside>
                <aside v-else class="min-w-0 lg:sticky lg:top-4" :aria-label="t('ad.placementsPreviewOf', { section: t(current.label) })" data-testid="ad-placements-preview-pane">
                    <AdPlacementPostPreview v-if="tab === 'post'" :enabled="form.postInline.enabled" :ad-type="form.postInline.adType" :position="form.postInline.position" :unsaved="changes.postInline > 0" />
                    <AdPlacementWingsPreview v-else-if="tab === 'wings'" :left="form.wings.left" :right="form.wings.right" :screens="form.wings.screens" :unsaved="changes.wings > 0" />
                    <AdPlacementListPreview v-else-if="tab === 'list'" :enabled="form.boardList.enabled" :after="form.boardList.after" :unsaved="changes.boardList > 0" />
                    <AdPlacementAdultPreview v-else-if="tab === 'adult'" :form="form" :unsaved="changes.adult > 0" />
                    <AdPlacementAdSensePreview v-else-if="tab === 'adsense'" :adsense="form.adsense" :unsaved="changes.adsense > 0" />
                    <AdPlacementRefundPreview v-else :rate="form.refund.stopFeeRate" :unsaved="changes.refund > 0" />
                </aside>
            </div>
        </form>

        <!-- 상품 하나의 가격 변경 예약(새 예약 · 고치기) -->
        <AdPriceScheduleDialog
            v-model:visible="scheduleDialog.open"
            :rule="scheduleDialogRule"
            :schedule="scheduleDialog.schedule"
            :busy="scheduleDialog.busy"
            :error="scheduleDialog.error"
            :field-errors="scheduleDialog.fieldErrors"
            @submit="submitSchedule"
            @clear-error="(key) => clearDialogError(scheduleDialog, key)"
        />
        <!-- 여러 상품 가격 변경 -->
        <AdPriceBulkDialog
            v-model:visible="bulkDialog.open"
            :rules="priceSaved?.rules ?? []"
            :busy="bulkDialog.busy"
            :error="bulkDialog.error"
            :field-errors="bulkDialog.fieldErrors"
            @submit="submitBulk"
            @clear-error="(key) => clearDialogError(bulkDialog, key)"
        />

        <!-- 설정 이력 — 제목 옆 단추가 오른쪽 서랍으로 연다(최신이 위, 메일 서버 설정과 같은 부품). 광고 자리 설정 / 상품 가격 이력은 API 가 따로라 위에서 고른다 -->
        <SettingHistoryDrawer v-model:visible="historyOpen" :hint="historyKind === 'prices' ? t('ad.pricesHistoryHint') : t('ad.placementsHistoryHint')">
            <template #filters>
                <SelectButton
                    :model-value="historyKind"
                    :options="historyKindOptions"
                    option-label="label"
                    option-value="value"
                    :allow-empty="false"
                    size="small"
                    :aria-label="t('ad.historyKindLabel')"
                    data-testid="ad-placements-history-kind"
                    @update:model-value="onHistoryKind"
                />
            </template>
            <SettingHistoryTable
                :key="historyKind"
                :items="historyRows"
                :total="activeHistory.total.value"
                :first="activeHistory.first.value"
                :page-size="activeHistory.size.value"
                :loading="activeHistory.loading.value"
                :loaded="activeHistory.loaded.value"
                :error="activeHistory.error.value"
                :compact="prefs.density === 'compact'"
                @page="activeHistory.onPage"
                @retry="activeHistory.load"
            />
        </SettingHistoryDrawer>
    </div>
</template>

<script setup lang="ts">
import type { AdPlacementsRequest, AdPlacementsView, AdPriceBulkScheduleRequest, AdPriceProduct, AdPriceRule, AdPriceRulesView, AdPriceSchedule, AdPriceScheduleRequest } from '~/composables/ad/api/useAdApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilAd from '~/utils/util-ad';
import UtilAdMessages from '~/utils/util-ad-messages';
import UtilAdPrice, { type AdPriceScheduledField } from '~/utils/util-ad-price';
import UtilAdSense from '~/utils/util-adsense';

type Section = 'postInline' | 'wings' | 'boardList' | 'adult' | 'prices' | 'refund' | 'adsense';
type PlacementTab = 'post' | 'wings' | 'list' | 'adult' | 'prices' | 'refund' | 'adsense';

/** 탭 일곱(주소 `?tab=` 의 값 · 묶음 · 이름 · 설정 카드 머리의 한 줄 설명) — 차례가 화면 차례이고, 저장 실패 때 첫 오류 탭을 고르는 차례다 */
const TABS: readonly { key: PlacementTab; section: Section; label: string; desc: string }[] = [
    { key: 'post', section: 'postInline', label: 'ad.postInlineSection', desc: 'ad.postInlineDesc' },
    { key: 'wings', section: 'wings', label: 'ad.wingsSection', desc: 'ad.wingsDesc' },
    { key: 'list', section: 'boardList', label: 'ad.boardListSection', desc: 'ad.boardListDesc' },
    { key: 'adult', section: 'adult', label: 'ad.adultSection', desc: 'ad.adultDesc' },
    // 상품 가격(요금표) · 중도 해지 수수료 — 돈 관련 둘을 나란히
    { key: 'prices', section: 'prices', label: 'ad.pricesSection', desc: 'ad.pricesDesc' },
    { key: 'refund', section: 'refund', label: 'ad.refundSection', desc: 'ad.refundDesc' },
    // 구글 애드센스
    { key: 'adsense', section: 'adsense', label: 'ad.adsenseSection', desc: 'ad.adsenseDesc' },
];
const DEFAULT_TAB = TABS[0]!;


const { t, te, locale } = useI18n();
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

/** 목록 한 쪽 광고 수(1~2) */
const feedCountOptions = computed(() => [1, 2].map((n) => ({ value: n, label: t('ad.boardListCountOption', { n }) })));
function onFeedCount(value: number | null): void {
    if (!form.value || value === null) return;
    form.value.boardList.count = value;
    clearServer('boardList.count');
}

/** 두 광고 사이 글 수 — 지우는 중의 null 은 버린다 */
const gapModel = computed<number | null>({
    get: () => form.value?.boardList.gap ?? null,
    set: (value) => {
        if (!form.value || value === null) return;
        form.value.boardList.gap = value;
        clearServer('boardList.gap');
    },
});

/** 중도 해지 수수료율 — `InputNumber` 가 지우는 중에 주는 null 은 버린다(늘 0~30 정수) */
const feeRateModel = computed<number | null>({
    get: () => form.value?.refund.stopFeeRate ?? null,
    set: (value) => {
        if (!form.value || value === null) return;
        form.value.refund.stopFeeRate = Math.round(value);
        clearServer('refund.stopFeeRate');
    },
});

/* ── 상품 가격(요금표) — 광고 자리 설정과 자원이 따로라 상태도 따로 둔다(읽기 실패는 이 탭만) ──
 * 줄은 보기 전용. 동작은 모두 바로 서버를 부른다 — [가격 변경]·[고치기](다이얼로그 → 예약) · [취소]·[예약 모두 취소](확인 창) · 받기 스위치(끌 때 확인 창) · [여러 상품 가격 변경](다이얼로그).
 * 그래서 이 탭은 저장 줄·저장 전 상태가 없다.
 */

const priceSaved = ref<AdPriceRulesView | null>(null);
const priceLoading = ref(false);
const priceLoadError = ref<ApiError | null>(null);
/** 요청이 도는 상품(받기·예약 취소) — `'*'` 는 모두 취소. 도는 동안 다른 단추를 잠근다 */
const priceBusy = ref<AdPriceProduct | '*' | null>(null);
/** 요약 카드에서 눌러 옮겨 온 상품(잠깐 강조) */
const highlightedProduct = ref<AdPriceProduct | null>(null);

/** 묶음 둘(광고·핫딜)의 줄 — 서버가 주지 않은 상품은 뺀다 */
const priceGroups = computed(() =>
    UtilAdPrice.GROUPS.map((group) => ({
        key: group.key,
        rules: group.products.flatMap((product) => priceSaved.value?.rules.filter((r) => r.product === product) ?? []),
    })).filter((group) => group.rules.length > 0),
);

/** 다가오는 예약 — 모든 상품, 가까운 순(전→후는 그 상품의 바로 앞 값 기준) */
const upcoming = computed(() => UtilAdPrice.upcoming(priceSaved.value?.rules ?? []));

const priceRowId = (product: AdPriceProduct) => `${idBase}-price-row-${product}`;

function ruleOf(product: AdPriceProduct): AdPriceRule | null {
    return priceSaved.value?.rules.find((r) => r.product === product) ?? null;
}

/** 다가오는 예약 카드의 값 글자 — 「50,000원」·「2건」·「한도 없음」·「14일」 */
function diffValue(field: AdPriceScheduledField, n: number | null): string {
    return UtilAdMessages.priceRuleValue(t, field, n === null ? null : String(n), locale.value) ?? '';
}

let highlightTimer: ReturnType<typeof setTimeout> | undefined;
/** 요약 카드의 예약을 누르면 그 상품 줄로 옮겨 잠깐 강조한다 */
function focusProduct(product: AdPriceProduct): void {
    highlightedProduct.value = product;
    document.getElementById(priceRowId(product))?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    clearTimeout(highlightTimer);
    highlightTimer = setTimeout(() => (highlightedProduct.value = null), 2500);
}
onBeforeUnmount(() => clearTimeout(highlightTimer));

/** 예약 성공 등으로 요금표가 바뀌면 받은 적 있는 이력을 첫 쪽부터 다시 */
function afterPriceChange(view: AdPriceRulesView): void {
    priceSaved.value = view;
    if (priceHistory.loaded.value) void priceHistory.reloadFirst();
}

/** 다이얼로그 오류 상태 — 칸 오류는 칸 아래, 나머지는 아래 한 줄 */
interface DialogState {
    open: boolean;
    busy: boolean;
    error: string;
    fieldErrors: Record<string, string>;
}

function clearDialogError(state: DialogState, key: string): void {
    if (!(key in state.fieldErrors)) return;
    const { [key]: _removed, ...rest } = state.fieldErrors;
    state.fieldErrors = rest;
}

/** 실패를 칸 오류(`keys` 에 있는 것만)나 한 줄 문구로 */
function applyDialogFailure(state: DialogState, e: unknown, keys: readonly string[]): void {
    const failure = toApiError(e);
    const fields: Record<string, string> = {};
    if (failure.code === 'VALIDATION') for (const key of keys) if (failure.field(key)) fields[key] = failure.field(key)!;
    state.fieldErrors = fields;
    state.error = Object.keys(fields).length ? '' : failure.message;
}

/* 상품 하나의 예약 다이얼로그([가격 변경] · [고치기]) */
const scheduleDialog = reactive<DialogState & { product: AdPriceProduct | null; schedule: AdPriceSchedule | null }>({ open: false, busy: false, error: '', fieldErrors: {}, product: null, schedule: null });
const scheduleDialogRule = computed(() => (scheduleDialog.product ? ruleOf(scheduleDialog.product) : null));

function openSchedule(product: AdPriceProduct, schedule: AdPriceSchedule | null): void {
    Object.assign(scheduleDialog, { product, schedule, error: '', fieldErrors: {}, open: true });
}

async function submitSchedule(body: AdPriceScheduleRequest): Promise<void> {
    const product = scheduleDialog.product;
    if (!product || scheduleDialog.busy) return;
    const editing = scheduleDialog.schedule;
    scheduleDialog.busy = true;
    scheduleDialog.error = '';
    scheduleDialog.fieldErrors = {};
    try {
        afterPriceChange(editing ? await api.updatePriceSchedule(product, editing.id, body) : await api.createPriceSchedule(product, body));
        scheduleDialog.open = false;
        toast.success(t(editing ? 'ad.priceScheduleUpdated' : 'ad.priceScheduleSaved', { product: t(UtilAdPrice.nameKey(product)), at: time.value.full(body.effectiveAt) }));
    } catch (e) {
        applyDialogFailure(scheduleDialog, e, ['effectiveAt', ...UtilAdPrice.SCHEDULED_FIELDS]);
        // 고치려던 예약이 그사이 적용·취소됐으면(404) 목록을 다시 읽어 맞춘다
        if (editing && toApiError(e).status === 404) void loadPrices();
    } finally {
        scheduleDialog.busy = false;
    }
}

/* 여러 상품 가격 변경 다이얼로그 */
const bulkDialog = reactive<DialogState>({ open: false, busy: false, error: '', fieldErrors: {} });

function openBulk(): void {
    Object.assign(bulkDialog, { error: '', fieldErrors: {}, open: true });
}

async function submitBulk(body: AdPriceBulkScheduleRequest): Promise<void> {
    if (bulkDialog.busy) return;
    bulkDialog.busy = true;
    bulkDialog.error = '';
    bulkDialog.fieldErrors = {};
    try {
        afterPriceChange(await api.bulkPriceSchedules({ ...body, products: [...body.products], adjust: { ...body.adjust } }));
        bulkDialog.open = false;
        toast.success(t('ad.priceBulkSaved', { n: body.products.length, at: time.value.full(body.effectiveAt) }));
    } catch (e) {
        applyDialogFailure(bulkDialog, e, ['effectiveAt', 'products', 'adjust.value', 'adjust.kind']);
    } finally {
        bulkDialog.busy = false;
    }
}

/** 확인 창 뒤 바로 부르는 동작 공통 — 실패는 토스트(사유 8초) */
async function runPriceAction(busyKey: AdPriceProduct | '*', call: () => Promise<AdPriceRulesView>, done: string): Promise<void> {
    priceBusy.value = busyKey;
    try {
        afterPriceChange(await call());
        toast.success(done);
    } catch (e) {
        const failure = toApiError(e);
        toast.error(failure.message, 8000);
        // 없는 예약(그사이 적용·취소됨) — 목록을 다시 읽어 맞춘다
        if (failure.status === 404) void loadPrices();
    } finally {
        priceBusy.value = null;
    }
}

/** 예약 한 건 [취소] */
async function cancelSchedule(product: AdPriceProduct, schedule: AdPriceSchedule): Promise<void> {
    if (!canEdit.value || priceBusy.value) return;
    const name = t(UtilAdPrice.nameKey(product));
    const at = time.value.full(schedule.effectiveAt);
    const ok = await confirm({ title: t('ad.priceScheduleCancelTitle'), message: t('ad.priceScheduleCancelOne', { product: name, at }), confirmLabel: t('ad.priceScheduleCancelConfirm'), cancelLabel: t('ad.priceScheduleCancelKeep'), tone: 'danger' });
    if (ok) await runPriceAction(product, () => api.cancelPriceSchedule(product, schedule.id), t('ad.priceScheduleCanceled', { product: name, at }));
}

/** [예약 모두 취소] */
async function cancelAllSchedules(): Promise<void> {
    const n = upcoming.value.length;
    if (!canEdit.value || priceBusy.value || !n) return;
    const ok = await confirm({ title: t('ad.priceScheduleCancelAllTitle', { n }), message: t('ad.priceScheduleCancelAllMessage', { n }), confirmLabel: t('ad.priceScheduleCancelConfirm'), cancelLabel: t('ad.priceScheduleCancelKeep'), tone: 'danger' });
    if (ok) await runPriceAction('*', () => api.cancelAllPriceSchedules(), t('ad.priceScheduleCanceledAll', { n }));
}

/** 신청 받기 — 바로 적용. 끌 때만 확인 창 */
async function setAccepting(product: AdPriceProduct, accepting: boolean): Promise<void> {
    if (!canEdit.value || priceBusy.value) return;
    const name = t(UtilAdPrice.nameKey(product));
    if (!accepting) {
        const ok = await confirm({ title: t('ad.priceAcceptingOffTitle', { product: name }), message: t('ad.priceAcceptingOffMessage'), confirmLabel: t('ad.priceAcceptingOffConfirm'), cancelLabel: t('ad.priceScheduleCancelKeep'), tone: 'danger' });
        if (!ok) return;
    }
    await runPriceAction(product, () => api.setPriceAccepting(product, accepting), t(accepting ? 'ad.priceAcceptingOnDone' : 'ad.priceAcceptingOffDone', { product: name }));
}

const placementChanges = computed(() => (form.value && saved.value ? UtilAd.placementChanges(form.value, saved.value) : { postInline: 0, wings: 0, boardList: 0, adult: 0, refund: 0, adsense: 0 }));
/** 묶음마다 저장 전 칸 수(탭 점·카드 꼬리표) — 상품 가격은 저장 전 상태가 없다 */
const changes = computed(() => ({ ...placementChanges.value, prices: 0 }));
const changeCount = computed(() => {
    const c = placementChanges.value;
    return c.postInline + c.wings + c.boardList + c.adult + c.refund + c.adsense;
});
const dirty = computed(() => changeCount.value > 0);

/** 마지막 저장 줄 — 상품 가격 탭은 요금표의 저장 시각 */
const savedAtText = computed(() => {
    const view = tab.value === 'prices' ? priceSaved.value : saved.value;
    if (!view) return '';
    return view.updatedAt ? t('ad.placementsLastSaved', { at: time.value.full(view.updatedAt), by: view.updatedBy?.name ?? '—' }) : t('ad.placementsNeverSaved');
});

/*
 * 애드센스 칸 미리 검사(서버와 같은 규칙 — `UtilAdSense.problems`). 「게시자 ID 필요」는 단계를 바꾸자마자,
 * 형식 오류는 그 칸을 떠난 뒤(`touched`)나 저장을 누른 뒤(`showAllProblems`)에 보인다 — 입력하는 중에 빨갛게 뜨지 않게.
 */
const touched = ref<ReadonlySet<string>>(new Set());
const showAllProblems = ref(false);
const adsenseProblems = computed(() => (form.value ? UtilAdSense.problems(form.value.adsense) : {}));
/** 지금 보이는 화면 검사 오류(칸 키 → 문구 키) */
const localErrors = computed<Record<string, string>>(() => {
    const out: Record<string, string> = {};
    for (const [key, problem] of Object.entries(adsenseProblems.value)) {
        if (problem === 'clientRequired' || showAllProblems.value || touched.value.has(key)) out[key] = `ad.adsenseProblem.${problem}`;
    }
    return out;
});
const localErrorCount = computed(() => Object.keys(localErrors.value).length);
const errorCount = computed(() => new Set([...Object.keys(serverErrors.value), ...Object.keys(localErrors.value)]).size);

function errorOf(key: string): string {
    return serverErrors.value[key] ?? '';
}

/** 애드센스 칸 오류 — 서버 문구가 먼저, 없으면 화면 검사 */
function adsenseError(key: string): string {
    const local = localErrors.value[key];
    return serverErrors.value[key] ?? (local ? t(local) : '');
}

/** 칸을 떠날 때 — 붙여 넣은 값을 다듬고(`pub-…` → `ca-pub-…`, 광고 코드 통째면 그 안의 값만) 그 칸의 형식 검사를 보이기 시작한다 */
function onAdsenseBlur(key: string): void {
    const value = form.value?.adsense;
    if (!value) return;
    if (key === 'adsense.client') {
        value.client = UtilAdSense.normalizeClient(value.client);
    } else if (key === 'adsense.units.BOARD_LIST.slot') {
        const raw = value.units.BOARD_LIST.slot;
        value.units.BOARD_LIST.slot = UtilAdSense.normalizeSlot(raw);
        // 인피드 광고 코드를 통째로 붙여 넣었으면 레이아웃 키도 채운다(비어 있을 때만)
        if (!value.units.BOARD_LIST.layoutKey && raw && /data-ad-layout-key/i.test(raw)) value.units.BOARD_LIST.layoutKey = UtilAdSense.normalizeLayoutKey(raw);
    } else if (key === 'adsense.units.BOARD_LIST.layoutKey') {
        value.units.BOARD_LIST.layoutKey = UtilAdSense.normalizeLayoutKey(value.units.BOARD_LIST.layoutKey);
    } else {
        const slot = UtilAdSense.DISPLAY_SLOTS.find((s) => key === `adsense.units.${s}`);
        if (slot) value.units[slot] = UtilAdSense.normalizeSlot(value.units[slot]);
    }
    if (!touched.value.has(key)) touched.value = new Set([...touched.value, key]);
}

/** 묶음의 오류 칸 수(묶음 자체 오류 포함 · 애드센스는 화면 검사 오류도) */
function invalidOf(section: Section): number {
    const keys = new Set([...Object.keys(serverErrors.value), ...Object.keys(localErrors.value)]);
    return [...keys].filter((key) => key === section || key.startsWith(`${section}.`)).length;
}

function clearServer(key: string): void {
    if (!(key in serverErrors.value)) return;
    const { [key]: _removed, ...rest } = serverErrors.value;
    serverErrors.value = rest;
}

onMounted(() => {
    void load();
    void loadPrices();
});

/** 요금표 읽기 — 실패는 「상품 가격」 탭에만 보인다 */
async function loadPrices(): Promise<void> {
    priceLoading.value = true;
    priceLoadError.value = null;
    try {
        priceSaved.value = await api.priceRules();
    } catch (e) {
        priceLoadError.value = toApiError(e);
    } finally {
        priceLoading.value = false;
    }
}

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
    touched.value = new Set();
    showAllProblems.value = false;
}

function revert(): void {
    if (saved.value) applySaved(saved.value);
}

/** 저장 — 광고 자리 설정 여섯 묶음 PUT 한 번(상품 가격은 저장 줄을 쓰지 않는다) */
async function save(): Promise<void> {
    if (!canEdit.value || saving.value || !dirty.value || !form.value) return;
    // 애드센스 칸이 맞지 않으면 보내지 않는다 — 모든 칸 오류를 보이고 그 탭으로(판정은 서버, 이건 미리 알림)
    if (Object.keys(adsenseProblems.value).length) {
        showAllProblems.value = true;
        if (tab.value !== 'adsense') selectTab('adsense');
        return;
    }
    // 반응형 객체를 그대로 보내지 않는다 — 새로 만든 평범한 객체(애드센스 칸은 다듬어서)
    const body = UtilAd.placementsBody(toRaw(form.value));
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

/* ── 설정 이력 — 광고 자리 설정 · 상품 가격(이력 API 가 따로라 쪽 넘김 상태도 따로) ── */

type HistoryKind = 'placements' | 'prices';

const history = useSettingHistory((page, size) => api.placementHistory(page, size));
const priceHistory = useSettingHistory((page, size) => api.priceRuleHistory(page, size));
const historyKind = ref<HistoryKind>('placements');
const activeHistory = computed(() => (historyKind.value === 'prices' ? priceHistory : history));
const historyKindOptions = computed(() => [
    { value: 'placements' as const, label: t('ad.historyKind.placements') },
    { value: 'prices' as const, label: t('ad.historyKind.prices') },
]);
/** 표에 넘길 줄 — 칸 이름·값을 사람이 읽는 글자로(언어를 바꾸면 따라 바뀌게 받은 원본에서 매번 만든다) */
const historyRows = computed(() =>
    historyKind.value === 'prices' ? UtilAdMessages.priceRuleHistoryItems(t, priceHistory.rows.value, locale.value, (iso) => time.value.full(iso)) : UtilAdMessages.placementHistoryItems(t, te, history.rows.value),
);

/** 고른 이력을 아직 안 받았으면 받는다 */
function ensureHistory(kind: HistoryKind): void {
    const target = kind === 'prices' ? priceHistory : history;
    if (!target.loaded.value && !target.loading.value) void target.load();
}

/** 이력 서랍 — 처음 열 때 받는다(화면을 열 때마다 이력을 부르지 않는다). 「상품 가격」 탭에서 열면 상품 가격 이력부터 */
const historyOpen = ref(false);
function openHistory(): void {
    historyKind.value = tab.value === 'prices' ? 'prices' : 'placements';
    historyOpen.value = true;
    ensureHistory(historyKind.value);
}

function onHistoryKind(kind: HistoryKind | null): void {
    if (!kind) return;
    historyKind.value = kind;
    ensureHistory(kind);
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('ad.placementsLeaveTitle'), message: t('ad.placementsLeaveMessage'), confirmLabel: t('ad.placementsLeaveConfirm'), cancelLabel: t('ad.placementsLeaveCancel'), tone: 'danger' });
});
</script>
