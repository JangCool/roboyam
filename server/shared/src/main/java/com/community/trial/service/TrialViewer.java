package com.community.trial.service;

import com.community.ad.repository.master.AdAdvertiserStore;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.bizapplication.repository.master.BizApplicationStore;
import com.community.commerce.image.GroupBuyImageUploader;
import com.community.pointshop.dto.AuthorLook;
import com.community.pointshop.service.ShopDecorationResolver;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.trial.dto.TrialViews;
import com.community.trial.entity.TrialActorType;
import com.community.trial.entity.TrialApplicationEntity;
import com.community.trial.entity.TrialApplicationStatus;
import com.community.trial.entity.TrialConsent;
import com.community.trial.entity.TrialDamageReportEntity;
import com.community.trial.entity.TrialEntity;
import com.community.trial.entity.TrialParticipantEntity;
import com.community.trial.entity.TrialPhotoEntity;
import com.community.trial.entity.TrialPhotoKind;
import com.community.trial.entity.TrialShipper;
import com.community.trial.entity.TrialStatus;
import com.community.trial.entity.TrialStockReceiptEntity;
import com.community.trial.repository.master.TrialApplicationRepository;
import com.community.trial.repository.master.TrialDamageReportRepository;
import com.community.trial.repository.master.TrialParticipantRepository;
import com.community.trial.repository.master.TrialPhotoRepository;
import com.community.trial.repository.master.TrialQueries;
import com.community.trial.repository.master.TrialStockReceiptRepository;
import com.community.user.entity.ProfileEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.crypto.DataPurpose;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 체험단 응답 만들기(trial-group-contract 「구현 결정」 8·9·10) — 관리자 상세·신청자 목록·광고주 상세가 같은 모양을 쓴다. 트랜잭션은 부르는 서비스가 연다.
 *
 * <p><b>주소를 보이는 범위</b>(계약 「광고주 화면」): 운영자는 운영자가 보내는 모집의 뽑힌 줄만, 광고주는 광고주가 보내는 모집의 뽑힌 줄(신청 때 제공 동의)만 받는 분·연락처·주소를
 * 본다. 운영자가 보내는 모집이라도 미반납·과실 파손으로 이름·연락처를 넘긴 줄은 광고주가 이름·연락처만 본다. 파기한 줄은 아무도 못 본다.
 */
@Component
public class TrialViewer {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TrialApplicationRepository applications;
    private final TrialParticipantRepository participants;
    private final TrialStockReceiptRepository stocks;
    private final TrialPhotoRepository photos;
    private final TrialDamageReportRepository damages;
    private final TrialQueries queries;
    private final TrialSettingsService settings;
    private final AdAdvertiserStore advertisers;
    private final BizApplicationStore bizApplications;
    private final BoardCatalog catalog;
    private final ProfileRepository profiles;
    private final ShopDecorationResolver decorations;
    private final PostRepository posts;
    private final GroupBuyImageUploader images;
    private final DataCipher cipher;

    public TrialViewer(TrialApplicationRepository applications, TrialParticipantRepository participants, TrialStockReceiptRepository stocks, TrialPhotoRepository photos,
                       TrialDamageReportRepository damages, TrialQueries queries, TrialSettingsService settings, AdAdvertiserStore advertisers,
                       BizApplicationStore bizApplications, BoardCatalog catalog, ProfileRepository profiles, ShopDecorationResolver decorations, PostRepository posts,
                       GroupBuyImageUploader images, DataCipher cipher) {
        this.applications = applications;
        this.participants = participants;
        this.stocks = stocks;
        this.photos = photos;
        this.damages = damages;
        this.queries = queries;
        this.settings = settings;
        this.advertisers = advertisers;
        this.bizApplications = bizApplications;
        this.catalog = catalog;
        this.profiles = profiles;
        this.decorations = decorations;
        this.posts = posts;
        this.images = images;
        this.cipher = cipher;
    }

    // ── 조각 ────────────────────────────────────────────────────────────

    public String advertiserName(long advertiserId) {
        return advertisers.find(advertiserId).map(AdAdvertiserStore.AdvertiserRow::name).orElse("");
    }

    public TrialViews.BoardRef board(long boardId) {
        return catalog.snapshot().board(boardId).map(b -> new TrialViews.BoardRef(Long.toString(b.id()), b.slug(), b.name())).orElse(null);
    }

    public String imageUrl(String key) {
        return key == null ? null : images.url(key);
    }

