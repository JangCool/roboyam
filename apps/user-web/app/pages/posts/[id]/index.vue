<template>
    <!--
        글 보기 `/posts/{id}` — 머리(말머리·제목·작성자·시각·조회) → 본문 → (투표) → (찬반 토론) → [추천][링크 복사][신고] · [찬반 토론 열기][수정][삭제] → 투자 고지 → 이전·다음 글 → 댓글.
        community 대비: 투표·스크랩·글 ⋯ 메뉴의 차단·메모·지킴이 처리·리비전·소모임 글·핫딜/리뷰 글 모양을 뺐다. 본문은 에디터 HTML 이 아니라 평문(빈 줄 = 문단, 주소만 링크 — `v-html` 없음).
          비회원 글이 있어 작성자 옆에 「비회원」과 작성 IP 앞 두 자리가 보이고, 비회원 글의 수정·삭제는 비밀번호 확인 뒤에 한다.
        비회원 글 IP 는 서버가 앞 두 묶음만 남겨 준다(IPv4 `123.45.*.*` · IPv6 `2001:db8:*:*`, 회원 글은 없다). 19금 글은 서버가 성인(로그인 + 본인인증 + 만 19세 이상)에게만 준다 — 아니면 `403 POST_ADULT_ONLY`(`reason` LOGIN·IDENTITY·UNDERAGE)라 본문 대신 안내(로그인 · 「본인인증하고 보기」 → `useIdentityGate` 대화상자 → 인증되면 다시 읽기 · 미성년은 19세 생일 안내). adult-content-contract.
          추천은 한 번만(취소·비추천 없음). 신고는 회원만. 수정은 비회원 글이면 수정 화면에서 비밀번호를 다시 묻는다(비밀번호를 화면 사이로 넘기지 않는다).
          댓글 많은 글 보호(commentProtect*)는 서버가 `protection` 으로 알려 주고, 막힌 동작은 단추를 누르면 까닭을 알린다.
        차단한 회원의 글·차단한 단어가 제목에 든 글(`blocked`)은 제목·본문·추천 줄을 「차단한 사용자의 글이에요 · 보기」로 접는다(목록처럼 제목도 가린다 — 단어 차단은 제목에 걸린다).
          게시판 이름·작성자 줄(메모 칩)·예약 삭제/보호 알림·수정/삭제·이전·다음 글·댓글(댓글은 따로 접힌다)은 남긴다. 「보기」를 누르면 그 자리에서 펼치고 「다시 숨기기」로 다시 접는다.
        지금 하이라이트로 노출 중인 글(`highlighted`)은 게시판 이름 옆에 「하이라이트」 표시.
        찬반 토론은 글의 기능이다(`post-debate-contract.md` — 하이라이트와 무관, 글쓴이·운영자·그 게시판 매니저가 연다).
        토론 화면 배치(확정 시안 — `popular-highlight-contract.md` 「결정」): **본문 영역 안**(본문 글 바로 다음, 추천 줄 위)에 토론 구획(`CommentSection variant="debate"`, 목록 `kind=OPINION|PRO|CON|NEUTRAL`),
          **맨 아래 원래 댓글 자리**에 일반 댓글(`variant="general"`, 늘 `kind=GENERAL`). 댓글 목록을 둘 부른다(`usePostComments` 두 개). 토론 구획은 `UtilDebate.visible`(열림 · 표 · 의견이 있음)일 때만 보이고,
          그때만 의견 목록을 부른다(글 상세의 `debate` 또는 일반 목록의 `debate` 로 판정). 토론이 없는 글은 예전처럼 댓글만. 차단으로 본문이 접히면 토론도 함께 가린다.
          토론을 바꿀 수 있는 사람(`debate.canManage`)은 토론 구획 머리의 [토론 설정], 토론이 없는 글에서는 글 아래 [찬반 토론 열기]로 설정 대화창(`DebateSettingsDialog`)을 열어 `PUT /api/posts/{id}/debate` 한다.
          응답(새 토론 요약)을 글에 넣고 두 댓글 목록을 다시 받는다. `403 DEBATE_MANAGE_DENIED` 는 토스트 + 목록 다시 받기, `409 DEBATE_HAS_VOTES` 는 대화창 안 서버 문구 + 목록 다시 받기.
        핫딜 글(`deal` — `hotdeal-contract.md`): 본문 위에 딜 카드(`HotdealDealCard` — 제휴 고지·가격·몰·배송·재고·기간·가격 기준·수정 이력·[구매하기]·[이 상품 새 딜 올리기]·품절 알리기·작성자 종료/되돌리기/「아직 진행 중」·의심/종료 표시·같은 딜 묶음·판매자 아님 고지).
          같은 딜(`sameDealCount > 0`)은 글을 받은 뒤 `GET …/deal/same` 으로 따로 받는다. 신고 대화창은 핫딜 글이면 사유 2개(가격·정보 / 숨긴 광고)를 더하고, 「가격 허위」는 종료 요청으로 접수된다(D4 — 성공하면 카드의 「보낸 사유」를 화면이 고친다). 동작은 `useHotdealApi` 로 이 화면이 한다(카드는 `actions` 만).
          사업자 핫딜 글(2차 — `deal.source BUSINESS`)은 작성자 자리에 상호 + 「사업자」, 딜 카드 배지 「광고」·카드 첫 줄 광고 고지, 작성자에게도 고치기·지우기 없음(종료만).
          작성자(`deal.business.applicationId` 가 있을 때)는 카드에 [일시 정지](신청 건 `useBusinessApi.pause`)와 「중단·환불은 신청 건에서」. 판매자가 내린 글(광고주 일시 정지·중단 — 글 단건 조회의 `403 DEAL_SELLER_HIDDEN`, biz-pause-contract 규칙 9)은
          404 대신 안내만: 「판매자가 잠시 내린 딜이에요」/「판매자가 내린 딜이에요」 + 게시판 이름 + [핫딜 게시판으로], 정지이고 보는 사람이 그 사업자면 「신청 건에서 다시 켤 수 있어요 ›」.
          그 게시판 매니저·스텝이 보면(`dealModeration`) 카드에 작성자 조치와 따로 「게시판 관리」([핫딜 종료]·[다시 진행 중으로])가 서고, 딜이 바뀌면 단추를 새 상태에 맞추며 그새 상태가 바뀐 거절(`stale`)이면 글을 다시 읽는다.
        게시판 제재(board-sanction-contract): 그 게시판 매니저·스텝(`viewer.staffTools.abilities.sanction`)에게만 글 아래 ⋯ 와 댓글 ⋯ 에 「작성자 제재」(남의 회원 글·댓글만) → `BoardSanctionDialog`(흐름 `useBoardSanctionForm`).
          익명 글·댓글(anonymous-board-contract 정책 9)도 남의 것이면 「작성자 제재」 — 대상은 닉네임 없이 「이 글(댓글) 작성자」, 요청은 `anonymousPostId`·`anonymousCommentId`.
        스텝 처리(board-staff-action-contract): 글 ⋯ 「글 숨기기」·「글 삭제」(`abilities.postHide|postDelete`, 본인 글·소모임 글·`staffProtected`(공지·고정·같은 게시판 운영진의 글) 제외 — 「작성자 제재」는 `sanctionProtected` 면 없음), 댓글 ⋯ 「댓글 삭제」(`abilities.commentDelete`, 본인·지운 댓글 제외)
          → `BoardStaffActionDialog`(사유 → 확인, 흐름 `useBoardStaffActionForm`) → 토스트(오늘 남은 횟수) → 글이면 게시판 목록으로, 댓글이면 그 목록 새로. 그새 상태가 바뀌었으면 다시 읽는다.
        투표(post-poll-contract): 글에 `poll` 이 있으면 본문 바로 다음·찬반 토론 위에 투표 카드(`PostPollCard`). 투표·다시 읽기·지우기는 `usePollApi` 로 이 화면이 하고(카드는 `actions` 만 받는다),
          카드가 올린 새 투표(지우면 null)를 글의 `poll` 에 넣는다. 차단으로 본문이 접히면 함께 가린다.
        소모임 글(2단계, community `docs/api-groups.md` 8절 · 「화면 (SPA) — 6-2」): 글의 `group` 이 있으면 머리의 게시판 이름 대신 「소모임 › 이름」(→ 소모임, 해산했으면 링크 없이 「해산한 소모임」)과
          공개 라벨(「멤버에게만 보여요」/「전체 공개」). 게시판 정보(댓글 설정 등)는 공개 게시판 API 가 아니라 `GET /api/groups/{id}/board`(로보얌 추가 후보)로 받는다.
          글 아래 공개 범위 패널(`GroupPostPanel` — 5단계, 11-6 네 가지 일): 공개 범위 기능이 꺼진 서버(`group.groupVisibility` 없음)는 옛 패널(작성자의 [전체 공개로 전환]·[비공개로 되돌리기]),
          켜져 있으면 공개 요청·요청 취소·승인·거절·직접 공개·되돌리기·멤버 전용으로 돌리기·다시 공개. 쓰기는 이 화면이 `useGroupApi` 로 만든 `actions` 가 하고 패널이 올린 바뀐 칸을 `group` 에 덮어쓴다.
          비멤버가 보는 비공개 소모임의 개별 공개 글이면 본문 위에 「이 글은 {이름} 멤버가 모두에게 공개한 글이에요」, 댓글 위에 「멤버에게만 보이는 댓글 N개」(`membersOnlyCount`) 안내.
          이전·다음 글 대신 [소모임 글 목록으로]. 비멤버가 비공개 글을 열면(`403 GROUP_POST_PRIVATE`) 「멤버에게만 보이는 글이에요」(로그인 전이면 로그인, 아니면 소모임 둘러보기 — 오류에 소모임 ID 가 없다).
    -->
    <!--
        운영 게시판(버그 신고·기능 요청): 비밀글(`secret`)은 제목 옆 「비밀글」 딱지. 읽을 수 없는 사람은 `403 SECRET_POST` → 본문·댓글 대신 「비밀글이에요…」 + [목록으로]
          (비밀글의 댓글은 글을 볼 수 있는 사람만 보고 쓴다 — 별도 화면 없음). 처리 상태가 켜진 게시판(`status`)은 제목 아래 상태 딱지 + 「마지막 변경 시각」, 운영진(`viewer.staff`)이면 상태 바꾸기 Select.
        2차(2026-11-08): 비회원 비밀글(extras `guestPost: true`)은 잠금 화면에 글 비밀번호 칸 + [열기](`POST …/secret-unlock` → 글을 다시 받는다).
          상태 바꾸기는 Select 대신 [상태 바꾸기] 대화창(상태 + 운영진 메모 0~200자, 보일지는 `viewer.canChangeStatus` — 옛 응답은 `viewer.staff`), 메모가 있으면 「운영진 메모: …」 한 줄,
          그 아래 「처리 이력」 접기(`GET …/process-status/history`). 소모임 글도 같다(소모임 비밀글은 공개 요청·직접 공개 단추를 숨긴다 — `GroupPostPanel secret`).
    -->
    <div>
        <!-- 모바일 안쪽 화면 머리 — 셸의 위·아래 막대가 숨겨진다(`shell: 'inner'`) -->
        <div class="-mx-4 -mt-4 mb-3 flex items-center gap-1 border-b border-surface bg-card px-2 py-1.5 md:hidden">
            <Button type="button" icon="pi pi-chevron-left" variant="text" severity="secondary" :aria-label="t('post.backToBoard')" @click="goBoard" />
            <span class="truncate text-sm font-semibold">{{ post?.group ? UtilGroupMessages.postLocation(t, post.group) : boardName }}</span>
        </div>

        <!-- 게시판 레이아웃(테마마다 하나 — 글 보기에 켜져 있으면 이 글 화면이 격자의 「게시판 본문」 칸에 들어가고 둘레에 가젯, 아니면 그대로. 소모임 글은 쓰지 않는다. 2026-10-30) -->
        <BoardLayoutFrame screen="POST" :board="post && !post.group ? post.board.slug : null">
            <div v-if="pageState === 'loading'" role="status" :aria-label="t('post.loading')">
                <div class="h-6 w-3/4 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div class="mt-3 h-3 w-40 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                <div v-for="n in 5" :key="n" class="mt-4 h-3.5 animate-pulse rounded bg-surface-200 dark:bg-surface-800" :class="n % 2 ? 'w-full' : 'w-5/6'" />
            </div>

            <ErrorState v-else-if="pageState === 'notfound'" code="404" :title="t('post.notFoundTitle')" :description="pageError">
                <Button type="button" :label="t('post.back')" severity="secondary" variant="outlined" @click="router.back()" />
            </ErrorState>
            <ErrorState v-else-if="pageState === 'hidden'" :title="t('post.hiddenTitle')" :description="pageError">
                <Button type="button" :label="t('post.back')" severity="secondary" variant="outlined" @click="router.back()" />
            </ErrorState>
            <!-- 판매자가 내린 사업자 핫딜 글(광고주 일시 정지·중단 — 403 DEAL_SELLER_HIDDEN) — 404 가 아니라 안내만(본문·구매 링크·댓글 없음) -->
            <div
                v-else-if="pageState === 'sellerHidden' && sellerHidden"
                class="flex flex-col items-center gap-3 rounded-xl border border-surface px-5 py-10 text-center"
                data-testid="post-seller-hidden"
                :data-state="sellerHidden.state"
            >
                <span class="grid size-12 place-items-center rounded-full bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true">
                    <i :class="[sellerHidden.state === 'PAUSED' ? 'pi pi-pause' : 'pi pi-ban', 'text-lg']" />
                </span>
                <h1 class="m-0 text-lg font-semibold">{{ sellerHidden.state === 'PAUSED' ? t('hotdeal.sellerPausedTitle') : t('hotdeal.sellerStoppedTitle') }}</h1>
                <p class="m-0 max-w-md text-sm leading-relaxed text-muted-color">{{ sellerHidden.state === 'PAUSED' ? t('hotdeal.sellerPausedText') : t('hotdeal.sellerStoppedText') }}</p>
                <p v-if="sellerHiddenBoard" class="m-0 text-xs text-muted-color">{{ t('hotdeal.sellerHiddenBoard', { board: sellerHiddenBoard }) }}</p>
                <div class="flex flex-wrap justify-center gap-2">
                    <Button v-if="sellerHidden.boardSlug" as="router-link" :to="`/boards/${sellerHidden.boardSlug}`" :label="t('hotdeal.sellerHiddenToBoard')" icon="pi pi-list" data-testid="post-seller-hidden-board" />
                    <Button v-else type="button" :label="t('post.back')" severity="secondary" variant="outlined" @click="router.back()" />
                </div>
                <!-- 보는 사람이 그 사업자면 신청 건으로(서버가 작성자에게만 `applicationId` 를 싣는다) -->
                <NuxtLink v-if="sellerHidden.state === 'PAUSED' && sellerHidden.applicationId" :to="UtilBusiness.applicationPath(sellerHidden.applicationId)" class="text-sm text-primary hover:underline" data-testid="post-seller-hidden-resume">
                    {{ t('hotdeal.sellerResumeLink') }}
                </NuxtLink>
            </div>
            <!-- 비밀글을 읽을 수 없는 사람이 열었다(403 SECRET_POST — 글쓴이·그 게시판 운영진·운영자만 읽는다) — 본문·댓글 대신 안내 + [목록으로] -->
            <div v-else-if="pageState === 'secretLocked'" class="content-card flex flex-col items-center gap-3 px-5 py-10 text-center" data-testid="post-secret-locked">
                <span class="inline-flex size-12 items-center justify-center rounded-full bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true">
                    <i class="pi pi-lock text-lg" />
                </span>
                <h1 class="m-0 text-lg font-semibold">{{ t('post.secretLockedTitle') }}</h1>
                <p class="m-0 max-w-md text-sm leading-relaxed text-muted-color">{{ secretGuest ? t('post.secretLockedGuestText') : t('post.secretLockedText') }}</p>
                <!-- 비회원 비밀글(extras `guestPost: true`) — 글 비밀번호로 연다(`POST …/secret-unlock`, 204 → 글을 다시 받는다). 비밀번호는 이 화면 메모리에만 있고 열면 지운다 -->
                <form v-if="secretGuest" class="mt-1 flex w-full max-w-xs flex-col gap-2 text-left" data-testid="post-secret-unlock" @submit.prevent="unlockSecret">
                    <PasswordField v-model="unlockPassword" :label="t('post.guestPassword')" autocomplete="off" name="guest-password-unlock" :error="unlockError" :disabled="unlocking" />
                    <Button type="submit" :label="t('post.secretUnlock')" icon="pi pi-unlock" :loading="unlocking" :disabled="unlockPassword.length === 0 || unlocking" fluid data-testid="post-secret-unlock-submit" />
                </form>
                <Button type="button" :label="t('post.backToBoard')" icon="pi pi-list" severity="secondary" :variant="secretGuest ? 'text' : 'outlined'" class="mt-1" @click="leaveSecret" />
            </div>
            <!-- 멤버에게만 보이는 소모임 글을 멤버가 아닌 사람이 열었다(403 GROUP_POST_PRIVATE) — 오류에 소모임 ID 가 없어 소모임 목록으로 보낸다 -->
            <ErrorState v-else-if="pageState === 'groupPrivate'" :title="t('group.privatePostTitle')" :description="user ? t('group.privatePostText') : t('group.privatePostLoginText')" data-testid="post-group-private">
                <Button v-if="!user" type="button" :label="t('common.login')" @click="prompt('view')" />
                <Button v-else as="router-link" :to="UtilGroup.LIST_PATH" :label="t('group.browse')" />
            </ErrorState>
            <!-- 19금 글을 성인(로그인 + 본인인증 + 만 19세 이상)이 아닌 사람이 열었다(403 POST_ADULT_ONLY) — 본문·댓글을 받지 못한다 -->
            <ErrorState
                v-else-if="pageState === 'adultOnly' && adultBlock"
                :title="t('post.adultGateTitle')"
                :description="adultBlock.reason === 'LOGIN' ? t('post.adultGateLoginText') : adultBlock.reason === 'UNDERAGE' ? `${t('post.adultGateMinorText')} ${UtilIdentityMessages.minorRelease(t, adultBlock.adultFrom)}` : t('post.adultGateText')"
                data-testid="post-adult-gate"
            >
                <Button type="button" :label="t('post.back')" severity="secondary" variant="outlined" @click="router.back()" />
                <Button v-if="adultBlock.reason === 'LOGIN'" type="button" :label="t('common.login')" @click="prompt('view')" />
                <Button v-else-if="adultBlock.reason === 'IDENTITY'" type="button" :label="t('post.adultVerify')" icon="pi pi-mobile" :loading="adultVerifying" data-testid="post-adult-verify" @click="verifyForAdult" />
            </ErrorState>
            <ErrorState v-else-if="pageState === 'error' || !post || !board" :title="t('post.loadFailed')" :description="pageError" retry @retry="load" />

            <!-- AI 회원 글이면 기계가 읽는 표시 `data-ai-generated`(AI 기본법 제31조 — 계약 「법무 메모」) -->
            <article v-else :aria-labelledby="titleId" :data-ai-generated="postAi ? 'true' : undefined">
                <!-- 본문 영역 카드(머리 ~ 게시판으로) · 댓글 영역 카드 — 기본 테마는 카드가 보이지 않고(역할 변수 기본값), 다음식·네이버식은 흰 카드다(`main.css` `content-card`) -->
                <div class="content-card" data-testid="post-body-card">
                    <!-- 머리 -->
                    <header class="border-b border-surface pb-3">
                        <div class="flex flex-wrap items-center gap-2">
                            <!-- 소모임 글 — 「소모임 › 이름」(해산했으면 링크 없이) + 공개 라벨(글자가 늘 있다) -->
                            <template v-if="post.group">
                                <span v-if="post.group.dissolved" class="text-xs font-semibold text-muted-color" data-testid="post-group-dissolved">{{ UtilGroupMessages.postLocation(t, post.group) }}</span>
                                <NuxtLink v-else :to="UtilGroup.detailPath(post.group.id)" class="min-w-0 truncate text-xs font-semibold text-primary hover:underline" data-testid="post-group-link">
                                    {{ UtilGroupMessages.postLocation(t, post.group) }}
                                </NuxtLink>
                                <GroupTag :tone="post.group.visibility === 'PUBLIC' ? 'blue' : 'gray'" data-testid="post-group-visibility">{{ UtilGroupMessages.postVisibility(t, post.group.visibility) }}</GroupTag>
                            </template>
                            <NuxtLink v-else :to="`/boards/${board.slug}`" class="text-xs font-semibold text-primary hover:underline max-md:hidden">{{ boardName }}</NuxtLink>
                            <!-- 말머리 — 글자 모양(`TEXT`, mobile-nav-prefix-style-contract 1절)이면 제목 앞 칩 대신 이 줄에 「게시판 | 말머리」 글자(게시판 이름은 데스크톱만이라 막대도 데스크톱만) -->
                            <span v-if="post.prefix && headPrefixStyle === 'TEXT'" class="inline-flex min-w-0 items-center gap-1" data-testid="post-prefix-text">
                                <span v-if="!post.group" class="-ml-1 text-xs text-[var(--prefix-sep)] max-md:hidden" aria-hidden="true">|</span>
                                <PostPrefixTag :prefix="post.prefix" mode="TEXT" />
                            </span>
                            <!-- 지금 하이라이트로 노출 중인 글 -->
                            <span v-if="post.highlighted" class="inline-flex items-center justify-center gap-1 rounded bg-amber-100 px-1.5 py-1 text-[0.6875rem] font-semibold leading-none text-amber-800 dark:bg-amber-900/50 dark:text-amber-200" data-testid="post-highlighted">
                                <span class="relative top-[-0.09em]">{{ t('post.highlighted') }}</span>
                            </span>
                        </div>
                        <!-- 접힌 글은 제목(말머리)을 가린다 — 글의 이름표(`aria-labelledby`)는 접힌 까닭으로 둔다(아래 접힌 줄이 같은 글자를 보이므로 화면에서는 숨김) -->
                        <h1 v-if="folded && foldReason" :id="titleId" class="sr-only">{{ UtilBlockMessages.folded(t, foldReason, 'post') }}</h1>
                        <h1 v-else :id="titleId" class="post-title mt-1 break-words text-lg font-bold leading-snug md:text-xl">
                            <PostPrefixTag v-if="post.prefix && headPrefixStyle !== 'TEXT'" :prefix="post.prefix" :mode="headPrefixStyle" class="mr-1.5 align-[3px]" />{{ post.title }}
                            <!-- 비밀글 딱지(운영 게시판) — 제목 옆, 글자가 늘 있다 -->
                            <span
                                v-if="post.secret"
                                class="ml-1.5 inline-flex items-baseline gap-1 rounded bg-surface-100 px-1.5 pb-[5px] pt-[3px] align-[3px] text-[0.6875rem] font-semibold leading-none text-surface-600 dark:bg-surface-800 dark:text-surface-300"
                                data-testid="post-secret-tag"
                            >
                                <i class="pi pi-lock text-[0.625rem]" aria-hidden="true" /><span>{{ t('post.secretMark') }}</span>
                            </span>
                        </h1>
                        <!-- 처리 상태(상태가 켜진 운영 게시판 — 일반·소모임 게시판) — 딱지 · 마지막 변경 시각 · 바꿀 수 있으면(`viewer.canChangeStatus`, 옛 응답은 게시판 `viewer.staff`) [상태 바꾸기] 대화창
                             · 운영진 메모(`processStatusNote`) 한 줄 · 처리 이력 접기(펼칠 때 한 번 부르고, 바꾼 뒤 다시) -->
                        <div v-if="post.processStatus" class="mt-2" data-testid="post-status">
                            <div class="flex flex-wrap items-center gap-x-2 gap-y-1.5">
                                <PostStatusTag :status="post.processStatus" />
                                <span v-if="post.processStatusAt" class="text-xs text-muted-color" :title="time.full(post.processStatusAt)" data-testid="post-status-changed-at">
                                    {{ t('post.statusChangedAt', { at: time.detail(post.processStatusAt) }) }}
                                </span>
                                <Button
                                    v-if="canChangeStatus"
                                    type="button"
                                    :label="t('post.statusChange')"
                                    icon="pi pi-sync"
                                    size="small"
                                    severity="secondary"
                                    variant="outlined"
                                    class="ml-auto"
                                    data-testid="post-status-change"
                                    @click="openStatusDialog"
                                />
                            </div>
                            <p v-if="post.processStatusNote" class="m-0 mt-1.5 whitespace-pre-line break-words text-xs leading-relaxed [overflow-wrap:anywhere]" data-testid="post-status-note">
                                <span class="font-semibold text-muted-color">{{ t('post.statusNotePrefix') }}</span> {{ post.processStatusNote }}
                            </p>
                            <PostStatusHistory v-model:open="historyOpen" :state="history.state" :items="history.items" :error-message="history.errorMessage" @retry="loadHistory" />
                        </div>
                        <div class="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-color">
                            <!-- 회원 작성자 앞 작은 이니셜 동그라미 — 장착한 프로필 테두리가 여기 보인다(글 목록·피드·가젯 글 줄도 같은 크기 — `PostAuthorLabel avatar`, 가젯 촘촘 줄은 제외 · 댓글·쪽지는 줄이 좁아 닉네임 색만) -->
                            <!-- 사업자 핫딜 글(2차)은 회원 닉네임 대신 상호 + 「사업자」(프로필·테두리 없음) -->
                            <ProfileBorderAvatar v-if="post.author.kind === 'MEMBER' && !bizDeal" :initial="UtilPointShop.initial(post.author.name)" :src="authorAvatar" :still-src="authorAvatarStill" size="xs" :border-style="authorDecoration?.profileBorder ?? null" :ai="!!post.author.ai" />
                            <!-- 익명 글은 회색 원 안 사람 아이콘 + 「익명」(프로필 링크·호버 없음 — `PostAuthorLabel` 이 가린다) -->
                            <PostAuthorLabel :author="UtilHotdeal.authorOf(post.author, post.deal)" :avatar="post.author.kind === 'ANONYMOUS'" show-ip strong link class="text-sm" />
                            <span aria-hidden="true">·</span>
                            <time :datetime="post.createdAt" :title="time.full(post.createdAt)">{{ time.detail(post.createdAt) }}</time>
                            <span v-if="post.updatedAt" :title="time.full(post.updatedAt)">{{ t('post.edited') }}</span>
                            <span aria-hidden="true">·</span>
                            <span>{{ t('post.views', { n: post.viewCount.toLocaleString() }) }}</span>
                            <span aria-hidden="true">·</span>
                            <span>{{ t('post.likes', { n: post.likeCount.toLocaleString() }) }}</span>
                        </div>
                    </header>

                    <!-- 비멤버가 보는 비공개 소모임의 개별 공개 글(5단계) — 어느 소모임 멤버가 공개한 글인지 -->
                    <p v-if="individualGroup" class="m-0 mt-3 flex items-start gap-2 rounded-lg bg-surface-100 px-3 py-2.5 text-sm dark:bg-surface-800" data-testid="post-group-individual-notice">
                        <i class="pi pi-globe mt-0.5 shrink-0 text-xs text-muted-color" aria-hidden="true" />
                        <i18n-t keypath="groupVisibility.individualNotice" scope="global" tag="span" class="min-w-0">
                            <template #name>
                                <NuxtLink :to="UtilGroup.detailPath(individualGroup.id)" class="font-semibold text-primary hover:underline">{{ individualGroup.name }}</NuxtLink>
                            </template>
                        </i18n-t>
                    </p>

                    <!-- 예약 삭제된 글 · 댓글 많은 글 보호(수정·삭제할 수 있는 사람에게만) -->
                    <Message v-if="post.deleteScheduledAt" severity="warn" :closable="false" size="small" class="mt-3" data-testid="post-delete-scheduled">
                        {{ t('post.deleteScheduledNotice', { at: time.full(post.deleteScheduledAt) }) }}
                    </Message>
                    <Message v-if="manageable && protectionNotice" severity="info" :closable="false" size="small" class="mt-3" data-testid="post-protected">
                        <b>{{ protectionNotice.title }}</b>
                        <span v-for="item in protectionNotice.items" :key="item"> · {{ item }}</span>
                    </Message>

                    <!-- 차단한 회원·단어의 글 — 본문·추천 줄을 접힌 칸으로(게시판 글 목록과 같은 `FoldedRow`·문구). 펼치면 아래에 「다시 숨기기」 -->
                    <FoldedRow
                        v-model:open="foldRevealed"
                        :reason="foldReason"
                        kind="post"
                        class="my-6 justify-center rounded-lg border border-surface px-5 py-8 text-center text-sm"
                        hide-class="flex justify-center pb-2"
                        data-testid="post-folded"
                    >
                        <!-- 핫딜 딜 카드 — 본문 위 -->
                        <HotdealDealCard
                            v-if="post.deal"
                            :deal="post.deal"
                            :post-id="post.id"
                            :board-slug="post.board.slug"
                            :mine="post.mine"
                            :guest="board.viewer.guest"
                            :same-deals="sameDeals"
                            :moderation="post.dealModeration ?? null"
                            :actions="dealActions"
                            @update:deal="onDealUpdate"
                            @update:moderation="onDealModerationUpdate"
                            @stale="refreshDeal(post.id)"
                        />
                        <!--
                            에디터로 쓴 글(`contentFormat === 'HTML'`) — 서버가 정화(sanitize)한 HTML 만 온다(공개 게시판 계약 「에디터 화면」: 서버가 허용 태그 밖을 지우고 이미지 주소를 채운다).
                            새 `v-html` 은 이곳(글 본문)과 `CommentItem`(댓글 본문)·`TermsDocumentView`(약관 본문 — 서버 정화본) 세 곳뿐이다. 다른 경로의 글자(제목·닉네임·안내문 등)는 절대 넣지 않는다.
                            그린 뒤 유튜브(누르면 youtube-nocookie iframe)·이미지(실패 자리표시)는 `UtilRichBody.enhance` 가 DOM API 로만 손질한다. 평문 글은 아래처럼 글자로 그린다
                        -->
                        <!-- eslint-disable-next-line vue/no-v-html -->
                        <div v-if="post.contentFormat === 'HTML'" ref="htmlBody" class="rich-text py-5 text-[0.9375rem]" data-testid="post-body" v-html="post.content" />
                        <div v-else class="py-5 text-[0.9375rem] leading-relaxed" data-testid="post-body">
                            <p v-for="(lines, i) in paragraphs" :key="i" class="mb-4 break-words last:mb-0">
                                <template v-for="(line, j) in lines" :key="j">
                                    <br v-if="j > 0" />
                                    <template v-for="(seg, k) in UtilPost.segments(line, siteOrigin)" :key="k">
                                        <a
                                            v-if="seg.kind === 'link'"
                                            :href="seg.href"
                                            :target="seg.external ? '_blank' : undefined"
                                            :rel="UtilPost.linkRel(seg.external, post.externalLinkNofollow)"
                                            class="break-all text-primary underline underline-offset-2"
                                        >{{ seg.text }}</a>
                                        <EmoticonText v-else :text="seg.text" />
                                    </template>
                                </template>
                            </p>
                        </div>

                        <!-- AI 회원 글 — 본문 아래 한 줄 고지(배지와 따로, 사람이 읽는 표시 — 계약 「법무 메모」) -->
                        <p v-if="postAi" class="m-0 mb-4 flex items-center gap-1.5 rounded-lg bg-violet-50 px-3 py-2 text-xs leading-relaxed text-violet-900 dark:bg-violet-950/50 dark:text-violet-200" data-testid="post-ai-notice">
                            <i class="pi pi-microchip-ai text-[0.75rem]" aria-hidden="true" />{{ t('aiMember.postNotice', { provider: t(`aiMember.provider.${postAi.provider}`), model: postAi.model }) }}
                        </p>

                        <!-- 투표 — 본문 바로 다음, 찬반 토론 위 -->
                        <PostPollCard v-if="post.poll" :poll="post.poll" :actions="pollActions" @update:poll="onPollUpdate" />

                        <!-- 찬반 토론 — 본문 바로 다음, 추천 줄 위 -->
                        <CommentSection
                            v-if="showDebate"
                            variant="debate"
                            :board="board"
                            :data="opinions.data.value"
                            :state="opinions.state.value"
                            :error-message="opinions.errorMessage.value"
                            :sort="opinions.sort.value"
                            :kind="opinions.kind.value"
                            :actions="opinionActions"
                            :debate-actions="debateActions"
                            :sanction-enabled="sanctionEnabled"
                            :comment-delete-enabled="commentDeleteEnabled"
                            @kind="opinions.setKind"
                            @retry="opinions.load"
                            @report="(c) => openReport('COMMENT', c.id)"
                            @settings="openDebateSettings"
                            @sanction="openCommentSanction"
                            @staff-delete="(c) => openCommentDelete(c, 'debate')"
                        />

                        <!-- 자리 `AFTER_BODY` — 차단으로 접힌 글은 이 칸도 함께 접힌다 -->
                        <div v-if="inlineAd?.position === 'AFTER_BODY'" class="py-3" data-testid="post-inline-ad">
                            <AdTextLines v-if="inlineAd.type === 'TEXT_LINE'" :ads="inlineAd.ads" :count="1" placement="POST_INLINE" />
                            <AdSlot v-else :type="inlineAd.type" :ads="inlineAd.ads" placement="POST_INLINE" align="center" />
                        </div>
                        <!-- 장부 글 보기 광고가 없으면 애드센스 반응형 디스플레이(유형이 한 줄이어도). 광고가 오지 않으면 칸째 접힌다 -->
                        <AdSenseUnit v-else-if="inlineAdsense?.position === 'AFTER_BODY'" :key="`${post.id}:adsense-inline`" :spec="inlineAdsense.spec" class="py-3" data-testid="post-inline-adsense" @unfilled="inlineAdsenseUnfilled = post.id" />

                        <!-- 추천 · 링크 복사 · 신고 -->
                        <div class="flex flex-wrap items-center justify-center gap-2 py-3">
                            <Button
                                type="button"
                                :icon="post.liked ? 'pi pi-thumbs-up-fill' : 'pi pi-thumbs-up'"
                                :label="`${t('post.like')} ${post.likeCount.toLocaleString()}`"
                                :variant="post.liked ? undefined : 'outlined'"
                                :aria-pressed="post.liked"
                                :disabled="post.mine"
                                :loading="liking"
                                rounded
                                data-testid="post-like"
                                @click="onLike"
                            />
                            <Button type="button" icon="pi pi-link" :label="t('post.copyLink')" severity="secondary" variant="outlined" rounded @click="copyLink" />
                            <Button v-if="board.reportEnabled && !post.mine" type="button" icon="pi pi-flag" :label="t('post.report')" severity="secondary" variant="text" rounded @click="openReport('POST', post.id)" />
                        </div>

                        <!-- 작성자 한 줄 소개 — 프로필을 공개한 회원이 소개를 썼을 때만(사업자 핫딜 글은 상호로 보여 빼고, 비공개·비회원·탈퇴는 값이 없다) -->
                        <PostAuthorBioCard v-if="authorCard && !bizDeal" :author="post.author" :bio="authorBio" :private-profile="post.author.profile === 'PRIVATE'" :avatar-src="authorAvatar" :avatar-still-src="authorAvatarStill" :border-style="authorDecoration?.profileBorder ?? null" class="mb-3" />
                    </FoldedRow>

                    <!-- 찬반 토론 열기(토론이 없는 글 — 열 수 있는 사람만) · 수정·삭제 — 내 글은 바로, 비회원 글은 비밀번호 -->
                    <div v-if="manageable || canOpenDebate || postMenuItems.length" class="flex flex-wrap justify-end gap-1 border-t border-surface pt-2">
                        <Button v-if="canOpenDebate" type="button" icon="pi pi-comments" :label="t('comment.debateOpenAction')" size="small" severity="secondary" variant="text" data-testid="post-debate-open" @click="openDebateSettings" />
                        <template v-if="manageable">
                            <Button type="button" icon="pi pi-pencil" :label="t('common.edit')" size="small" severity="secondary" variant="text" data-testid="post-edit" @click="onEdit" />
                            <Button type="button" icon="pi pi-trash" :label="t('common.delete')" size="small" severity="danger" variant="text" data-testid="post-delete" @click="onDelete" />
                        </template>
                        <!-- ⋯ 게시판 운영 메뉴 — 그 게시판 매니저·스텝에게만(본인 글에는 없다). 「글 숨기기」·「글 삭제」(board-staff-action-contract) · 「작성자 제재」(남의 회원 글 — board-sanction-contract) -->
                        <template v-if="postMenuItems.length">
                            <Button
                                type="button"
                                icon="pi pi-ellipsis-h"
                                size="small"
                                severity="secondary"
                                variant="text"
                                rounded
                                :aria-label="t('boardSanction.postMenu')"
                                aria-haspopup="menu"
                                data-testid="post-staff-menu"
                                @click="postMenu?.toggle($event)"
                            />
                            <Menu ref="postMenu" :model="postMenuItems" popup />
                        </template>
                    </div>

                    <!-- 소모임 글의 공개 범위 — 안내는 모두에게, 단추는 서버가 준 권한(`group.can*`)이 있는 사람에게만 -->
                    <GroupPostPanel
                        v-if="post.group"
                        :group="post.group"
                        :mine="post.mine"
                        :secret="post.secret === true"
                        :author-name="post.author.memberId ? post.author.name : null"
                        :actions="groupPanelActions"
                        @change="onGroupChange"
                        @session="prompt('view')"
                    />

                    <BoardInvestmentNotice v-if="board.investmentNotice" variant="post" class="mt-3" />

                    <!-- 이전·다음 글 · 게시판으로(소모임 글에는 이전·다음 글이 없다) -->
                    <nav v-if="!post.group" class="mt-4 divide-y divide-surface border-y border-surface text-sm" :aria-label="t('post.neighborNav')">
                        <NuxtLink v-if="post.next" :to="`/posts/${post.next.id}`" class="flex gap-3 px-1 py-2.5 hover:bg-surface-100 dark:hover:bg-surface-800/60">
                            <span class="w-14 shrink-0 text-xs text-muted-color"><i class="pi pi-angle-up mr-1 text-[0.625rem]" aria-hidden="true" />{{ t('post.next') }}</span>
                            <span class="truncate">{{ post.next.title }}</span>
                        </NuxtLink>
                        <NuxtLink v-if="post.prev" :to="`/posts/${post.prev.id}`" class="flex gap-3 px-1 py-2.5 hover:bg-surface-100 dark:hover:bg-surface-800/60">
                            <span class="w-14 shrink-0 text-xs text-muted-color"><i class="pi pi-angle-down mr-1 text-[0.625rem]" aria-hidden="true" />{{ t('post.prev') }}</span>
                            <span class="truncate">{{ post.prev.title }}</span>
                        </NuxtLink>
                    </nav>
                    <div class="mt-3 flex justify-center">
                        <Button type="button" icon="pi pi-list" :label="post.group ? t('group.postListBack') : t('post.backToBoard')" severity="secondary" variant="outlined" size="small" @click="goBoard" />
                    </div>
                </div>

                <!-- 두 카드 사이라 `.content-card + .content-card` 간격이 걸리지 않아 위아래 여백이 그 반씩(0 이면 8px)을 맡는다 -->
                <div
                    v-if="inlineAd?.position === 'BETWEEN_CARDS' && inlineAd.ads && !folded"
                    class="my-[max(calc(var(--content-card-gap)/2),0.5rem)]"
                    :class="inlineAd.type === 'TEXT_LINE' ? 'rounded-md border border-hairline px-3' : ''"
                    data-testid="post-inline-ad"
                >
                    <AdTextLines v-if="inlineAd.type === 'TEXT_LINE'" :ads="inlineAd.ads" :count="1" slim placement="POST_INLINE" />
                    <AdSlot v-else :type="inlineAd.type" :ads="inlineAd.ads" placement="POST_INLINE" />
                </div>
                <!-- 두 카드 사이 애드센스 — 장부 광고가 없을 때만. 접히면 요소가 없어 원래 간격 -->
                <AdSenseUnit
                    v-else-if="inlineAdsense?.position === 'BETWEEN_CARDS' && !folded"
                    :key="`${post.id}:adsense-between`"
                    :spec="inlineAdsense.spec"
                    class="my-[max(calc(var(--content-card-gap)/2),0.5rem)]"
                    data-testid="post-inline-adsense"
                    @unfilled="inlineAdsenseUnfilled = post.id"
                />

                <div class="content-card" data-testid="post-comments-card">
                    <!-- 멤버 전용 댓글 안내(5단계) — 비공개 소모임의 개별 공개 글을 비멤버·비회원이 볼 때, 공개 전에 달려 가려진 댓글 수(목록의 댓글 수는 전체 그대로) -->
                    <p
                        v-if="membersOnlyCommentCount > 0"
                        class="m-0 mt-4 flex items-center gap-1.5 rounded-lg bg-surface-100 px-3 py-2 text-xs text-muted-color dark:bg-surface-800/50"
                        data-testid="comment-members-only-banner"
                    >
                        <i class="pi pi-lock text-[0.6875rem]" aria-hidden="true" />
                        <span>{{ t('groupVisibility.membersOnlyBanner', { n: membersOnlyCommentCount }) }}</span>
                    </p>

                    <!-- 일반 댓글 — 맨 아래 원래 댓글 자리(입장 없는 원댓글 + 답글) -->
                    <CommentSection
                        variant="general"
                        :board="board"
                        :data="comments.data.value"
                        :state="comments.state.value"
                        :error-message="comments.errorMessage.value"
                        :sort="comments.sort.value"
                        kind="GENERAL"
                        :actions="commentActions"
                        :sanction-enabled="sanctionEnabled"
                        :comment-delete-enabled="commentDeleteEnabled"
                        :ai-post="postAi"
                        @sort="comments.setSort"
                        @retry="comments.load"
                        @report="(c) => openReport('COMMENT', c.id)"
                        @sanction="openCommentSanction"
                        @staff-delete="(c) => openCommentDelete(c, 'general')"
                    />
                </div>
            </article>
        </BoardLayoutFrame>

        <BoardPasswordDialog
            v-model:visible="passwordOpen"
            :title="t('post.deleteTitle')"
            :description="t('post.guestPasswordDescription')"
            :confirm-label="t('common.delete')"
            danger
            :loading="deleting"
            :error="passwordError"
            @submit="deleteWithPassword"
        />
        <PostStatusChangeDialog
            v-if="post?.processStatus"
            v-model:visible="statusDialogOpen"
            :current="post.processStatus"
            :loading="statusSaving"
            :error="statusError"
            :note-error="statusNoteError"
            @submit="changeStatus"
        />
        <DebateSettingsDialog v-model:visible="debateDialogOpen" :initial="debateInitial" :close-locked="debateCloseLocked" :loading="debateSaving" :error="debateError" @save="saveDebate" />
        <BoardSanctionDialog
            :visible="sanctionForm.visible.value"
            :nickname="sanctionForm.target.value?.nickname ?? ''"
            :sealed="sanctionForm.target.value?.anonymous === true"
            :state="sanctionForm.state.value"
            :preview="sanctionForm.preview.value"
            :load-error="sanctionForm.loadError.value"
            :submitting="sanctionForm.submitting.value"
            :form-error="sanctionForm.formError.value"
            :field-errors="sanctionForm.fieldErrors.value"
            @update:visible="(open) => !open && sanctionForm.close()"
            @submit="sanctionForm.submit"
            @retry="sanctionForm.retry"
        />
        <BoardStaffActionDialog
            :visible="staffActionForm.visible.value"
            :kind="staffActionForm.target.value?.kind ?? 'POST_HIDE'"
            :target-text="staffActionForm.target.value?.text ?? ''"
            :quota="staffActionForm.quota.value"
            :unlimited="staffActionForm.unlimited.value"
            :submitting="staffActionForm.submitting.value"
            :form-error="staffActionForm.formError.value"
            :reason-field-error="staffActionForm.reasonFieldError.value"
            @update:visible="(open) => !open && staffActionForm.close()"
            @submit="staffActionForm.submit"
        />
        <BoardReportDialog v-model:visible="reportOpen" :target="reportTarget?.type ?? 'POST'" :deal="Boolean(post?.deal) && reportTarget?.type === 'POST'" :deal-request-sent="Boolean(post?.deal?.endRequests.mine)" :loading="reporting" :error="reportError" :field-error="reportFieldError" @submit="submitReport" />
    </div>
