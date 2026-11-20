package com.community.jjal.service;

import com.community.board.dto.BoardActor;
import com.community.board.exception.BoardWriteBlockedException;
import com.community.board.service.BoardAccessPolicy;
import com.community.board.service.BoardActorResolver;
import com.community.board.service.BoardCatalog;
import com.community.jjal.config.JjalProperties;
import com.community.jjal.dto.request.JjalCollectRequest;
import com.community.jjal.dto.request.JjalOrderRequest;
import com.community.jjal.dto.response.JjalSaveResultView;
import com.community.jjal.dto.response.MyJjalItemView;
import com.community.jjal.dto.response.MyJjalListView;
import com.community.jjal.entity.JjalBoxItemEntity;
import com.community.jjal.entity.JjalFileEntity;
import com.community.jjal.entity.JjalMemberStateEntity;
import com.community.jjal.entity.JjalSource;
import com.community.jjal.entity.JjalUploadEventEntity;
import com.community.jjal.exception.JjalAlreadyInBoxException;
import com.community.jjal.exception.JjalBlockedException;
import com.community.jjal.exception.JjalDailyLimitException;
import com.community.jjal.exception.JjalEmailNotVerifiedException;
import com.community.jjal.exception.JjalLimitReachedException;
import com.community.jjal.exception.JjalNotCollectableException;
import com.community.jjal.exception.JjalNotFoundException;
import com.community.jjal.exception.JjalOrderConflictException;
import com.community.jjal.exception.JjalRateLimitedException;
import com.community.jjal.exception.JjalTooLargeException;
import com.community.jjal.repository.master.JjalBoxItemRepository;
import com.community.jjal.repository.master.JjalFileRepository;
import com.community.jjal.repository.master.JjalMemberStateRepository;
import com.community.jjal.repository.master.JjalRepositoryQDSL;
import com.community.jjal.repository.master.JjalUploadEventRepository;
import com.community.media.service.ImageStorage;
import com.community.post.repository.master.PostCounterQueries;
import com.community.post.service.PostQueryService;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 내 짤 보관함({@code /api/me/jjals…}) — 목록·올리기·담기·빼기·순서·처음 안내 확인. docs/features/my-jjal-contract.md 「규칙」·「화면 확정 결과」.
 *
 * <p><b>서버 여러 대</b>: 올리기·담기·빼기·순서·안내 확인은 회원 단위 어드바이저리 잠금({@code jjal-box:<회원>}) 아래 한 트랜잭션에서 판정하고 쓴다 — 상한·속도·순서가 서버 대수와 무관하게 맞다
 * (메모리 카운터 없음, 속도는 {@code jjal_upload_events} 를 센다). 같은 해시의 파일 행은 유일 제약이 하나로 지키고, 동시에 같은 새 파일을 올리면 늦은 쪽이 한 번 다시 시도해 그 파일을 가리킨다.
 *
 * <p><b>자격</b>(올리기·담기): 로그인(401) · 정지 {@code 403 ACCOUNT_RESTRICTED} · 이메일 미인증 {@code 403 EMAIL_NOT_VERIFIED}. 나이 제한 없음. 목록·빼기·순서·안내 확인은 로그인만.
 *
 * <p><b>올리기 검사 차례</b>(계약 「올리기」 그대로): 자격 → 파일 칸({@code 400 fields.file}) → 용량({@code 413 JJAL_TOO_LARGE}) → 형식·머리 크기·디코딩·GIF 장 수·재생 시간
 * ({@link JjalImageProcessor}, 트랜잭션 밖 — {@code 400 fields.file}) → 상한({@code 409 JJAL_LIMIT_REACHED}, 내려간 짤 포함) → 속도(1분 {@code 429 JJAL_RATE_LIMITED} · 24시간
 * {@code 429 JJAL_DAILY_LIMIT}, {@code retryAfterSeconds}) → 해시(원본 바이트 SHA-256 — 내린 짤과 같으면 {@code 409 JJAL_BLOCKED} · 이미 내 보관함에 있으면 {@code 409 JJAL_ALREADY_IN_BOX}
 * · 있는 파일이면 새로 저장하지 않고 그 파일을 가리킨다 — 올린 사람 화면은 같다). 저장 순서는 검사·처리 → 저장소 쓰기(새 파일일 때만, 키 {@code jjal/{UTC 년월}/{무작위 32자}.{확장자}})
 * → 장부·보관함 줄 트랜잭션 → 실패하거나 쓰지 않게 된 새 저장소 파일은 지운다.
 *
 * <p><b>담기</b>: 자격 → 칸({@code 400 fields.jjalId|postId|commentId}) → 짤·맥락(없는 짤 · 보이지 않는 글·댓글 · 그 글·댓글에 쓰이지 않은 짤 {@code 404 JJAL_NOT_FOUND})
 * → 19금 게시판·19금 글 {@code 403 JJAL_NOT_COLLECTABLE} → 내려간 짤 {@code 409 JJAL_BLOCKED} → 이미 있음 {@code 409 JJAL_ALREADY_IN_BOX} → 꽉 참 {@code 409 JJAL_LIMIT_REACHED}.
 * 담은 사람·수는 어디에도 남기거나 보이지 않는다(정책 2). 새 줄은 맨 앞.
 */
