package com.community.nicknameimage.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.media.service.ImageStorage;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.nicknameimage.dto.response.AdminNicknameImagePage;
import com.community.nicknameimage.dto.response.AdminNicknameImageView;
import com.community.nicknameimage.dto.response.NicknameImageMemberView;
import com.community.nicknameimage.dto.response.NicknameImageReviewerView;
import com.community.nicknameimage.entity.NicknameImageRequestEntity;
import com.community.nicknameimage.entity.NicknameImageStatus;
import com.community.nicknameimage.exception.NicknameImageNotActiveException;
import com.community.nicknameimage.exception.NicknameImageNotFoundException;
import com.community.nicknameimage.exception.NicknameImageNotPendingException;
import com.community.nicknameimage.repository.master.AdminNicknameImageRepositoryQDSL;
import com.community.nicknameimage.repository.master.NicknameImageRequestRepository;
import com.community.permission.dto.AdminActor;
import com.community.user.repository.master.ProfileRepository;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 닉네임 이미지 심사(관리자 — 운영자 용어는 「닉네임 이미지 심사」, 회원 화면은 「닉네임 그림」) — 목록({@code nickname-image.read})과 승인·반려·해제({@code nickname-image.manage}, 컨트롤러가 선언). docs/features/member-social-contract.md 「닉네임 그림」.
 *
 * <ul>
 *   <li><b>목록</b>: {@code status}(기본 PENDING · APPROVED(지금 적용 중) · REJECTED · REVOKED) · {@code keyword}(닉네임 부분 일치) · {@code page}(<b>1부터</b> — 화면 계약. 다른 관리자 목록은 0부터다) ·
 *       {@code size}(기본 20, 1~{@value #MAX_SIZE}). 틀리면 400 {@code fields.status|page|size}. {@code pendingCount} 는 거르기와 상관없는 검토 대기 수.</li>
 *   <li><b>승인</b>: 대기 신청만(아니면 409 {@code NICKNAME_IMAGE_NOT_PENDING}) — 회원의 지금 그림이 되고 앞 그림은 교체(REMOVED, 파일은 커밋 뒤 삭제). 승인 때 자격을 다시 보지 않는다(신청할 때 봤다).</li>
 *   <li><b>반려</b>: 사유 1~{@value NicknameImageRules#REASON_MAX}자(400 {@code fields.reason}), 대기 신청만 — 반려 + 72시간 뒤 다시 신청. 파일은 회원의 다음 신청 때까지 둔다.</li>
 *   <li><b>해제</b>: 사유 1~200자, 적용 중인 그림만(아니면 409 {@code NICKNAME_IMAGE_NOT_ACTIVE}) — 해제 + 7일 뒤 다시 신청, 파일은 커밋 뒤 삭제.</li>
 *   <li>모두 회원 프로필 행 → 신청 행 차례로 잠근다(회원의 올리기·취소·해제와 겹쳐도 한쪽만 이긴다). 감사 {@code NICKNAME_IMAGE_APPROVE|REJECT|REVOKE}(대상 NICKNAME_IMAGE + 신청 ID),
 *       회원 알림 {@code NICKNAME_IMAGE_REVIEWED}(끌 수 없음, 사유와 함께) — 한 트랜잭션. 없는·형식이 틀린 ID 404 {@code NICKNAME_IMAGE_NOT_FOUND}.</li>
 * </ul>
 */
@Service
public class NicknameImageReviewService {

    private static final Logger log = LoggerFactory.getLogger(NicknameImageReviewService.class);

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;
    static final String TARGET_TYPE = "NICKNAME_IMAGE";

    private final NicknameImageRequestRepository requests;
    private final AdminNicknameImageRepositoryQDSL queries;
    private final ProfileRepository profiles;
    private final ImageStorage storage;
    private final AuditLogService audit;
    private final MemberNotificationPublisher memberNotices;

    public NicknameImageReviewService(NicknameImageRequestRepository requests, AdminNicknameImageRepositoryQDSL queries, ProfileRepository profiles, ImageStorage storage,
                                      AuditLogService audit, MemberNotificationPublisher memberNotices) {
        this.requests = requests;
        this.queries = queries;
        this.profiles = profiles;
        this.storage = storage;
        this.audit = audit;
        this.memberNotices = memberNotices;
    }

    @Transactional(readOnly = true)
    public AdminNicknameImagePage list(String statusText, String keyword, String pageText, String sizeText) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        NicknameImageStatus status = statusOf(statusText);
        if (status == null) {
            fields.put("status", new MessageRef("nicknameImage.admin.status.invalid"));
        }
        MessageRef range = new MessageRef("nicknameImage.admin.page.range", String.valueOf(MAX_SIZE));
        Integer page = integer(pageText, 1, 1, Integer.MAX_VALUE / MAX_SIZE);
        if (page == null) {
            fields.put("page", range);
        }
        Integer size = integer(sizeText, DEFAULT_SIZE, 1, MAX_SIZE);
        if (size == null) {
            fields.put("size", range);
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
        String q = keyword == null || keyword.isBlank() ? null : keyword.strip();
        long total = queries.count(status, q);
        List<AdminNicknameImageView> items = total == 0 || (long) (page - 1) * size >= total ? List.of()
                : queries.page(status, q, page - 1, size).stream().map(this::view).toList();
        return new AdminNicknameImagePage(items, total, page, size, status == NicknameImageStatus.PENDING && q == null ? total : queries.count(NicknameImageStatus.PENDING, null));
    }

    @Transactional
    public AdminNicknameImageView approve(AdminActor actor, String idText) {
        NicknameImageRequestEntity request = lock(idText);
        if (request.getStatus() != NicknameImageStatus.PENDING) {
            throw new NicknameImageNotPendingException();
        }
        Instant now = Instant.now();
        List<String> stale = new ArrayList<>();
        requests.findFirstByUserIdAndStatus(request.getUserId(), NicknameImageStatus.APPROVED).ifPresent(previous -> {
            previous.replacedByApproval(now);
            collect(stale, previous);
        });
        requests.flush(); // 적용 중인 그림은 회원당 하나(부분 유일 색인) — 앞 그림을 먼저 닫는다
        request.approve(actor.operatorId(), now);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NICKNAME_IMAGE_APPROVE, AuditResult.SUCCESS)
                .target(TARGET_TYPE, Long.toString(request.getId())).with("memberId", Long.toString(request.getUserId())));
        memberNotices.nicknameImageReviewed(request.getUserId(), request.getId(), "APPROVED", null);
        afterCommit(stale);
        log.info("닉네임 그림을 승인했다 requestId={} memberId={} by operatorId={}", request.getId(), request.getUserId(), actor.operatorId());
        return reread(request.getId());
    }

    @Transactional
    public AdminNicknameImageView reject(AdminActor actor, String idText, String rawReason) {
        String reason = reason(rawReason);
        NicknameImageRequestEntity request = lock(idText);
        if (request.getStatus() != NicknameImageStatus.PENDING) {
            throw new NicknameImageNotPendingException();
        }
        Instant now = Instant.now();
        request.reject(actor.operatorId(), reason, now.plus(Duration.ofHours(NicknameImageRules.REJECTED_REAPPLY_HOURS)), now);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NICKNAME_IMAGE_REJECT, AuditResult.SUCCESS)
                .target(TARGET_TYPE, Long.toString(request.getId())).with("memberId", Long.toString(request.getUserId())).with("reason", reason));
        memberNotices.nicknameImageReviewed(request.getUserId(), request.getId(), "REJECTED", reason);
        log.info("닉네임 그림을 반려했다 requestId={} memberId={} by operatorId={}", request.getId(), request.getUserId(), actor.operatorId());
        return reread(request.getId());
    }

    @Transactional
    public AdminNicknameImageView revoke(AdminActor actor, String idText, String rawReason) {
        String reason = reason(rawReason);
        NicknameImageRequestEntity request = lock(idText);
        if (request.getStatus() != NicknameImageStatus.APPROVED) {
            throw new NicknameImageNotActiveException();
        }
        Instant now = Instant.now();
        List<String> stale = new ArrayList<>();
        request.revoke(actor.operatorId(), reason, now.plus(NicknameImageRules.REVOKED_REAPPLY), now);
        collect(stale, request);
        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.NICKNAME_IMAGE_REVOKE, AuditResult.SUCCESS)
                .target(TARGET_TYPE, Long.toString(request.getId())).with("memberId", Long.toString(request.getUserId())).with("reason", reason));
        memberNotices.nicknameImageReviewed(request.getUserId(), request.getId(), "REVOKED", reason);
        afterCommit(stale);
        log.info("닉네임 그림을 해제했다 requestId={} memberId={} by operatorId={}", request.getId(), request.getUserId(), actor.operatorId());
        return reread(request.getId());
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    /** 회원 프로필 행 → 신청 행 차례로 잠근다 */
    private NicknameImageRequestEntity lock(String idText) {
        long id = parseId(idText);
        Long userId = queries.userIdOf(id); // 엔티티를 먼저 올리지 않는다 — 잠근 뒤 새로 읽은 상태로 판정한다
        if (userId == null) {
            throw NicknameImageNotFoundException.forReview();
        }
        profiles.findWithLockByUserId(userId);
        return requests.findWithLockById(id).orElseThrow(NicknameImageNotFoundException::forReview);
    }

    private AdminNicknameImageView reread(long id) {
        requests.flush();
        AdminNicknameImageRepositoryQDSL.Row row = queries.row(id);
        if (row == null) {
            throw NicknameImageNotFoundException.forReview();
        }
        return view(row);
    }

    private AdminNicknameImageView view(AdminNicknameImageRepositoryQDSL.Row row) {
        NicknameImageRequestEntity r = row.request();
        NicknameImageMemberView member = new NicknameImageMemberView(Long.toString(r.getUserId()), row.nickname(), row.gradeKey(),
                row.gradeLabel() == null ? row.gradeKey() : row.gradeLabel());
        NicknameImageReviewerView reviewer = r.getReviewedBy() == null ? null : new NicknameImageReviewerView(Long.toString(r.getReviewedBy()), row.reviewerName());
        return new AdminNicknameImageView(Long.toString(r.getId()), member, r.getStatus().name(), r.getImageKey() == null ? null : storage.publicUrl(r.getImageKey()),
                r.getContentType(), r.getBytes(), r.getWidth(), r.getHeight(), r.getFrames(), r.getCreatedAt(), r.getReviewedAt(), reviewer, r.getRejectReason(),
                r.getRevokedAt(), r.getRevokeReason());
    }

    /** 사유 — 앞뒤 공백을 자르고 1~200자. 틀리면 400 {@code fields.reason} */
    private static String reason(String raw) {
        String reason = raw == null ? "" : raw.strip();
        int length = reason.codePointCount(0, reason.length());
        if (length == 0 || length > NicknameImageRules.REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("nicknameImage.admin.reason.range", String.valueOf(NicknameImageRules.REASON_MAX)));
        }
        return reason;
    }

    private static NicknameImageStatus statusOf(String text) {
        if (text == null || text.isBlank()) {
            return NicknameImageStatus.PENDING;
        }
        return switch (text.strip()) {
            case "PENDING" -> NicknameImageStatus.PENDING;
            case "APPROVED" -> NicknameImageStatus.APPROVED;
            case "REJECTED" -> NicknameImageStatus.REJECTED;
            case "REVOKED" -> NicknameImageStatus.REVOKED;
            default -> null;
        };
    }

    /** 비었으면 기본값, 정수가 아니거나 범위 밖이면 null */
    private static Integer integer(String raw, int fallback, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            return value < min || value > max ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static long parseId(String text) {
        if (text == null || text.isEmpty() || text.length() > 19 || !text.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            throw NicknameImageNotFoundException.forReview();
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw NicknameImageNotFoundException.forReview();
        }
    }

    private static void collect(List<String> keys, NicknameImageRequestEntity request) {
        if (request.getImageKey() != null) {
            keys.add(request.getImageKey());
        }
        if (request.getStillKey() != null) {
            keys.add(request.getStillKey());
        }
        request.dropFiles();
    }

    private void afterCommit(List<String> keys) {
        if (keys.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String key : keys) {
                    try {
                        storage.delete(key);
                    } catch (RuntimeException e) {
                        log.warn("닉네임 그림 파일을 지우지 못했다 key={}", key, e);
                    }
                }
            }
        });
    }
}
