package com.community.rewardevent.repository.master;

import com.community.comment.entity.QCommentEntity;
import com.community.grade.entity.QGradeEntity;
import com.community.identity.entity.QIdentityVerificationEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.post.entity.QPostEntity;
import com.community.rewardevent.entity.QRewardEventRecipientEntity;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.jpa.Ilike;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.community.zconfiguration.time.ServiceTime;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 보상 이벤트가 읽는 회원·운영자·글·댓글·등급 — {@code com.community.rewardevent.service} 만 부른다(읽기만). QueryDSL 이다(네이티브 없음).
 *
 * <p><b>대상 규칙</b>(event-admin-contract 「대상 규칙」): 정상 회원 = {@code status = ACTIVE} 이고 지워지지 않음. 정지({@code SUSPENDED}·{@code BANNED})·탈퇴({@code WITHDRAWING}·
 * {@code WITHDRAWN}·지움)는 늘 뺀다. 운영자 회원 계정은 부르는 쪽이 넘기는 번호 묶음으로 뺀다(운영자 수만큼 — {@code OperatorMemberAccountService}).
 */
@Repository
public class RewardMemberQueries extends RoboyamRepositoryQDSL {

    /** 회원 한 명 — 닉네임 · 이메일(원문 — 가리기는 부르는 쪽) · 상태 · 지움 */
    public record MemberRow(long id, String nickname, String email, UserStatus status, boolean deleted) {

        /** 탈퇴(진행 중 포함)·지움 */
        public boolean withdrawn() {
            return deleted || status == UserStatus.WITHDRAWN || status == UserStatus.WITHDRAWING;
        }

        /** 정지(기간·영구) */
        public boolean suspended() {
            return !withdrawn() && (status == UserStatus.SUSPENDED || status == UserStatus.BANNED);
        }

        public boolean active() {
            return !deleted && status == UserStatus.ACTIVE;
        }
    }

    /** 글 한 개 — 제목 · 작성자 · 작성자 닉네임 */
    public record PostRow(long id, String title, Long authorId, String authorNickname) {
    }

    /** 일괄 조건(표시 시간대 날짜 범위 · 최근 로그인 일수) */
    public record BulkCondition(boolean all, Integer minGradeLevel, LocalDate joinedFrom, LocalDate joinedTo, boolean identityOnly, Integer activeWithinDays) {
    }

    private static final QUserEntity U = QUserEntity.userEntity;
    private static final QProfileEntity P = QProfileEntity.profileEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;
    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QCommentEntity C = QCommentEntity.commentEntity;
    private static final QGradeEntity G = QGradeEntity.gradeEntity;
    private static final QIdentityVerificationEntity IV = QIdentityVerificationEntity.identityVerificationEntity;
    private static final QRewardEventRecipientEntity R = QRewardEventRecipientEntity.rewardEventRecipientEntity;

    public RewardMemberQueries(JPAQueryFactory query) {
        super(query);
    }

    // ── 회원 ────────────────────────────────────────────────────────────────

    public Optional<MemberRow> member(long id) {
        return Optional.ofNullable(members(List.of(id)).get(id));
    }