@Service
public class MyJjalService {

    private static final Logger log = LoggerFactory.getLogger(MyJjalService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    /** 공개 ID 글자 수(무작위 영문·숫자 — 약 95비트) */
    private static final int PUBLIC_ID_LENGTH = 16;
    /** 처음 안내 판 — 안내 내용이 바뀌면 올린다(올린 판을 확인하지 않은 회원에게 다시 나온다) */
    public static final int INTRO_VERSION = 1;

    private final BoardCatalog catalog;
    private final BoardActorResolver actors;
    private final JjalImageProcessor processor;
    private final JjalProperties props;
    private final JjalFileRepository files;
    private final JjalBoxItemRepository items;
    private final JjalMemberStateRepository states;
    private final JjalUploadEventRepository uploads;
    private final JjalRepositoryQDSL queries;
    private final PostCounterQueries locks;
    private final ImageStorage storage;
    private final JjalPostContext context;
    private final SnowflakeIdGenerator ids;
    private final TransactionTemplate tx;
    private final TransactionTemplate readTx;

    public MyJjalService(BoardCatalog catalog, BoardActorResolver actors, JjalImageProcessor processor, JjalProperties props, JjalFileRepository files,
                         JjalBoxItemRepository items, JjalMemberStateRepository states, JjalUploadEventRepository uploads, JjalRepositoryQDSL queries,
                         PostCounterQueries locks, ImageStorage storage, JjalPostContext context, SnowflakeIdGenerator ids, PlatformTransactionManager txManager) {
        this.catalog = catalog;
        this.actors = actors;
        this.processor = processor;
        this.props = props;
        this.files = files;
        this.items = items;
        this.states = states;
        this.uploads = uploads;
        this.queries = queries;
        this.locks = locks;
        this.storage = storage;
        this.context = context;
        this.ids = ids;
        this.tx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    // ── 목록 ──────────────────────────────────────────────────────────────

    public MyJjalListView list(Long userId) {
        long me = requireLogin(userId);
        return readTx.execute(status -> {
            Instant now = Instant.now();
            List<MyJjalItemView> out = new ArrayList<>();
            for (JjalRepositoryQDSL.BoxRow row : queries.box(me)) {
                out.add(itemView(row.item(), row.file(), now));
            }
            boolean acknowledged = states.findById(me).map(s -> s.acknowledged(INTRO_VERSION)).orElse(false);
            return new MyJjalListView(out, out.size(), props.boxLimit(), acknowledged);
        });
    }

    // ── 올리기 ────────────────────────────────────────────────────────────

    public JjalSaveResultView upload(Long userId, MultipartFile file) {
        long me = requireEligible(userId);
        if (file == null || file.isEmpty()) {
            throw ValidationException.of("file", new MessageRef(file == null ? "file.required" : "site.image.empty"));
        }
        if (file.getSize() > props.maxBytes()) {
            throw new JjalTooLargeException(props.maxBytes() / (1024 * 1024));
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ValidationException.of("file", new MessageRef("site.image.corrupt"));
        }
        if (data.length > props.maxBytes()) {
            throw new JjalTooLargeException(props.maxBytes() / (1024 * 1024));
        }
        JjalImageProcessor.Processed processed = processor.process(data); // 형식 → 머리 크기 → 디코딩 · 장 수 · 재생 시간(400 fields.file)
        String hash = sha256(data); // 원본 바이트

        // 미리 보기(잠금 없음) — 상한 · 속도 · 해시 차례로 거절하고, 이미 있는 파일이면 새로 저장하지 않는다. 트랜잭션에서 잠근 뒤 다시 본다
        Boolean exists = readTx.execute(status -> {
            Instant now = Instant.now();
            checkLimitAndRate(me, now);
            JjalFileEntity same = files.findBySha256(hash).orElse(null);
            checkSameFile(me, same, now);
            return same != null;
        });
        String newKey = Boolean.TRUE.equals(exists) ? null : store(processed);
        boolean keyUsed = false;
        try {
            for (int attempt = 0; ; attempt++) {
                try {
                    String key = newKey;
                    Saved saved = tx.execute(status -> saveUpload(me, hash, processed, key));
                    if (saved == null) {
                        throw new IllegalStateException("올리기 저장 결과가 없다");
                    }
                    if (saved.needsBytes()) {
                        newKey = store(processed); // 미리 본 파일이 그사이 정리됐다 — 새로 저장해 한 번 더
                        continue;
                    }
                    keyUsed = saved.usedNewKey();
                    log.info("짤을 올렸다 userId={} fileId={} newFile={} bytes={}", me, saved.view().item().id(), saved.usedNewKey(), processed.data().length);
                    return saved.view();
                } catch (DuplicateKeyException e) {
                    if (attempt >= 1) {
                        throw e;
                    }
                    // 같은 해시(또는 무작위 공개 ID)가 동시에 들어갔다 — 한 번 더(이번엔 그 파일을 가리킨다)
                }
            }
        } finally {
            if (newKey != null && !keyUsed) {
                deleteQuietly(newKey);
            }
        }
    }

    /**
     * 올리기 저장 결과.
     *
     * @param needsBytes 미리 볼 때 있던 같은 해시 파일이 그사이 없어져 새로 저장해야 한다(저장소 키가 없었다)
     * @param usedNewKey 새 저장소 파일을 장부에 넣었다(아니면 부르는 쪽이 지운다)
     */
    private record Saved(JjalSaveResultView view, boolean needsBytes, boolean usedNewKey) {
    }

    /** 회원 잠금 아래 상한·속도·해시를 다시 보고 파일(없으면 새로)·보관함 줄·올리기 기록을 넣는다 */
    private Saved saveUpload(long me, String hash, JjalImageProcessor.Processed p, String newKey) {
        locks.lockWriter(lockKey(me));
        Instant now = Instant.now();
        checkLimitAndRate(me, now);
        JjalFileEntity file = files.findWithLockBySha256(hash).orElse(null);
        checkSameFile(me, file, now);
        boolean created = false;
        if (file == null) {
            if (newKey == null) {
                return new Saved(null, true, false);
            }
            file = JjalFileEntity.create(ids.nextId(), newPublicId(), hash, newKey, p.format().contentType(), p.data().length, p.width(), p.height(), p.animated(), me, now);
            files.persist(file);
            created = true;
        }
        JjalBoxItemEntity item = JjalBoxItemEntity.create(ids.nextId(), me, file.getId(), JjalSource.UPLOAD, frontKey(me), now);
        items.persist(item);
        uploads.persist(JjalUploadEventEntity.create(ids.nextId(), me, now));
        items.flush(); // 유일 위반을 여기서(부르는 쪽이 다시 시도한다)
        int count = (int) items.countByUserId(me);
        return new Saved(new JjalSaveResultView(itemView(item, file, now), count, props.boxLimit()), false, created);
    }

    /** 상한(내려간 짤 포함) → 1분 속도 → 24시간 속도 */
    private void checkLimitAndRate(long me, Instant now) {
        if (items.countByUserId(me) >= props.boxLimit()) {
            throw new JjalLimitReachedException(props.boxLimit());
        }
        Instant minuteAgo = now.minus(Duration.ofMinutes(1));
        if (queries.uploadsSince(me, minuteAgo) >= props.perMinute()) {
            throw new JjalRateLimitedException(props.perMinute(), retryAfter(queries.oldestUploadSince(me, minuteAgo), Duration.ofMinutes(1), now));
        }
        Instant dayAgo = now.minus(Duration.ofDays(1));
        if (queries.uploadsSince(me, dayAgo) >= props.perDay()) {
            throw new JjalDailyLimitException(props.perDay(), retryAfter(queries.oldestUploadSince(me, dayAgo), Duration.ofDays(1), now));
        }
    }

    /** 같은 해시 파일 — 내려간 짤이면 409 JJAL_BLOCKED, 이미 내 보관함에 있으면 409 JJAL_ALREADY_IN_BOX */
    private void checkSameFile(long me, JjalFileEntity same, Instant now) {
        if (same == null) {
            return;
        }
        if (same.isBlockedAt(now)) {
            throw new JjalBlockedException(null); // 올린 사람에게 그 짤의 공개 ID 를 알리지 않는다
        }
        if (items.existsByUserIdAndFileId(me, same.getId())) {
            throw new JjalAlreadyInBoxException();
        }
    }

    private static long retryAfter(Instant oldest, Duration window, Instant now) {
        if (oldest == null) {
            return 1;
        }
        return Math.max(1, Duration.between(now, oldest.plus(window)).toSeconds() + 1);
    }

    private String store(JjalImageProcessor.Processed p) {
        String key = JjalStorageSync.newKey(false, p.format().extension()); // 공개 위치의 무작위 키
        storage.put(key, p.data());
        return key;
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("쓰지 않게 된 짤 저장소 파일을 지우지 못했다 key={}", key, e);
        }
    }

    // ── 담기 ──────────────────────────────────────────────────────────────

    public JjalSaveResultView collect(Long userId, JjalCollectRequest body) {
        long me = requireEligible(userId);
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        String jjalId = body == null ? null : trim(body.jjalId());
        if (!JjalGuard.validPublicId(jjalId)) {
            fields.put("jjalId", new MessageRef("jjal.id.invalid"));
        }
        Long postId = body == null ? null : PostQueryService.parseId(body.postId());
        if (postId == null) {
            fields.put("postId", new MessageRef("jjal.postId.invalid"));
        }
        String commentRaw = body == null ? null : trim(body.commentId());
        Long commentId = commentRaw == null ? null : PostQueryService.parseId(commentRaw);
        if (commentRaw != null && commentId == null) {
            fields.put("commentId", new MessageRef("jjal.commentId.invalid"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }

        Instant start = Instant.now();
        JjalFileEntity file = files.findByPublicId(jjalId).orElseThrow(JjalNotFoundException::new);
        JjalPostContext.Visible visible = context.post(postId, me, start);
        if (visible == null || (commentId != null && !context.commentVisible(postId, commentId, start)) || !queries.usedAt(file.getId(), postId, commentId)) {
            throw new JjalNotFoundException(); // 보이는 글·댓글의 짤만(읽을 수 없는 글은 없는 글과 같다)
        }
        if (visible.adult()) {
            throw new JjalNotCollectableException();
        }
        long fileId = file.getId();
        return tx.execute(status -> {
            locks.lockWriter(lockKey(me));
            Instant now = Instant.now();
            JjalFileEntity locked = files.findWithLockById(fileId).orElseThrow(JjalNotFoundException::new);
            if (locked.isBlockedAt(now)) {
                throw new JjalBlockedException(locked.getPublicId());
            }
            if (items.existsByUserIdAndFileId(me, fileId)) {
                throw new JjalAlreadyInBoxException();
            }
            long count = items.countByUserId(me);
            if (count >= props.boxLimit()) {
                throw new JjalLimitReachedException(props.boxLimit());
            }
            JjalBoxItemEntity item = JjalBoxItemEntity.create(ids.nextId(), me, fileId, JjalSource.COLLECT, frontKey(me), now);
            items.persist(item);
            return new JjalSaveResultView(itemView(item, locked, now), (int) count + 1, props.boxLimit());
        });
    }

    // ── 빼기 · 순서 · 안내 확인 ─────────────────────────────────────────────

    /** 내 보관함에서 빼기 — 파일은 남는다(다른 보관함·글·댓글이 쓰면 그대로, 아무도 안 쓰면 정리 배치가 지운다). 없으면 404 JJAL_NOT_FOUND */
    public void remove(Long userId, String idText) {
        long me = requireLogin(userId);
        String jjalId = trim(idText);
        if (!JjalGuard.validPublicId(jjalId)) {
            throw new JjalNotFoundException();
        }
        tx.executeWithoutResult(status -> {
            locks.lockWriter(lockKey(me));
            JjalFileEntity file = files.findByPublicId(jjalId).orElseThrow(JjalNotFoundException::new);
            JjalBoxItemEntity item = items.findByUserIdAndFileId(me, file.getId()).orElseThrow(JjalNotFoundException::new);
            items.delete(item);
        });
    }

    /** 순서 저장 — 보관함 전체 공개 ID 와 빠짐·겹침 없이 같아야 한다(아니면 409 JJAL_ORDER_CONFLICT). 앞에서부터 0, 1, 2 … */
    public void order(Long userId, JjalOrderRequest body) {
        long me = requireLogin(userId);
        if (body == null || body.ids() == null) {
            throw ValidationException.of("ids", new MessageRef("jjal.order.ids.required"));
        }
        List<String> wanted = body.ids().stream().map(MyJjalService::trim).toList();
        tx.executeWithoutResult(status -> {
            locks.lockWriter(lockKey(me));
            List<JjalRepositoryQDSL.BoxRow> rows = queries.box(me);
            Map<String, JjalBoxItemEntity> byPublicId = new LinkedHashMap<>();
            rows.forEach(r -> byPublicId.put(r.file().getPublicId(), r.item()));
            Set<String> seen = new HashSet<>();
            boolean same = wanted.size() == byPublicId.size();
            for (String id : wanted) {
                if (!same) {
                    break;
                }
                same = id != null && byPublicId.containsKey(id) && seen.add(id);
            }
            if (!same) {
                throw new JjalOrderConflictException();
            }
            for (int i = 0; i < wanted.size(); i++) {
                byPublicId.get(wanted.get(i)).moveTo(i);
            }
        });
    }

    /** 처음 안내 확인 — 계정에 기록(시각·안내 판). 이미 확인했으면 그대로 */
    public void acknowledgeIntro(Long userId) {
        long me = requireLogin(userId);
        tx.executeWithoutResult(status -> {
            locks.lockWriter(lockKey(me));
            Instant now = Instant.now();
            JjalMemberStateEntity state = states.findById(me).orElseGet(() -> {
                JjalMemberStateEntity created = JjalMemberStateEntity.create(me, now);
                states.persist(created);
                return created;
            });
            state.acknowledge(INTRO_VERSION, now);
        });
    }

    // ── 안쪽 ──────────────────────────────────────────────────────────────

    /** 회원 단위 잠금 열쇠 — 올리기·담기·빼기·순서·안내 확인이 줄을 선다 */
    private static String lockKey(long userId) {
        return "jjal-box:" + userId;
    }

    /** 맨 앞 자리 — 지금 가장 작은 순서 값 − 1(비었으면 0). 회원 잠금 아래에서 부른다 */
    private long frontKey(long me) {
        Long min = queries.minSortKey(me);
        return min == null ? 0 : min - 1;
    }

    private MyJjalItemView itemView(JjalBoxItemEntity item, JjalFileEntity file, Instant now) {
        boolean blocked = !file.servableAt(now); // 내려갔거나 공개 위치로 옮기기 전 — 그림을 내지 않는다
        return new MyJjalItemView(file.getPublicId(), blocked ? null : storage.publicUrl(file.getStorageKey()), file.getWidth(), file.getHeight(), file.isAnimated(),
                item.getSource().name(), blocked);
    }

    private static long requireLogin(Long userId) {
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }

    /** 올리기·담기 자격 — 로그인 · 정지 아님 · 이메일 인증 */
    private long requireEligible(Long userId) {
        long me = requireLogin(userId);
        BoardActor actor = actors.resolve(me, catalog.snapshot());
        if (actor.guest()) {
            throw new CommonException(ErrorCode.UNAUTHENTICATED);
        }
        if (actor.restricted()) {
            throw new BoardWriteBlockedException(BoardAccessPolicy.ACCOUNT_RESTRICTED);
        }
        if (!actor.emailVerified()) {
            throw new JjalEmailNotVerifiedException(); // 코드는 EMAIL_NOT_VERIFIED 그대로, 문구만 짤용
        }
        return me;
    }

    private static String trim(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        return s.isEmpty() ? null : s;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 이 없다", e);
        }
    }

    private static String newPublicId() {
        StringBuilder sb = new StringBuilder(PUBLIC_ID_LENGTH);
        for (int i = 0; i < PUBLIC_ID_LENGTH; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }
}
