package com.community.emoticon.service;

import com.community.emoticon.dto.request.EmoticonStudioSaveRequest;
import com.community.emoticon.dto.response.EmoticonStudioView;
import com.community.emoticon.entity.EmoticonImageKind;
import com.community.emoticon.entity.EmoticonPackSource;
import com.community.emoticon.entity.EmoticonPackStatus;
import com.community.emoticon.entity.EmoticonReviewKind;
import com.community.emoticon.entity.EmoticonReviewStatus;
import com.community.emoticon.entity.EmoticonSaleWish;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.exception.EmoticonCreatorBlockedException;
import com.community.emoticon.exception.EmoticonPackNotFoundException;
import com.community.emoticon.exception.EmoticonPendingLimitException;
import com.community.emoticon.repository.master.EmoticonImageStore;
import com.community.emoticon.repository.master.EmoticonImageStore.ImageRow;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.emoticon.repository.master.EmoticonPackStore.ItemRow;
import com.community.emoticon.repository.master.EmoticonPackStore.PackRow;
import com.community.emoticon.repository.master.EmoticonPackStore.RevisionRow;
import com.community.emoticon.repository.master.EmoticonPayoutStore;
import com.community.emoticon.repository.master.EmoticonPriceEventStore;
import com.community.emoticon.repository.master.EmoticonPriceEventStore.PriceEventRow;
import com.community.emoticon.repository.master.EmoticonPurchaseStore;
import com.community.emoticon.repository.master.EmoticonReadStore;
import com.community.emoticon.repository.master.EmoticonReadStore.MemberState;
import com.community.emoticon.repository.master.EmoticonReadStore.RewardRow;
import com.community.emoticon.repository.master.EmoticonReviewStore;
import com.community.emoticon.repository.master.EmoticonReviewStore.ReviewRow;
import com.community.emoticon.repository.master.EmoticonSettingsStore.SettingsRow;
import com.community.identity.exception.IdentityUnderageException;
import com.community.identity.repository.master.IdentityStore;
import com.community.identity.service.AdultAge;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로보티콘 스튜디오(작가 — 계약 가정 7·8·9, S1). 내 팩 요약 · 편집 화면 · 임시 저장 · 심사 요청.
 *
 * <ul>
 *   <li><b>자격</b>(쓰기 — 저장·심사 요청): 로그인 · 정상 회원({@code 403 ACCOUNT_RESTRICTED}) · 이메일 인증({@code 403 EMAIL_NOT_VERIFIED}). 읽기는 로그인만.</li>
 *   <li><b>잠금</b>: 심사 대기(처음 신청) · 판매 중 · 새 판 심사 중인 팩은 임시 저장이 없다 — {@code 409 EMOTICON_PACK_LOCKED}. 판매 중인 팩은 고친 내용을 심사 요청으로만 낸다(새 판).
 *       판매 중단 팩은 임시 저장·심사 요청이 된다(새 판 심사 — 승인되면 다시 판매).</li>
 *   <li><b>판</b>: 임시 저장은 팩의 임시 판(하나)을 고친다. 심사 요청은 그 임시 판을(없으면 새로) 본문으로 채워 <b>낸다</b>(판 번호 = 그 팩의 마지막 + 1, 그 뒤로 고치지 못한다 — V140 트리거)
 *       — 심사 건은 그 판을 가리킨다. 판매 중·판매 중단 팩의 심사 요청은 새 판 심사(UPDATE, 비교용으로 그때의 상점 판을 함께 가리킨다)이고 그동안 상점 판이 그대로 나온다.</li>
 *   <li><b>로보티콘 ID</b>: 차례의 그림이 이 팩의 어느 판에서 쓰던 그림이면 그 로보티콘 ID 를, 아니면 같은 지문(SHA-256)의 그림을 쓰던 로보티콘 ID 를 잇고, 둘 다 아니면 새 ID —
 *       판이 바뀌어도 같은 로보티콘은 같은 ID(이미 올라간 본문의 토큰이 그대로 그려진다).</li>
 *   <li><b>검사</b>(400 {@code VALIDATION} — 칸을 모은다, user-web 목업 {@code validate} 차례): 이름(낼 때 또는 적었을 때 2~20자 · 한글·영문·숫자·띄어쓰기) · 태그(5개 · 한 개 10자 · 겹침)
 *       · 설명 200자 · 가격 변경 요청 메모 200자 · 이름표({@code emoticons[i].label}, 낼 때 1~10자) · 개수({@code emoticons}, 낼 때 설정 최소~최대, 임시 저장은 최대까지) · 그림(내가 올렸거나
 *       이 팩이 쓰던 로보티콘 그림 · 겹침 없음) · 대표({@code coverImageId}, 낼 때 필수 · 내가 올렸거나 이 팩이 쓰던 대표) · 판매 희망 · 동의 셋(낼 때).</li>
 *   <li><b>심사 요청 차례</b>: 자격 → 팩(남의 것·없음 404) → 잠금 409 → 칸 400(유료 판매가 꺼져 있는데 새 유료 희망이면 {@code fields.saleWish}) →
 *       <b>유료 조건</b>(새 유료 희망 · 이미 유료로 나온 팩의 새 판 — S2): 만 19세 미만 {@code 403 IDENTITY_UNDERAGE}(후속 결정 3 — 본인인증한 회원만 판정) →
 *       정산 정보 {@code 409 EMOTICON_PAYOUT_REQUIRED} → 지금 판 상품화 계약 {@code 409 EMOTICON_CONTRACT_REQUIRED}
 *       (이미 지금 판에 동의했거나 본문 {@code agreeContractVersion} 이 지금 판이어야 — 처음이면 그 판 동의를 한 줄 남긴다) → 심사 대기 상한 {@code 409 EMOTICON_PENDING_LIMIT}
 *       (같은 작가의 요청을 줄 세워 센다 — 새 판 심사도 센다) → 저장 → 운영자 알림 {@code EMOTICON_REVIEW_PENDING}(같은 트랜잭션 — 실패해도 신청은 깨지지 않는다).
 *       무료로 나온 팩의 새 판은 판매 희망을 바꾸지 않아 유료 조건을 보지 않는다(무료 → 유료는 운영자 가격 바꾸기).</li>
 * </ul>
 */
