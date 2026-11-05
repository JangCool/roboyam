package com.community.emoticon.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.emoticon.dto.request.EmoticonReviewDecisionRequest;
import com.community.emoticon.dto.response.EmoticonAdminView;
import com.community.emoticon.entity.EmoticonReviewKind;
import com.community.emoticon.entity.EmoticonReviewStatus;
import com.community.emoticon.exception.EmoticonConflictException;
import com.community.emoticon.exception.EmoticonReviewNotFoundException;
import com.community.emoticon.repository.master.EmoticonPackStore;
import com.community.emoticon.repository.master.EmoticonPackStore.PackRow;
import com.community.emoticon.repository.master.EmoticonReadStore;
import com.community.emoticon.repository.master.EmoticonReadStore.MemberRef;
import com.community.emoticon.repository.master.EmoticonReviewStore;
import com.community.emoticon.repository.master.EmoticonReviewStore.ReviewRow;
import com.community.emoticon.entity.EmoticonSaleWish;
import com.community.emoticon.repository.master.EmoticonPayoutStore;
import com.community.emoticon.repository.master.EmoticonPriceEventStore;
import com.community.emoticon.repository.master.EmoticonSettingsStore.SettingsRow;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로보티콘 심사(관리자 — admin-web {@code useEmoticonAdminApi} 의 심사 부분, S1). 목록·상세는 {@code emoticon.read}, 승인·반려는 {@code emoticon.review}(컨트롤러가 본다).
 *
 * <ul>
 *   <li><b>목록</b> 탭: {@code PENDING}(처음 신청 대기) · {@code UPDATE}(새 판 대기) · {@code REJECTED} · {@code ALL}. 대기·새 판은 오래 기다린 것이 위, 반려·전체는 최근이 위. 탭 숫자 함께.</li>
 *   <li><b>상세</b>: 신청 때의 판({@code next}) + 새 판이면 신청 순간의 상점 판({@code current}, 처리가 끝난 건도 같은 비교 — 심사 건이 판 번호를 가리킨다) · 동의 셋 · 심사 기준 · 처리 기록.</li>
 *   <li><b>승인</b> {@code { price }}: 없는 건 404 → 처리된 건 {@code 409 EMOTICON_REVIEW_NOT_PENDING} → 가격 없음 400 → 작가 탈퇴로 멈춘 팩·탈퇴한 작가
 *       {@code 409 EMOTICON_PACK_CREATOR_WITHDRAWN}(후속 결정 6) → 가격 {@code 400 fields.price}(0 또는 가격 단계 — {@link #approve}).
 *       그 판이 상점 판이 된다(처음이면 판매 시작, 새 판이면 내용 교체 — 판매 중단 팩의 새 판이면 다시 판매). 감사 · 작가 알림(유료 승인은 가격과 함께).</li>
 *   <li><b>반려</b> {@code { reason }}: 사유 5~200자 {@code 400 fields.reason} → 404 → 409. 처음 신청이면 팩이 반려, 새 판이면 상점 판이 그대로. 감사 · 작가 알림(사유).</li>
 * </ul>
 * 처리는 심사 건 행 → 팩 행 차례로 잠근다(같은 건의 동시 처리·같은 팩의 받기와 줄을 선다).
 */
@Service
public class EmoticonReviewService {

    public static final String TARGET = "EMOTICON_REVIEW";

    private final EmoticonReviewStore reviews;
    private final EmoticonPackStore packs;
    private final EmoticonReadStore reads;
    private final EmoticonSettingsService settings;
    private final EmoticonContent content;
    private final AuditLogService audit;
    private final MemberNotificationPublisher memberNotices;
    private final EmoticonContractService contracts;
    private final EmoticonPayoutStore payouts;
    private final EmoticonPriceEventStore priceEvents;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public EmoticonReviewService(EmoticonReviewStore reviews, EmoticonPackStore packs, EmoticonReadStore reads, EmoticonSettingsService settings, EmoticonContent content,
                                 AuditLogService audit, MemberNotificationPublisher memberNotices, EmoticonContractService contracts, EmoticonPayoutStore payouts,
                                 EmoticonPriceEventStore priceEvents, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.contracts = contracts;
        this.payouts = payouts;
        this.priceEvents = priceEvents;
        this.ids = ids;
        this.reviews = reviews;
        this.packs = packs;
        this.reads = reads;
        this.settings = settings;
        this.content = content;
        this.audit = audit;
        this.memberNotices = memberNotices;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Transactional(readOnly = true)
    public EmoticonAdminView.ReviewList list(String tab) {
        String t = tab == null ? "PENDING" : tab;
        List<ReviewRow> rows = switch (t) {
            case "UPDATE" -> reviews.list(EmoticonReviewStatus.PENDING, EmoticonReviewKind.UPDATE, true);
            case "REJECTED" -> reviews.list(EmoticonReviewStatus.REJECTED, null, false);
            case "ALL" -> reviews.list(null, null, false);
            default -> reviews.list(EmoticonReviewStatus.PENDING, EmoticonReviewKind.FIRST, true);
        };
        return new EmoticonAdminView.ReviewList(rows(rows), reviews.counts());
    }

    @Transactional(readOnly = true)
    public EmoticonAdminView.ReviewDetail detail(String idText) {
        ReviewRow r = find(idText);
        EmoticonAdminView.ReviewRow row = rows(List.of(r)).get(0);
        Map<Long, EmoticonContent.Loaded> loaded = content.load(List.of(r.revisionId(), r.previousRevisionId() == null ? r.revisionId() : r.previousRevisionId()));
        List<EmoticonAdminView.ConsentRecord> consents = List.of(new EmoticonAdminView.ConsentRecord("ORIGINAL", r.agreeOwnershipAt(), r.consentVersion()),
                new EmoticonAdminView.ConsentRecord("LICENSE", r.agreeLicenseAt(), r.consentVersion()),
                new EmoticonAdminView.ConsentRecord("POLICY", r.agreePolicyAt(), r.consentVersion()));
        EmoticonAdminView.Decision decision = r.status() == EmoticonReviewStatus.PENDING ? null
                : new EmoticonAdminView.Decision(r.status().name(), r.reason(), r.decidedAt(), r.decidedByName(), r.systemCause() == null ? "OPERATOR" : r.systemCause());
        return new EmoticonAdminView.ReviewDetail(row.id(), row.packId(), row.packName(), row.coverUrl(), row.creator(), row.itemCount(), row.saleWish(), row.currentPrice(),
                row.submittedAt(), row.kind(), row.version(), row.status(), content.adminVersion(loaded.get(r.revisionId())),
                r.previousRevisionId() == null ? null : content.adminVersion(loaded.get(r.previousRevisionId())), consents, settings.packRules(settings.current()),
                decision, reviews.pendingCount(r.creatorId()), contracts.consentOf(r.creatorId()), payouts.registered(r.creatorId()));
    }

    /**
     * 승인 {@code { price }} — 0 = 무료, 유료는 설정의 가격 단계 중 하나(카카오 방식 — 운영자가 고른다). 차례: 없는 건 404 → 처리된 건 409 → 가격 없음 400 → (설정 행 나눔 잠금)
     * 단계 밖 {@code 400 fields.price} → 유료 판매 꺼짐 → 처음 신청이 무료 희망 → 유료 조건({@link #paidBlock} — 정산 정보 · 지금 판 계약 동의). 모두 {@code 400 fields.price}(목업 {@code priceFields}·
     * {@code paidBlockReason}). 새 판 승인은 판매 희망을 보지 않는다(나온 팩의 판매 희망은 바뀌지 않고, 가격은 운영자가 정한다 — 유료로 정하면 같은 유료 조건).
     * 새 판 승인으로 가격이 바뀌면 가격 전환 기록(심사 건을 가리킨다, 사유 없음)을 남기고 작가에게 가격 바뀜도 알린다.
     */
    @Transactional
    public void approve(long operatorId, String ip, String idText, EmoticonReviewDecisionRequest body) {
        ReviewRow r = lock(idText);
        if (r.status() != EmoticonReviewStatus.PENDING) {
            throw new EmoticonConflictException(EmoticonConflictException.REVIEW_NOT_PENDING);
        }
        Integer price = body == null ? null : body.price();
        if (price == null) {
            throw ValidationException.of("price", new MessageRef("emoticon.review.price.required"));
        }
        PackRow p = packs.lock(r.packId()).orElseThrow(EmoticonReviewNotFoundException::new);
        if (p.creatorWithdrawn() || creatorWithdrawn(r.creatorId())) {
            // 작가 탈퇴로 멈춘 팩 · 탈퇴한 작가의 건 — 승인하면 다시 판매가 되므로 받지 않는다(후속 결정 6, 탈퇴 때 대기 건은 이미 닫힌다 — 그 사이의 그물)
            throw new EmoticonConflictException(EmoticonConflictException.PACK_CREATOR_WITHDRAWN, "error.EMOTICON_PACK_CREATOR_WITHDRAWN.review");
        }
        SettingsRow s = settings.currentForPricing();
        EmoticonPackStore.RevisionRow revision = packs.revision(r.revisionId()).orElseThrow(EmoticonReviewNotFoundException::new);
        if (price != 0) {
            priceFields(s, price);
            if (r.kind() == EmoticonReviewKind.FIRST && revision.saleWish() != EmoticonSaleWish.PAID) {
                throw ValidationException.of("price", new MessageRef("emoticon.review.price.freeWish"));
            }
            MessageRef blocked = paidBlock(r.creatorId());
            if (blocked != null) {
                throw ValidationException.of("price", blocked);
            }
        }
        Instant now = clock.instant();
        int before = p.price();
        reviews.decide(r.id(), EmoticonReviewStatus.APPROVED, operatorId, null, price, now);
        packs.publish(p.id(), r.revisionId(), now);
        if (before != price) {
            packs.setPrice(p.id(), price, now);
            if (r.kind() == EmoticonReviewKind.UPDATE) {
                priceEvents.insert(ids.nextId(), p.id(), before, price, null, r.id(), now, operatorId); // 처음 승인의 가격은 시작 값이라 기록하지 않는다(목업과 같다)
            }
        }
        String name = revision.name();
        int version = revision.version() == null ? 0 : revision.version();
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.EMOTICON_REVIEW_APPROVE, AuditResult.SUCCESS).target(TARGET, Long.toString(r.id()))
                .with("packId", Long.toString(p.id())).with("kind", r.kind().name()).with("version", version).with("price", price).with("priceBefore", before));
        if (r.kind() == EmoticonReviewKind.UPDATE) {
            memberNotices.emoticonPack(r.creatorId(), "UPDATE_APPROVED", p.id(), name, null);
            if (before != price) {
                memberNotices.emoticonPackPrice(r.creatorId(), p.id(), name, before, price, null);
            }
        } else if (price > 0) {
            memberNotices.emoticonPack(r.creatorId(), "APPROVED_PAID", p.id(), name, String.format("%,d", price));
        } else {
            memberNotices.emoticonPack(r.creatorId(), "APPROVED", p.id(), name, null);
        }
    }

    private boolean creatorWithdrawn(long creatorId) {
        MemberRef m = reads.members(List.of(creatorId)).get(creatorId);
        return m != null && m.withdrawn();
    }

    /** 유료 가격 검사 — 설정의 가격 단계 중 하나 · 유료 판매 켜짐({@code 400 fields.price}) */
    static void priceFields(SettingsRow s, int price) {
        if (!s.priceTiers().contains(price)) {
            throw ValidationException.of("price", new MessageRef("emoticon.review.price.tier"));
        }
        if (!s.paidSalesEnabled()) {
            throw ValidationException.of("price", new MessageRef("emoticon.review.price.paidDisabled"));
        }
    }

    /** 작가의 유료 판매 조건(목업 {@code paidBlockReason}) — 정산 정보 · 지금 판 상품화 계약 동의. 걸리면 그 문구, 아니면 null */
    MessageRef paidBlock(long creatorId) {
        if (!payouts.registered(creatorId)) {
            return new MessageRef("emoticon.review.price.noPayout");
        }
        if (!contracts.agreedCurrent(creatorId)) {
            return new MessageRef("emoticon.review.price.noContract");
        }
        return null;
    }

    @Transactional
    public void reject(long operatorId, String ip, String idText, EmoticonReviewDecisionRequest body) {
        String reason = EmoticonPackAdminService.reason(body == null ? null : body.reason());
        ReviewRow r = lock(idText);
        if (r.status() != EmoticonReviewStatus.PENDING) {
            throw new EmoticonConflictException(EmoticonConflictException.REVIEW_NOT_PENDING);
        }
        PackRow p = packs.lock(r.packId()).orElseThrow(EmoticonReviewNotFoundException::new);
        Instant now = clock.instant();
        reviews.decide(r.id(), EmoticonReviewStatus.REJECTED, operatorId, reason, null, now);
        if (r.kind() == EmoticonReviewKind.FIRST) {
            packs.setStatus(p.id(), com.community.emoticon.entity.EmoticonPackStatus.REJECTED, now);
        } else {
            packs.touch(p.id(), now); // 새 판 반려 — 상점 판은 그대로 판매 중(또는 판매 중단)
        }
        String name = packs.revision(r.revisionId()).map(EmoticonPackStore.RevisionRow::name).orElse("");
        int version = packs.revision(r.revisionId()).map(EmoticonPackStore.RevisionRow::version).orElse(0);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.EMOTICON_REVIEW_REJECT, AuditResult.SUCCESS).target(TARGET, Long.toString(r.id()))
                .with("packId", Long.toString(p.id())).with("kind", r.kind().name()).with("version", version).with("reason", reason));
        memberNotices.emoticonPack(r.creatorId(), r.kind() == EmoticonReviewKind.UPDATE ? "UPDATE_REJECTED" : "REJECTED", p.id(), name, reason);
    }

    private ReviewRow find(String idText) {
        long id = EmoticonShopService.parse(idText);
        return (id < 0 ? java.util.Optional.<ReviewRow>empty() : reviews.find(id)).orElseThrow(EmoticonReviewNotFoundException::new);
    }

    private ReviewRow lock(String idText) {
        long id = EmoticonShopService.parse(idText);
        return (id < 0 ? java.util.Optional.<ReviewRow>empty() : reviews.lock(id)).orElseThrow(EmoticonReviewNotFoundException::new);
    }

    private List<EmoticonAdminView.ReviewRow> rows(List<ReviewRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, EmoticonContent.Loaded> loaded = content.load(rows.stream().map(ReviewRow::revisionId).toList());
        Set<Long> packIds = rows.stream().map(ReviewRow::packId).collect(Collectors.toSet());
        Map<Long, PackRow> byPack = packs.byIds(packIds).stream().collect(Collectors.toMap(PackRow::id, p -> p));
        Set<Long> creators = new HashSet<>();
        rows.forEach(r -> creators.add(r.creatorId()));
        Map<Long, MemberRef> members = reads.members(creators);
        List<EmoticonAdminView.ReviewRow> out = new ArrayList<>();
        for (ReviewRow r : rows) {
            EmoticonContent.Loaded l = loaded.get(r.revisionId());
            PackRow p = byPack.get(r.packId());
            out.add(new EmoticonAdminView.ReviewRow(Long.toString(r.id()), Long.toString(r.packId()), l == null ? "" : l.revision().name(),
                    l == null || l.cover() == null ? null : content.url(l.cover()), creatorRef(r.creatorId(), members), l == null ? 0 : l.items().size(),
                    l == null ? "FREE" : l.revision().saleWish().name(), r.kind() == EmoticonReviewKind.UPDATE && p != null ? p.price() : 0, r.submittedAt(), r.kind().name(),
                    l == null || l.revision().version() == null ? 0 : l.revision().version(), r.status().name()));
        }
        return out;
    }

    static EmoticonAdminView.CreatorRef creatorRef(Long creatorId, Map<Long, MemberRef> members) {
        if (creatorId == null) {
            return null;
        }
        MemberRef m = members.get(creatorId);
        return new EmoticonAdminView.CreatorRef(Long.toString(creatorId), m == null ? null : m.nickname(), m == null || m.withdrawn());
    }
}
