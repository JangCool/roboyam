package com.community.block.service;

import com.community.block.dto.response.BlockResultView;
import com.community.block.dto.response.BlockedUserListView;
import com.community.block.dto.response.BlockedUserView;
import com.community.block.dto.response.BlockedWordListView;
import com.community.block.dto.response.BlockedWordView;
import com.community.block.entity.BlockedWordEntity;
import com.community.block.entity.UserBlockEntity;
import com.community.block.exception.BlockLimitException;
import com.community.block.exception.BlockSelfException;
import com.community.block.exception.BlockTargetProtectedException;
import com.community.block.exception.BlockedWordDuplicateException;
import com.community.block.exception.BlockedWordLimitException;
import com.community.block.repository.master.BlockRepositoryQDSL;
import com.community.block.repository.master.BlockedWordRepository;
import com.community.block.repository.master.UserBlockRepository;
import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.service.BoardCatalog;
import com.community.user.repository.master.UserRepository;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ErrorCode;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 차단·차단 단어 — 로그인한 회원 본인 것만(개인 설정이라 이메일 인증 전·정지 중에도 된다). docs/features/member-social-contract.md 「차단·메모」.
 *
 * <ul>
 *   <li><b>차단</b>({@code PUT /api/users/{id}/block}, 멱등): 나 {@code 400 BLOCK_SELF} → 없는·탈퇴한 회원 {@code 404 PROFILE_NOT_FOUND} → 운영진(게시판 매니저·스텝)
 *       {@code 403 BLOCK_TARGET_PROTECTED} → 이미 차단했으면 처음 시각 그대로 → {@value #BLOCK_LIMIT}명 {@code 409 BLOCK_LIMIT_REACHED}. 상대에게 알리지 않는다.</li>
 *   <li><b>풀기</b>({@code DELETE}): 멱등 204 — 차단하지 않은·없는 회원도 204.</li>
 *   <li><b>단어</b>: 앞뒤 공백을 자르고 속의 연속 공백은 한 칸, {@value #WORD_MIN}~{@value #WORD_MAX}자(글자 단위)·줄바꿈 등 제어 문자 없음({@code 400 fields.word}) →
 *       같은 단어(대소문자 무시) {@code 409 BLOCKED_WORD_DUPLICATE} → {@value #WORD_LIMIT}개 {@code 409 BLOCKED_WORD_LIMIT}. 지우기는 멱등(남의 항목도 204 — 지우지 않는다).</li>
 *   <li>상한 판정과 넣기가 겹치지 않게 내 회원 행을 잠근 뒤 센다.</li>
 * </ul>
 */
@Service
public class BlockService {

    private static final Logger log = LoggerFactory.getLogger(BlockService.class);

    static final int BLOCK_LIMIT = 500;
    static final int WORD_LIMIT = 50;
    static final int WORD_MIN = 2;
    static final int WORD_MAX = 20;
    static final int DEFAULT_SIZE = 20;
    static final String ROLE_USER = "USER";
    private static final Pattern CONTROL = Pattern.compile("[\\u0000-\\u001f\\u007f-\\u009f]");

    private final UserBlockRepository blocks;
    private final BlockedWordRepository words;
    private final BlockRepositoryQDSL queries;
    private final UserRepository users;
    private final SocialMembers members;
    private final BoardCatalog catalog;
    private final SnowflakeIdGenerator ids;

    public BlockService(UserBlockRepository blocks, BlockedWordRepository words, BlockRepositoryQDSL queries, UserRepository users, SocialMembers members,
                        BoardCatalog catalog, SnowflakeIdGenerator ids) {
        this.blocks = blocks;
        this.words = words;
        this.queries = queries;
        this.users = users;
        this.members = members;
        this.catalog = catalog;
        this.ids = ids;
    }

    @Transactional
    public BlockResultView block(long userId, String targetText) {
        Long targetId = SocialPaging.id(targetText);
        if (targetId != null && targetId == userId) {
            throw new BlockSelfException();
        }
        SocialMembers.Member target = members.require(targetText);
        if (members.staff(target.id())) {
            throw new BlockTargetProtectedException();
        }
        lockMe(userId);
        Optional<UserBlockEntity> have = blocks.findByUserIdAndTargetId(userId, target.id());
        if (have.isPresent()) {
            return new BlockResultView(true, have.get().getCreatedAt());
        }
        if (blocks.countByUserId(userId) >= BLOCK_LIMIT) {
            throw new BlockLimitException(BLOCK_LIMIT);
        }
        Instant now = Instant.now();
        blocks.persist(UserBlockEntity.create(ids.nextId(), userId, target.id(), now));
        log.info("회원을 차단했다 userId={} targetId={}", userId, target.id());
        return new BlockResultView(true, now);
    }

    @Transactional
    public void unblock(long userId, String targetText) {
        Long targetId = SocialPaging.id(targetText);
        if (targetId == null) {
            return;
        }
        blocks.findByUserIdAndTargetId(userId, targetId).ifPresent(blocks::delete);
    }

    @Transactional(readOnly = true)
    public BlockedUserListView users(long userId, Integer size, String cursor) {
        int limit = SocialPaging.size(size, DEFAULT_SIZE);
        Long before = SocialPaging.cursor(cursor);
        List<BlockRepositoryQDSL.BlockedRow> rows = queries.blockedPage(userId, before, limit + 1);
        boolean more = rows.size() > limit;
        List<BlockRepositoryQDSL.BlockedRow> page = more ? rows.subList(0, limit) : rows;
        BoardCatalogSnapshot s = catalog.snapshot();
        List<BlockedUserView> items = page.stream()
                .map(r -> new BlockedUserView(Long.toString(r.targetId()), r.nickname(), r.gradeKey(), label(s, r.gradeKey()), ROLE_USER, r.blockedAt())).toList();
        String next = more ? Long.toString(page.get(page.size() - 1).rowId()) : null;
        return new BlockedUserListView(queries.blockedTotal(userId), items, more, next);
    }

    @Transactional(readOnly = true)
    public BlockedWordListView words(long userId) {
        List<BlockedWordView> items = words.findByUserIdOrderByIdDesc(userId).stream().map(BlockService::view).toList();
        return new BlockedWordListView(WORD_LIMIT, WORD_MIN, WORD_MAX, items);
    }

    @Transactional
    public BlockedWordView addWord(long userId, String raw) {
        String word = normalizeWord(raw);
        int length = word.codePointCount(0, word.length());
        if (raw == null || CONTROL.matcher(raw).find() || length < WORD_MIN || length > WORD_MAX) {
            throw ValidationException.of("word", new MessageRef("block.word.length", String.valueOf(WORD_MIN), String.valueOf(WORD_MAX)));
        }
        lockMe(userId);
        if (queries.wordExists(userId, word)) {
            throw new BlockedWordDuplicateException();
        }
        if (words.countByUserId(userId) >= WORD_LIMIT) {
            throw new BlockedWordLimitException(WORD_LIMIT);
        }
        BlockedWordEntity created = BlockedWordEntity.create(ids.nextId(), userId, word, Instant.now());
        words.persist(created);
        return view(created);
    }

    @Transactional
    public void removeWord(long userId, String idText) {
        Long id = SocialPaging.id(idText);
        if (id == null) {
            return;
        }
        words.findByIdAndUserId(id, userId).ifPresent(words::delete);
    }

    /** 앞뒤 공백을 자르고 속의 연속 공백은 한 칸 */
    static String normalizeWord(String raw) {
        return raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
    }

    private void lockMe(long userId) {
        users.findWithLockById(userId).filter(u -> !u.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
    }

    private static BlockedWordView view(BlockedWordEntity e) {
        return new BlockedWordView(Long.toString(e.getId()), e.getWord(), e.getCreatedAt());
    }

    /** 등급 이름 — 등급 목록에 없는 키(지워진 등급)는 키 그대로 */
    public static String label(BoardCatalogSnapshot s, String gradeKey) {
        if (gradeKey == null) {
            return null;
        }
        String label = s.gradeLabel(gradeKey);
        return label == null ? gradeKey : label;
    }
}