</template>

<script setup lang="ts">
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';
import type { PostDetail, PostProcessStatus, PostProcessStatusHistoryItem } from '~/composables/board/api/usePostApi.types';
import type { CommentView } from '~/composables/board/api/useCommentApi.types';
import type { PollView } from '~/composables/board/api/usePollApi.types';
import type { DealModeration, DealSameRef, PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { DealCardActions } from '~/components/hotdeal/HotdealDealCard.types';
import type { ReportReason, ReportTargetType } from '~/composables/report/api/useReportApi.types';
import type { CommentActions, DebateActions } from '~/components/board/CommentSection.types';
import type { DebateSettingsValues } from '~/components/board/DebateSettingsFields.types';
import type { PollCardActions } from '~/components/board/PostPollCard.types';
import type { GroupPostPanelActions } from '~/components/group/GroupPostPanel.types';
import UtilRichBody from '#layers/rich-editor/utils/util-rich-body';
import UtilRichEmoticon from '#layers/rich-editor/utils/util-rich-emoticon';
import UtilAd from '~/utils/util-ad';
import UtilBlockMessages from '~/utils/util-block-messages';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilBoardStaffAction from '~/utils/util-board-staff-action';
import UtilBusiness from '~/utils/util-business';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';
import UtilDebate from '~/utils/util-debate';
import UtilPost from '~/utils/util-post';
import UtilPostMessages from '~/utils/util-post-messages';
import UtilPostOps from '~/utils/util-post-ops';
import UtilPointShop from '~/utils/util-point-shop';
import UtilGroup from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';
import UtilIdentityMessages from '~/utils/util-identity-messages';
import UtilPostGroupPanel, { type PostGroupPatch } from '~/utils/util-post-group-panel';

definePageMeta({ shell: 'inner' });

const { t } = useI18n();
const route = useRoute();
const router = useRouter();
const postApi = usePostApi();
const boardApi = useBoardApi();
const reportApi = useReportApi();
const debateApi = useDebateApi();
const pollApi = usePollApi();
const groupApi = useGroupApi();
const hotdealApi = useHotdealApi();
const businessApi = useBusinessApi();
const { user } = useAuth();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { prompt } = useLoginPrompt();
const { localizedName } = useLocalizedName();
const current = useCurrentBoard();
const time = useDisplayTime();
const overrides = useBlockOverrides();
const decorations = useMyDecoration();
const titleId = useId();

const id = computed(() => String(route.params.id ?? ''));
const siteOrigin = typeof window === 'undefined' ? '' : window.location.origin;

/* ── 불러오기 ── */
type PageState = 'loading' | 'ready' | 'notfound' | 'hidden' | 'groupPrivate' | 'sellerHidden' | 'adultOnly' | 'secretLocked' | 'error';
const post = ref<PostDetail | null>(null);
/** AI 회원의 글 — 본문 아래 고지·기계가 읽는 표시·댓글 입력 안내(`author.ai` — 이 글을 만든 모델) */
const postAi = computed(() => (post.value?.author.kind === 'MEMBER' ? (post.value.author.ai ?? null) : null));
/** 읽을 수 없는 비밀글(`403 SECRET_POST` 의 extras `boardSlug` — 서버 후보) — [목록으로]가 갈 게시판. 없으면 뒤로 */
const secretBoardSlug = ref<string | null>(null);
/** 소모임 비밀글이면 extras `groupId`(서버 후보) — [목록으로]가 소모임으로 간다 */
const secretGroupId = ref<string | null>(null);
/** 비회원 비밀글(extras `guestPost: true`) — 잠금 화면에 글 비밀번호 칸 */
const secretGuest = ref(false);
const unlockPassword = ref('');
const unlockError = ref('');
const unlocking = ref(false);

/** 비회원 비밀글을 글 비밀번호로 연다 — 성공(204)하면 글을 다시 받는다. 틀린 비밀번호(`403 PASSWORD_MISMATCH`)·여러 번 틀림(`429 PASSWORD_ATTEMPTS_EXCEEDED`)은 칸 아래 서버 문구 */
async function unlockSecret(): Promise<void> {
    const password = unlockPassword.value;
    if (!password || unlocking.value) return;
    unlocking.value = true;
    unlockError.value = '';
    try {
        await postApi.secretUnlock(id.value, password);
        unlockPassword.value = '';
        await load();
    } catch (e) {
        unlockError.value = UtilBoardMessages.of(t, e);
    } finally {
        unlocking.value = false;
    }
}

/** 잠금 화면의 [목록으로] — 소모임 비밀글은 소모임, 일반 비밀글은 게시판, 모르면 뒤로 */
function leaveSecret(): void {
    if (secretGroupId.value) void navigateTo(UtilGroup.detailPath(secretGroupId.value));
    else if (secretBoardSlug.value) void navigateTo(`/boards/${secretBoardSlug.value}`);
    else router.back();
}
/** 19금 글을 성인이 아닌 사람이 열었다(`403 POST_ADULT_ONLY` 의 extras — `reason` LOGIN·IDENTITY·UNDERAGE, `adultFrom` 은 미성년일 때) */
const adultBlock = ref<{ reason: 'LOGIN' | 'IDENTITY' | 'UNDERAGE'; adultFrom: string | null } | null>(null);
const identityGate = useIdentityGate();
const adultVerifying = ref(false);
/** 판매자가 내린 사업자 핫딜 글(`403 DEAL_SELLER_HIDDEN` 의 extras — 서버 `DealSellerHiddenException`, `applicationId` 는 작성자에게만) */
const sellerHidden = ref<{ state: 'PAUSED' | 'STOPPED'; boardSlug: string; boardName: string; boardNameEn: string | null; applicationId: string | null } | null>(null);
const board = ref<BoardDetail | null>(null);
/** 글 보기 머리의 말머리 모양 — 게시판 정보의 `prefixStyle`(서버가 사이트 기본까지 푼 값), 칸이 없으면 글자(mobile-nav-prefix-style-contract 1절) */
const headPrefixStyle = computed(() => UtilBoard.prefixStyle(board.value?.prefixStyle));
const pageState = ref<PageState>('loading');
/** 글쓴이 장식(테두리) — 서버 `author.decoration`(후보)에 내가 방금 장착·해제한 것을 겹친다 */
const authorDecoration = computed(() => (post.value ? decorations.decorationOf(post.value.author.memberId, post.value.author.decoration) : null));
/** 글쓴이 아바타 — 서버 `author.avatarUrl`(후보)에 내가 방금 고른 것을 겹친다(비회원은 null → 이니셜) */
const authorAvatar = computed(() => (post.value ? decorations.avatarOf(post.value.author.memberId, post.value.author.avatarUrl) : null));
/** 움직이는 아바타의 정지 그림(움직임 줄이기) */
const authorAvatarStill = computed(() => (post.value ? decorations.avatarStillOf(post.value.author.memberId, post.value.author.avatarStillUrl) : null));
/** 작성자 카드를 그리는가 — 회원 글이고 서버가 공개 범위(`author.profile`)를 줬을 때(비회원·탈퇴·숨긴 글은 null 이라 그리지 않는다). 소개가 없거나 비공개여도 카드는 그린다(사용자 2026-11-08) */
const authorCard = computed(() => {
    const author = post.value?.author;
    return !!(author?.kind === 'MEMBER' && author.memberId && author.name && author.profile);
});
/** 작성자 자기소개 — 서버가 회원·프로필 공개·소개 있음일 때만 `author.bio` 를 채운다(그 밖은 null → 카드가 「자기소개가 없습니다」·「비공개 프로필」) */
const authorBio = computed(() => post.value?.author.bio?.trim() || null);
const pageError = ref('');
/** 접힌 글을 「보기」로 펼쳤다 — 이 글을 보는 동안만(다른 글로 가면 다시 접힌다) */
const foldRevealed = ref(false);

const publicAds = usePublicAds();
void publicAds.ensurePlacements();
/**
 * 글 보기 광고를 물을 게시판 — 소모임 글·`adsHidden` 이면 빈 글자, 19금 글(`post.adult`)·19금 게시판은 자리 설정 `adult.enabled` 가 꺼져 있으면 빈 글자
 * (게시판 맥락 호출이라 `board` 로 넘긴다 — 서버는 게시판만 판정하고 19금 글은 화면이 거른다)
 */
const inlineBoard = computed(() => {
    const p = post.value;
    const adultOn = publicAds.adultEnabled.value;
    if (!p || p.group || (p.adult && !adultOn) || board.value?.slug !== p.board.slug || UtilAd.boardAdsHidden(board.value, adultOn)) return '';
    return p.board.slug;
});
/*
 * 글 보기 애드센스 — 장부 글 보기 광고 후보가 0건인 자리를 반응형 디스플레이로(글 보기 유형이 한 줄이어도). 위치는 자리 설정 그대로.
 * 장부 규칙(`inlineBoard` — 소모임·광고 숨김·19금 옵션)에 더해 19금 글·19금 게시판(옵션과 상관없이)·비밀글은 늘 뺀다. 관리자 미리보기는 `useAdSense` 가 뺀다.
 */
const adsense = useAdSense();
/** 광고가 오지 않아 접은 글 — 다른 글로 가면 다시 그린다 */
const inlineAdsenseUnfilled = ref<string | null>(null);
const inlineAdsense = computed(() => {
    const p = publicAds.placements.value?.postInline;
    const cur = post.value;
    if (!p?.enabled || !inlineBoard.value || !cur || cur.adult || cur.secret || board.value?.allowAdult || inlineAdsenseUnfilled.value === cur.id) return null;
    const ads = publicAds.candidates(UtilAd.inlineType(p.adType), inlineBoard.value);
    if (ads === undefined || ads.length > 0) return null;
    const spec = adsense.unit('POST_INLINE');
    return spec ? { spec, position: UtilAd.inlinePosition(p.position) } : null;
});

/** 자리 꺼짐·광고 없는 글·후보 0건이면 null */
const inlineAd = computed(() => {
    const p = publicAds.placements.value?.postInline;
    if (!p?.enabled || !inlineBoard.value) return null;
    const type = UtilAd.inlineType(p.adType);
    const ads = publicAds.candidates(type, inlineBoard.value);
    if (ads !== undefined && ads.length === 0) return null;
    return { type, position: UtilAd.inlinePosition(p.position), ads: ads ?? null };
});
watch(
    () => {
        const p = publicAds.placements.value?.postInline;
        return p?.enabled && inlineBoard.value ? `${UtilAd.inlineType(p.adType)}@${inlineBoard.value}` : '';
    },
    (key) => {
        const p = publicAds.placements.value?.postInline;
        if (key && p) void publicAds.ensure([UtilAd.inlineType(p.adType)], inlineBoard.value);
    },
    { immediate: true },
);

let request = 0;
async function load(): Promise<void> {
    const mine = ++request;
    pageState.value = 'loading';
    foldRevealed.value = false;
    try {
        const loaded = await postApi.post(id.value);
        if (mine !== request) return;
        // 소모임 글의 게시판은 공개 게시판 API 에 없다 — 소모임 게시판 정보(`GET /api/groups/{id}/board`)로 받는다
        const loadedBoard =
            board.value?.slug === loaded.board.slug && (post.value?.group?.id ?? null) === (loaded.group?.id ?? null)
                ? board.value
                : loaded.group
                  ? await groupApi.board(loaded.group.id)
                  : await boardApi.board(loaded.board.slug);
        if (mine !== request) return;
        post.value = loaded;
        board.value = loadedBoard;
        // 날개 배너 — 그 게시판 `board` 로 부른다. 소모임 글·광고 숨김 게시판·19금 글/게시판(옵션 꺼짐)이면 그리지 않는다
        publicAds.setWingContext({
            path: route.path,
            board: loaded.board.slug,
            adsHidden: loadedBoard.adsHidden === true,
            adult: loaded.adult || loadedBoard.allowAdult === true,
            group: Boolean(loaded.group),
            // 비밀글은 애드센스만 뺀다(장부 날개는 지금 규칙 그대로)
            secret: loaded.secret === true,
        });
        if (!loaded.group) {
            current.set(loaded.board.slug);
            // 떠 있는 글쓰기 단추(`WriteFab`)가 이 게시판에 쓸 수 있을 때만 보이게 판정을 알린다
            current.setWrite({ slug: loaded.board.slug, canWrite: loadedBoard.viewer.canWrite, reason: loadedBoard.viewer.writeBlockedReason });
        }
        pageState.value = 'ready';
        opinionsStarted = false;
        void comments.load();
        startOpinions();
        void loadSameDeals(loaded);
    } catch (e) {
        if (mine !== request) return;
        pageError.value = UtilBoardMessages.of(t, e);
        const code = e instanceof ApiError ? e.code : '';
        sellerHidden.value = e instanceof ApiError && code === 'DEAL_SELLER_HIDDEN' ? sellerHiddenOf(e) : null;
        adultBlock.value = e instanceof ApiError && code === 'POST_ADULT_ONLY' ? adultBlockOf(e) : null;
        secretBoardSlug.value = e instanceof ApiError && code === 'SECRET_POST' ? (e.extraText('boardSlug') ?? null) : null;
        secretGroupId.value = e instanceof ApiError && code === 'SECRET_POST' ? (e.extraText('groupId') ?? null) : null;
        secretGuest.value = e instanceof ApiError && code === 'SECRET_POST' && e.extras?.guestPost === true;
        pageState.value = sellerHidden.value
            ? 'sellerHidden'
            : adultBlock.value
              ? 'adultOnly'
              : code === 'SECRET_POST'
                ? 'secretLocked'
                : code === 'DEAL_SELLER_HIDDEN'
                ? 'hidden'
                : code === 'GROUP_POST_PRIVATE'
                  ? 'groupPrivate'
                  : code === 'POST_NOT_FOUND' || code === 'BOARD_NOT_FOUND' || code === 'GROUP_NOT_FOUND'
                    ? 'notfound'
                    : code === 'POST_HIDDEN'
                      ? 'hidden'
                      : 'error';
    }
}

function adultBlockOf(e: ApiError): NonNullable<typeof adultBlock.value> {
    const reason = e.extraText('reason');
    return { reason: reason === 'LOGIN' || reason === 'UNDERAGE' ? reason : 'IDENTITY', adultFrom: e.extraText('adultFrom') ?? null };
}

/** 「본인인증하고 보기」 — 본인인증 대화상자(인증 → 만 19세 이상이면 닫힘)가 통과하면 글을 다시 읽는다. 미성년이면 대화상자가 막는 안내로 바뀐다 */
async function verifyForAdult(): Promise<void> {
    adultVerifying.value = true;
    try {
        if (await identityGate.ensure({ purpose: 'ADULT_CONTENT' })) await load();
    } finally {
        adultVerifying.value = false;
    }
}

/** 댓글 동작 한 벌 — 목록 하나(`usePostComments`)의 함수들 */
function actionsOf(list: ReturnType<typeof usePostComments>): CommentActions {
    return {
        create: (content, contentFormat, target, guest, opinionStance) => list.create(content, contentFormat, target, guest, opinionStance),
        update: (commentId, content, contentFormat, password) => list.update(commentId, content, contentFormat, password),
        remove: (commentId, password) => list.remove(commentId, password),
        like: (commentId) => list.like(commentId),
        dislike: (commentId) => list.dislike(commentId),
        undislike: (commentId) => list.undislike(commentId),
    };
}

/** 일반 댓글(맨 아래) — 늘 `GENERAL` */
const comments = usePostComments(id, 'GENERAL');
const commentActions = actionsOf(comments);
/** 토론 의견(본문 영역 안) — `OPINION` 에서 시작, 칩으로 한 입장 */
const opinions = usePostComments(id, 'OPINION');
const opinionActions = actionsOf(opinions);
const debateActions: DebateActions = {
    vote: (stance) => opinions.vote(stance),
    commenters: (q, signal) => opinions.commenters(q, signal),
    requestPin: (commentId) => opinions.requestPin(commentId),
    cancelPin: (commentId) => opinions.cancelPin(commentId),
    pinRequests: () => opinions.pinRequests(),
    approvePin: (requestId) => opinions.approvePin(requestId),
    rejectPin: (requestId, reason) => opinions.rejectPin(requestId, reason),
};

/** 토론 구획을 보일까 — 가장 최근 목록의 `debate`(의견 수 포함), 받기 전에는 글 상세의 `debate`(열림·표 수) */
const showDebate = computed(() => {
    const listed = opinions.data.value?.debate ?? comments.data.value?.debate;
    return listed ? UtilDebate.visible(listed) : UtilDebate.visible(post.value?.debate);
});
/** 이 글의 의견 목록을 이미 불렀다 — 토론이 보일 때 한 번만 부르고, 그 뒤로는 목록이 스스로 다시 받는다 */
let opinionsStarted = false;
function startOpinions(): void {
    if (opinionsStarted || pageState.value !== 'ready' || !showDebate.value) return;
    opinionsStarted = true;
    void opinions.load();
}
// 글 상세로는 몰랐지만 일반 목록이 의견이 있다고 알려 줄 때(닫힌 토론 등)·토론을 새로 열었을 때
watch(showDebate, () => startOpinions());

/* ── 핫딜 ── */
/** 같은 딜(진행 중인 다른 글) — 받는 중이면 null */
const sameDeals = ref<DealSameRef[] | null>(null);
async function loadSameDeals(loaded: PostDetail): Promise<void> {
    sameDeals.value = null;
    if (!loaded.deal || loaded.deal.sameDealCount === 0) return;
    try {
        const list = await hotdealApi.same(loaded.id);
        if (post.value?.id === loaded.id) sameDeals.value = list;
    } catch {
        // 못 받으면 개수만 보인다(묶음 목록 없이)
        if (post.value?.id === loaded.id) sameDeals.value = [];
    }
}
/** 딜 카드가 부르는 동작 — 지금 보는 글의 딜 */
const dealActions: DealCardActions = {
    endRequest: (reason) => hotdealApi.endRequest(id.value, reason),
    end: () => hotdealApi.end(id.value),
    reopen: () => hotdealApi.reopen(id.value),
    keep: () => hotdealApi.keep(id.value),
    moderateEnd: (reason) => hotdealApi.moderateEnd(id.value, reason),
    moderateReopen: (reason) => hotdealApi.moderateReopen(id.value, reason),
    // 사업자 글 작성자 [일시 정지] — 그 글의 신청 건(`deal.business.applicationId` — 작성자에게만 온다)으로
    bizPauseInfo: async () => {
        const appId = post.value?.deal?.business?.applicationId;
        if (!appId) return null;
        const a = await businessApi.application(appId);
        return UtilBusiness.runningPausable(a) && a.pause ? { endDate: a.endDate, used: a.pause.used, limit: a.pause.limit } : null;
    },
    bizPause: async () => {
        const appId = post.value?.deal?.business?.applicationId;
        if (appId) await businessApi.pause(appId);
    },
};

/** `403 DEAL_SELLER_HIDDEN` 의 extras → 안내 칸 — 모르는 상태면 null(숨김 글 화면으로) */
function sellerHiddenOf(e: ApiError): NonNullable<typeof sellerHidden.value> | null {
    const state = e.extraText('state');
    if (state !== 'PAUSED' && state !== 'STOPPED') return null;
    return {
        state,
        boardSlug: e.extraText('boardSlug') ?? '',
        boardName: e.extraText('boardName') ?? '',
        boardNameEn: e.extraText('boardNameEn') ?? null,
        applicationId: e.extraText('applicationId') ?? null,
    };
}
const sellerHiddenBoard = computed(() => (sellerHidden.value?.boardName ? localizedName(sellerHidden.value.boardName, sellerHidden.value.boardNameEn) : ''));
/** 카드가 올린 새 딜 — 글을 다시 받지 않는다. 게시판 관리 단추(`dealModeration`)는 새 딜 상태에 맞춘다 */
function onDealUpdate(next: PostDeal): void {
    if (!post.value) return;
    post.value.deal = next;
    post.value.dealModeration = UtilHotdeal.moderationAfter(post.value.dealModeration ?? null, next);
}
/** 게시판 관리 자리가 빠졌다(`403 DEAL_MODERATE_FORBIDDEN`) — 카드의 영역을 거둔다 */
function onDealModerationUpdate(next: DealModeration | null): void {
    if (post.value) post.value.dealModeration = next;
}
/**
 * 가격 허위 신고(D4) 뒤 딜만 서버 값으로 — 신고 응답(204)에는 딜이 없고, 서버는 이미 알렸거나 자격 미달·한도·끝난 딜이면 신고만 받아 집계가 그대로일 수 있다.
 * 게시판 관리가 그새 바뀐 상태(`409 DEAL_ENDED`·`DEAL_NOT_ENDED`)를 만났을 때도 쓴다(카드 `stale`).
 * 딜만 읽는 API 가 없어 글을 다시 읽고 `deal`·`dealModeration` 만 바꾼다(조회수는 같은 회원 중복 창 안이라 다시 세지 않는다). 못 받으면 지금 카드 그대로 두고 null
 */
async function refreshDeal(postId: string): Promise<PostDeal | null> {
    try {
        const fresh = await postApi.post(postId);
        if (post.value?.id === postId) {
            post.value.deal = fresh.deal ?? null;
            post.value.dealModeration = fresh.dealModeration ?? null;
        }
        return fresh.deal ?? null;
    } catch {
        // 다음에 글을 받을 때 바로잡힌다
        return null;
    }
}

/* ── 투표 ── */
/** 투표 카드가 부르는 동작 — 지금 보는 글의 투표 */
const pollActions: PollCardActions = {
    vote: (optionIds) => pollApi.vote(id.value, optionIds),
    reload: () => pollApi.poll(id.value),
    remove: () => pollApi.remove(id.value),
};
/** 카드가 올린 새 투표 — 지웠거나 없어졌으면 null(카드가 사라진다) */
function onPollUpdate(next: PollView | null): void {
    if (post.value) post.value.poll = next;
}

/* ── 토론 열기·설정·닫기 ── */
/** 가장 최근의 토론 요약 — 의견 목록 → 일반 목록 → 글 상세 차례 */
const currentDebate = computed(() => opinions.data.value?.debate ?? comments.data.value?.debate ?? post.value?.debate ?? null);
/** 토론이 없는 글에서 열 수 있다(글쓴이 규칙·그 게시판 매니저) — 토론 구획이 보이면 그 머리의 [토론 설정]을 쓴다 */
const canOpenDebate = computed(() => Boolean(currentDebate.value?.canManage) && !showDebate.value);
const debateInitial = computed<DebateSettingsValues>(() => UtilDebate.settingsOf(currentDebate.value));
/** 투표가 있어 닫을 수 없다(글쓴이) */
const debateCloseLocked = computed(() => Boolean(currentDebate.value?.enabled && !currentDebate.value.canClose));
const debateDialogOpen = ref(false);
const debateSaving = ref(false);
const debateError = ref('');

function openDebateSettings(): void {
    debateError.value = '';
    debateDialogOpen.value = true;
}

/** 두 댓글 목록을 다시 받아 토론 상태(열림·설정·권한)를 맞춘다 — 의견 목록은 이미 불렀거나 토론이 보일 때만 */
function reloadDebateLists(): void {
    void comments.load();
    if (opinionsStarted) void opinions.load();
    else startOpinions();
}

async function saveDebate(values: DebateSettingsValues): Promise<void> {
    const p = post.value;
    if (!p || debateSaving.value) return;
    const before = debateInitial.value;
    debateSaving.value = true;
    debateError.value = '';
    try {
        p.debate = await debateApi.settings(p.id, UtilDebate.settingsBody(values));
        debateDialogOpen.value = false;
        toast.success(!before.enabled && values.enabled ? t('comment.debateOpenedToast') : before.enabled && !values.enabled ? t('comment.debateClosedToast') : t('comment.debateSavedToast'));
        reloadDebateLists();
    } catch (e) {
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') {
            debateDialogOpen.value = false;
            prompt('opinion');
        } else if (e instanceof ApiError && e.code === 'DEBATE_MANAGE_DENIED') {
            // 그새 바꿀 수 없게 됐다(운영자가 연 토론·게시판 옵션 꺼짐 등) — 알리고 지금 상태로
            debateDialogOpen.value = false;
            toast.error(e.message, 8000);
            reloadDebateLists();
        } else {
            // 409 DEBATE_HAS_VOTES(그새 투표가 생김) 등 — 대화창 안에 서버 문구, 목록을 다시 받아 「닫을 수 없음」을 맞춘다
            debateError.value = e instanceof ApiError && e.fields ? Object.values(e.fields).join(' ') || e.message : UtilBoardMessages.of(t, e);
            if (e instanceof ApiError && e.code === 'DEBATE_HAS_VOTES') reloadDebateLists();
        }
    } finally {
        debateSaving.value = false;
    }
}