    /** 회원들의 닉네임·아바타(한 번에) */
    public Map<Long, TrialViews.Member> members(Collection<Long> userIds) {
        Map<Long, TrialViews.Member> out = new HashMap<>();
        if (userIds.isEmpty()) {
            return out;
        }
        Map<Long, String> names = new HashMap<>();
        profiles.findAllById(new HashSet<>(userIds)).forEach(p -> names.put(p.getUserId(), p.getNickname()));
        Map<Long, AuthorLook> looks = decorations.resolve(new HashSet<>(userIds));
        for (Long id : userIds) {
            AuthorLook look = looks.get(id);
            out.put(id, new TrialViews.Member(Long.toString(id), names.getOrDefault(id, ""), look == null ? null : look.avatarUrl()));
        }
        return out;
    }

    public TrialViews.Member member(long userId) {
        return members(List.of(userId)).get(userId);
    }

    public List<TrialViews.Photo> photos(List<TrialPhotoEntity> rows) {
        return rows.stream().map(p -> new TrialViews.Photo(Long.toString(p.getId()), p.getKind().name(), images.url(p.getImageKey()), p.getCreatedAt())).toList();
    }

    /** 신청 동의 항목 — 보내는 쪽·형태로 정해진다(계약 「신청」·「대여형 파손」) */
    public static List<TrialConsent> requiredConsents(TrialEntity t) {
        List<TrialConsent> out = new ArrayList<>();
        if (t.getShipper() == TrialShipper.ADVERTISER) {
            out.add(TrialConsent.PROVIDE_CONTACT);
        } else if (t.lend()) {
            out.add(TrialConsent.UNRETURNED_CONTACT);
        }
        if (t.lend()) {
            out.add(TrialConsent.PRODUCT_CHARGE);
            out.add(TrialConsent.DAMAGE_CHARGE);
        }
        return out;
    }

    public static List<String> consents(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return MAPPER.readValue(json, new TypeReference<List<String>>() { });
    }

    public static String consentsJson(List<String> codes) {
        return MAPPER.writeValueAsString(codes);
    }

    // ── 관리자 ──────────────────────────────────────────────────────────

    public TrialViews.TrialRow row(TrialEntity t, long applicants) {
        return new TrialViews.TrialRow(Long.toString(t.getId()), t.getTitle(), t.getProductName(), t.getForm().name(), t.getShipper().name(), t.getStatus().name(),
                new TrialViews.Ref(Long.toString(t.getAdvertiserId()), advertiserName(t.getAdvertiserId())), t.getRecruitCount(), t.getReviewerShare(), applicants,
                t.getSelectedCount(), t.getApplyStartAt(), t.getApplyEndAt(), board(t.getReviewBoardId()), t.getAmount(), t.getCreatedAt());
    }

