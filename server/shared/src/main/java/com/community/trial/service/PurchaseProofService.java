package com.community.trial.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.board.dto.EffectiveBoard;
import com.community.board.entity.BoardStaffEntity;
import com.community.board.entity.BoardStaffId;
import com.community.board.repository.master.BoardStaffRepository;
import com.community.board.service.BoardCatalog;
import com.community.membernotification.entity.MemberNotificationType;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.point.service.PointService;
import com.community.post.entity.PostEntity;
import com.community.post.repository.master.PostRepository;
import com.community.trial.dto.TrialViews;
import com.community.trial.entity.PurchaseProofEntity;
import com.community.trial.entity.PurchaseProofStatus;
import com.community.trial.entity.PurchaseSource;
import com.community.trial.entity.QPurchaseProofEntity;
import com.community.trial.exception.TrialException;
import com.community.trial.repository.master.PurchaseProofRepository;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용기 구매 인증(trial-group-contract 「구입 경로」·「구현 결정」 7·9·13) — 글쓴이가 주문 내역·영수증 사진을 올리고, 그 게시판 매니저 또는 운영자가 확인한다.
 *
 * <ul>
 *   <li><b>올리기</b>: 내 글 · 구입 경로를 받는 게시판 · 「내 돈으로 샀어요」(SELF) 글만(아니면 409 {@code PURCHASE_PROOF_NOT_ALLOWED}). 대기·승인이 있으면 409
 *       {@code PURCHASE_PROOF_EXISTS}. JPEG·PNG·WebP 정지 그림 10MB 까지 — 다시 인코딩한 JPEG(EXIF·위치 정보가 빠진다)의 암호문만 DB 에 둔다(비공개).</li>
 *   <li><b>확인</b>: 운영자({@code trial.review}) 또는 그 게시판 매니저(자기 글 제외 403 {@code PURCHASE_PROOF_SELF}). 승인 = 「구매 인증」 칩 + 보너스(설정, 원장 {@code PURCHASE_PROOF} —
 *       글마다 한 번) + 회원 알림, 거절 = 사유 필수 + 회원 알림(다시 올릴 수 있다). 감사 기록.</li>
 *   <li><b>사진 보기</b>: 확인하는 사람만, 볼 때마다 감사 기록({@code PURCHASE_PROOF_VIEW} — 개인정보 분류). 파기된 사진은 410.</li>
 * </ul>
 */
@Service
public class PurchaseProofService {

    public static final long MAX_BYTES = TrialPhotoCodec.MAX_BYTES;
    public static final int LONG_SIDE = TrialPhotoCodec.LONG_SIDE;
    private static final QPurchaseProofEntity PP = QPurchaseProofEntity.purchaseProofEntity;

    /** 확인하는 사람 — 운영자 또는 게시판 매니저(회원) */
    public record Reviewer(Long operatorId, Long memberId, String ip) {

        public static Reviewer operator(long operatorId, String ip) {
            return new Reviewer(operatorId, null, ip);
        }

        public static Reviewer manager(long memberId, String ip) {
            return new Reviewer(null, memberId, ip);
        }

        AuditEvent event(String action) {
            return operatorId != null ? AuditEvent.byOperator(operatorId, ip, action, AuditResult.SUCCESS) : AuditEvent.byMember(memberId, ip, action, AuditResult.SUCCESS);
        }
    }

    /** 사진(복호화한 JPEG) */
    public record Photo(byte[] bytes, String contentType) {

        @Override
        public String toString() {
            return "Photo[가림]";
        }
    }

    private final PurchaseProofRepository proofs;
    private final PostRepository posts;
    private final BoardCatalog catalog;
    private final BoardStaffRepository staff;
    private final TrialSettingsService settings;
    private final TrialViewer viewer;
    private final PointService points;
    private final MemberNotificationPublisher notices;
    private final AuditLogService audit;
    private final TrialPhotoCodec photoCodec;
    private final DataCipher cipher;
    private final JPAQueryFactory query;
    private final Messages messages;
    private final SnowflakeIdGenerator ids;
    private final Clock clock;

