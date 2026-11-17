package com.community.trial.service;

import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.EffectiveBoard;
import com.community.board.entity.BoardPrefixEntity;
import com.community.board.repository.master.BoardPrefixRepository;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.post.entity.PostEntity;
import com.community.trial.entity.PurchaseProofEntity;
import com.community.trial.entity.PurchaseProofStatus;
import com.community.trial.entity.PurchaseSource;
import com.community.trial.entity.QPurchaseProofEntity;
import com.community.trial.entity.TrialEntity;
import com.community.trial.entity.TrialParticipantEntity;
import com.community.trial.entity.TrialStatus;
import com.community.trial.exception.TrialException;
import com.community.trial.repository.master.PurchaseProofRepository;
import com.community.trial.repository.master.TrialParticipantRepository;
import com.community.trial.repository.master.TrialRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 글과 체험단·구입 경로 잇기(trial-group-contract 「구입 경로」·「사용기 쓰는 곳」·「구현 결정」 5) — 공개 글쓰기·고치기가 부른다.
 *
 * <ul>
 *   <li>글쓰기 {@code trialParticipantId}: 내 뽑힌 줄 · 아직 연결 글 없음 · 미반납 아님 · 모집이 진행 중/끝남 · 그 모집의 사용기 게시판 · 그 게시판이 「체험단 사용기 받기」 —
 *       아니면 400 {@code fields.trialParticipantId}. 연결하면 구입 경로 TRIAL 고정, 서버가 공통 말머리 TRIAL(「체험단」)을 단다(없으면 만든다).</li>
 *   <li>{@code purchaseSource}: 「구입 경로 받기」가 꺼진 게시판이면 무시. SELF·SPONSORED·BORROWED(체험단 TRIAL 은 연결로만 — 400). 꼭 골라야 하는 게시판에서 빠지면 400
 *       {@code fields.purchaseSource}. 체험단 말머리를 직접 고르면 400 {@code fields.prefixId}.</li>
 *   <li>글 응답 {@code purchase} = 구입 경로 · 구매 인증 상태(NONE·PENDING·APPROVED) · 표시 상자 문구(체험단·협찬이면 — 설정 문구, 글쓴이가 지울 수 없다).</li>
 *   <li>목록 거르기 {@code purchase=PROOF}(구매 인증 승인 글만) · {@code NO_TRIAL}(체험단 빼고).</li>
 * </ul>
 */
@Component
public class TrialPostLinker {

    public static final String FILTER_PROOF = "PROOF";
    public static final String FILTER_NO_TRIAL = "NO_TRIAL";

    /** 글쓰기 판단 — 구입 경로(null = 받지 않음·고르지 않음) · 연결할 줄 */
    public record Decision(String source, Long participantId) {

        public static final Decision NONE = new Decision(null, null);

        public boolean trial() {
            return participantId != null;
        }
    }

    /**
     * 글 응답의 구입 경로 — {@code proof} NONE · PENDING · APPROVED(구입 경로가 「내 돈으로 샀어요」일 때만 — 점검 L3), {@code disclosure} 표시 상자(체험단·협찬이면),
     * {@code locked} 글쓴이가 구입 경로를 바꿀 수 없다(체험단 연결 글 · 운영자가 협찬 숨김을 확인해 협찬으로 고정한 글 — 점검 D2).
     */
    public record PurchaseView(String source, String proof, String disclosure, boolean locked) {
    }

    /** 목록 줄의 구입 경로 · 구매 인증 승인(구입 경로가 SELF 일 때만) */
    public record ListInfo(String source, boolean proofApproved) {
    }

    private static final QPurchaseProofEntity PP = QPurchaseProofEntity.purchaseProofEntity;

    private final TrialParticipantRepository participants;
    private final TrialRepository trials;
    private final PurchaseProofRepository proofs;
    private final TrialPromiseService promise;
    private final TrialSettingsService settings;
    private final TrialViewer viewer;
    private final BoardPrefixRepository prefixes;
    private final BoardCatalog catalog;
    private final JPAQueryFactory query;
    private final Messages messages;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate separate;
    private final com.community.trial.repository.master.TrialSponsorReviewRepository sponsorReviews;
    private final PurchaseProofService proofService;