    /**
     * 모집 상세 — {@code viewer} OPERATOR(관리자) · ADVERTISER(광고주 화면 — 동의 범위 안 주소만).
     */
    public TrialViews.TrialDetail detail(TrialEntity t, TrialActorType viewer, Instant now) {
        List<TrialApplicationEntity> apps = applications.findByTrialIdOrderByCreatedAtAscIdAsc(t.getId());
        Map<Long, TrialApplicationEntity> appById = new HashMap<>();
        apps.forEach(a -> appById.put(a.getId(), a));
        List<TrialParticipantEntity> people = participants.findByTrialIdOrderByIdAsc(t.getId());
        List<TrialPhotoEntity> allPhotos = photos.findByTrialIdAndAttachedAtIsNotNullOrderByIdAsc(t.getId());
        Map<Long, List<TrialPhotoEntity>> photosByParticipant = new HashMap<>();
        Map<Long, List<TrialPhotoEntity>> photosByDamage = new HashMap<>();
        List<TrialPhotoEntity> stockPhotos = new ArrayList<>();
        for (TrialPhotoEntity p : allPhotos) {
            if (p.getKind() == TrialPhotoKind.STOCK) {
                stockPhotos.add(p);
            } else if (p.getDamageReportId() != null) {
                photosByDamage.computeIfAbsent(p.getDamageReportId(), k -> new ArrayList<>()).add(p);
            } else if (p.getParticipantId() != null) {
                photosByParticipant.computeIfAbsent(p.getParticipantId(), k -> new ArrayList<>()).add(p);
            }
        }
        Set<Long> userIds = new HashSet<>();
        people.forEach(p -> userIds.add(p.getUserId()));
        Map<Long, TrialViews.Member> who = members(userIds);
        Map<Long, String> postTitles = new HashMap<>();
        List<Long> postIds = people.stream().map(TrialParticipantEntity::getReviewPostId).filter(java.util.Objects::nonNull).toList();
        posts.findAllById(postIds).forEach(p -> postTitles.put(p.getId(), p.getTitle()));
        List<TrialViews.Participant> rows = new ArrayList<>();
        for (TrialParticipantEntity p : people) {
            rows.add(participant(t, p, appById.get(p.getApplicationId()), who.get(p.getUserId()), postTitles, photosByParticipant.getOrDefault(p.getId(), List.of()), viewer));
        }
        List<TrialViews.DamageReport> damageRows = damages.findByTrialIdOrderByCreatedAtDesc(t.getId()).stream().map(d -> {
            TrialParticipantEntity p = people.stream().filter(x -> x.getId().equals(d.getParticipantId())).findFirst().orElse(null);
            return damage(d, p == null ? null : who.get(p.getUserId()), photosByDamage.getOrDefault(d.getId(), List.of()));
        }).toList();
        TrialViews.Stock stock = null;
        TrialStockReceiptEntity receipt = stocks.findByTrialId(t.getId()).orElse(null);
        if (receipt != null) {
            stock = new TrialViews.Stock(receipt.getQuantity(), receipt.getNote(), receipt.getReceivedAt(),
                    receipt.getReceivedAt().plus(Duration.ofDays(settings.current().stockKeepDays())), receipt.getReturnCarrier(), receipt.getReturnTrackingNo(),
                    receipt.getReturnShippedAt(), photos(stockPhotos));
        }
        TrialViews.Counts counts = new TrialViews.Counts(apps.stream().filter(TrialApplicationEntity::live).count(), people.size(),
                people.stream().filter(TrialParticipantEntity::shipped).count(), people.stream().filter(TrialParticipantEntity::received).count(),
                people.stream().filter(p -> p.getReviewPostId() != null).count(), people.stream().filter(TrialParticipantEntity::returned).count(),
                people.stream().filter(p -> Boolean.TRUE.equals(p.getKept())).count(), people.stream().filter(p -> p.getUnreturnedAt() != null).count());
        String applicationNo = bizApplications.find(t.getBizApplicationId()).map(BizApplicationStore.AppRow::no).orElse(null);
        return new TrialViews.TrialDetail(Long.toString(t.getId()), t.getTitle(), t.getProductName(), t.getProductPrice(), t.getForm().name(), t.getShipper().name(),
                t.getStatus().name(), new TrialViews.Ref(Long.toString(t.getAdvertiserId()), advertiserName(t.getAdvertiserId())),
                new TrialViews.Ref(Long.toString(t.getBizApplicationId()), applicationNo), board(t.getReviewBoardId()), t.getRecruitCount(), t.getReviewerShare(),
                t.reviewerSlots(), t.getReviewerGoldHours(), t.getReviewerSilverHours(), t.getApplyStartAt(), t.getApplyEndAt(), t.getReviewDays(), t.getReturnDays(),
                t.getDescription(), t.getImageKey(), imageUrl(t.getImageKey()), t.getPriceUnit(), t.getUnitPrice(), t.getShippingFeePerPerson(), t.getSelectedCount(),
                t.getAmount(), counts, viewer == TrialActorType.OPERATOR || t.operatorShips() ? stock : null, rows, damageRows, t.getCreatedAt(), t.getUpdatedAt());
    }

    private TrialViews.Participant participant(TrialEntity t, TrialParticipantEntity p, TrialApplicationEntity a, TrialViews.Member member, Map<Long, String> postTitles,
                                               List<TrialPhotoEntity> ownPhotos, TrialActorType viewer) {
        boolean purged = a == null || a.getAddressPurgedAt() != null;
        boolean full = !purged && (viewer == TrialActorType.OPERATOR ? t.operatorShips() : t.getShipper() == TrialShipper.ADVERTISER);
        boolean contactOnly = !purged && !full && viewer == TrialActorType.ADVERTISER && p.getContactProvidedAt() != null;
        String recipient = full || contactOnly ? open(TrialPii.RECIPIENT, a.getRecipientEnc()) : null;
        String phone = full || contactOnly ? open(TrialPii.PHONE, a.getPhoneEnc()) : null;
        return new TrialViews.Participant(Long.toString(p.getId()), Long.toString(p.getApplicationId()), member, p.getQuota().name(), recipient, phone,
                full ? a.getPostcode() : null, full ? open(TrialPii.ADDRESS1, a.getAddress1Enc()) : null, full ? open(TrialPii.ADDRESS2, a.getAddress2Enc()) : null,
                full ? open(TrialPii.MEMO, a.getMemoEnc()) : null, purged, p.getCarrier(), p.getTrackingNo(), p.getShippedAt(),
                p.getShippedByType() == null ? null : p.getShippedByType().name(), p.getReceivedAt(), p.getReceivedBy(),
                p.getReviewPostId() == null ? null : new TrialViews.Ref(Long.toString(p.getReviewPostId()), postTitles.get(p.getReviewPostId())), p.getReviewDueAt(),
                p.getReturnDueAt(), p.getReturnedAt(), p.getKept(), p.getReviewLateAt(), p.getUnreturnedAt(), p.getContactProvidedAt(), photos(ownPhotos));
    }