// 댓글 수가 바뀌면(쓰기·지우기) 글 머리의 보호 상태가 달라질 수 있다 — 글은 다시 받지 않고 댓글 수만 맞춘다(두 목록 모두 글 전체 수를 준다)
watch(
    () => [comments.data.value?.totalCount, opinions.data.value?.totalCount] as const,
    ([general, opinion], [prevGeneral, prevOpinion]) => {
        const count = opinion !== prevOpinion && opinion !== undefined ? opinion : general;
        if (post.value && count !== undefined) post.value.commentCount = count;
    },
);

watch(id, () => void load(), { immediate: true });
// 19금 안내에서 로그인했으면 다시 읽는다(성인이면 글이 열리고, 아니면 「본인인증하고 보기」로 바뀐다)
watch(
    () => user.value?.id,
    (now, before) => {
        if (now && now !== before && pageState.value === 'adultOnly') void load();
    },
);
onBeforeUnmount(() => {
    if (post.value && !post.value.group) current.clear(post.value.board.slug);
});

const boardName = computed(() => (board.value ? localizedName(board.value.name, board.value.nameEn) : post.value ? localizedName(post.value.board.name, post.value.board.nameEn) : ''));
const paragraphs = computed(() => (post.value ? UtilPost.paragraphs(post.value.content) : []));
/** 접는 까닭 — 서버의 `blocked`(`USER|WORD|null`)에 이 기기에서 방금 바꾼 차단을 겹친다(게시판 글 목록과 같다). 내 글은 접지 않는다 */
const foldReason = computed(() => (post.value && !post.value.mine ? overrides.blockedOf(post.value.author.memberId, post.value.blocked) : null));
/** 지금 접혀 있다 — 제목·본문을 가린다 */
const folded = computed(() => foldReason.value !== null && !foldRevealed.value);

