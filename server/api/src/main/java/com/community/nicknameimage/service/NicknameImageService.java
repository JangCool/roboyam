package com.community.nicknameimage.service;

import com.community.board.dto.BoardActor;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.media.service.ImageFormat;
import com.community.media.service.ImageStorage;
import com.community.member.repository.master.MemberActivityRepository;
import com.community.nicknameimage.dto.response.MyNicknameImageView;
import com.community.nicknameimage.dto.response.NicknameImageCurrentView;
import com.community.nicknameimage.dto.response.NicknameImageGradeView;
import com.community.nicknameimage.dto.response.NicknameImageLimitsView;
import com.community.nicknameimage.dto.response.NicknameImageRequestView;
import com.community.nicknameimage.dto.response.NicknameImageRequirementItemView;
import com.community.nicknameimage.dto.response.NicknameImageRequirementsView;
import com.community.nicknameimage.entity.NicknameImageRequestEntity;
import com.community.nicknameimage.entity.NicknameImageRequirementMode;
import com.community.nicknameimage.entity.NicknameImageSettingEntity;
import com.community.nicknameimage.entity.NicknameImageStatus;
import com.community.nicknameimage.exception.NicknameImageBlockedException;
import com.community.nicknameimage.exception.NicknameImageNotFoundException;
import com.community.nicknameimage.exception.NicknameImageRateLimitedException;
import com.community.nicknameimage.exception.NicknameImageReapplyTooSoonException;
import com.community.nicknameimage.exception.NicknameImageRequestNotFoundException;
import com.community.nicknameimage.exception.NicknameImageRequirementsNotMetException;
import com.community.nicknameimage.repository.master.NicknameImageRequestRepository;
import com.community.nicknameimage.repository.master.NicknameImageSettingRepository;
import com.community.notification.dto.NotificationContext;
import com.community.notification.entity.AdminNotificationEvent;
import com.community.notification.service.NotificationPublisher;
import com.community.point.entity.PointBalanceEntity;
import com.community.point.repository.master.PointBalanceRepository;
import com.community.user.entity.UserEntity;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 내 닉네임 그림 — 신청(올리기)·대기 신청 취소·적용 중인 그림 해제와 내 상태. docs/features/member-social-contract.md 「닉네임 그림」·user-web {@code useNicknameImageApi.ts}.
 *
 * <ul>
 *   <li><b>자격</b>: 관리자 설정({@code nickname_image_settings}, 요청마다 읽는다)의 조건을 <b>모두</b> 채우고 정지·영구 정지({@code users.status})가 아니어야 한다.
 *       {@code GRADE} 방식 = 이메일 인증 + 최소 등급 이상. {@code CUSTOM} 방식 = 이메일 인증(끌 수 있음) · 최소 등급(없음 가능) · 가입 후 일수 · 보이는 글+댓글 수(등급 즉시 승급과 같은 셈,
 *       필요한 수까지만 센다) · 포인트 잔액 — 0 인 칸은 조건이 아니다. 고른 등급이 지워졌으면 등급 조건은 채울 수 없다. 내 등급 서열은 {@link BoardActorResolver}(모르는 등급은 0).
 *       {@code ineligibleReason} 은 올리기가 낼 오류와 같은 차례다: 이메일 인증 조건 미달 → {@code REQUIREMENTS_NOT_MET}, 정지 → {@code SUSPENDED}, 나머지 조건 미달 →
 *       {@code REQUIREMENTS_NOT_MET}. 이미 적용 중인 그림은 조건이 바뀌거나 등급이 내려가도 그대로 둔다.</li>
 *   <li><b>올리기 순서</b>: 이메일 인증 전(설정이 요구할 때만) {@code 403 EMAIL_NOT_VERIFIED} → 정지 {@code 403 ACCOUNT_RESTRICTED} → 조건 {@code 403 NICKNAME_IMAGE_REQUIREMENTS_NOT_MET} →
 *       재신청 제한(반려 72시간·운영 해제 7일) {@code 409 NICKNAME_IMAGE_REAPPLY_TOO_SOON} → 파일(빈 파일·200KB·형식 {@code 400 fields.file}) → 속도(간격 10초·하루 10번,
 *       한국 시간 0시 초기화 — 저장된 신청 수로 센다) {@code 429 RATE_LIMITED} → 처리(비율·크기·프레임 {@code 400 fields.file}, 트랜잭션 밖) → 저장소 쓰기 → 트랜잭션(프로필 행 잠금 →
 *       앞 대기 신청 대체 · 반려 파일 정리 → 새 대기 신청 → 운영자 알림 {@code NICKNAME_IMAGE_PENDING}) → 커밋 뒤 예전 파일 삭제(실패하면 새 파일 삭제).</li>
 *   <li><b>파일 주소</b>: 저장소 키 {@code nickname-image/{무작위 32자}/image.{확장자}}(+ 움직이는 GIF 의 {@code still.png}). 검토 전 그림도 기존 미디어처럼 {@code /media/**} 공개 주소다 —
 *       주소를 짐작할 수 없고 한 번 쓴 파일은 고치지 않는다(글 이미지와 같은 판단).</li>
 *   <li>취소·해제도 정지 아님과 이메일 인증(설정이 요구할 때만)이 필요하다(community 규칙). 등급 등 나머지 조건은 보지 않는다. 본인 취소·해제에는 재신청 제한·알림이 없다.</li>
 * </ul>
 */
@Service
public class NicknameImageService {

    private static final Logger log = LoggerFactory.getLogger(NicknameImageService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final NicknameImageRequestRepository requests;
    private final NicknameImageSettingRepository settings;
    private final NicknameImageProcessor processor;
    private final ImageStorage storage;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final BoardActorResolver actors;
    private final BoardCatalog catalog;
    private final MemberActivityRepository activities;
    private final PointBalanceRepository balances;
    private final NotificationPublisher publisher;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public NicknameImageService(NicknameImageRequestRepository requests, NicknameImageSettingRepository settings, NicknameImageProcessor processor,
                                ImageStorage storage, UserRepository users, ProfileRepository profiles, BoardActorResolver actors, BoardCatalog catalog,
                                MemberActivityRepository activities, PointBalanceRepository balances, NotificationPublisher publisher, SnowflakeIdGenerator ids,
                                PlatformTransactionManager txManager) {
        this.requests = requests;
        this.settings = settings;
        this.processor = processor;
        this.storage = storage;
        this.users = users;
        this.profiles = profiles;
        this.actors = actors;
        this.catalog = catalog;
        this.activities = activities;
        this.balances = balances;
        this.publisher = publisher;
        this.ids = ids;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    /** 지금 적용되는 신청 조건(설정 행의 사본) — 행이 없으면 기본값(GRADE · 베테랑 · 이메일 인증 필요) */
    private record Settings(NicknameImageRequirementMode mode, String gradeKey, int minDays, int minActivity, int minPoints, boolean requireEmailVerified) {

        static final Settings DEFAULTS = new Settings(NicknameImageRequirementMode.GRADE, NicknameImageRules.DEFAULT_GRADE_KEY, 0, 0, 0, true);

        static Settings of(NicknameImageSettingEntity row) {
            return new Settings(row.getMode(), row.getGradeKey(), row.getMinDays(), row.getMinActivity(), row.getMinPoints(), row.isRequireEmailVerified());
        }

        boolean custom() {
            return mode == NicknameImageRequirementMode.CUSTOM;
        }
    }

    /** 올리기·취소·해제의 앞 관문 — 회원·역할 판정(정지)·설정 */
    private record Gate(UserEntity user, BoardActor actor, BoardCatalogSnapshot snapshot, Settings settings) {

        boolean emailBlocked() {
            return settings.requireEmailVerified() && !user.isEmailVerified();
        }
    }

    /** 신청 자격 — 관문 + 조건 판정 */
    private record Eligibility(Gate gate, NicknameImageGradeView grade, NicknameImageRequirementsView requirements) {

        boolean allMet() {
            return requirements.items().stream().allMatch(NicknameImageRequirementItemView::met);
        }

        /** 올리기가 낼 오류와 같은 차례 — 이메일 인증 조건 → 정지 → 나머지 조건 */
        String ineligibleReason() {
            if (gate.emailBlocked()) {
                return "REQUIREMENTS_NOT_MET";
            }
            if (gate.actor().restricted()) {
                return "SUSPENDED";
            }
            return allMet() ? null : "REQUIREMENTS_NOT_MET";
        }
    }

    public MyNicknameImageView status(long userId) {
        return readTx.execute(status -> view(userId, Instant.now()));
    }

    public MyNicknameImageView apply(long userId, MultipartFile file) {
        Instant start = Instant.now();
        Eligibility e = readTx.execute(status -> eligibility(gate(userId), start));
        guard(e.gate());
        if (!e.allMet()) {
            throw new NicknameImageRequirementsNotMetException();
        }
        Optional<Instant> reapply = readTx.execute(status -> reapplyAt(userId, start));
        if (reapply.isPresent()) {
            throw new NicknameImageReapplyTooSoonException(Duration.between(start, reapply.get()).toSeconds() + 1);
        }
        byte[] data;
        try {
            data = file == null ? null : file.getBytes();
        } catch (IOException ex) {
            throw ValidationException.of("file", new MessageRef("nicknameImage.file.corrupt"));
        }
        ImageFormat format = processor.precheck(data);
        readTx.executeWithoutResult(status -> rateLimit(userId, start));
        NicknameImageProcessor.Processed processed = processor.process(data, format);

        String folder = "nickname-image/" + token();
        String imageKey = folder + "/image." + processed.format().extension();
        String stillKey = processed.still() == null ? null : folder + "/still.png";
        storage.put(imageKey, processed.data());
        List<String> stale;
        try {
            if (stillKey != null) {
                storage.put(stillKey, processed.still());
            }
            long requestId = ids.nextId();
            stale = tx.execute(status -> {
                Instant now = Instant.now();
                profiles.findWithLockByUserId(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
                List<String> old = new ArrayList<>();
                requests.findFirstByUserIdAndStatus(userId, NicknameImageStatus.PENDING).ifPresent(pending -> {
                    pending.cancel(now); // 새 신청이 대체한다
                    collect(old, pending);
                });
                for (NicknameImageRequestEntity rejected : requests.findByUserIdAndStatus(userId, NicknameImageStatus.REJECTED)) {
                    collect(old, rejected); // 반려된 파일은 다음 신청 때까지 두었다
                }
                requests.flush(); // 대기 신청은 회원당 하나(부분 유일 색인) — 앞 신청을 먼저 닫는다
                requests.persist(NicknameImageRequestEntity.pending(requestId, userId, imageKey, stillKey, processed.format().contentType(), data.length,
                        processed.width(), processed.height(), processed.frames(), now));
                publisher.publish(AdminNotificationEvent.NICKNAME_IMAGE_PENDING, NotificationContext.empty());
                return old;
            });
            log.info("닉네임 그림을 신청했다 requestId={} userId={} format={} bytes={}", requestId, userId, processed.format(), data.length);
        } catch (RuntimeException ex) {
            deleteQuietly(imageKey);
            deleteQuietly(stillKey);
            throw ex;
        }
        if (stale != null) {
            stale.forEach(this::deleteQuietly);
        }
        return status(userId);
    }

    /** 대기 신청 취소 — 올린 파일은 커밋 뒤 지운다. 없으면 404 {@code NICKNAME_IMAGE_REQUEST_NOT_FOUND} */
    public void cancelRequest(long userId) {
        guard(readTx.execute(status -> gate(userId)));
        tx.executeWithoutResult(status -> {
            profiles.findWithLockByUserId(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
            NicknameImageRequestEntity pending = requests.findFirstByUserIdAndStatus(userId, NicknameImageStatus.PENDING)
                    .orElseThrow(NicknameImageRequestNotFoundException::new);
            pending.cancel(Instant.now());
            List<String> keys = new ArrayList<>();
            collect(keys, pending);
            afterCommit(keys);
        });
    }

    /** 적용 중인 그림 해제 — 글자 닉네임으로 돌아간다. 없으면 404 {@code NICKNAME_IMAGE_NOT_FOUND}(대기 신청만 있어도) */
    public void remove(long userId) {
        guard(readTx.execute(status -> gate(userId)));
        tx.executeWithoutResult(status -> {
            profiles.findWithLockByUserId(userId).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
            NicknameImageRequestEntity current = requests.findFirstByUserIdAndStatus(userId, NicknameImageStatus.APPROVED)
                    .orElseThrow(NicknameImageNotFoundException::new);
            current.removeByOwner(Instant.now());
            List<String> keys = new ArrayList<>();
            collect(keys, current);
            afterCommit(keys);
        });
    }

    // ── 내부 ────────────────────────────────────────────────────────────────

    private static void guard(Gate g) {
        if (g.emailBlocked()) {
            throw new NicknameImageBlockedException("EMAIL_NOT_VERIFIED");
        }
        if (g.actor().restricted()) {
            throw new NicknameImageBlockedException("ACCOUNT_RESTRICTED");
        }
    }

    private Gate gate(long userId) {
        UserEntity user = users.findById(userId).filter(u -> !u.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        BoardCatalogSnapshot s = catalog.snapshot();
        BoardActor actor = actors.resolve(userId, s);
        Settings rules = settings.findById(NicknameImageSettingEntity.ROW_ID).map(Settings::of).orElse(Settings.DEFAULTS);
        return new Gate(user, actor, s, rules);
    }

    /**
     * 켜진 조건만 이 차례로 판정한다: 이메일 인증 → 등급 → 가입 후 일수 → 글+댓글 수 → 포인트. 일수·활동·포인트는 CUSTOM 이고 0 보다 클 때만.
     * 활동 수는 필요한 수까지만 센다(등급 즉시 승급과 같은 정의 — {@link MemberActivityRepository#countUpTo}).
     */
    private Eligibility eligibility(Gate g, Instant now) {
        long userId = g.user().getId();
        BoardCatalogSnapshot s = g.snapshot();
        Settings rules = g.settings();
        String myKey = profiles.findById(userId).map(p -> p.getGradeKey()).orElse(s.firstGradeKey());
        NicknameImageGradeView mine = grade(s, myKey);
        List<NicknameImageRequirementItemView> items = new ArrayList<>();
        if (rules.requireEmailVerified()) {
            items.add(NicknameImageRequirementItemView.Email.of(g.user().isEmailVerified()));
        }
        if (rules.gradeKey() != null) {
            Integer requiredLevel = s.gradeLevel(rules.gradeKey()); // 지워진 등급이면 null — 채울 수 없다
            Integer myLevel = s.gradeLevel(myKey);
            boolean met = requiredLevel != null && myLevel != null && myLevel >= requiredLevel;
            items.add(NicknameImageRequirementItemView.Grade.of(requiredLevel == null ? null : grade(s, rules.gradeKey()), mine, met));
        }
        if (rules.custom()) {
            if (rules.minDays() > 0) {
                long days = Math.max(0, Duration.between(g.user().getCreatedAt(), now).toDays());
                items.add(NicknameImageRequirementItemView.Count.of("DAYS", rules.minDays(), days));
            }
            if (rules.minActivity() > 0) {
                items.add(NicknameImageRequirementItemView.Count.of("ACTIVITY", rules.minActivity(), activities.countUpTo(userId, rules.minActivity(), now)));
            }
            if (rules.minPoints() > 0) {
                long balance = balances.findById(userId).map(PointBalanceEntity::getBalance).orElse(0L);
                items.add(NicknameImageRequirementItemView.Count.of("POINTS", rules.minPoints(), balance));
            }
        }
        return new Eligibility(g, mine, new NicknameImageRequirementsView(rules.mode().name(), List.copyOf(items)));
    }

    private static NicknameImageGradeView grade(BoardCatalogSnapshot s, String key) {
        String label = s.gradeLabel(key);
        return new NicknameImageGradeView(key, label == null ? key : label);
    }

    /** 지금 새 신청을 막는 시각 제한의 끝 */
    private Optional<Instant> reapplyAt(long userId, Instant now) {
        return requests.findFirstByUserIdAndReapplyAtAfterOrderByReapplyAtDesc(userId, now).map(NicknameImageRequestEntity::getReapplyAt);
    }

    /** 간격 10초 · 하루 10번(한국 시간 0시부터) — 저장된 신청(대체·취소된 것 포함)으로 센다 */
    private void rateLimit(long userId, Instant now) {
        Optional<NicknameImageRequestEntity> last = requests.findFirstByUserIdOrderByIdDesc(userId);
        if (last.isPresent()) {
            Duration since = Duration.between(last.get().getCreatedAt(), now);
            if (since.compareTo(NicknameImageRules.UPLOAD_INTERVAL) < 0) {
                throw new NicknameImageRateLimitedException(NicknameImageRules.UPLOAD_INTERVAL.minus(since).toSeconds() + 1);
            }
        }
        Instant midnight = now.atZone(KST).toLocalDate().atStartOfDay(KST).toInstant();
        if (requests.countByUserIdAndCreatedAtGreaterThanEqual(userId, midnight) >= NicknameImageRules.DAILY_UPLOADS) {
            Instant next = now.atZone(KST).toLocalDate().plusDays(1).atStartOfDay(KST).toInstant();
            throw new NicknameImageRateLimitedException(Duration.between(now, next).toSeconds());
        }
    }

    private MyNicknameImageView view(long userId, Instant now) {
        Eligibility e = eligibility(gate(userId), now);
        String reason = e.ineligibleReason();
        NicknameImageCurrentView current = requests.findFirstByUserIdAndStatus(userId, NicknameImageStatus.APPROVED)
                .filter(r -> r.getImageKey() != null)
                .map(r -> new NicknameImageCurrentView(Long.toString(r.getId()), storage.publicUrl(r.getImageKey()),
                        r.getStillKey() == null ? null : storage.publicUrl(r.getStillKey())))
                .orElse(null);
        NicknameImageRequestView request = requests.findFirstByUserIdOrderByIdDesc(userId)
                .filter(r -> r.getStatus() == NicknameImageStatus.PENDING || r.getStatus() == NicknameImageStatus.REJECTED)
                .map(r -> new NicknameImageRequestView(Long.toString(r.getId()), r.getStatus().name(), r.getImageKey() == null ? null : storage.publicUrl(r.getImageKey()),
                        r.getCreatedAt(), r.getRejectReason(), r.getStatus() == NicknameImageStatus.REJECTED ? r.getReapplyAt() : null))
                .orElse(null);
        NicknameImageLimitsView limits = new NicknameImageLimitsView(NicknameImageRules.MAX_BYTES, NicknameImageRules.WIDTH, NicknameImageRules.HEIGHT,
                NicknameImageRules.RATIO, NicknameImageRules.MAX_GIF_FRAMES, NicknameImageRules.FORMATS, NicknameImageRules.REJECTED_REAPPLY_HOURS);
        return new MyNicknameImageView(reason == null, reason, e.grade(), e.requirements(), current, request, reapplyAt(userId, now).orElse(null), limits);
    }

    /** 이 신청의 파일 키를 모으고 신청에서 지운다(파일은 커밋 뒤 지운다) */
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
                keys.forEach(NicknameImageService.this::deleteQuietly);
            }
        });
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("닉네임 그림 파일을 지우지 못했다 key={}", key, e);
        }
    }

    private static String token() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