    public PurchaseProofService(PurchaseProofRepository proofs, PostRepository posts, BoardCatalog catalog, BoardStaffRepository staff, TrialSettingsService settings,
                                TrialViewer viewer, PointService points, MemberNotificationPublisher notices, AuditLogService audit, TrialPhotoCodec photoCodec, DataCipher cipher,
                                JPAQueryFactory query, Messages messages, SnowflakeIdGenerator ids, ObjectProvider<Clock> clocks) {
        this.proofs = proofs;
        this.posts = posts;
        this.catalog = catalog;
        this.staff = staff;
        this.settings = settings;
        this.viewer = viewer;
        this.points = points;
        this.notices = notices;
        this.audit = audit;
        this.photoCodec = photoCodec;
        this.cipher = cipher;
        this.query = query;
        this.messages = messages;
        this.ids = ids;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    // ── 올리기 ───────────────────────────────────────────────────────────

    @Transactional
    public TrialViews.ProofResult submit(long userId, String postIdText, byte[] data) {
        cipher.requireConfigured();
        PostEntity post = posts.findWithLockById(TrialService.id(postIdText, TrialException::proofNotFound)).filter(p -> p.getDeletedAt() == null)
                .orElseThrow(TrialException::proofNotFound);
        EffectiveBoard b = catalog.snapshot().board(post.getBoardId()).orElseThrow(TrialException::proofNotAllowed);
        if (post.getAuthorId() == null || post.getAuthorId() != userId || !b.purchaseSourceAccepted() || !PurchaseSource.SELF.name().equals(post.getPurchaseSource())) {
            throw TrialException.proofNotAllowed();
        }
        PurchaseProofEntity last = proofs.findFirstByPostIdOrderByCreatedAtDescIdDesc(post.getId()).orElse(null);
        if (last != null && last.getStatus() != PurchaseProofStatus.REJECTED) {
            throw TrialException.proofExists();
        }
        byte[] jpeg = photoCodec.jpeg(data); // 다시 인코딩(JPEG — EXIF·위치 정보가 빠진다, 긴 변 2000px) — 틀리면 400 fields.file
        PurchaseProofEntity proof = PurchaseProofEntity.submit(ids.nextId(), post.getId(), b.id(), userId, cipher.encryptBytes(TrialPii.PROOF, jpeg), "image/jpeg", now());
        try {
            proofs.persist(proof);
            proofs.flush();
        } catch (DuplicateKeyException e) {
            throw TrialException.proofExists();
        }
        return new TrialViews.ProofResult(Long.toString(proof.getId()), proof.getStatus().name());
    }


    // ── 확인 ─────────────────────────────────────────────────────────────

    /** 승인 — 지운 글(작성자·운영자 삭제, 삭제 예약 포함)의 인증은 승인할 수 없다(409 {@code PURCHASE_PROOF_POST_DELETED} — 점검 L3, 대기 줄은 삭제 때·하루 배치가 자동 반려한다) */
    @Transactional
    public void approve(Reviewer reviewer, String idText) {
        PurchaseProofEntity proof = lockForReview(reviewer, idText);
        Instant now = now();
        PostEntity post = posts.findById(proof.getPostId()).orElse(null);
        if (post == null || post.getDeletedAt() != null || post.getDeleteScheduledAt() != null) {
            throw TrialException.proofPostDeleted();
        }
        proof.approve(reviewer.operatorId(), reviewer.memberId(), now);
        int bonus = settings.current().proofBonusPoints();
        long given = points.purchaseProofReward(proof.getUserId(), proof.getPostId(), bonus);
        notices.trial(proof.getUserId(), MemberNotificationType.PURCHASE_PROOF_APPROVED, post == null ? "" : post.getTitle(),
                given > 0 ? messages.get(new MessageRef("trial.notice.proofBonus", String.valueOf(given))) : null, "/posts/" + proof.getPostId());
        audit.record(reviewer.event(AuditActions.PURCHASE_PROOF_APPROVE).target("POST", Long.toString(proof.getPostId())).with("proofId", Long.toString(proof.getId()))
                .with("points", given));
    }

    @Transactional
    public void reject(Reviewer reviewer, String idText, String reasonRaw) {
        String reason = reasonRaw == null ? "" : reasonRaw.strip();
        if (reason.isEmpty() || reason.length() > 300) {
            throw ValidationException.of("reason", new MessageRef(reason.isEmpty() ? "trial.field.required" : "trial.text.length", "300"));
        }
        PurchaseProofEntity proof = lockForReview(reviewer, idText);
        proof.reject(reviewer.operatorId(), reviewer.memberId(), reason, now());
        PostEntity post = posts.findById(proof.getPostId()).orElse(null);
        notices.trial(proof.getUserId(), MemberNotificationType.PURCHASE_PROOF_REJECTED, post == null ? "" : post.getTitle(), reason, "/posts/" + proof.getPostId());
        audit.record(reviewer.event(AuditActions.PURCHASE_PROOF_REJECT).target("POST", Long.toString(proof.getPostId())).with("proofId", Long.toString(proof.getId()))
                .with("reason", reason));
    }

    private PurchaseProofEntity lockForReview(Reviewer reviewer, String idText) {
        PurchaseProofEntity proof = proofs.findWithLockById(TrialService.id(idText, TrialException::proofNotFound)).orElseThrow(TrialException::proofNotFound);
        authorize(reviewer, proof);
        if (!proof.pending()) {
            throw TrialException.proofClosed();
        }
        return proof;
    }

    /** 매니저는 그 게시판 것만·자기 글은 안 된다 */
    private void authorize(Reviewer reviewer, PurchaseProofEntity proof) {
        if (reviewer.memberId() == null) {
            return;
        }
        if (!isManager(reviewer.memberId(), proof.getBoardId())) {
            throw TrialException.proofForbidden();
        }
        if (reviewer.memberId().equals(proof.getUserId())) {
            throw TrialException.proofSelf();
        }
    }

    public boolean isManager(long memberId, long boardId) {
        return staff.findById(new BoardStaffId(boardId, memberId)).map(BoardStaffEntity::getRole).filter(BoardStaffEntity.MANAGER::equals).isPresent();
    }

    /** 사진 — 확인하는 사람만, 볼 때마다 감사 기록 */
    @Transactional
    public Photo photo(Reviewer reviewer, String idText) {
        PurchaseProofEntity proof = proofs.findById(TrialService.id(idText, TrialException::proofNotFound)).orElseThrow(TrialException::proofNotFound);
        authorize(reviewer, proof);
        if (proof.getContent() == null) {
            throw TrialException.proofPurged();
        }
        byte[] bytes = cipher.decryptBytes(TrialPii.PROOF, proof.getContent());
        audit.record(reviewer.event(AuditActions.PURCHASE_PROOF_VIEW).target("POST", Long.toString(proof.getPostId())).with("proofId", Long.toString(proof.getId())));
        return new Photo(bytes, proof.getContentType());
    }

    // ── 목록 ─────────────────────────────────────────────────────────────

    /** 구매 인증 목록 — 상태(PENDING 기본 · DONE = 승인·거절 · ALL) · 게시판(null 이면 전부), 대기는 오래된 차례 · 그 밖 최근 차례 200건 */
    @Transactional(readOnly = true)
    public List<TrialViews.Proof> list(String statusRaw, Long boardId) {
        String s = statusRaw == null || statusRaw.isBlank() ? "PENDING" : statusRaw.strip().toUpperCase(java.util.Locale.ROOT);
        BooleanExpression where = switch (s) {
            case "PENDING" -> PP.status.eq(PurchaseProofStatus.PENDING);
            case "DONE" -> PP.status.ne(PurchaseProofStatus.PENDING);
            case "ALL" -> null;
            default -> throw ValidationException.of("status", new MessageRef("trial.value.invalid"));
        };
        if (boardId != null) {
            where = where == null ? PP.boardId.eq(boardId) : where.and(PP.boardId.eq(boardId));
        }
        List<PurchaseProofEntity> rows = query.selectFrom(PP).where(where).orderBy("PENDING".equals(s) ? PP.createdAt.asc() : PP.createdAt.desc(), PP.id.desc())
                .limit(200).fetch();
        return views(rows);
    }

    /** 게시판 매니저의 구매 인증 목록(회원 앱 스텝 화면) — 매니저가 아니면 403 */
    @Transactional(readOnly = true)
    public List<TrialViews.Proof> listForManager(String slug, long memberId, String statusRaw) {
        return list(statusRaw, managedBoard(slug, memberId));
    }

    /** 그 게시판의 매니저면 게시판 ID, 아니면 403 {@code PURCHASE_PROOF_FORBIDDEN} */
    public long managedBoard(String slug, long memberId) {
        EffectiveBoard b = catalog.snapshot().board(slug).orElseThrow(TrialException::proofForbidden);
        if (!isManager(memberId, b.id())) {
            throw TrialException.proofForbidden();
        }
        return b.id();
    }

    public List<TrialViews.Proof> views(List<PurchaseProofEntity> rows) {
        Set<Long> users = new HashSet<>();
        rows.forEach(r -> users.add(r.getUserId()));
        Map<Long, TrialViews.Member> who = viewer.members(users);
        Map<Long, String> titles = new HashMap<>();
        posts.findAllById(rows.stream().map(PurchaseProofEntity::getPostId).toList()).forEach(p -> titles.put(p.getId(), p.getTitle()));
        // 확인한 사람 — 운영자 이름 · 게시판 매니저 닉네임(칭호 신청 reviewedBy 와 같은 규칙)
        Map<Long, String> operatorNames = viewer.operatorNames(rows.stream().map(PurchaseProofEntity::getReviewedBy).filter(java.util.Objects::nonNull).toList());
        Map<Long, String> managerNames = viewer.nicknames(rows.stream().map(PurchaseProofEntity::getReviewedByMember).filter(java.util.Objects::nonNull).toList());
        return rows.stream().map(r -> new TrialViews.Proof(Long.toString(r.getId()), Long.toString(r.getPostId()), titles.get(r.getPostId()), viewer.board(r.getBoardId()),
                who.get(r.getUserId()), r.getStatus().name(), r.getRejectReason(), r.getContent() == null, r.getCreatedAt(), r.getReviewedAt(),
                r.getReviewedBy() != null ? operatorNames.get(r.getReviewedBy()) : r.getReviewedByMember() != null ? managerNames.get(r.getReviewedByMember()) : null))
                .toList();
    }

    /** 파기(하루 배치) — 확인 뒤 보관 일수가 지난 사진 한 묶음(200건). 배치는 묶음이 꽉 차면 다시 부른다(M4-4 — 하루 상한 없이 끝까지) */
    @Transactional
    public int purgeDue(Instant before) {
        int n = 0;
        Instant now = now();
        for (PurchaseProofEntity p : proofs.findTop200ByStatusNotAndPurgedAtIsNullAndReviewedAtBefore(PurchaseProofStatus.PENDING, before)) {
            p.purge(now);
            n++;
        }
        return n;
    }

    // ── 자동 반려·파기(점검 L3·M4-4·D8) ─────────────────────────────────────

    /**
     * 글이 지워졌다(작성자 삭제 — 같은 트랜잭션) — 그 글의 대기 인증을 자동 반려하고 사진을 지운다(사유 = 번들 {@code trial.proof.autoReject.postDeleted}, 확인자 없음).
     * 승인·반려된 인증은 그대로(승인 칩·보너스는 글과 함께 사라지고 사진은 보관 일수 뒤 파기). 반려한 수.
     */
    @Transactional
    public int onPostDeleted(long postId) {
        Instant now = now();
        String reason = messages.get(new MessageRef("trial.proof.autoReject.postDeleted"));
        int n = 0;
        for (PurchaseProofEntity p : query.selectFrom(PP).where(PP.postId.eq(postId), PP.status.eq(PurchaseProofStatus.PENDING)).fetch()) {
            p.rejectBySystem(reason, now);
            p.purge(now);
            n++;
        }
        return n;
    }

    /** 하루 배치 — 운영자가 지웠거나(작성자 삭제는 그때 처리) 삭제 예약 시각이 지난 글의 대기 인증 한 묶음(200건)을 자동 반려·파기한다. 반려한 수 */
    @Transactional
    public int rejectForDeletedPosts(Instant now) {
        com.community.post.entity.QPostEntity post = com.community.post.entity.QPostEntity.postEntity;
        List<Long> postIds = query.select(PP.postId).from(PP).join(post).on(post.id.eq(PP.postId))
                .where(PP.status.eq(PurchaseProofStatus.PENDING), post.deletedAt.isNotNull().or(post.deleteScheduledAt.loe(now))).limit(200).fetch();
        int n = 0;
        for (Long postId : new java.util.LinkedHashSet<>(postIds)) {
            n += onPostDeleted(postId);
        }
        return n;
    }

    /** 회원 탈퇴 완료(같은 트랜잭션 — 점검 D8) — 대기 인증은 자동 반려, 그 회원의 모든 인증 사진을 지운다(줄·상태는 남는다). 지운 수 */
    @Transactional
    public int eraseForWithdrawal(long userId) {
        Instant now = now();
        String reason = messages.get(new MessageRef("trial.proof.autoReject.withdrawn"));
        int n = 0;
        for (PurchaseProofEntity p : query.selectFrom(PP).where(PP.userId.eq(userId), PP.purgedAt.isNull()).fetch()) {
            if (p.pending()) {
                p.rejectBySystem(reason, now);
            }
            p.purge(now);
            n++;
        }
        return n;
    }
}