/** 에디터 글 본문 — 그린 직후(마운트·본문이 바뀔 때)에 유튜브·이미지를 손질한다(`flush: 'post'` 라 `v-html` 이 DOM 에 들어간 뒤에 돈다) */
const htmlBody = ref<HTMLElement | null>(null);
watch(
    [htmlBody, () => post.value?.content],
    () => {
        if (htmlBody.value) UtilRichBody.enhance(t, htmlBody.value);
    },
    { flush: 'post' },
);
/** 글 본문의 이모티콘 토큰 — 글자 노드만 그림으로(링크·코드 안 제외). 토큰이 있을 때만 카탈로그를 받고, 늦게 오면 다시 바꾼다 */
const emoticonCatalog = useEmoticonCatalog();
watch(
    [htmlBody, () => post.value?.content, emoticonCatalog.version],
    () => {
        const el = htmlBody.value;
        if (!el || !UtilRichEmoticon.has(post.value?.content)) return;
        void emoticonCatalog.ensureLoaded();
        UtilRichEmoticon.replaceIn(el, emoticonCatalog.resolve);
    },
    { flush: 'post' },
);
/** 사업자 핫딜 글(2차 — `deal.source BUSINESS`) — 게시 뒤 고칠 수 없다(종료만, 사업자 센터·딜 카드에서) */
const bizDeal = computed(() => UtilHotdeal.isBusiness(post.value?.deal));
/** 수정·삭제 단추 — 내 회원 글이거나 비회원 글(비밀번호로 확인). 사업자 핫딜 글은 작성자에게도 없다(가정: 삭제도 종료로 대신) */
const manageable = computed(() => Boolean(post.value && !bizDeal.value && (post.value.mine || post.value.author.kind === 'GUEST')));
const protectionNotice = computed(() => (post.value ? UtilPostMessages.protection(t, post.value.protection) : null));