    private String open(DataPurpose purpose, String enc) {
        return enc == null ? null : cipher.decrypt(purpose, enc);
    }

    public TrialViews.DamageReport damage(TrialDamageReportEntity d, TrialViews.Member member, List<TrialPhotoEntity> own) {
        return new TrialViews.DamageReport(Long.toString(d.getId()), Long.toString(d.getParticipantId()), member, d.getReporterType().name(), d.getDescription(),
                d.getStatus().name(), d.getObjectionDueAt(), d.getObjection(), d.getObjectedAt(), d.getVerdict() == null ? null : d.getVerdict().name(), d.getDecisionNote(),
                d.getDecidedAt(), photos(own), d.getCreatedAt());
    }

    /** 신청자 목록(관리자 뽑기 · 광고주 추천) */
    public TrialViews.ApplicantPage applicants(TrialEntity t) {
        List<TrialApplicationEntity> apps = applications.findByTrialIdOrderByCreatedAtAscIdAsc(t.getId()).stream().filter(TrialApplicationEntity::live).toList();
        Set<Long> userIds = new HashSet<>();
        apps.forEach(a -> userIds.add(a.getUserId()));
        Map<Long, TrialViews.Member> who = members(userIds);
        Map<Long, TrialQueries.Stats> stats = queries.stats(userIds, t.getReviewBoardId());
        List<TrialViews.Applicant> items = apps.stream().map(a -> {
            TrialQueries.Stats s = stats.get(a.getUserId());
            Integer keptRate = s == null || s.judged() == 0 ? null : (int) Math.round(s.kept() * 100.0 / s.judged());
            return new TrialViews.Applicant(Long.toString(a.getId()), who.get(a.getUserId()), a.getStatus().name(), a.getQuota().name(), a.getReviewerTier(),
                    a.getRecommendedAt() != null, s == null ? new TrialViews.ApplicantStats(0, 0, null, 0)
                    : new TrialViews.ApplicantStats(s.reviewPosts(), s.averageLikes(), keptRate, s.judged()), consents(a.getConsents()), a.getCreatedAt());
        }).toList();
        int reviewerSlots = t.reviewerSlots();
        return new TrialViews.ApplicantPage(t.getRecruitCount(), reviewerSlots, t.getRecruitCount() - reviewerSlots, items);
    }

    // ── 공개 ─────────────────────────────────────────────────────────────

    /** 공개 상태 — 모집 중인데 마감이 지났으면 뽑는 중으로 보인다 */
    public static TrialStatus publicStatus(TrialEntity t, Instant now) {
        return t.getStatus() == TrialStatus.RECRUITING && !now.isBefore(t.getApplyEndAt()) ? TrialStatus.SELECTING : t.getStatus();
    }

    public TrialViews.TrialCard card(TrialEntity t, long applicants, Instant now) {
        return new TrialViews.TrialCard(Long.toString(t.getId()), t.getTitle(), t.getProductName(), t.getProductPrice(), t.getForm().name(), publicStatus(t, now).name(), true,
                advertiserName(t.getAdvertiserId()), imageUrl(t.getImageKey()), t.getRecruitCount(), applicants, t.getApplyStartAt(), t.getApplyEndAt(),
                t.applyOpenFor("GOLD"), t.applyOpenFor("SILVER"), board(t.getReviewBoardId()));
    }

    public List<TrialViews.Review> reviews(TrialEntity t, Instant now) {
        var rows = queries.reviews(t.getId(), now);
        Set<Long> authors = new HashSet<>();
        rows.forEach(r -> {
            Long a = r.get(3, Long.class);
            if (a != null) {
                authors.add(a);
            }
        });
        Map<Long, TrialViews.Member> who = members(authors);
        BoardCatalogSnapshot s = catalog.snapshot();
        return rows.stream().map(r -> {
            Long boardId = r.get(2, Long.class);
            Long author = r.get(3, Long.class);
            return new TrialViews.Review(Long.toString(r.get(0, Long.class)), r.get(1, String.class),
                    boardId == null ? null : s.board(boardId).map(EffectiveBoard::slug).orElse(null), author == null ? null : who.get(author), r.get(4, Instant.class));
        }).toList();
    }

    public static boolean selectedOrLater(TrialApplicationEntity a) {
        return a.getStatus() == TrialApplicationStatus.SELECTED;
    }

    /** 글 하나 — 제목(없거나 지운 글 null) */
    public String postTitle(Long postId) {
        return postId == null ? null : posts.findById(postId).filter(p -> p.getDeletedAt() == null).map(PostEntity::getTitle).orElse(null);
    }

    /** 회원 프로필(없을 수 있다) */
    public ProfileEntity profile(long userId) {
        return profiles.findById(userId).orElse(null);
    }
}
