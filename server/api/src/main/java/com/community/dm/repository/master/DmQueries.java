package com.community.dm.repository.master;

import com.community.block.entity.QUserBlockEntity;
import com.community.dm.entity.DmBoxEntity;
import com.community.dm.entity.DmMessageEntity;
import com.community.dm.entity.DmUserStateEntity;
import com.community.dm.entity.QDmBoxEntity;
import com.community.dm.entity.QDmMessageEntity;
import com.community.dm.entity.QDmThreadEntity;
import com.community.dm.entity.QDmUserStateEntity;
import com.community.membernotification.entity.QMemberNotificationEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 쪽지 표(V70 {@code dm_threads}·{@code dm_boxes}·{@code dm_messages}·{@code dm_user_states} — shared {@code com.community.dm.entity}) 읽기·쓰기 — 쪽지 서비스만 부른다.
 * 트랜잭션은 부르는 서비스가 연다.
 *
 * <p>JPA·QueryDSL 이다(넣기는 {@code persistAndFlush} — 예전 JDBC 처럼 그 자리에서 INSERT 해 문장·잠금 차례가 같다, 고치기는 대량 UPDATE, 읽기는 프로젝션).
 * Native SQL 로 남긴 것과 이유: {@code ON CONFLICT} 세 문장 — 대화 짝 만들기({@link #createThread}, 두 사람이 동시에 첫 쪽지를 보내도 대화는 하나)와 회원 상태 줄 만들기·수신 설정
 * ({@link #lockState}·{@link #setAccept}). 모두 유일 제약 경합이고 PostgreSQL 은 위반 한 번에 트랜잭션 전체를 못 쓰게 한다.
 */
@Repository
public class DmQueries extends RoboyamRepositoryQDSL {

    private static final QUserEntity USER = QUserEntity.userEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QUserBlockEntity BLOCK = QUserBlockEntity.userBlockEntity;
    private static final QDmThreadEntity T = QDmThreadEntity.dmThreadEntity;
    private static final QDmBoxEntity B = QDmBoxEntity.dmBoxEntity;
    private static final QDmMessageEntity M = QDmMessageEntity.dmMessageEntity;
    private static final QDmUserStateEntity S = QDmUserStateEntity.dmUserStateEntity;
    private static final QMemberNotificationEntity NOTIFICATION = QMemberNotificationEntity.memberNotificationEntity;

    /** 상대가 이 대화에서 쪽지를 보낸 적이 있다 — 내가 작은 쪽이면 큰 쪽 표시, 아니면 작은 쪽 표시 */
    private static final Expression<Boolean> PARTNER_SENT = new CaseBuilder().when(T.userLowId.eq(B.userId)).then((Predicate) T.highSent).otherwise(T.lowSent);

    private final DmBoxRepository boxes;
    private final DmMessageRepository messages;
    private final JdbcTemplate jdbc;

    public DmQueries(JPAQueryFactory query, DmBoxRepository boxes, DmMessageRepository messages, DataSource dataSource) {
        super(query);
        this.boxes = boxes;
        this.messages = messages;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    // ── 회원 한 줄(수신 설정·오늘 한도) ──────────────────────────────────────

    /**
     * @param quotaDay 한도를 센 날(한국 시간) — 오늘이 아니면 센 수는 0 으로 본다
     */
    public record State(boolean acceptDm, LocalDate quotaDay, int sentToday, int newPartnersToday, Instant lastSentAt) {

        public static State initial() {
            return new State(true, null, 0, 0, null);
        }

        public int sentOn(LocalDate day) {
            return day.equals(quotaDay) ? sentToday : 0;
        }

        public int newPartnersOn(LocalDate day) {
            return day.equals(quotaDay) ? newPartnersToday : 0;
        }
    }

    /** 줄이 없으면 처음 값(받기·0통) */
    public State state(long userId) {
        State row = query.select(Projections.constructor(State.class, S.acceptDm, S.quotaDay, S.sentToday, S.newPartnersToday, S.lastSentAt)).from(S)
                .where(S.userId.eq(userId)).fetchFirst();
        return row == null ? State.initial() : row;
    }

    /**
     * 보내기 전에 내 줄을 잠근다(없으면 만든 뒤) — 간격·한도 판정과 차감이 겹치지 않는다(서버가 여러 대여도). 잠금은 {@code FOR NO KEY UPDATE} — 이 표를 FK 로 가리키는 표가 없어
     * {@code FOR UPDATE} 와 막는 범위가 같다.
     *
     * <p>줄 만들기가 Native SQL 인 이유: {@code INSERT … ON CONFLICT DO NOTHING} — 같은 회원이 동시에 처음 보내도 줄 하나(위반 예외 없이).
     */
    public State lockState(long userId, Instant now) {
        jdbc.update("insert into dm_user_states (user_id, updated_at) values (?, ?) on conflict (user_id) do nothing", userId, Timestamp.from(now));
        DmUserStateEntity locked = query.selectFrom(S).where(S.userId.eq(userId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne();
        if (locked == null) {
            throw new IllegalStateException("쪽지 상태 줄을 만들지 못했다");
        }
        return new State(locked.isAcceptDm(), locked.getQuotaDay(), locked.getSentToday(), locked.getNewPartnersToday(), locked.getLastSentAt());
    }

    /** 이 회원들의 수신 설정 — 줄이 없으면 받기 */
    public boolean acceptsDm(long userId) {
        return state(userId).acceptDm();
    }

    /**
     * 수신 설정을 저장한다(줄이 없으면 만든다).
     *
     * <p>Native SQL 인 이유: {@code INSERT … ON CONFLICT DO UPDATE} 한 문장 upsert — 처음 저장이 동시에 와도 키 충돌로 실패하지 않는다.
     */
    public void setAccept(long userId, boolean accept, Instant now) {
        jdbc.update("insert into dm_user_states (user_id, accept_dm, updated_at) values (?, ?, ?) "
                + "on conflict (user_id) do update set accept_dm = excluded.accept_dm, updated_at = excluded.updated_at", userId, accept, Timestamp.from(now));
    }

    /** 보낸 뒤의 오늘 사용량(잠근 줄에 쓴다) */
    public void recordSend(long userId, LocalDate day, int sentToday, int newPartnersToday, Instant now) {
        query.update(S).set(S.quotaDay, day).set(S.sentToday, sentToday).set(S.newPartnersToday, newPartnersToday).set(S.lastSentAt, now).set(S.updatedAt, now)
                .where(S.userId.eq(userId)).execute();
    }

    // ── 대화·쪽지함 ───────────────────────────────────────────────────────

    /**
     * 내 쪽지함의 대화 한 줄.
     *
     * @param partnerSent 상대가 이 대화에서 쪽지를 보낸 적이 있다(링크·연락처 허용)
     * @param lastMessageId 대화의 마지막 쪽지(없으면 0)
     */
    public record Box(long threadId, long userId, long partnerId, long lastMessageId, long readUpTo, long clearedUpTo, boolean partnerSent) {

        /** 내 쪽지함에 보이는 쪽지의 아래 경계(그 ID 까지는 안 보인다) */
        public long visibleAfter() {
            return clearedUpTo;
        }
    }

    private static final Expression<Box> BOX = Projections.constructor(Box.class, B.threadId, B.userId, B.partnerId, B.lastMessageId, B.readUpTo, B.clearedUpTo,
            PARTNER_SENT);

    /** 쪽지함 줄 + 대화(상대가 보냈는가) */
    private JPAQuery<Box> boxes() {
        return query.select(BOX).from(B).join(T).on(T.id.eq(B.threadId));
    }

    public Optional<Box> box(long userId, long threadId) {
        return Optional.ofNullable(boxes().where(B.userId.eq(userId), B.threadId.eq(threadId)).fetchFirst());
    }

    /** 두 사람 사이의 대화(내 쪽지함 줄) — 내가 지웠어도 있다 */
    public Optional<Box> boxWith(long userId, long partnerId) {
        return Optional.ofNullable(boxes().where(B.userId.eq(userId), B.partnerId.eq(partnerId)).fetchFirst());
    }

    /**
     * 대화를 만든다(두 사람의 쪽지함 줄과 함께). 같은 짝의 대화가 이미 있으면(동시에 첫 쪽지를 보냈다) 아무것도 하지 않는다.
     *
     * <p>대화 줄이 Native SQL 인 이유: {@code INSERT … ON CONFLICT (user_low_id, user_high_id) DO NOTHING} — 진 쪽은 위반 예외 없이 0 줄로 안다(그 뒤 이긴 쪽의 대화를 읽는다).
     * 쪽지함 두 줄은 엔티티로 넣는다(작은 쪽 → 큰 쪽 차례, 그 자리에서 INSERT).
     *
     * @return 이번에 만들었으면 true
     */
    public boolean createThread(long threadId, long a, long b, Instant now) {
        long low = Math.min(a, b);
        long high = Math.max(a, b);
        int made = jdbc.update("insert into dm_threads (id, user_low_id, user_high_id, created_at) values (?, ?, ?, ?) on conflict (user_low_id, user_high_id) do nothing",
                threadId, low, high, Timestamp.from(now));
        if (made == 0) {
            return false;
        }
        boxes.persistAllAndFlush(List.of(DmBoxEntity.open(threadId, low, high), DmBoxEntity.open(threadId, high, low)));
        return true;
    }

    /** 대화 행을 잠근다(같은 대화의 동시 전송·보관 정리가 줄 선다 — {@code FOR NO KEY UPDATE}, 보관 정리의 대화 삭제와도 막힌다) */
    public void lockThread(long threadId) {
        query.selectFrom(T).where(T.id.eq(threadId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch();
    }

    /** 쪽지를 넣는다 — 그 자리에서 INSERT 한다 */
    public void insertMessage(long id, long threadId, long senderId, String body, Instant now) {
        messages.persistAndFlush(DmMessageEntity.of(id, threadId, senderId, body, now));
    }

    /** 보낸 뒤 — 대화의 마지막 쪽지·보낸 쪽 표시, 두 쪽지함의 마지막 쪽지, 보낸 사람의 읽음 위치(보낸 쪽지까지) */
    public void afterSend(long threadId, long senderId, long messageId, Instant now) {
        query.update(T).set(T.lastMessageId, messageId).set(T.lastMessageAt, now)
                .set(T.lowSent, new CaseBuilder().when(T.userLowId.eq(senderId)).then(true).otherwise(T.lowSent))
                .set(T.highSent, new CaseBuilder().when(T.userHighId.eq(senderId)).then(true).otherwise(T.highSent))
                .where(T.id.eq(threadId)).execute();
        query.update(B).set(B.lastMessageId, messageId)
                .set(B.readUpTo, new CaseBuilder().when(B.userId.eq(senderId)).then(greatest(B.readUpTo, Expressions.constant(messageId))).otherwise(B.readUpTo))
                .where(B.threadId.eq(threadId)).execute();
    }

    /** 쪽지함 한 줄(마지막 쪽지 포함) */
    public record InboxRow(Box box, long lastSenderId, String lastBody, Instant lastAt) {
    }

    /** 내 쪽지함 — 보이는 쪽지가 있는 대화만, 마지막 쪽지가 최근인 것이 위. {@code beforeLastId} 는 앞 쪽의 마지막 줄의 마지막 쪽지 ID */
    public List<InboxRow> inbox(long userId, Long beforeLastId, int limit) {
        BooleanExpression page = beforeLastId == null ? null : B.lastMessageId.lt(beforeLastId);
        return query.select(BOX, M.senderId, M.body, M.createdAt).from(B).join(T).on(T.id.eq(B.threadId)).join(M).on(M.id.eq(B.lastMessageId))
                .where(B.userId.eq(userId), B.lastMessageId.gt(B.clearedUpTo), page)
                .orderBy(B.lastMessageId.desc()).limit(limit).fetch().stream()
                .map(row -> new InboxRow(row.get(BOX), row.get(M.senderId), row.get(M.body), row.get(M.createdAt))).toList();
    }

    /** 대화마다 상대가 보낸 안 읽은 쪽지 수(내가 지운 이전 쪽지는 세지 않는다) */
    public Map<Long, Integer> unreadCounts(long userId, Collection<Long> threadIds) {
        Map<Long, Integer> out = new HashMap<>();
        if (threadIds.isEmpty()) {
            return out;
        }
        NumberExpression<Long> n = M.id.count();
        for (Tuple row : query.select(B.threadId, n).from(B)
                .join(M).on(M.threadId.eq(B.threadId), M.id.gt(greatest(B.readUpTo, B.clearedUpTo)), M.senderId.ne(B.userId))
                .where(B.userId.eq(userId), B.threadId.in(threadIds)).groupBy(B.threadId).fetch()) {
            out.put(row.get(B.threadId), Math.toIntExact(row.get(n)));
        }
        return out;
    }

    /** 안 읽은 쪽지가 있는 대화 수 — {@code cap} 까지만 센다(색인 {@code ix_dm_boxes_inbox} 안에서, {@code cap} 줄에서 멈춘다) */
    public int unreadThreads(long userId, int cap) {
        return query.select(B.threadId).from(B).where(B.userId.eq(userId), B.lastMessageId.gt(greatest(B.readUpTo, B.clearedUpTo))).limit(cap).fetch().size();
    }

    /** 쪽지 한 통 */
    public record MessageRow(long id, long senderId, String body, Instant createdAt) {
    }

    /** 대화방 — {@code afterId}(내가 지운 지점) 뒤의 쪽지, 최근이 앞. {@code beforeId} 는 커서(그보다 오래된 쪽) */
    public List<MessageRow> messages(long threadId, long afterId, Long beforeId, int limit) {
        return query.select(Projections.constructor(MessageRow.class, M.id, M.senderId, M.body, M.createdAt)).from(M)
                .where(M.threadId.eq(threadId), M.id.gt(afterId), beforeId == null ? null : M.id.lt(beforeId))
                .orderBy(M.id.desc()).limit(limit).fetch();
    }

    /** 지금까지 온 것을 읽음으로(내려가지 않는다) */
    public void markRead(long userId, long threadId) {
        query.update(B).set(B.readUpTo, greatest(B.readUpTo, B.lastMessageId)).where(B.userId.eq(userId), B.threadId.eq(threadId)).execute();
    }

    /** 내 쪽지함에서 지운다 — 지금 마지막 쪽지까지 안 보이게, 읽음도 그 자리까지 */
    public void clear(long userId, long threadId) {
        query.update(B).set(B.clearedUpTo, greatest(B.clearedUpTo, B.lastMessageId)).set(B.readUpTo, greatest(B.readUpTo, B.lastMessageId))
                .where(B.userId.eq(userId), B.threadId.eq(threadId)).execute();
    }

    /** 양쪽 쪽지함 모두에서 지운 대화면 원문을 지운다(대화·쪽지함 줄은 남는다 — 새 상대 판정·링크 허용이 그대로) */
    public int purgeIfBothCleared(long threadId) {
        QDmBoxEntity open = new QDmBoxEntity("openBox");
        return Math.toIntExact(query.delete(M).where(M.threadId.eq(threadId),
                JPAExpressions.selectOne().from(open).where(open.threadId.eq(threadId), open.clearedUpTo.lt(open.lastMessageId)).notExists()).execute());
    }

    // ── 사람 ───────────────────────────────────────────────────────────────

    /**
     * 상대 회원의 지금 값 — {@code gone} 이면 탈퇴·삭제(표시는 「탈퇴한 회원」).
     */
    public record Person(long id, String nickname, String gradeKey, boolean gone, boolean withdrawing) {

        /** 쪽지를 받을 수 없는가 — 탈퇴했거나 탈퇴 진행 중(표시는 그대로 닉네임, 보내기만 막는다 — member-withdrawal-contract) */
        public boolean unreachable() {
            return gone || withdrawing;
        }
    }

    /** 회원·프로필만 읽는다(QueryDSL) — 프로필이 없으면 탈퇴·삭제로 본다 */
    public Map<Long, Person> people(Collection<Long> ids) {
        Map<Long, Person> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Tuple row : query.select(USER.id, PROFILE.nickname, PROFILE.gradeKey, USER.status, USER.deletedAt, PROFILE.userId).from(USER)
                .leftJoin(PROFILE).on(PROFILE.userId.eq(USER.id)).where(USER.id.in(ids)).fetch()) {
            long id = row.get(USER.id);
            UserStatus status = row.get(USER.status);
            boolean gone = status == UserStatus.WITHDRAWN || row.get(USER.deletedAt) != null || row.get(PROFILE.userId) == null;
            out.put(id, new Person(id, row.get(PROFILE.nickname), row.get(PROFILE.gradeKey), gone, status == UserStatus.WITHDRAWING));
        }
        return out;
    }

    /** 받는 사람 찾기 한 줄 — {@code threadId} 는 두 사람 사이의 대화(없으면 null) */
    public record RecipientRow(long id, String nickname, String gradeKey, Long threadId) {
    }

    /**
     * 닉네임에 검색어가 들어 있는 회원 — 앞부분이 같은 것 먼저, 그다음 닉네임 순. 나·탈퇴한 회원·차단 관계(어느 쪽이든)는 뺀다.
     * 회원 찾기(정렬·개수는 DB) 뒤 찾은 회원과의 대화 번호(쪽지함)를 한 번 더 읽는다.
     *
     * @param needle 소문자로 바꾼 검색어(LIKE 의 %·_·\ 는 부르는 쪽이 이스케이프하지 않는다 — 여기서 한다)
     */
    public List<RecipientRow> recipients(long userId, String needle, int limit) {
        String escaped = needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        BooleanExpression blocked = JPAExpressions.selectOne().from(BLOCK)
                .where(BLOCK.userId.eq(userId).and(BLOCK.targetId.eq(PROFILE.userId)).or(BLOCK.userId.eq(PROFILE.userId).and(BLOCK.targetId.eq(userId)))).exists();
        NumberExpression<Integer> prefixFirst = new CaseBuilder().when(PROFILE.nickname.lower().like(escaped + "%", '\\')).then(1).otherwise(0);
        List<Tuple> found = query.select(PROFILE.userId, PROFILE.nickname, PROFILE.gradeKey).from(PROFILE).join(USER).on(USER.id.eq(PROFILE.userId))
                .where(USER.status.notIn(UserStatus.WITHDRAWN, UserStatus.WITHDRAWING), USER.deletedAt.isNull(), PROFILE.userId.ne(userId),
                        PROFILE.nickname.lower().like("%" + escaped + "%", '\\'), blocked.not())
                .orderBy(prefixFirst.desc(), PROFILE.nickname.lower().asc(), PROFILE.userId.asc()).limit(limit).fetch();
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> threads = threadsWith(userId, found.stream().map(row -> row.get(PROFILE.userId)).toList());
        return found.stream().map(row -> {
            long id = row.get(PROFILE.userId);
            return new RecipientRow(id, row.get(PROFILE.nickname), row.get(PROFILE.gradeKey), threads.get(id));
        }).toList();
    }

    /** 내 쪽지함에서 그 상대들과의 대화 번호(상대 → 대화) — 한 짝에 대화는 하나다 */
    private Map<Long, Long> threadsWith(long userId, Collection<Long> partnerIds) {
        Map<Long, Long> out = new HashMap<>();
        for (Tuple row : query.select(B.partnerId, B.threadId).from(B).where(B.userId.eq(userId), B.partnerId.in(partnerIds)).fetch()) {
            out.put(row.get(B.partnerId), row.get(B.threadId));
        }
        return out;
    }

    /**
     * 이 대화의 내 쪽지 알림(회원 알림 {@code DM}, 덧붙임 {@code threadId})을 읽음으로 — 대화를 읽음·지움·답장할 때. 덧붙임의 대화 번호는 PostgreSQL 함수 {@code jsonb_extract_path_text}(HQL 이 이름 그대로 내려보낸다)
     * ({@code payload ->> 'threadId'} 와 같은 글자 값)로 견준다.
     */
    public int markDmNotificationsRead(long userId, long threadId, Instant now) {
        return Math.toIntExact(query.update(NOTIFICATION).set(NOTIFICATION.readAt, now)
                .where(NOTIFICATION.userId.eq(userId), NOTIFICATION.type.eq("DM"), NOTIFICATION.readAt.isNull(),
                        Expressions.stringTemplate("jsonb_extract_path_text({0}, 'threadId')", NOTIFICATION.payload).eq(Long.toString(threadId)))
                .execute());
    }

    /** {@code greatest(a, b)} — 읽음·지움 위치는 내려가지 않는다 */
    private static NumberExpression<Long> greatest(Expression<Long> a, Expression<Long> b) {
        return Expressions.numberTemplate(Long.class, "greatest({0}, {1})", a, b);
    }
}