/*
 * ── 처리 상태(운영 게시판 — 일반·소모임 게시판. 서버가 글쓴이에게 「처리 상태가 '완료'로 바뀌었어요」 알림) ──
 * [상태 바꾸기] → 대화창(상태 + 메모 0~200자). 실패: `400 fields.note` 는 메모 칸 아래, 그 밖(409 POST_STATUS_DISABLED·400 fields.status·403)은 대화창 안 서버 문구.
 */
const canChangeStatus = computed(() => Boolean(post.value && board.value && UtilPostOps.canChangeStatus(post.value, board.value.viewer.staff)));
const statusDialogOpen = ref(false);
const statusSaving = ref(false);
const statusError = ref('');
const statusNoteError = ref('');
function openStatusDialog(): void {
    statusError.value = '';
    statusNoteError.value = '';
    statusDialogOpen.value = true;
}
/** 고른 상태(+ 메모)로 바꾼다 — 같은 값이면 아무것도 하지 않는다. 바꾼 뒤 펼친 이력은 다시 받는다 */
async function changeStatus(value: PostProcessStatus, note: string | undefined): Promise<void> {
    const p = post.value;
    if (!p || value === p.processStatus || statusSaving.value) return;
    statusSaving.value = true;
    statusError.value = '';
    statusNoteError.value = '';
    try {
        const result = await postApi.changeProcessStatus(p.id, value, note);
        if (post.value?.id !== p.id) return;
        const savedNote = result.processStatusNote !== undefined ? result.processStatusNote : (note ?? null);
        post.value = { ...post.value, processStatus: result.processStatus, processStatusAt: result.processStatusAt, processStatusNote: savedNote };
        statusDialogOpen.value = false;
        toast.success(t('post.statusChanged', { name: t(UtilPostOps.labelKey(result.processStatus)) }));
        if (historyOpen.value) void loadHistory();
        else history.state = 'idle'; // 다음에 펼칠 때 새로 받는다
    } catch (e) {
        const noteField = e instanceof ApiError ? e.field('note') : undefined;
        if (noteField) statusNoteError.value = noteField;
        else statusError.value = e instanceof ApiError && e.field('status') ? (e.field('status') ?? '') : UtilBoardMessages.of(t, e);
    } finally {
        statusSaving.value = false;
    }
}

