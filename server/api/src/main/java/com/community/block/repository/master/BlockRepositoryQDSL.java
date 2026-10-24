package com.community.block.repository.master;

import com.community.block.entity.QBlockedWordEntity;
import com.community.block.entity.QUserBlockEntity;
import com.community.block.entity.QUserMemoEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/**
 * 차단·차단 단어·메모 읽기 — 내 목록(상대 회원의 지금 닉네임·등급과 함께, 탈퇴한 회원은 뺀다)과, 글 목록·댓글·쪽지가 <b>한 번에</b> 묻는 「이 회원들 중 내가 차단한 사람·메모」(N+1 없이).
 * 목록 차례·커서는 줄 ID(Snowflake — 만든 시각 순) 내림차순이다.
 */
@Repository
public class BlockRepositoryQDSL extends RoboyamRepositoryQDSL {

    private static final QUserBlockEntity BLOCK = QUserBlockEntity.userBlockEntity;
    private static final QBlockedWordEntity WORD = QBlockedWordEntity.blockedWordEntity;
    private static final QUserMemoEntity MEMO = QUserMemoEntity.userMemoEntity;
    private static final QUserEntity USER = QUserEntity.userEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;

    public BlockRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    /** 차단한 회원 한 줄(목록) — {@code rowId} 는 커서 */
    public record BlockedRow(long rowId, long targetId, Instant blockedAt, String nickname, String gradeKey) {
    }

    /** 메모 한 줄(목록) — {@code rowId} 는 커서 */
    public record MemoRow(long rowId, long targetId, String memo, Instant updatedAt, String nickname, String gradeKey) {
    }

    /** 내가 차단한 회원(탈퇴한 회원 뺌) — 차단한 시각이 최근인 것이 위. {@code beforeRowId} 보다 앞(오래된) 줄만 */
    public List<BlockedRow> blockedPage(long userId, Long beforeRowId, int limit) {
        List<Tuple> rows = query.select(BLOCK.id, BLOCK.targetId, BLOCK.createdAt, PROFILE.nickname, PROFILE.gradeKey)
                .from(BLOCK).join(USER).on(USER.id.eq(BLOCK.targetId)).join(PROFILE).on(PROFILE.userId.eq(BLOCK.targetId))
                .where(BLOCK.userId.eq(userId), alive(), beforeRowId == null ? null : BLOCK.id.lt(beforeRowId))
                .orderBy(BLOCK.id.desc()).limit(limit).fetch();
        return rows.stream().map(t -> new BlockedRow(t.get(BLOCK.id), t.get(BLOCK.targetId), t.get(BLOCK.createdAt), t.get(PROFILE.nickname),
                t.get(PROFILE.gradeKey))).toList();
    }

    public long blockedTotal(long userId) {
        Long n = query.select(BLOCK.count()).from(BLOCK).join(USER).on(USER.id.eq(BLOCK.targetId)).where(BLOCK.userId.eq(userId), alive()).fetchOne();
        return n == null ? 0 : n;
    }

    /** 내가 남긴 메모(탈퇴한 회원 뺌) — 처음 남긴 것이 최근인 순서 */
    public List<MemoRow> memoPage(long userId, Long beforeRowId, int limit) {
        List<Tuple> rows = query.select(MEMO.id, MEMO.targetId, MEMO.memo, MEMO.updatedAt, PROFILE.nickname, PROFILE.gradeKey)
                .from(MEMO).join(USER).on(USER.id.eq(MEMO.targetId)).join(PROFILE).on(PROFILE.userId.eq(MEMO.targetId))
                .where(MEMO.userId.eq(userId), alive(), beforeRowId == null ? null : MEMO.id.lt(beforeRowId))
                .orderBy(MEMO.id.desc()).limit(limit).fetch();
        return rows.stream().map(t -> new MemoRow(t.get(MEMO.id), t.get(MEMO.targetId), t.get(MEMO.memo), t.get(MEMO.updatedAt), t.get(PROFILE.nickname),
                t.get(PROFILE.gradeKey))).toList();
    }

    public long memoTotal(long userId) {
        Long n = query.select(MEMO.count()).from(MEMO).join(USER).on(USER.id.eq(MEMO.targetId)).where(MEMO.userId.eq(userId), alive()).fetchOne();
        return n == null ? 0 : n;
    }

    /** 이 회원들 중 내가 차단한 회원 */
    public Set<Long> blockedAmong(long userId, Collection<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return Set.of();
        }
        return query.select(BLOCK.targetId).from(BLOCK).where(BLOCK.userId.eq(userId), BLOCK.targetId.in(targetIds)).fetch().stream()
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 이 회원들에게 내가 남긴 메모 — 회원 ID → 메모 */
    public Map<Long, String> memosAmong(long userId, Collection<Long> targetIds) {
        Map<Long, String> out = new HashMap<>();
        if (targetIds.isEmpty()) {
            return out;
        }
        query.select(MEMO.targetId, MEMO.memo).from(MEMO).where(MEMO.userId.eq(userId), MEMO.targetId.in(targetIds)).fetch()
                .forEach(t -> out.put(t.get(MEMO.targetId), t.get(MEMO.memo)));
        return out;
    }

    /** 내 차단 단어(글자만) */
    public List<String> words(long userId) {
        return query.select(WORD.word).from(WORD).where(WORD.userId.eq(userId)).fetch();
    }

    /** 두 회원 사이에 차단이 있는가(어느 쪽이든) */
    public boolean blockedEitherWay(long a, long b) {
        return query.selectOne().from(BLOCK)
                .where(BLOCK.userId.eq(a).and(BLOCK.targetId.eq(b)).or(BLOCK.userId.eq(b).and(BLOCK.targetId.eq(a))))
                .fetchFirst() != null;
    }

    /** 내가 차단 단어로 이미 가진 것(대소문자 무시) — {@code ux_blocked_words_user_word(user_id, lower(word))} 와 같은 기준 */
    public boolean wordExists(long userId, String word) {
        return query.selectOne().from(WORD)
                .where(WORD.userId.eq(userId), WORD.word.lower().eq(Expressions.stringTemplate("lower({0})", word)))
                .fetchFirst() != null;
    }

    private static BooleanExpression alive() {
        return USER.status.ne(UserStatus.WITHDRAWN).and(USER.deletedAt.isNull());
    }
}
