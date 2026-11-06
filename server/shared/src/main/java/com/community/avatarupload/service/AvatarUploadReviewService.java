package com.community.avatarupload.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.avatarupload.dto.response.AdminAvatarUploadPage;
import com.community.avatarupload.dto.response.AdminAvatarUploadView;
import com.community.avatarupload.dto.response.AvatarUploadMemberView;
import com.community.avatarupload.dto.response.AvatarUploadReviewerView;
import com.community.avatarupload.entity.AvatarUploadStatus;
import com.community.avatarupload.exception.AvatarUploadNotActiveException;
import com.community.avatarupload.exception.AvatarUploadNotFoundException;
import com.community.avatarupload.exception.AvatarUploadNotPendingException;
import com.community.avatarupload.repository.master.AvatarUploadStore;
import com.community.avatarupload.repository.master.AvatarUploadStore.AdminRow;
import com.community.avatarupload.repository.master.AvatarUploadStore.Row;
import com.community.media.service.ImageStorage;
import com.community.membernotification.service.MemberNotificationPublisher;
import com.community.pointshop.entity.UploadTicketUse;
import com.community.pointshop.repository.master.PointShopMemberStore;
import com.community.pointshop.service.AvatarCatalog;
import com.community.pointshop.service.AvatarLooks;
import com.community.pointshop.service.ProfileAvatarService;
import com.community.pointshop.service.UploadTicketService;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.message.MessageRef;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 아바타 올리기 심사(관리자 — commerce-motion-avatar-contract 「API 계약」 admin, 권한은 닉네임 이미지 심사와 같은 {@code nickname-image.read}·{@code .manage} — 컨트롤러가 선언).
 *
 * <ul>
 *   <li><b>목록</b>: {@code status}(기본 PENDING · APPROVED · REJECTED · REVOKED — 「승인」은 승인된 뒤 바뀐·지운 그림도 {@code current: false} 로) · {@code keyword}(닉네임 부분 일치) ·
 *       {@code page}(1부터) · {@code size}(기본 20, 1~{@value #MAX_SIZE}). 틀리면 400 {@code fields.status|page|size}. {@code pendingCount} 는 거르기와 상관없는 심사 대기 수.</li>
 *   <li><b>승인</b>(대기만, 아니면 {@code 409 AVATAR_UPLOAD_NOT_PENDING}): 예약된 변경권 <b>사용</b> → 회원의 「내가 올린 아바타」가 되고(앞 그림은 REMOVED, 파일은 커밋 뒤 삭제)
 *       <b>바로 프로필 아바타</b>(규칙 0-2 — 나중에 고른 쪽이 남는다, 움직이는 GIF 면 움직이는 닉네임 이미지는 쉰다). 승인 때 조건을 다시 보지 않는다.</li>
 *   <li><b>반려</b>(대기만, 사유 1~200자 {@code 400 fields.reason}): 예약된 변경권 <b>반환</b>. 파일은 회원의 다음 신청 때까지 둔다.</li>
 *   <li><b>해제</b>(지금 그림만, 아니면 {@code 409 AVATAR_UPLOAD_NOT_ACTIVE}, 사유 1~200자): 변경권 반환 없음 · 쓰는 중이었으면 마지막 정지 아바타로 · 파일은 커밋 뒤 삭제.</li>
 *   <li>모두 회원 프로필 행 → 신청 행 차례로 잠근다(회원의 신청·취소·지우기·아바타 고르기와 겹쳐도 한쪽만 이긴다). 감사 {@code AVATAR_UPLOAD_APPROVE|REJECT|REVOKE}(일반 운영, 대상 AVATAR_UPLOAD +
 *       신청 ID), 회원 알림 {@code AVATAR_UPLOAD_REVIEWED}(끌 수 없음, 사유와 함께) — 한 트랜잭션. 없는·형식이 틀린 ID 404 {@code AVATAR_UPLOAD_NOT_FOUND}.</li>
 * </ul>
 */
@Service
public class AvatarUploadReviewService {

    private static final Logger log = LoggerFactory.getLogger(AvatarUploadReviewService.class);

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;
    public static final String TARGET_TYPE = "AVATAR_UPLOAD";

    private final AvatarUploadStore uploads;
    private final UploadTicketService tickets;
    private final PointShopMemberStore members;
    private final ProfileAvatarService profileAvatars;
    private final AvatarLooks looks;
    private final ImageStorage storage;
    private final AuditLogService audit;
    private final MemberNotificationPublisher memberNotices;
    private final Clock clock;

    public AvatarUploadReviewService(AvatarUploadStore uploads, UploadTicketService tickets, PointShopMemberStore members, ProfileAvatarService profileAvatars,
                                     AvatarLooks looks, ImageStorage storage, AuditLogService audit, MemberNotificationPublisher memberNotices,
                                     ObjectProvider<Clock> clocks) {
        this.uploads = uploads;
        this.tickets = tickets;
        this.members = members;
        this.profileAvatars = profileAvatars;
        this.looks = looks;
        this.storage = storage;
        this.audit = audit;
        this.memberNotices = memberNotices;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    @Transactional(readOnly = true)
    public AdminAvatarUploadPage list(String statusText, String keyword, String pageText, String sizeText) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        AvatarUploadStatus status = statusOf(statusText);
        if (status == null) {
            fields.put("status", new MessageRef("avatarUpload.admin.status.invalid"));
        }
        MessageRef range = new MessageRef("avatarUpload.admin.page.range", String.valueOf(MAX_SIZE));
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
        long total = uploads.adminCount(status, q);
        List<AdminRow> rows = total == 0 || (long) (page - 1) * size >= total ? List.of() : uploads.adminPage(status, q, (page - 1) * size, size);
        List<AdminAvatarUploadView> items = views(rows);
        long pending = status == AvatarUploadStatus.PENDING && q == null ? total : uploads.adminCount(AvatarUploadStatus.PENDING, null);
        return new AdminAvatarUploadPage(items, total, page, size, pending);
    }

    @Transactional
    public AdminAvatarUploadView approve(long operatorId, String ip, String idText) {
        Row request = lock(idText);
        if (request.status() != AvatarUploadStatus.PENDING) {
            throw new AvatarUploadNotPendingException();
        }
        Instant now = now();
        List<String> stale = new ArrayList<>();
        uploads.byStatus(request.userId(), AvatarUploadStatus.APPROVED).ifPresent(previous -> {
            uploads.remove(previous.id(), now); // 「내가 올린 아바타」는 한 장 — 앞 그림은 바뀐다(승인은 회원당 하나 — 부분 유일 색인이라 먼저 닫는다)
            collect(stale, previous);
        });
        if (!uploads.approve(request.id(), operatorId, now)) {
            throw new AvatarUploadNotPendingException();
        }
        boolean used = tickets.consume(UploadTicketUse.AVATAR_UPLOAD, request.id());
        if (!used) {
            log.warn("승인한 아바타 올리기 신청에 예약된 변경권이 없다 requestId={}", request.id());
        }
        Row approved = uploads.find(request.id()).orElseThrow(AvatarUploadNotFoundException::new);
        profileAvatars.applyUpload(request.userId(), approved, now); // 바로 프로필 아바타 — 움직이는 GIF 면 움직이는 닉네임 이미지는 쉰다(저장하지 않는다)
        audit.record(AuditEvent.byOperator(operatorId, ip, com.community.audit.dto.AuditActions.AVATAR_UPLOAD_APPROVE, AuditResult.SUCCESS)
                .target(TARGET_TYPE, Long.toString(request.id())).with("memberId", Long.toString(request.userId())).with("animated", request.animated())
                .with("ticketUsed", used));
        memberNotices.avatarUploadReviewed(request.userId(), request.id(), "APPROVED", null);
        afterCommit(stale);
        log.info("아바타 올리기를 승인했다 requestId={} memberId={} by operatorId={}", request.id(), request.userId(), operatorId);
        return reread(request.id());
    }

    @Transactional
    public AdminAvatarUploadView reject(long operatorId, String ip, String idText, String rawReason) {
        String reason = reason(rawReason);
        Row request = lock(idText);
        if (request.status() != AvatarUploadStatus.PENDING) {
            throw new AvatarUploadNotPendingException();
        }
        if (!uploads.reject(request.id(), operatorId, reason, now())) {
            throw new AvatarUploadNotPendingException();
        }
        boolean returned = tickets.release(UploadTicketUse.AVATAR_UPLOAD, request.id());
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.AVATAR_UPLOAD_REJECT, AuditResult.SUCCESS).target(TARGET_TYPE, Long.toString(request.id()))
                .with("memberId", Long.toString(request.userId())).with("reason", reason).with("ticketReturned", returned));
        memberNotices.avatarUploadReviewed(request.userId(), request.id(), "REJECTED", reason);
        log.info("아바타 올리기를 반려했다 requestId={} memberId={} by operatorId={}", request.id(), request.userId(), operatorId);
        return reread(request.id());
    }

    @Transactional
    public AdminAvatarUploadView revoke(long operatorId, String ip, String idText, String rawReason) {
        String reason = reason(rawReason);
        Row request = lock(idText);
        if (request.status() != AvatarUploadStatus.APPROVED) {
            throw new AvatarUploadNotActiveException();
        }
        if (!uploads.revoke(request.id(), operatorId, reason, now())) {
            throw new AvatarUploadNotActiveException();
        }
        boolean wasInUse = AvatarCatalog.isUpload(members.profileImage(request.userId()));
        if (wasInUse) {
            profileAvatars.fallBackToStill(request.userId()); // 쓰는 중이었으면 마지막 정지 아바타로(변경권 반환 없음)
        }
        List<String> stale = new ArrayList<>();
        collect(stale, request);
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.AVATAR_UPLOAD_REVOKE, AuditResult.SUCCESS).target(TARGET_TYPE, Long.toString(request.id()))
                .with("memberId", Long.toString(request.userId())).with("reason", reason).with("wasInUse", wasInUse));
        memberNotices.avatarUploadReviewed(request.userId(), request.id(), "REVOKED", reason);
        afterCommit(stale);
        log.info("아바타 올리기를 해제했다 requestId={} memberId={} by operatorId={}", request.id(), request.userId(), operatorId);
        return reread(request.id());
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    /** 회원 프로필 행 → 신청 행 차례로 잠근다(잠근 뒤 새로 읽은 상태로 판정한다) */
    private Row lock(String idText) {
        long id = parseId(idText);
        long userId = uploads.userIdOf(id).orElseThrow(AvatarUploadNotFoundException::new);
        members.lockAvatar(userId);
        return uploads.findForUpdate(id).orElseThrow(AvatarUploadNotFoundException::new);
    }

    private AdminAvatarUploadView reread(long id) {
        return views(List.of(uploads.adminRow(id).orElseThrow(AvatarUploadNotFoundException::new))).get(0);
    }

    private List<AdminAvatarUploadView> views(List<AdminRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> images = new HashMap<>();
        rows.forEach(row -> images.put(row.request().userId(), row.profileImage()));
        Map<Long, AvatarLooks.Look> lookOf = looks.of(images);
        Map<Long, com.community.pointshop.repository.master.UserItemStore.UserItemRow> ticketOf = tickets.ticketsOf(UploadTicketUse.AVATAR_UPLOAD,
                rows.stream().map(row -> row.request().id()).toList());
        return rows.stream().map(row -> view(row, lookOf.get(row.request().userId()), ticketOf.containsKey(row.request().id()))).toList();
    }

    private AdminAvatarUploadView view(AdminRow row, AvatarLooks.Look look, boolean ticketLinked) {
        Row r = row.request();
        AvatarUploadMemberView member = new AvatarUploadMemberView(Long.toString(r.userId()), row.nickname(), row.gradeKey(),
                row.gradeLabel() == null ? row.gradeKey() : row.gradeLabel());
        AvatarUploadReviewerView reviewer = r.reviewedBy() == null ? null : new AvatarUploadReviewerView(Long.toString(r.reviewedBy()), row.reviewerName());
        boolean current = r.status() == AvatarUploadStatus.APPROVED;
        boolean inUse = current && AvatarCatalog.isUpload(row.profileImage());
        boolean pauses = r.status() == AvatarUploadStatus.PENDING && r.animated() && row.nicknameMotion() && (look == null || !look.animated());
        String status = r.status() == AvatarUploadStatus.REMOVED ? AvatarUploadStatus.APPROVED.name() : r.status().name();
        return new AdminAvatarUploadView(Long.toString(r.id()), member, status, url(r.imageKey()), url(r.stillKey()), r.contentType(), r.bytes(), r.width(), r.height(),
                r.frames(), ticket(r.status(), ticketLinked), inUse, current, pauses, r.createdAt(), r.reviewedAt(), reviewer, r.rejectReason(), r.revokedAt(),
                r.revokeReason());
    }

    /** 신청에 든 변경권 — 대기 = 예약, 승인(뒤 바뀜·해제 포함) = 사용, 반려·취소 = 반환 */
    static String ticket(AvatarUploadStatus status, boolean linked) {
        return switch (status) {
            case PENDING -> "RESERVED";
            case APPROVED, REMOVED, REVOKED -> linked ? "USED" : "RETURNED";
            case REJECTED, CANCELED -> "RETURNED";
        };
    }

    private String url(String key) {
        return key == null ? null : storage.publicUrl(key);
    }

    /** 사유 — 앞뒤 공백을 자르고 1~200자. 틀리면 400 {@code fields.reason} */
    private static String reason(String raw) {
        String reason = raw == null ? "" : raw.strip();
        int length = reason.codePointCount(0, reason.length());
        if (length == 0 || length > AvatarUploadRules.REASON_MAX) {
            throw ValidationException.of("reason", new MessageRef("avatarUpload.admin.reason.range", String.valueOf(AvatarUploadRules.REASON_MAX)));
        }
        return reason;
    }

    private static AvatarUploadStatus statusOf(String text) {
        if (text == null || text.isBlank()) {
            return AvatarUploadStatus.PENDING;
        }
        return switch (text.strip()) {
            case "PENDING" -> AvatarUploadStatus.PENDING;
            case "APPROVED" -> AvatarUploadStatus.APPROVED;
            case "REJECTED" -> AvatarUploadStatus.REJECTED;
            case "REVOKED" -> AvatarUploadStatus.REVOKED;
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
            throw new AvatarUploadNotFoundException();
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new AvatarUploadNotFoundException();
        }
    }

    private void collect(List<String> keys, Row row) {
        if (row.imageKey() != null) {
            keys.add(row.imageKey());
        }
        if (row.stillKey() != null) {
            keys.add(row.stillKey());
        }
        uploads.dropFiles(row.id());
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
                        log.warn("아바타 올리기 파일을 지우지 못했다 key={}", key, e);
                    }
                }
            }
        });
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