/* 처리 이력 — 기본 접힘, 펼칠 때 한 번 받는다(바꾼 뒤엔 다시). 글이 바뀌면 접고 비운다 */
const historyOpen = ref(false);
const history = reactive({ state: 'idle' as 'idle' | 'loading' | 'ready' | 'error', items: [] as PostProcessStatusHistoryItem[], errorMessage: '' });
let historyRequest = 0;
async function loadHistory(): Promise<void> {
    const p = post.value;
    if (!p) return;
    const mine = ++historyRequest;
    history.state = 'loading';
    history.errorMessage = '';
    try {
        const result = await postApi.processStatusHistory(p.id);
        if (mine !== historyRequest) return;
        history.items = result.items ?? [];
        history.state = 'ready';
    } catch (e) {
        if (mine !== historyRequest) return;
        history.errorMessage = UtilBoardMessages.of(t, e);
        history.state = 'error';
    }
}
watch(historyOpen, (open) => {
    if (open && history.state === 'idle') void loadHistory();
});
watch(
    () => post.value?.id ?? null,
    () => {
        historyRequest++;
        historyOpen.value = false;
        history.state = 'idle';
        history.items = [];
    },
);

function goBoard(): void {
    // 소모임 글은 소모임 글 탭으로(해산한 소모임은 소모임 목록으로)
    const group = post.value?.group;
    if (group) {
        void navigateTo(group.dissolved ? UtilGroup.LIST_PATH : UtilGroup.tabPath(group.id, 'posts'));
        return;
    }
    const slug = post.value?.board.slug ?? board.value?.slug;
    void navigateTo(slug ? `/boards/${slug}` : '/');
}