    public TrialPostLinker(TrialParticipantRepository participants, TrialRepository trials, PurchaseProofRepository proofs, TrialPromiseService promise,
                           TrialSettingsService settings, TrialViewer viewer, BoardPrefixRepository prefixes, BoardCatalog catalog, JPAQueryFactory query,
                           Messages messages, SnowflakeIdGenerator ids, PlatformTransactionManager transactions,
                           com.community.trial.repository.master.TrialSponsorReviewRepository sponsorReviews, PurchaseProofService proofService) {
        this.sponsorReviews = sponsorReviews;
        this.proofService = proofService;
        this.participants = participants;
        this.trials = trials;
        this.proofs = proofs;
        this.promise = promise;
        this.settings = settings;
        this.viewer = viewer;
        this.prefixes = prefixes;
        this.catalog = catalog;
        this.query = query;
        this.messages = messages;
        this.ids = ids;
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ── 글쓰기 ───────────────────────────────────────────────────────────

    /** 새 글 — 칸 오류는 {@code fields} 에 넣고 {@link Decision#NONE} */
    public Decision checkForCreate(EffectiveBoard b, Long authorId, String sourceRaw, String participantRaw, Long chosenPrefixId, Map<String, MessageRef> fields) {
        Decision d = Decision.NONE;
        if (participantRaw != null && !participantRaw.isBlank()) {
            Long pid = TrialService.parseId(participantRaw);
            if (pid == null || authorId == null || !linkable(pid, authorId, b)) {
                fields.put("trialParticipantId", new MessageRef("trial.post.link.invalid"));
                return Decision.NONE;
            }
            d = new Decision(PurchaseSource.TRIAL.name(), pid);
        } else if (b.purchaseSourceAccepted()) {
            d = source(b, sourceRaw, fields);
        }
        guardPrefix(b, chosenPrefixId, d, fields);
        return d;
    }

    /**
     * 고치기 — 연결 글은 TRIAL 고정(그대로). 운영자가 협찬 숨김을 확인해 협찬으로 바꾼 글은 SPONSORED 고정 — 다른 값을 보내면 400 {@code fields.purchaseSource}(점검 D2).
     * 칸이 없으면(null) 그대로 · 값이면 바꾸기
     */
    public Decision checkForUpdate(EffectiveBoard b, PostEntity post, String sourceRaw, Long chosenPrefixId, Map<String, MessageRef> fields) {
        if (post.getTrialParticipantId() != null) {
            return new Decision(PurchaseSource.TRIAL.name(), post.getTrialParticipantId());
        }
        if (sponsorLocked(post)) {
            if (sourceRaw != null && !PurchaseSource.SPONSORED.name().equalsIgnoreCase(sourceRaw.strip())) {
                fields.put("purchaseSource", new MessageRef("trial.purchase.sponsorLocked"));
            }
            Decision locked = new Decision(PurchaseSource.SPONSORED.name(), null);
            guardPrefix(b, chosenPrefixId, locked, fields);
            return locked;
        }
        Decision d = new Decision(post.getPurchaseSource(), null);
        if (sourceRaw != null && b.purchaseSourceAccepted()) {
            d = source(b, sourceRaw, fields);
        }
        guardPrefix(b, chosenPrefixId, d, fields);
        return d;
    }

    private Decision source(EffectiveBoard b, String raw, Map<String, MessageRef> fields) {
        if (raw == null || raw.isBlank()) {
            if (b.purchaseSourceRequired()) {
                fields.put("purchaseSource", new MessageRef("trial.purchase.required"));
            }
            return Decision.NONE;
        }
        PurchaseSource s = PurchaseSource.parse(raw).orElse(null);
        if (s == null) {
            fields.put("purchaseSource", new MessageRef("trial.value.invalid"));
            return Decision.NONE;
        }
        if (s == PurchaseSource.TRIAL) {
            fields.put("purchaseSource", new MessageRef("trial.purchase.trialNeedsLink")); // 체험단은 뽑힌 체험단과 이어서만
            return Decision.NONE;
        }
        return new Decision(s.name(), null);
    }

    /** 체험단 말머리는 서버만 단다 — 직접 고르면 400 */
    private void guardPrefix(EffectiveBoard b, Long chosenPrefixId, Decision d, Map<String, MessageRef> fields) {
        if (d.trial() || chosenPrefixId == null) {
            return;
        }
        b.prefix(chosenPrefixId).filter(p -> BoardFields.TRIAL_PREFIX.equals(p.commonKey())).ifPresent(p -> fields.put("prefixId", new MessageRef("trial.prefix.reserved")));
    }

    private boolean linkable(long participantId, long authorId, EffectiveBoard b) {
        TrialParticipantEntity p = participants.findById(participantId).orElse(null);
        if (p == null || p.cancelled() || p.getUserId() != authorId || p.getReviewPostId() != null || p.getUnreturnedAt() != null) {
            return false;
        }
        TrialEntity t = trials.findById(p.getTrialId()).orElse(null);
        return t != null && (t.getStatus() == TrialStatus.RUNNING || t.getStatus() == TrialStatus.CLOSED) && t.getReviewBoardId() == b.id() && b.trialReviewsEnabled();
    }

    /**
     * 게시판의 공통 말머리 TRIAL — 없으면 만들고(꺼져 있으면 켠다) 게시판 캐시를 비운다. 따로 짧은 트랜잭션(두 글이 동시에 만들면 유일 색인 — 다시 읽는다). 말머리 ID.
     */
    public long ensureTrialPrefix(EffectiveBoard b) {
        Optional<BoardPrefixInfo> existing = b.prefixes().stream().filter(p -> BoardFields.TRIAL_PREFIX.equals(p.commonKey()) && p.active()).findFirst();
        if (existing.isPresent()) {
            return existing.get().id();
        }
        Long id;
        try {
            id = separate.execute(status -> upsertPrefix(b.id()));
        } catch (DuplicateKeyException e) {
            id = separate.execute(status -> upsertPrefix(b.id()));
        }
        catalog.invalidate();
        return id;
    }

    private Long upsertPrefix(long boardId) {
        Instant now = Instant.now();
        for (BoardPrefixEntity p : prefixes.findByBoardIdOrderBySortOrderAscIdAsc(boardId)) {
            if (BoardFields.TRIAL_PREFIX.equals(p.getCommonKey())) {
                if (!p.isActive()) {
                    p.changeActive(true, now);
                }
                return p.getId();
            }
        }
        int order = prefixes.findByBoardIdOrderBySortOrderAscIdAsc(boardId).stream().mapToInt(BoardPrefixEntity::getSortOrder).max().orElse(0) + 1;
        BoardPrefixEntity created = BoardPrefixEntity.common(ids.nextId(), boardId, BoardFields.TRIAL_PREFIX, messages.get(new MessageRef("trial.prefix.name")), true, order,
                now);
        prefixes.persist(created);
        prefixes.flush();
        return created.getId();
    }

    /**
     * 새 글에 구입 경로를 남기고 체험단 줄과 잇는다 — 글 저장 트랜잭션 안(persist 전). 줄을 잠근 뒤 다시 본다(동시에 두 글을 잇지 못하게 — 409 는 400 칸 오류로).
     */
    public void applyOnCreate(PostEntity created, Decision d) {
        if (d.source() == null) {
            return;
        }
        if (!d.trial()) {
            created.changePurchaseSource(d.source());
            return;
        }
        created.linkTrial(d.participantId());
    }

    /** 글을 넣은 뒤(같은 트랜잭션) — 체험단 줄에 글을 잇고 받음(안 눌렀으면)·약속 판정 */
    public void afterCreate(PostEntity created, Decision d, Instant now) {
        if (!d.trial()) {
            return;
        }
        TrialParticipantEntity p = participants.findWithLockById(d.participantId()).orElseThrow(TrialException::participantNotFound);
        if (p.getReviewPostId() != null || p.cancelled()) { // 그사이 운영자가 줄을 취소했으면 잇지 않는다
            throw com.community.zconfiguration.exception.ValidationException.of("trialParticipantId", new MessageRef("trial.post.link.invalid"));
        }
        TrialEntity t = trials.findById(p.getTrialId()).orElseThrow(TrialException::notFound);
        if (!p.received()) {
            p.receive(TrialParticipantEntity.RECEIVED_BY_MEMBER, now, t.getReviewDays(), t.getReturnDays()); // 사용기를 썼으면 받은 것으로 본다
        }
        p.linkReview(created.getId(), now);
        promise.judgeIfDone(p, t, now);
    }

    /** 고친 글의 구입 경로(연결 글은 그대로) */
    public void applyOnUpdate(PostEntity post, Decision d) {
        if (post.getTrialParticipantId() == null && d != null) {
            post.changePurchaseSource(d.source());
        }
    }

    /** 운영자가 협찬 숨김을 확인해 협찬으로 고정한 글인가(확인 줄 CONFIRMED · 지금 구입 경로 SPONSORED — 점검 D2) */
    public boolean sponsorLocked(PostEntity post) {
        return PurchaseSource.SPONSORED.name().equals(post.getPurchaseSource())
                && sponsorReviews.findById(post.getId()).filter(r -> r.getStatus() == com.community.trial.entity.SponsorReviewStatus.CONFIRMED).isPresent();
    }

    /**
     * 글쓴이가 글을 지웠다(지우기 트랜잭션 안 — 예약 삭제도 요청 때) — 대기 구매 인증 자동 반려·사진 파기(점검 L3·M4-4) · 체험단 사용기면 연결을 끊고 「지킴」 판정을 되돌려 리뷰어 단계를 다시 정한다
     * (점검 L4 — 기한 안에 다시 쓰면 지킴, 기한이 지나면 하루 배치가 기한 넘김으로 판정).
     */
    public void onAuthorDelete(PostEntity post) {
        proofService.onPostDeleted(post.getId());
        if (post.getTrialParticipantId() == null) {
            return;
        }
        TrialParticipantEntity p = participants.findWithLockById(post.getTrialParticipantId()).orElse(null);
        if (p == null || !post.getId().equals(p.getReviewPostId())) {
            return;
        }
        boolean reverted = p.unlinkReview();
        participants.flush();
        if (reverted) {
            promise.recalcReviewer(p.getUserId());
        }
    }

    // ── 글 응답·목록 ──────────────────────────────────────────────────────

    /** 글 응답 {@code purchase} — 구입 경로도 구매 인증도 없으면 null. 구매 인증 상태는 구입 경로가 「내 돈으로 샀어요」일 때만(바꾸면 칩을 숨긴다 — 포인트는 회수하지 않는다, 점검 L3) */
    public PurchaseView view(PostEntity post) {
        PurchaseProofEntity proof = proofs.findFirstByPostIdOrderByCreatedAtDescIdDesc(post.getId()).orElse(null);
        boolean self = PurchaseSource.SELF.name().equals(post.getPurchaseSource());
        String proofStatus = !self || proof == null || proof.getStatus() == PurchaseProofStatus.REJECTED ? "NONE" : proof.getStatus().name();
        if (post.getPurchaseSource() == null && "NONE".equals(proofStatus)) {
            return null;
        }
        return new PurchaseView(post.getPurchaseSource(), proofStatus, disclosure(post), post.getTrialParticipantId() != null || sponsorLocked(post));
    }

    /** 표시 상자 문구 — 체험단: 설정 문구의 {광고주}·{제공/대여} 를 채운다 · 협찬: 협찬 문구 · 그 밖 null */
    public String disclosure(PostEntity post) {
        if (PurchaseSource.SPONSORED.name().equals(post.getPurchaseSource())) {
            return settings.current().sponsorDisclosureText();
        }
        if (!PurchaseSource.TRIAL.name().equals(post.getPurchaseSource()) || post.getTrialParticipantId() == null) {
            return null;
        }
        TrialEntity t = participants.findById(post.getTrialParticipantId()).flatMap(p -> trials.findById(p.getTrialId())).orElse(null);
        if (t == null) {
            return null;
        }
        String kind = messages.get(new MessageRef("trial.disclosure.kind." + t.getForm().name()));
        return settings.current().disclosureText().replace("{광고주}", viewer.advertiserName(t.getAdvertiserId())).replace("{제공/대여}", kind);
    }

    /** 목록 줄 — 글 묶음의 구입 경로·구매 인증 승인(질의 두 번 — 게시판 목록·섞인 목록 모두). 구매 인증 칩은 구입 경로가 SELF 일 때만(점검 L3) */
    public Map<Long, ListInfo> forList(Collection<Long> postIds) {
        Map<Long, ListInfo> out = new HashMap<>();
        if (postIds.isEmpty()) {
            return out;
        }
        com.community.post.entity.QPostEntity post = com.community.post.entity.QPostEntity.postEntity;
        Map<Long, String> sources = new HashMap<>();
        for (com.querydsl.core.Tuple t : query.select(post.id, post.purchaseSource).from(post).where(post.id.in(postIds), post.purchaseSource.isNotNull()).fetch()) {
            sources.put(t.get(post.id), t.get(post.purchaseSource));
        }
        Set<Long> approved = new HashSet<>(query.select(PP.postId).from(PP).where(PP.postId.in(postIds), PP.status.eq(PurchaseProofStatus.APPROVED)).fetch());
        for (Long id : postIds) {
            String source = sources.get(id);
            out.put(id, new ListInfo(source, approved.contains(id) && PurchaseSource.SELF.name().equals(source)));
        }
        return out;
    }

    /** 목록 거르기 조건 — PROOF(구매 인증 승인 + 구입 경로 SELF 글만) · NO_TRIAL(체험단 빼고), 그 밖·null 은 null(거르지 않음) */
    public static BooleanExpression filter(com.community.post.entity.QPostEntity post, String purchase) {
        if (purchase == null) {
            return null;
        }
        return switch (purchase.strip().toUpperCase(java.util.Locale.ROOT)) {
            case FILTER_PROOF -> post.purchaseSource.eq(PurchaseSource.SELF.name())
                    .and(JPAExpressions.selectOne().from(PP).where(PP.postId.eq(post.id), PP.status.eq(PurchaseProofStatus.APPROVED)).exists());
            case FILTER_NO_TRIAL -> post.purchaseSource.isNull().or(post.purchaseSource.ne(PurchaseSource.TRIAL.name()));
            default -> null;
        };
    }
}