    public Map<Long, MemberRow> members(Collection<Long> ids) {
        Map<Long, MemberRow> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Tuple t : memberSelect().where(U.id.in(ids)).fetch()) {
            MemberRow row = rowOf(t);
            out.put(row.id(), row);
        }
        return out;
    }

    /** 회원 찾기 — 닉네임·이메일 일부 또는 회원 번호(탈퇴·지운 회원 빼고), 번호 내림차순 */
    public List<MemberRow> search(String q, int limit) {
        BooleanExpression text = Ilike.contains(P.nickname, q).or(Ilike.contains(U.email, q));
        if (q.matches("^[0-9]{1,19}$")) {
            text = text.or(U.id.stringValue().contains(q));
        }
        return memberSelect().where(text, U.deletedAt.isNull(), U.status.notIn(UserStatus.WITHDRAWN, UserStatus.WITHDRAWING)).orderBy(U.id.desc()).limit(limit).fetch()
                .stream().map(RewardMemberQueries::rowOf).toList();
    }

    /** 붙여 넣은 한 줄 → 회원(번호 · 이메일 · 닉네임 그대로 — 대소문자 무시) */
    public Optional<MemberRow> resolve(String line) {
        BooleanExpression where;
        if (line.matches("^[0-9]{1,19}$")) {
            where = U.id.eq(Long.parseLong(line));
        } else if (line.contains("@")) {
            where = U.email.lower().eq(line.toLowerCase(Locale.ROOT));
        } else {
            where = P.nickname.lower().eq(line.toLowerCase(Locale.ROOT));
        }
        return memberSelect().where(where).orderBy(U.id.asc()).fetch().stream().findFirst().map(RewardMemberQueries::rowOf);
    }

    private com.querydsl.jpa.impl.JPAQuery<Tuple> memberSelect() {
        return query.select(U.id, P.nickname, U.email, U.status, U.deletedAt).from(U).leftJoin(P).on(P.userId.eq(U.id));
    }

    private static MemberRow rowOf(Tuple t) {
        return new MemberRow(t.get(U.id), t.get(P.nickname), t.get(U.email), t.get(U.status), t.get(U.deletedAt) != null);
    }

    /** 운영자 이름 — 번호 → 이름(지운 운영자는 없다) */
    public Map<Long, String> operatorNames(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(O.id, O.operatorName).from(O).where(O.id.in(ids)).fetch()) {
            out.put(t.get(O.id), t.get(O.operatorName));
        }
        return out;
    }

    /** 등급 목록 — 서열 차례 */
    public List<Tuple> grades() {
        return query.select(G.level, G.label, G.gradeKey).from(G).orderBy(G.level.asc()).fetch();
    }

    public static int gradeLevel(Tuple t) {
        return t.get(G.level);
    }

    public static String gradeLabel(Tuple t) {
        return t.get(G.label);
    }

    /** 판매 중인 로보티콘 팩 한 개 — 출처 · 가격 · 상점의 판 이름 */
    public record PackOption(long id, String source, int price, String name) {
    }

    /** 판매 중(APPROVED) 팩 — 번호 차례(보상 고르기용) */
    public List<PackOption> approvedPacks() {
        com.community.emoticon.entity.QEmoticonPackEntity k = com.community.emoticon.entity.QEmoticonPackEntity.emoticonPackEntity;
        com.community.emoticon.entity.QEmoticonPackRevisionEntity v = com.community.emoticon.entity.QEmoticonPackRevisionEntity.emoticonPackRevisionEntity;
        return query.select(k.id, k.source, k.price, v.name).from(k).leftJoin(v).on(v.id.eq(k.liveRevisionId))
                .where(k.status.eq(com.community.emoticon.entity.EmoticonPackStatus.APPROVED)).orderBy(k.id.asc()).fetch().stream()
                .map(t -> new PackOption(t.get(k.id), t.get(k.source).name(), t.get(k.price), t.get(v.name))).toList();
    }

    // ── 일괄 대상 ───────────────────────────────────────────────────────────

    /**
     * 일괄 대상의 다음 덩어리 — 회원 번호 {@code after} 뒤로 {@code limit} 명(번호 차례 · 키셋). 정상 회원 · 운영자 회원 계정 빼기 · 조건 · 이 이벤트에 아직 없는 회원.
     */
    public List<Long> bulkTargets(BulkCondition c, Set<Long> excludedUsers, long eventId, long after, int limit, Instant now) {
        BooleanExpression where = targetWhere(c, excludedUsers, now).and(U.id.gt(after))
                .and(JPAExpressions.selectOne().from(R).where(R.eventId.eq(eventId), R.userId.eq(U.id)).notExists());
        return query.select(U.id).from(U).leftJoin(P).on(P.userId.eq(U.id)).where(where).orderBy(U.id.asc()).limit(limit).fetch();
    }

    /** 일괄 대상 수(미리 보기) */
    public long countBulkTargets(BulkCondition c, Set<Long> excludedUsers, Instant now) {
        Long n = query.select(U.count()).from(U).leftJoin(P).on(P.userId.eq(U.id)).where(targetWhere(c, excludedUsers, now)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 늘 빼는 회원 수(정지·탈퇴 — 지운 회원 포함) */
    public long countNotActive() {
        Long n = query.select(U.count()).from(U).where(U.status.ne(UserStatus.ACTIVE).or(U.deletedAt.isNotNull())).fetchOne();
        return n == null ? 0 : n;
    }

    /** 그 회원들 중 정상 회원 수 */
    public long countActive(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        Long n = query.select(U.count()).from(U).where(U.id.in(ids), U.status.eq(UserStatus.ACTIVE), U.deletedAt.isNull()).fetchOne();
        return n == null ? 0 : n;
    }

    private BooleanExpression targetWhere(BulkCondition c, Set<Long> excludedUsers, Instant now) {
        BooleanExpression where = U.status.eq(UserStatus.ACTIVE).and(U.deletedAt.isNull());
        if (!excludedUsers.isEmpty()) {
            where = where.and(U.id.notIn(excludedUsers));
        }
        if (c.all()) {
            return where;
        }
        if (c.minGradeLevel() != null) {
            where = where.and(P.gradeKey.in(JPAExpressions.select(G.gradeKey).from(G).where(G.level.goe(c.minGradeLevel()))));
        }
        if (c.joinedFrom() != null) {
            where = where.and(U.createdAt.goe(c.joinedFrom().atStartOfDay(ServiceTime.ZONE).toInstant()));
        }
        if (c.joinedTo() != null) {
            where = where.and(U.createdAt.lt(c.joinedTo().plusDays(1).atStartOfDay(ServiceTime.ZONE).toInstant()));
        }
        if (c.identityOnly()) {
            where = where.and(JPAExpressions.selectOne().from(IV).where(IV.userId.eq(U.id)).exists());
        }
        if (c.activeWithinDays() != null) {
            where = where.and(U.lastLoginAt.goe(now.minus(Duration.ofDays(c.activeWithinDays()))));
        }
        return where;
    }

    // ── 글 · 댓글 ───────────────────────────────────────────────────────────

    /** 지우지 않은 글 */
    public Optional<PostRow> post(long id) {
        Tuple t = query.select(POST.id, POST.title, POST.authorId, P.nickname).from(POST).leftJoin(P).on(P.userId.eq(POST.authorId))
                .where(POST.id.eq(id), POST.deletedAt.isNull()).fetchFirst();
        return Optional.ofNullable(t).map(r -> new PostRow(r.get(POST.id), r.get(POST.title), r.get(POST.authorId), r.get(P.nickname)));
    }

    /** 글 제목들(지운 글도 — 추첨 기록·상세의 글 이름) */
    public Map<Long, String> postTitles(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Tuple t : query.select(POST.id, POST.title).from(POST).where(POST.id.in(ids)).fetch()) {
            out.put(t.get(POST.id), t.get(POST.title));
        }
        return out;
    }

    /** 그 글의 기간 안 댓글 수(지운 댓글 제외, 비회원 포함) — 기간은 [from, to] */
    public long commentCount(long postId, Instant from, Instant to) {
        Long n = query.select(C.count()).from(C).where(C.postId.eq(postId), C.deletedAt.isNull(), C.createdAt.goe(from), C.createdAt.loe(to)).fetchOne();
        return n == null ? 0 : n;
    }

    /** 그 글의 기간 안 댓글을 쓴 회원(1인 1회 — 지운 댓글·비회원 제외) — 회원 번호 오름차순 */
    public List<Long> commenters(long postId, Instant from, Instant to) {
        return query.select(C.authorId).distinct().from(C)
                .where(C.postId.eq(postId), C.deletedAt.isNull(), C.authorId.isNotNull(), C.createdAt.goe(from), C.createdAt.loe(to)).orderBy(C.authorId.asc()).fetch();
    }
}