/* ── 소모임 글 공개 범위(5단계 — 패널이 부르는 동작, 바뀐 칸은 패널이 `change` 로 올린다) ── */
/** 지금 보는 글의 소모임 — 동작을 부르는 순간의 값을 쓴다 */
function groupIds(): { groupId: string; postId: string } {
    const p = post.value;
    if (!p?.group) throw new Error('not a group post');
    return { groupId: p.group.id, postId: p.id };
}

const groupPanelActions: GroupPostPanelActions = {
    request: (note) => {
        const { groupId, postId } = groupIds();
        return groupApi.requestPublish(groupId, postId, { ...(note ? { note } : {}), acknowledged: true });
    },
    cancel: () => {
        const { groupId, postId } = groupIds();
        return groupApi.cancelPublishRequest(groupId, postId);
    },
    approve: () => {
        const { groupId, postId } = groupIds();
        return groupApi.approvePublishRequest(groupId, postId);
    },
    reject: (reason) => {
        const { groupId, postId } = groupIds();
        return groupApi.rejectPublishRequest(groupId, postId, reason || undefined);
    },
    setVisibility: (visibility, acknowledged) => {
        const { groupId, postId } = groupIds();
        return groupApi.setPostVisibility(groupId, postId, acknowledged ? { visibility, acknowledged: true } : { visibility });
    },
};

/** 패널이 올린 바뀐 칸을 글 상세의 `group` 에 덮어쓴다(글을 다시 받지 않는다 — 조회수·읽던 자리·댓글이 그대로) */
function onGroupChange(patch: PostGroupPatch): void {
    const p = post.value;
    if (p?.group) p.group = { ...p.group, ...patch };
}

/** 비멤버가 보는 비공개 소모임의 개별 공개 글이면 그 소모임 — 본문 위 안내 줄이 쓴다(멤버는 `membersOnlyCommentCount` 가 null 이라 없다) */
const individualGroup = computed(() => {
    const g = post.value?.group;
    return g && !g.dissolved && UtilPostGroupPanel.isIndividualForVisitor(g) ? g : null;
});
/** 멤버 전용으로 가려진 댓글 수 — 댓글 목록 응답(`membersOnlyCount`)이 먼저, 받기 전에는 글 상세의 수 */
const membersOnlyCommentCount = computed(() => {
    const fromList = comments.data.value?.membersOnlyCount;
    if (typeof fromList === 'number') return fromList;
    const fromPost = post.value?.group?.membersOnlyCommentCount;
    return typeof fromPost === 'number' ? fromPost : 0;
});

/* ── 추천·링크 복사 ── */
const liking = ref(false);
async function onLike(): Promise<void> {
    const p = post.value;
    if (!p || liking.value) return;
    if (board.value?.viewer.guest) return prompt('like');
    if (p.liked) return toast.show(t('post.alreadyLiked'));
    liking.value = true;
    try {
        const result = await postApi.like(p.id);
        p.likeCount = result.likeCount;
        p.liked = result.liked;
    } catch (e) {
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') prompt('like');
        else toast.error(UtilBoardMessages.of(t, e));
    } finally {
        liking.value = false;
    }
}

async function copyLink(): Promise<void> {
    try {
        await navigator.clipboard.writeText(window.location.href);
        toast.success(t('post.linkCopied'));
    } catch {
        toast.error(t('post.linkCopyFailed'));
    }
}

