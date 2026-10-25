package com.community.block.service;

import com.community.block.dto.response.MemoListView;
import com.community.block.dto.response.MemoResultView;
import com.community.block.dto.response.MemoView;
import com.community.block.entity.UserMemoEntity;
import com.community.block.exception.MemoLimitException;
import com.community.block.exception.MemoSelfException;
import com.community.block.repository.master.BlockRepositoryQDSL;
import com.community.block.repository.master.UserMemoRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용자 메모 — 내가 상대 회원에게 남기는 한 줄(나에게만 보인다). docs/features/member-social-contract.md 「차단·메모」.
 *
 * <p>{@code PUT /api/users/{id}/memo}: 앞뒤 공백을 자르고 {@value #MEMO_MAX}자 이하·한 줄(줄바꿈 등 제어 문자 없음, {@code 400 fields.memo}) → 나 {@code 400 MEMO_SELF}
 * → 없는·탈퇴한 회원 {@code 404 PROFILE_NOT_FOUND} → <b>빈 메모면 지우고 {@code memo: null}</b> → 처음 남길 때만 {@value #MEMO_LIMIT}개 {@code 409 MEMO_LIMIT_REACHED}.
 * 운영진에게도 남길 수 있다. 상한 판정과 넣기가 겹치지 않게 내 회원 행을 잠근 뒤 센다.
 */
@Service
public class MemoService {

    static final int MEMO_MAX = 20;
    static final int MEMO_LIMIT = 500;
    static final int DEFAULT_SIZE = 20;
    private static final Pattern CONTROL = Pattern.compile("[\\u0000-\\u001f\\u007f-\\u009f]");

    private final UserMemoRepository memos;
    private final BlockRepositoryQDSL queries;
    private final UserRepository users;
    private final SocialMembers members;
    private final BoardCatalog catalog;
    private final SnowflakeIdGenerator ids;

    public MemoService(UserMemoRepository memos, BlockRepositoryQDSL queries, UserRepository users, SocialMembers members, BoardCatalog catalog,
                       SnowflakeIdGenerator ids) {
        this.memos = memos;
        this.queries = queries;
        this.users = users;
        this.members = members;
        this.catalog = catalog;
        this.ids = ids;
    }

    @Transactional
    public MemoResultView save(long userId, String targetText, String raw) {
        String memo = raw == null ? "" : raw.strip();
        if (memo.codePointCount(0, memo.length()) > MEMO_MAX) {
            throw ValidationException.of("memo", new MessageRef("block.memo.max", String.valueOf(MEMO_MAX)));
        }
        if (CONTROL.matcher(memo).find()) {
            throw ValidationException.of("memo", new MessageRef("block.memo.oneLine"));
        }
        Long targetId = SocialPaging.id(targetText);
        if (targetId != null && targetId == userId) {
            throw new MemoSelfException();
        }
        SocialMembers.Member target = members.require(targetText);
        users.findWithLockById(userId).filter(u -> !u.isGone()).orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        Optional<UserMemoEntity> have = memos.findByUserIdAndTargetId(userId, target.id());
        if (memo.isEmpty()) {
            have.ifPresent(memos::delete);
            return new MemoResultView(null);
        }
        Instant now = Instant.now();
        if (have.isPresent()) {
            have.get().change(memo, now);
        } else {
            if (memos.countByUserId(userId) >= MEMO_LIMIT) {
                throw new MemoLimitException(MEMO_LIMIT);
            }
            memos.persist(UserMemoEntity.create(ids.nextId(), userId, target.id(), memo, now));
        }
        return new MemoResultView(memo);
    }

    @Transactional
    public void remove(long userId, String targetText) {
        Long targetId = SocialPaging.id(targetText);
        if (targetId == null) {
            return;
        }
        memos.findByUserIdAndTargetId(userId, targetId).ifPresent(memos::delete);
    }

    @Transactional(readOnly = true)
    public MemoListView list(long userId, Integer size, String cursor) {
        int limit = SocialPaging.size(size, DEFAULT_SIZE);
        Long before = SocialPaging.cursor(cursor);
        List<BlockRepositoryQDSL.MemoRow> rows = queries.memoPage(userId, before, limit + 1);
        boolean more = rows.size() > limit;
        List<BlockRepositoryQDSL.MemoRow> page = more ? rows.subList(0, limit) : rows;
        BoardCatalogSnapshot s = catalog.snapshot();
        List<MemoView> items = page.stream().map(r -> new MemoView(Long.toString(r.targetId()), r.nickname(), r.gradeKey(), BlockService.label(s, r.gradeKey()),
                BlockService.ROLE_USER, r.memo(), r.updatedAt())).toList();
        String next = more ? Long.toString(page.get(page.size() - 1).rowId()) : null;
        return new MemoListView(queries.memoTotal(userId), items, more, next);
    }
}