@Service
public class EmoticonStudioService {

    private static final Logger log = LoggerFactory.getLogger(EmoticonStudioService.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final EmoticonPackStore packs;
    private final EmoticonReviewStore reviews;
    private final EmoticonImageStore images;
    private final EmoticonReadStore reads;
    private final EmoticonSettingsService settings;
    private final EmoticonContent content;
    private final NotificationPublisher operatorNotices;
    private final EmoticonImageService imageService;
    private final EmoticonContractService contracts;
    private final EmoticonPayoutStore payouts;
    private final EmoticonPriceEventStore priceEvents;
    private final EmoticonPurchaseStore purchases;
    private final EmoticonSettlementCalculator settlements;
    private final IdentityStore identities;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public EmoticonStudioService(EmoticonPackStore packs, EmoticonReviewStore reviews, EmoticonImageStore images, EmoticonReadStore reads,
                                 EmoticonSettingsService settings, EmoticonContent content, NotificationPublisher operatorNotices, EmoticonImageService imageService,
                                 EmoticonContractService contracts, EmoticonPayoutStore payouts, EmoticonPriceEventStore priceEvents, EmoticonPurchaseStore purchases,
                                 EmoticonSettlementCalculator settlements, IdentityStore identities, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.identities = identities;
        this.purchases = purchases;
        this.settlements = settlements;
        this.contracts = contracts;
        this.payouts = payouts;
        this.priceEvents = priceEvents;
        this.imageService = imageService;
        this.packs = packs;
        this.reviews = reviews;
        this.images = images;
        this.reads = reads;
        this.settings = settings;
        this.content = content;
        this.operatorNotices = operatorNotices;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    // ── 읽기 ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EmoticonStudioView.Overview overview(long userId) {
        List<PackRow> mine = packs.byCreator(userId);
        List<EmoticonStudioView.PackRow> rows = new ArrayList<>();
        Map<Long, String> names = new LinkedHashMap<>();
        for (EmoticonStudioView.Pack pack : views(mine)) {
            rows.add(new EmoticonStudioView.PackRow(pack.id(), pack.name(), pack.coverUrl(), pack.status(), pack.rejectReason(), pack.suspendReason(), pack.revision(),
                    pack.saleWish(), pack.price(), pack.count(), pack.acquireCount(), pack.rewardTotal(), pack.salesTotal(), pack.lastPriceChange(), pack.updatedAt()));
            names.put(Long.parseLong(pack.id()), pack.name());
        }
        Instant now = clock.instant();
        Instant todayStart = LocalDate.ofInstant(now, ServiceTime.ZONE).atStartOfDay(ServiceTime.ZONE).toInstant();
        List<EmoticonStudioView.Reward> recent = new ArrayList<>();
        for (RewardRow r : reads.recentRewards(userId, 10)) {
            recent.add(new EmoticonStudioView.Reward(Long.toString(r.ledgerId()), Long.toString(r.packId()), names.get(r.packId()), r.points(), r.createdAt()));
        }
        LocalDate today = LocalDate.ofInstant(now, ServiceTime.ZONE);
        String month = today.format(MONTH);
        // 이번 달(서비스 시간대) 판매 요약 — 이번 달 정산 줄(예정)과 같은 값(S4 정산 계산 한 곳): 이번 달 판매 + 이번 달에서 빼는 환불(이번 달 판매의 환불 · 지난 달 정산이 확정된 뒤의 환불).
        // 예정 작가 몫(원천징수 전 — 넘어온 금액은 넣지 않는다)은 환불 기간이 남은 판매도 넣은 추정이다
        EmoticonSettlementCalculator.Line line = settlements.creator(userId, today.withDayOfMonth(1), false).at(today.withDayOfMonth(1));
        EmoticonStudioView.Sales sales = line == null || line.frozen() ? new EmoticonStudioView.Sales(month, 0, 0, 0, 0, 0, 0, 0)
                : new EmoticonStudioView.Sales(month, line.salesCount(), line.salesAmount(), line.refundAmount(), line.vat(), line.base(), line.fee(), line.share());
        return new EmoticonStudioView.Overview(rows, mine.stream().mapToInt(PackRow::acquireCount).sum(), reads.rewardSum(userId, null), reads.rewardSum(userId, todayStart),
                recent, reviews.pendingCount(userId), sales);
    }

    /** 내 팩 하나(편집) — 남의 팩·없는 팩·형식이 틀린 ID 는 404 */
    @Transactional(readOnly = true)
    public EmoticonStudioView.Pack pack(long userId, String idText) {
        long id = EmoticonShopService.parse(idText);
        PackRow p = id < 0 ? null : packs.find(id).filter(x -> Long.valueOf(userId).equals(x.creatorId())).orElse(null);
        if (p == null) {
            throw new EmoticonPackNotFoundException();
        }
        return views(List.of(p)).get(0);
    }

    // ── 쓰기 ────────────────────────────────────────────────────────────────

    /**
     * 그림 하나 올리기({@code POST /api/me/emoticon-studio/images} — multipart {@code file} · {@code kind=EMOTICON|COVER}). 자격 → 하루 한도 → 검사({@link EmoticonImageValidator}) → 저장.
     * 트랜잭션 밖에서 돈다(그림 처리 동안 DB 연결을 붙잡지 않는다 — 장부 행만 짧은 트랜잭션).
     */
    public EmoticonStudioView.Image upload(long userId, MultipartFile file, String kind) {
        requireCreator(userId);
        return content.studioImage(imageService.uploadByMember(userId, file, kind));
    }

    /** 임시 저장 — 새 팩은 {@code idText} 없이 */
    @Transactional
    public EmoticonStudioView.Pack save(long userId, String idText, EmoticonStudioSaveRequest body) {
        requireCreator(userId);
        SettingsRow s = settings.current();
        PackRow p = idText == null ? null : lockMine(userId, idText);
        if (p != null && (p.status() == EmoticonPackStatus.PENDING || p.status() == EmoticonPackStatus.APPROVED || reviews.pendingOf(p.id()).isPresent())) {
            throw new EmoticonConflictException(EmoticonConflictException.PACK_LOCKED);
        }
        Cleaned c = clean(userId, p, body, false, s);
        Instant now = clock.instant();
        long packId;
        if (p == null) {
            packId = ids.nextId();
            packs.insert(packId, EmoticonPackSource.CREATOR, userId, EmoticonPackStatus.DRAFT, null, null, now);
        } else {
            packId = p.id();
            if (p.status() == EmoticonPackStatus.REJECTED) {
                packs.setStatus(packId, EmoticonPackStatus.DRAFT, now); // 반려된 팩을 고치면 다시 임시(목업과 같다)
            } else {
                packs.touch(packId, now);
            }
        }
        writeDraft(packId, p, c, now);
        return pack(userId, Long.toString(packId));
    }

    /** 심사 요청 — 새 팩은 {@code idText} 없이 */
    @Transactional
    public EmoticonStudioView.Pack submit(long userId, String idText, EmoticonStudioSaveRequest body) {
        requireCreator(userId);
        SettingsRow s = settings.current();
        PackRow p = idText == null ? null : lockMine(userId, idText);
        if (p != null && (p.status() == EmoticonPackStatus.PENDING || reviews.pendingOf(p.id()).isPresent())) {
            throw new EmoticonConflictException(EmoticonConflictException.PACK_LOCKED);
        }
        Cleaned c = clean(userId, p, body, true, s);
        boolean published = p != null && p.status().published();
        // 유료 조건(계약 가정 1 · 「화면 확정」): 새로 유료 판매를 희망하는 신청, 그리고 이미 유료로 나온 팩의 새 판 — 정산 정보 → 지금 판 상품화 계약 동의
        Integer consentToRecord = null;
        if ((!published && c.saleWish() == EmoticonSaleWish.PAID) || (published && p.price() > 0)) {
            requireAdultCreator(userId); // 만 19세 미만은 유료 희망을 낼 수 없다(후속 결정 3 — 무료 팩은 된다)
            if (!payouts.registered(userId)) {
                throw new EmoticonConflictException(EmoticonConflictException.PAYOUT_REQUIRED);
            }
            consentToRecord = contracts.requireAgreement(userId, body == null ? null : body.agreeContractVersion());
        }
        reviews.lockCreator(userId);
        if (reviews.pendingCount(userId) >= s.pendingLimit()) {
            throw new EmoticonPendingLimitException(s.pendingLimit());
        }
        Instant now = clock.instant();
        long packId;
        if (p == null) {
            packId = ids.nextId();
            packs.insert(packId, EmoticonPackSource.CREATOR, userId, EmoticonPackStatus.DRAFT, null, null, now);
        } else {
            packId = p.id();
        }
        if (consentToRecord != null) {
            contracts.record(userId, consentToRecord, packId, now);
        }
        long revisionId = writeDraft(packId, p, c, now);
        int version = packs.maxVersion(packId) + 1;
        packs.seal(revisionId, version, now);
        long reviewId = ids.nextId();
        reviews.insert(reviewId, packId, revisionId, published ? p.liveRevisionId() : null, userId, published ? EmoticonReviewKind.UPDATE : EmoticonReviewKind.FIRST, now,
                EmoticonSpec.CONSENT_VERSION);
        if (published) {
            packs.touch(packId, now);
        } else {
            packs.setStatus(packId, EmoticonPackStatus.PENDING, now);
        }
        log.info("로보티콘 심사 요청 userId={} packId={} reviewId={} version={} kind={}", userId, packId, reviewId, version, published ? "UPDATE" : "FIRST");
        operatorNotices.publish(AdminNotificationEvent.EMOTICON_REVIEW_PENDING, NotificationContext.empty().link("reviewId", Long.toString(reviewId)));
        return pack(userId, Long.toString(packId));
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** 검사를 마친 본문 */
    private record Cleaned(String name, String description, List<String> tags, Long coverImageId, List<ImageRow> emoticons, List<String> labels, EmoticonSaleWish saleWish,
                           String memo) {
    }

    /** 작가 자격(쓰기) — 로그인 · 정상 회원 · 이메일 인증. 정산 정보 저장도 같은 자격 */
    void requireCreator(long userId) {
        MemberState m = reads.member(userId).filter(x -> !x.gone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        if (m.suspended()) {
            throw new EmoticonCreatorBlockedException(EmoticonCreatorBlockedException.ACCOUNT_RESTRICTED);
        }
        if (!m.emailVerified()) {
            throw new EmoticonCreatorBlockedException(EmoticonCreatorBlockedException.EMAIL_NOT_VERIFIED);
        }
    }

    /**
     * 미성년 작가는 유료 판매·정산을 하지 않는다(후속 결정 3 — 미성년자의 계약은 법정대리인 동의가 필요하다): 본인인증한 회원이 서비스 날짜(KST) 기준 만 19세 미만이면
     * {@code 403 IDENTITY_UNDERAGE}(+{@code adultFrom}, 문구 「무료 팩은 만들 수 있어요」). 본인인증 전이면 여기서 막지 않는다 — 정산 정보 저장은 먼저 {@code IDENTITY_REQUIRED},
     * 유료 희망은 정산 정보가 없어 {@code EMOTICON_PAYOUT_REQUIRED}. 판정은 결제 쪽 {@code IdentityVerificationService#requireAdult} 와 같은 {@link AdultAge}.
     */
    void requireAdultCreator(long userId) {
        identities.findVerification(userId).ifPresent(v -> {
            if (!AdultAge.isAdult(v.birthDate(), ServiceTime.today(clock))) {
                throw new IdentityUnderageException(AdultAge.adultFrom(v.birthDate()), "error.IDENTITY_UNDERAGE.emoticonCreator");
            }
        });
    }

    /** 내 팩을 잠근다 — 남의 팩·없는 팩·형식이 틀린 ID 는 404 */
    private PackRow lockMine(long userId, String idText) {
        long id = EmoticonShopService.parse(idText);
        PackRow p = id < 0 ? null : packs.lock(id).orElse(null);
        if (p == null || !Long.valueOf(userId).equals(p.creatorId())) {
            throw new EmoticonPackNotFoundException();
        }
        return p;
    }

    private Cleaned clean(long userId, PackRow p, EmoticonStudioSaveRequest body, boolean submit, SettingsRow s) {
        EmoticonStudioSaveRequest b = body == null ? new EmoticonStudioSaveRequest(null, null, null, null, null, null, null, null, null, null, null) : body;
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String name = b.name() == null ? "" : b.name().trim();
        if (submit || !name.isEmpty()) {
            if (EmoticonSpec.length(name) < EmoticonSpec.NAME_MIN || EmoticonSpec.length(name) > EmoticonSpec.NAME_MAX) {
                fields.put("name", new MessageRef("emoticon.studio.name.length", EmoticonSpec.NAME_MIN, EmoticonSpec.NAME_MAX));
            } else if (!EmoticonSpec.NAME_PATTERN.matcher(name).matches()) {
                fields.put("name", new MessageRef("emoticon.studio.name.pattern"));
            }
        }
        List<String> tags = new ArrayList<>();
        for (String raw : b.tags() == null ? List.<String>of() : b.tags()) {
            String tag = raw == null ? "" : raw.trim().replaceFirst("^#+", "").trim();
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }
        if (tags.size() > EmoticonSpec.MAX_TAGS) {
            fields.put("tags", new MessageRef("emoticon.studio.tags.tooMany", EmoticonSpec.MAX_TAGS));
        } else if (tags.stream().anyMatch(t -> EmoticonSpec.length(t) > EmoticonSpec.TAG_MAX)) {
            fields.put("tags", new MessageRef("emoticon.studio.tags.tooLong", EmoticonSpec.TAG_MAX));
        } else if (new HashSet<>(tags).size() != tags.size()) {
            fields.put("tags", new MessageRef("emoticon.studio.tags.duplicate"));
        }
        String description = b.description() == null ? "" : b.description().trim();
        if (EmoticonSpec.length(description) > EmoticonSpec.DESCRIPTION_MAX) {
            fields.put("description", new MessageRef("emoticon.studio.description.length", EmoticonSpec.DESCRIPTION_MAX));
        }
        String memo = b.priceRequestMemo() == null || b.priceRequestMemo().isBlank() ? null : b.priceRequestMemo().trim();
        if (memo != null && EmoticonSpec.length(memo) > EmoticonSpec.PRICE_MEMO_MAX) {
            fields.put("priceRequestMemo", new MessageRef("emoticon.studio.memo.length", EmoticonSpec.PRICE_MEMO_MAX));
        }
        List<EmoticonStudioSaveRequest.Item> items = b.emoticons() == null ? List.of() : b.emoticons();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            EmoticonStudioSaveRequest.Item item = items.get(i);
            String label = item == null || item.label() == null ? "" : item.label().trim();
            labels.add(label);
            int length = EmoticonSpec.length(label);
            if ((submit && (length < EmoticonSpec.LABEL_MIN || length > EmoticonSpec.LABEL_MAX)) || length > EmoticonSpec.LABEL_MAX) {
                fields.put("emoticons[" + i + "].label", new MessageRef("emoticon.studio.label.length", EmoticonSpec.LABEL_MIN, EmoticonSpec.LABEL_MAX));
            }
        }
        if ((submit && items.size() < s.itemMin()) || items.size() > s.itemMax()) {
            fields.put("emoticons", new MessageRef("emoticon.studio.count", s.itemMin(), s.itemMax()));
        }
        Set<Long> packImages = p == null ? Set.of() : packs.imageIds(p.id());
        List<Long> imageIds = new ArrayList<>();
        for (EmoticonStudioSaveRequest.Item item : items) {
            imageIds.add(item == null ? -1 : EmoticonShopService.parse(item.imageId()));
        }
        Long coverId = b.coverImageId() == null || b.coverImageId().isBlank() ? null : EmoticonShopService.parse(b.coverImageId());
        Set<Long> wanted = new HashSet<>(imageIds);
        if (coverId != null) {
            wanted.add(coverId);
        }
        wanted.remove(-1L);
        Map<Long, ImageRow> found = images.byIds(wanted);
        List<ImageRow> emoticons = new ArrayList<>();
        if (!fields.containsKey("emoticons")) {
            Set<Long> seen = new HashSet<>();
            for (Long imageId : imageIds) {
                ImageRow image = found.get(imageId);
                if (image == null || image.kind() != EmoticonImageKind.EMOTICON || !(Long.valueOf(userId).equals(image.uploadedByUser()) || packImages.contains(imageId))) {
                    fields.put("emoticons", new MessageRef("emoticon.studio.image.unknown"));
                    break;
                }
                if (!seen.add(imageId)) {
                    fields.put("emoticons", new MessageRef("emoticon.studio.image.duplicate"));
                    break;
                }
                emoticons.add(image);
            }
        }
        if (coverId == null) {
            if (submit) {
                fields.put("coverImageId", new MessageRef("emoticon.studio.cover.required"));
            }
        } else {
            ImageRow cover = found.get(coverId);
            if (cover == null || cover.kind() != EmoticonImageKind.COVER || !(Long.valueOf(userId).equals(cover.uploadedByUser()) || packImages.contains(coverId))) {
                fields.put("coverImageId", new MessageRef("emoticon.studio.cover.unknown"));
            }
        }
        EmoticonSaleWish wish = b.saleWish() == null ? EmoticonSaleWish.FREE : EmoticonSaleWish.of(b.saleWish()).orElse(null);
        if (wish == null) {
            fields.put("saleWish", new MessageRef("emoticon.studio.saleWish.invalid"));
        } else if (submit && wish == EmoticonSaleWish.PAID && !s.paidSalesEnabled() && (p == null || !p.status().published())) {
            fields.put("saleWish", new MessageRef("emoticon.studio.saleWish.paidDisabled")); // 유료 판매가 꺼져 있으면 유료 희망 신청을 받지 않는다(임시 저장은 된다)
        }
        if (submit) {
            if (!Boolean.TRUE.equals(b.agreeOwnership())) {
                fields.put("agreeOwnership", new MessageRef("emoticon.studio.agree.ownership"));
            }
            if (!Boolean.TRUE.equals(b.agreeLicense())) {
                fields.put("agreeLicense", new MessageRef("emoticon.studio.agree.license"));
            }
            if (!Boolean.TRUE.equals(b.agreePolicy())) {
                fields.put("agreePolicy", new MessageRef("emoticon.studio.agree.policy"));
            }
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        // 판매 중·판매 중단 팩은 판매 희망을 바꾸지 않는다(가격은 운영자만 — 계약 「목업 3차」) — 상점 판의 값을 그대로
        if (p != null && p.status().published()) {
            wish = packs.revision(p.liveRevisionId()).map(RevisionRow::saleWish).orElse(wish);
        }
        return new Cleaned(name, description, tags, coverId, emoticons, labels, wish, memo);
    }

    /** 임시 판을 본문으로 채운다(없으면 만든다) — 판 ID */
    private long writeDraft(long packId, PackRow p, Cleaned c, Instant now) {
        Optional<RevisionRow> draft = p == null ? Optional.empty() : packs.draftOf(packId);
        long revisionId;
        if (draft.isPresent()) {
            revisionId = draft.get().id();
            packs.updateDraft(revisionId, c.name(), c.description(), c.tags(), c.coverImageId(), c.saleWish(), c.memo());
        } else {
            revisionId = ids.nextId();
            packs.insertDraft(revisionId, packId, c.name(), c.description(), c.tags(), c.coverImageId(), c.saleWish(), c.memo(), now);
        }
        List<Long> emoticonIds = emoticonIds(packId, p != null, c.emoticons(), now);
        List<ItemRow> items = new ArrayList<>();
        for (int i = 0; i < c.emoticons().size(); i++) {
            items.add(new ItemRow(revisionId, i + 1, emoticonIds.get(i), c.emoticons().get(i).id(), c.labels().get(i)));
        }
        packs.replaceItems(revisionId, packId, items);
        return revisionId;
    }

    /**
     * 차례의 로보티콘 ID 들 — 판이 바뀌어도 같은 로보티콘은 같은 ID. 먼저 모든 칸을 「이 팩이 쓰던 그림」으로 잇고, 남은 칸을 「같은 지문의 그림을 쓰던 로보티콘」으로 잇고, 그래도 남으면 새 ID
     * (한 판에 같은 로보티콘은 한 번 — 같은 그림을 두 번 올려도 원래 그림이 원래 ID 를 갖는다). 운영 팩 고치기도 같은 규칙이다.
     */
    List<Long> emoticonIds(long packId, boolean existingPack, List<ImageRow> images, Instant now) {
        return EmoticonStudioService.assignEmoticonIds(packs, ids, packId, existingPack, images, now);
    }

    static List<Long> assignEmoticonIds(EmoticonPackStore packs, SnowflakeIdGenerator ids, long packId, boolean existingPack, List<ImageRow> images, Instant now) {
        Long[] out = new Long[images.size()];
        Set<Long> used = new HashSet<>();
        if (existingPack) {
            for (int i = 0; i < images.size(); i++) {
                Optional<Long> byImage = packs.emoticonByImage(packId, images.get(i).id()).filter(id -> !used.contains(id));
                if (byImage.isPresent()) {
                    out[i] = byImage.get();
                    used.add(byImage.get());
                }
            }
            for (int i = 0; i < images.size(); i++) {
                if (out[i] != null) {
                    continue;
                }
                Optional<Long> byHash = packs.emoticonByHash(packId, images.get(i).contentHash()).filter(id -> !used.contains(id));
                if (byHash.isPresent()) {
                    out[i] = byHash.get();
                    used.add(byHash.get());
                }
            }
        }
        for (int i = 0; i < images.size(); i++) {
            if (out[i] == null) {
                long id = ids.nextId();
                packs.insertEmoticon(id, packId, now);
                out[i] = id;
            }
        }
        return List.of(out);
    }

    /** 스튜디오 모양 — 보이는 판 = 임시 판, 없으면 마지막으로 낸 판(입력 차례) */
    private List<EmoticonStudioView.Pack> views(List<PackRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(PackRow::id).toList();
        Map<Long, RevisionRow> drafts = packs.drafts(ids);
        Map<Long, RevisionRow> sealed = packs.latestSealed(ids);
        Map<Long, ReviewRow> latest = reviews.latestOf(ids);
        Map<Long, Long> rewards = reads.rewardTotals(ids);
        Map<Long, PriceEventRow> lastPrices = priceEvents.latest(ids);
        Map<Long, EmoticonPurchaseStore.PackSales> sales = purchases.packSales(ids);
        List<Long> shownIds = new ArrayList<>();
        for (Long id : ids) {
            RevisionRow shown = drafts.containsKey(id) ? drafts.get(id) : sealed.get(id);
            shownIds.add(shown == null ? null : shown.id());
        }
        Map<Long, EmoticonContent.Loaded> loaded = content.load(shownIds);
        List<EmoticonStudioView.Pack> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            PackRow p = rows.get(i);
            EmoticonContent.Loaded shown = shownIds.get(i) == null ? null : loaded.get(shownIds.get(i));
            ReviewRow review = latest.get(p.id());
            String rejectReason = p.status() == EmoticonPackStatus.REJECTED && review != null ? review.reason() : null;
            EmoticonStudioView.Revision revision = null;
            if (p.liveRevisionId() != null && review != null && review.kind() == EmoticonReviewKind.UPDATE && review.status() != EmoticonReviewStatus.APPROVED) {
                revision = new EmoticonStudioView.Revision(review.status().name(), review.submittedAt(), review.reason());
            }
            RevisionRow r = shown == null ? null : shown.revision();
            ImageRow cover = shown == null ? null : shown.cover();
            PriceEventRow lastPrice = lastPrices.get(p.id());
            EmoticonStudioView.PriceChange priceChange = lastPrice == null ? null
                    : new EmoticonStudioView.PriceChange(lastPrice.fromPrice(), lastPrice.toPrice(), lastPrice.reason(), lastPrice.changedAt());
            out.add(new EmoticonStudioView.Pack(Long.toString(p.id()), r == null ? "" : r.name(), cover == null ? null : content.url(cover), p.status().name(), rejectReason,
                    p.suspendReason(), revision, r == null ? EmoticonSaleWish.FREE.name() : r.saleWish().name(), p.price(), shown == null ? 0 : shown.items().size(),
                    p.acquireCount(), rewards.getOrDefault(p.id(), 0L), sales.containsKey(p.id()) ? sales.get(p.id()).paidAmount() : 0, priceChange, p.updatedAt(), r == null ? "" : r.description(), r == null ? List.of() : r.tags(),
                    cover == null ? null : content.studioImage(cover), shown == null ? List.of() : content.studioItems(shown), r == null ? null : r.priceRequestMemo()));
        }
        return out;
    }
}