/* ── 수정·삭제 ── */
function onEdit(): void {
    const p = post.value;
    if (!p) return;
    if (p.protection.editBlocked) return toast.show(t('post.protectEditToast', { n: p.protection.threshold }));
    void navigateTo(`/posts/${p.id}/edit`);
}

const passwordOpen = ref(false);
const passwordError = ref('');
const deleting = ref(false);

async function onDelete(): Promise<void> {
    const p = post.value;
    if (!p) return;
    if (p.protection.active && p.protection.deleteMode === 'BLOCK') return toast.show(t('post.protectDeleteToast', { n: p.protection.threshold }));
    if (p.author.kind === 'GUEST') {
        passwordError.value = '';
        passwordOpen.value = true;
        return;
    }
    const delayed = p.protection.active && p.protection.deleteMode === 'DELAY';
    const ok = await confirm({
        title: t('post.deleteTitle'),
        message: delayed ? t('post.deleteDelayMessage', { minutes: p.protection.delayMinutes }) : t('post.deleteMessage'),
        confirmLabel: t('common.delete'),
        tone: 'danger',
    });
    if (!ok) return;
    deleting.value = true;
    try {
        await finishDelete(await postApi.remove(p.id, { password: null }));
    } catch (e) {
        toast.error(UtilHotdealMessages.dealError(t, e) ?? UtilBoardMessages.of(t, e), 8000); // 사업자 핫딜 글 `403 DEAL_BUSINESS_POST_LOCKED` 는 안내 문구
    } finally {
        deleting.value = false;
    }
}

async function deleteWithPassword(password: string): Promise<void> {
    const p = post.value;
    if (!p) return;
    deleting.value = true;
    passwordError.value = '';
    try {
        const result = await postApi.remove(p.id, { password });
        passwordOpen.value = false;
        await finishDelete(result);
    } catch (e) {
        // 틀린 비밀번호·보호 중 거절은 서버 문구 그대로
        passwordError.value = UtilBoardMessages.of(t, e);
    } finally {
        deleting.value = false;
    }
}

async function finishDelete(result: { scheduledAt: string | null }): Promise<void> {
    if (result.scheduledAt) {
        toast.show(t('post.deleteScheduledToast', { at: time.value.full(result.scheduledAt) }));
        await load();
        return;
    }
    toast.success(t('post.deleted'));
    goBoard();
}

/* ── 신고(글·댓글) ── */
const reportOpen = ref(false);
const reportTarget = ref<{ type: ReportTargetType; id: string } | null>(null);
const reporting = ref(false);
const reportError = ref('');
const reportFieldError = ref('');

/** 다시 시도해도 결과가 같은 신고 거절 — 대화상자 대신 토스트(그 밖의 `REPORT_TOO_FAST` 등은 대화상자 안에 보인다) */
const REPORT_FINAL: ReadonlySet<string> = new Set([
    'ALREADY_REPORTED',
    'CANNOT_REPORT_OWN',
    'REPORT_TARGET_NOT_FOUND',
    'REPORT_DISABLED',
    'REPORT_BLOCKED',
    'REPORT_DAILY_LIMIT',
    'ACCOUNT_RESTRICTED',
    'BOARD_RESTRICTED',
    'FORBIDDEN',
    // 핫딜 종료 요청 계열 — 가격 허위 신고(D4)는 서버가 204 로 받지만(이미 알렸거나 자격 미달이면 신고만) 혹시 오면 닫고 알린다
    'DEAL_END_REQUEST_EXISTS',
    'DEAL_END_REQUEST_NOT_ALLOWED',
    'DEAL_END_REQUEST_AUTHOR_LIMIT',
    'DEAL_END_REQUEST_DAILY_LIMIT',
    'DEAL_END_REQUEST_OWN',
    'DEAL_ENDED',
]);

function openReport(type: ReportTargetType, targetId: string): void {
    if (board.value?.viewer.guest) return prompt('report');
    reportTarget.value = { type, id: targetId };
    reportError.value = '';
    reportFieldError.value = '';
    reportOpen.value = true;
}

async function submitReport(value: { reason: ReportReason; detail: string | null }): Promise<void> {
    const target = reportTarget.value;
    if (!target) return;
    reporting.value = true;
    reportError.value = '';
    reportFieldError.value = '';
    try {
        await reportApi.report({ targetType: target.type, targetId: target.id, reason: value.reason, detail: value.detail });
        reportOpen.value = false;
        if (value.reason === 'DEAL_FALSE_PRICE' && target.type === 'POST' && post.value?.deal) {
            // D4 — 가격 허위 신고는 서버가 종료 요청 「가격 변경」 1건으로 접수할 수도, 신고만 받을 수도 있다 — 화면이 셈하지 않고 다시 읽는다.
            // 이번에 내 요청이 새로 생겼을 때만 「알리기로 접수」, 아니면(이미 알렸음·자격·한도·끝난 딜·다시 못 읽음) 보통 신고 접수 문구
            const before = post.value.deal.endRequests.mine;
            const fresh = await refreshDeal(target.id);
            toast.success(!before && fresh?.endRequests.mine ? t('hotdeal.reportFalsePriceDone') : t('post.reported'));
        } else toast.success(t('post.reported'));
    } catch (e) {
        if (e instanceof ApiError && e.code === 'UNAUTHENTICATED') {
            reportOpen.value = false;
            prompt('report');
        } else if (e instanceof ApiError && e.field('detail')) reportFieldError.value = e.field('detail') ?? '';
        else if (e instanceof ApiError && REPORT_FINAL.has(e.code)) {
            // 다시 눌러도 안 되는 거절 — 대화상자를 닫고 서버 문구를 알린다
            reportOpen.value = false;
            toast.error(UtilHotdealMessages.dealError(t, e) ?? e.message, 8000);
            if (e.code === 'REPORT_TARGET_NOT_FOUND' && reportTarget.value?.type === 'COMMENT') void comments.load();
        } else reportError.value = UtilBoardMessages.of(t, e);
    } finally {
        reporting.value = false;
    }
}

/* ── 작성자 제재(게시판 매니저·스텝 — board-sanction-contract) ── */
/** 그 게시판 매니저·스텝 본인에게만 오는 운영 도구(소모임 글은 공개 게시판 응답이 아니라 없다) */
const staffTools = computed(() => (post.value && !post.value.group ? (board.value?.viewer.staffTools ?? null) : null));
/** 작성자 제재 권한(`staffTools` 는 처리 권한 중 하나만 있어도 온다) */
const sanctionEnabled = computed(() => Boolean(staffTools.value?.abilities.sanction));
const commentDeleteEnabled = computed(() => Boolean(staffTools.value?.abilities.commentDelete));
const sanctionForm = useBoardSanctionForm(() => board.value);
/**
 * 글 ⋯ 「작성자 제재」 — 남의 회원 글과 남의 익명 글(「이 글 작성자」 — anonymous-board-contract 정책 9)만(본인·비회원·탈퇴 회원 글에는 없다. 그 밖의 대상 불가는 서버가 대화창에서 알린다 —
 * 익명 글은 `sanctionProtected` 가 늘 false 라 대상 보호도 대화창의 `409 ANONYMOUS_TARGET_UNAVAILABLE` 로 안다)
 */
const canSanctionPost = computed(() => {
    const p = post.value;
    if (!sanctionEnabled.value || !p || p.mine || p.sanctionProtected) return false;
    if (p.author.kind === 'ANONYMOUS') return true;
    return Boolean(p.author.kind === 'MEMBER' && p.author.memberId && p.author.name);
});
const postMenu = ref<{ toggle: (event: Event) => void } | null>(null);
/** 글 ⋯ 메뉴 — 할 수 있는 것만(숨기기 · 삭제 · 작성자 제재). 비면 ⋯ 를 그리지 않는다 */
const postMenuItems = computed(() => {
    const p = post.value;
    const items: { label: string; icon: string; command: () => void }[] = [];
    if (!p) return items;
    for (const kind of UtilBoardStaffAction.postKinds(staffTools.value, p)) {
        items.push(
            kind === 'POST_HIDE'
                ? { label: t('boardStaffAction.menuHidePost'), icon: 'pi pi-eye-slash', command: () => openPostAction(kind) }
                : { label: t('boardStaffAction.menuDeletePost'), icon: 'pi pi-trash', command: () => openPostAction(kind) },
        );
    }
    if (canSanctionPost.value) items.push({ label: t('boardSanction.menuSanction'), icon: 'pi pi-ban', command: () => openPostSanction() });
    return items;
});

function openPostSanction(): void {
    const p = post.value;
    if (!p) return;
    // 익명 글 — 닉네임 없이 「이 글 작성자」, 요청은 회원 ID 대신 이 글(`anonymousPostId`)
    if (p.author.kind === 'ANONYMOUS') {
        sanctionForm.open({ userId: null, nickname: t('boardSanction.sealedPostAuthor'), anonymous: true, postId: p.id, commentId: null });
        return;
    }
    if (!p.author.memberId || !p.author.name) return;
    sanctionForm.open({ userId: p.author.memberId, nickname: p.author.name, postId: p.id, commentId: null });
}

/** 댓글 ⋯ 「작성자 제재」 — 계기 댓글과 그 글을 함께 잇는다 */
function openCommentSanction(c: CommentView): void {
    // 익명 댓글 — 닉네임 없이 「이 댓글 작성자」, 요청은 회원 ID 대신 이 댓글(`anonymousCommentId`)
    if (c.author.kind === 'ANONYMOUS') {
        sanctionForm.open({ userId: null, nickname: t('boardSanction.sealedCommentAuthor'), anonymous: true, postId: post.value?.id ?? null, commentId: c.id });
        return;
    }
    if (!c.author.memberId || !c.author.name) return;
    sanctionForm.open({ userId: c.author.memberId, nickname: c.author.name, postId: post.value?.id ?? null, commentId: c.id });
}

/* ── 스텝 처리(글 숨기기·글 삭제·댓글 삭제 — board-staff-action-contract) ── */
/** 댓글 삭제를 누른 구획 — 뒤처리로 그 목록만 다시 받는다 */
let staffCommentSection: 'general' | 'debate' = 'general';
const staffActionForm = useBoardStaffActionForm(
    () => board.value,
    (target, result) => {
        if (target.kind !== 'COMMENT_DELETE') {
            // 숨기거나 지운 글은 게시판 목록으로, 그새 상태가 바뀌었으면 지금 글을 다시 읽는다
            if (result === 'done') goBoard();
            else void load();
            return;
        }
        if (staffCommentSection === 'debate') void opinions.load();
        else void comments.load();
    },
);

function openPostAction(kind: 'POST_HIDE' | 'POST_DELETE'): void {
    const p = post.value;
    if (!p) return;
    staffActionForm.open({ kind, postId: p.id, commentId: null, text: p.title });
}

function openCommentDelete(c: CommentView, section: 'general' | 'debate'): void {
    const p = post.value;
    if (!p) return;
    staffCommentSection = section;
    staffActionForm.open({ kind: 'COMMENT_DELETE', postId: p.id, commentId: c.id, text: UtilBoardStaffAction.commentExcerpt(c.content, c.contentFormat) });
}

useSeoMeta({
    // 접힌 글은 탭 제목에도 제목을 쓰지 않는다(차단 단어가 제목에 있다)
    title: () => (post.value && !folded.value ? post.value.title : t('post.pageTitle')),
    robots: () => (pageState.value === 'notfound' || pageState.value === 'hidden' || pageState.value === 'sellerHidden' || pageState.value === 'adultOnly' || post.value?.adult ? 'noindex' : undefined),
});
</script>
