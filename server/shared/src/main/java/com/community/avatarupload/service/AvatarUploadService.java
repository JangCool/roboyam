package com.community.avatarupload.service;

import com.community.avatarupload.dto.response.AvatarUploadCurrentView;
import com.community.avatarupload.dto.response.AvatarUploadLimitsView;
import com.community.avatarupload.dto.response.AvatarUploadRequestView;
import com.community.avatarupload.dto.response.MyAvatarUploadView;
import com.community.avatarupload.entity.AvatarUploadStatus;
import com.community.avatarupload.exception.AvatarUploadBlockedException;
import com.community.avatarupload.exception.AvatarUploadNotFoundException;
import com.community.avatarupload.exception.AvatarUploadRateLimitedException;
import com.community.avatarupload.exception.AvatarUploadRequestNotFoundException;
import com.community.avatarupload.repository.master.AvatarUploadStore;
import com.community.avatarupload.repository.master.AvatarUploadStore.Row;
import com.community.media.service.ImageStorage;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.pointshop.entity.UploadTicketUse;
import com.community.pointshop.repository.master.PointShopMemberStore;
import com.community.pointshop.service.AvatarCatalog;
import com.community.pointshop.service.ProfileAvatarService;
import com.community.pointshop.service.UploadTicketService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 내 아바타 올리기(commerce-motion-avatar-contract 규칙 0·0-1·0-2, 「API 계약」 api) — 상태·신청·대기 신청 취소·내가 올린 아바타 지우기. 파일 검사·변환·저장소 쓰기는 api 가 하고(트랜잭션 밖),
 * 이 서비스는 그 결과로 DB 를 바꾼다(한 트랜잭션).
 *
 * <ul>
 *   <li><b>신청 조건</b>(규칙 0-1): 이메일 인증({@code 403 EMAIL_NOT_VERIFIED}) → 정지 아님({@code 403 ACCOUNT_RESTRICTED}) → 아바타 변경권(파일 처리 전 {@code 409 AVATAR_UPLOAD_TICKET_REQUIRED}
 *       — 쓸 수 있는 장이 없고 대기 신청에 예약된 장도 없을 때). 닉네임 이미지의 관리자 신청 조건(등급 등)은 보지 않는다. 그다음 파일 → 속도(10초·하루 10번 {@code 429}).</li>
 *   <li><b>신청</b>({@link #submit}): 프로필 행 잠금 → 대기 신청이 있으면 CANCELED + 그 예약된 장을 새 신청으로 넘긴다(한 장 더 들지 않는다), 없으면 한 장 예약(동시 신청에도 한 장 —
 *       {@code SKIP LOCKED}, 장이 없으면 409) → 반려 파일 정리 → 새 대기 신청 → 운영자 알림 {@code AVATAR_UPLOAD_PENDING}. 예전 파일 키를 돌려준다(부르는 쪽이 커밋 뒤 지운다).</li>
 *   <li><b>취소</b>({@link #cancel}): 대기 신청 → CANCELED, 예약된 장 반환. 없으면 {@code 404 AVATAR_UPLOAD_REQUEST_NOT_FOUND}.</li>
 *   <li><b>지우기</b>({@link #remove}): 승인된 그림 → REMOVED(변경권 반환 없음), 쓰는 중이었으면 마지막 정지 아바타로. 없으면 {@code 404 AVATAR_UPLOAD_NOT_FOUND}.</li>
 *   <li>취소·지우기도 이메일 인증·정지 아님이 필요하다(닉네임 이미지와 같다). 파일은 커밋 뒤 지운다.</li>
 * </ul>
 */
@Service
public class AvatarUploadService {

    private static final Logger log = LoggerFactory.getLogger(AvatarUploadService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AvatarUploadStore uploads;
    private final UploadTicketService tickets;
    private final PointShopMemberStore members;
    private final ProfileAvatarService profileAvatars;
    private final ImageStorage storage;
    private final NotificationPublisher operatorNotices;
    private final Clock clock;

    public AvatarUploadService(AvatarUploadStore uploads, UploadTicketService tickets, PointShopMemberStore members, ProfileAvatarService profileAvatars,
                               ImageStorage storage, NotificationPublisher operatorNotices, ObjectProvider<Clock> clocks) {
        this.uploads = uploads;
        this.tickets = tickets;
        this.members = members;
        this.profileAvatars = profileAvatars;
        this.storage = storage;
        this.operatorNotices = operatorNotices;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 새 신청의 파일(저장소에 이미 썼다) */
    public record NewUpload(long requestId, String imageKey, String stillKey, String contentType, int bytes, int width, int height, int frames) {
    }

    /** 내 상태 — {@code GET /api/me/avatar-upload} */
    @Transactional(readOnly = true)
    public MyAvatarUploadView status(long userId) {
        UploadTicketService.Info ticket = tickets.info(userId, UploadTicketUse.AVATAR_UPLOAD);
        Optional<Row> pending = uploads.byStatus(userId, AvatarUploadStatus.PENDING);
        boolean reserved = pending.isPresent() && tickets.reserved(UploadTicketUse.AVATAR_UPLOAD, pending.get().id());
        AvatarUploadRequestView request = uploads.latest(userId).filter(r -> r.status() == AvatarUploadStatus.PENDING || r.status() == AvatarUploadStatus.REJECTED)
                .map(r -> new AvatarUploadRequestView(Long.toString(r.id()), r.status().name(), url(r.imageKey()), url(r.stillKey()), r.animated(), r.createdAt(),
                        r.rejectReason()))
                .orElse(null);
        Row approved = uploads.byStatus(userId, AvatarUploadStatus.APPROVED).filter(Row::hasFiles).orElse(null);
        AvatarUploadCurrentView current = approved == null ? null
                : new AvatarUploadCurrentView(url(approved.imageKey()), url(approved.stillKey()), approved.animated(), approved.reviewedAt());
        boolean inUse = approved != null && AvatarCatalog.isUpload(members.profileImage(userId));
        return new MyAvatarUploadView(ticket.count(), ticket.price(), ticket.itemId(), reserved, request, current, inUse, limits());
    }

    public static AvatarUploadLimitsView limits() {
        return new AvatarUploadLimitsView(AvatarUploadRules.MAX_BYTES, AvatarUploadRules.MIN_SIZE, AvatarUploadRules.MAX_SIZE, AvatarUploadRules.RATIO_TOLERANCE_PERCENT,
                AvatarUploadRules.MAX_GIF_FRAMES, AvatarUploadRules.FORMATS);
    }

    /** 올리기·취소·지우기의 앞 관문 — 회원(401) → 이메일 인증 전 403 → 정지 403 */
    @Transactional(readOnly = true)
    public void guard(long userId) {
        PointShopMemberStore.MemberState state = members.state(userId).filter(s -> !s.gone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        if (!state.emailVerified()) {
            throw new AvatarUploadBlockedException(AvatarUploadBlockedException.EMAIL_NOT_VERIFIED);
        }
        if (state.suspended()) {
            throw new AvatarUploadBlockedException(AvatarUploadBlockedException.ACCOUNT_RESTRICTED);
        }
    }

    /** 파일을 처리하기 전 — 쓸 수 있는 변경권이 있거나 대기 신청에 예약된 장이 있어야 한다(대체는 그 장을 넘겨받는다). 없으면 409 */
    @Transactional(readOnly = true)
    public void requireTicket(long userId) {
        if (tickets.count(userId, UploadTicketUse.AVATAR_UPLOAD) > 0) {
            return;
        }
        Optional<Row> pending = uploads.byStatus(userId, AvatarUploadStatus.PENDING);
        if (pending.isPresent() && tickets.reserved(UploadTicketUse.AVATAR_UPLOAD, pending.get().id())) {
            return;
        }
        throw tickets.required(userId, UploadTicketUse.AVATAR_UPLOAD);
    }

    /** 간격 10초 · 하루 10번(한국 시간 0시부터) — 저장된 신청(대체·취소된 것 포함)으로 센다 */
    @Transactional(readOnly = true)
    public void rateLimit(long userId) {
        Instant now = clock.instant();
        Optional<Row> last = uploads.latest(userId);
        if (last.isPresent()) {
            Duration since = Duration.between(last.get().createdAt(), now);
            if (since.compareTo(AvatarUploadRules.UPLOAD_INTERVAL) < 0) {
                throw new AvatarUploadRateLimitedException(AvatarUploadRules.UPLOAD_INTERVAL.minus(since).toSeconds() + 1);
            }
        }
        Instant midnight = now.atZone(KST).toLocalDate().atStartOfDay(KST).toInstant();
        if (uploads.countSince(userId, midnight) >= AvatarUploadRules.DAILY_UPLOADS) {
            Instant next = now.atZone(KST).toLocalDate().plusDays(1).atStartOfDay(KST).toInstant();
            throw new AvatarUploadRateLimitedException(Duration.between(now, next).toSeconds());
        }
    }

    /**
     * 새 대기 신청 — 파일은 이미 저장소에 있다. 실패하면(변경권 없음 등) 부르는 쪽이 새 파일을 지운다.
     *
     * @return 커밋 뒤 지울 예전 파일 키(대체된 대기 신청·반려 신청의 파일)
     */
    @Transactional
    public List<String> submit(long userId, NewUpload upload) {
        Instant now = now();
        members.lockAvatar(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        List<String> stale = new ArrayList<>();
        Optional<Row> pending = uploads.byStatus(userId, AvatarUploadStatus.PENDING);
        boolean carried = false;
        if (pending.isPresent()) {
            uploads.cancel(pending.get().id(), now); // 새 신청이 대체한다 — 대기 신청은 회원당 하나(부분 유일 색인)라 먼저 닫는다
            collect(stale, pending.get());
            carried = tickets.transfer(UploadTicketUse.AVATAR_UPLOAD, pending.get().id(), upload.requestId());
        }
        if (!carried) {
            tickets.reserve(userId, UploadTicketUse.AVATAR_UPLOAD, upload.requestId()); // 없으면 409 — 트랜잭션째 되돌린다(대기 신청도 그대로)
        }
        for (Row rejected : uploads.allByStatus(userId, AvatarUploadStatus.REJECTED)) {
            collect(stale, rejected); // 반려된 파일은 다음 신청 때까지 두었다
        }
        uploads.insertPending(upload.requestId(), userId, upload.imageKey(), upload.stillKey(), upload.contentType(), upload.bytes(), upload.width(), upload.height(),
                upload.frames(), now);
        operatorNotices.publish(AdminNotificationEvent.AVATAR_UPLOAD_PENDING, NotificationContext.empty());
        log.info("아바타 올리기를 신청했다 requestId={} userId={} frames={} bytes={} carried={}", upload.requestId(), userId, upload.frames(), upload.bytes(), carried);
        return stale;
    }

    /** 대기 신청 취소 — 예약된 변경권을 돌려준다. 파일은 커밋 뒤 지운다 */
    @Transactional
    public void cancel(long userId) {
        guard(userId);
        members.lockAvatar(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        Row pending = uploads.byStatus(userId, AvatarUploadStatus.PENDING).orElseThrow(AvatarUploadRequestNotFoundException::new);
        uploads.cancel(pending.id(), now());
        tickets.release(UploadTicketUse.AVATAR_UPLOAD, pending.id());
        List<String> keys = new ArrayList<>();
        collect(keys, pending);
        afterCommit(keys);
    }

    /** 내가 올린 아바타 지우기 — 변경권은 돌려주지 않는다. 쓰는 중이었으면 마지막 정지 아바타로 */
    @Transactional
    public void remove(long userId) {
        guard(userId);
        PointShopMemberStore.AvatarState avatar = members.lockAvatar(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        Row current = uploads.byStatus(userId, AvatarUploadStatus.APPROVED).orElseThrow(AvatarUploadNotFoundException::new);
        uploads.remove(current.id(), now());
        if (AvatarCatalog.isUpload(avatar.profileImage())) {
            profileAvatars.fallBackToStill(userId);
        }
        List<String> keys = new ArrayList<>();
        collect(keys, current);
        afterCommit(keys);
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    private String url(String key) {
        return key == null ? null : storage.publicUrl(key);
    }

    /** 이 신청의 파일 키를 모으고 신청에서 지운다(파일은 커밋 뒤 지운다) */
    void collect(List<String> keys, Row row) {
        if (row.imageKey() != null) {
            keys.add(row.imageKey());
        }
        if (row.stillKey() != null) {
            keys.add(row.stillKey());
        }
        if (row.hasFiles() || row.stillKey() != null) {
            uploads.dropFiles(row.id());
        }
    }

    private void afterCommit(List<String> keys) {
        if (keys.isEmpty() || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keys.forEach(AvatarUploadService.this::deleteQuietly);
            }
        });
    }

    /** 저장소 파일을 지운다 — 실패해도 로그만 */
    public void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("아바타 올리기 파일을 지우지 못했다 key={}", key, e);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
