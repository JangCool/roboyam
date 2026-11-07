package com.community.ad.repository.master;

import com.community.ad.entity.AdAdvertiserEntity;
import com.community.ad.entity.AdAdvertiserEventEntity;
import com.community.ad.entity.AdAdvertiserViewEntity;
import com.community.ad.entity.QAdAdvertiserEntity;
import com.community.ad.entity.QAdAdvertiserEventEntity;
import com.community.ad.entity.QAdAdvertiserViewEntity;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.QBizApplicationEntity;
import com.community.business.entity.BusinessProfileEntity;
import com.community.business.entity.BusinessStatus;
import com.community.business.entity.QBusinessProfileEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.Ilike;
import com.community.zconfiguration.jpa.JpaStoreWrites;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.community.zconfiguration.jpa.RowLocks;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.jpa.impl.JPAUpdateClause;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 계정 없는 광고주(V115 {@code ad_advertisers}, 보관·파기 V131) + 연락처 평문 열람 기록 + 처리 기록(V131 {@code ad_advertiser_events}).
 * 목록·검색은 가린 값만 읽고 암호문은 열람 때만 꺼낸다. 사업자 검색은 승인된 사업자 신원(상호·등록번호 끝 4자리)을 같이 본다. QueryDSL 만 쓴다.
 */
@Repository
public class AdAdvertiserStore extends RoboyamRepositoryQDSL {

    private static final QAdAdvertiserEntity A = QAdAdvertiserEntity.adAdvertiserEntity;
    private static final QAdAdvertiserViewEntity V = QAdAdvertiserViewEntity.adAdvertiserViewEntity;
    private static final QAdAdvertiserEventEntity E = QAdAdvertiserEventEntity.adAdvertiserEventEntity;
    private static final QBusinessProfileEntity P = QBusinessProfileEntity.businessProfileEntity;
    private static final QBizApplicationEntity APP = QBizApplicationEntity.bizApplicationEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;

    /** 광고주 한 줄(가린 값) — 사업자와 연결됐으면 그 상호·등록번호 끝 4자리. 보관(V131)·연락처 파기 시각 */
    public record AdvertiserRow(long id, String name, Long businessProfileId, String companyName, String bizNoLast4, String contactNameMasked,
                                String contactPhoneMasked, String emailMasked, String memo, Instant createdAt, Instant archivedAt, Instant contactPurgedAt) {

        /** 광고 담당자 칸이 하나라도 있나(사업자 광고주의 연락처 원천 — ad-advertiser-contract 규칙 8) */
        public boolean hasContact() {
            return contactNameMasked != null || contactPhoneMasked != null || emailMasked != null;
        }
    }

    /** 새 광고주 — 암호문과 가린 값을 같이(서비스가 만든다) */
    public record NewAdvertiser(long id, String name, Long businessProfileId, String contactNameEnc, String contactPhoneEnc, String emailEnc, String contactNameMasked,
                                String contactPhoneMasked, String emailMasked, String memo, long createdBy, Instant now) {
    }

    /** 고치기 — 칸마다 그대로({@code keep*} true)·새 값(암호문 + 가린 값, 지우기면 둘 다 null). 이름·메모는 평문 */
    public record Change(String name, boolean keepContactName, String contactNameEnc, String contactNameMasked, boolean keepPhone, String contactPhoneEnc,
                         String contactPhoneMasked, boolean keepEmail, String emailEnc, String emailMasked, boolean keepMemo, String memo) {
    }

    /** 암호문(열람 때만) */
    public record Secret(String contactNameEnc, String contactPhoneEnc, String emailEnc) {
    }

    /** 사업자 신원 연락처 암호문(대표자·연락처·이메일) — 파기된 신원은 셋 다 null */
    public record BusinessSecret(long profileId, String ownerNameEnc, String phoneEnc, String emailEnc) {
    }

    /** 광고주와 연결되지 않은 승인 사업자(검색 결과) */
    public record BusinessHit(long profileId, String companyName, String bizNoLast4) {
    }

    /** 연락처 평문 열람 기록 한 줄(최근 차례) — 운영자 이름(없어진 운영자는 null) */
    public record ViewRow(Instant at, Long operatorId, String operatorName, String reason) {
    }

    /** 처리 기록 한 줄(최근 차례) — {@code fields} 는 바뀐 칸 이름 */
    public record EventRow(long id, Instant at, String action, Long actorId, String actorName, List<String> fields) {
    }

    /** 광고주 한 줄(가린 값 + 연결된 사업자 상호·끝 4자리) — 관리자 광고주 읽기도 같은 모양을 쓴다 */
    static final Expression<AdvertiserRow> ROW = Projections.constructor(AdvertiserRow.class, A.id, A.name, A.businessProfileId, P.companyName, P.bizNoLast4,
            A.contactNameMasked, A.contactPhoneMasked, A.emailMasked, A.memo, A.createdAt, A.archivedAt, A.contactPurgedAt);

    private static final Expression<BusinessHit> HIT = Projections.constructor(BusinessHit.class, P.id, P.companyName, P.bizNoLast4);

    private final EntityManager entityManager;

    public AdAdvertiserStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    /** 광고주 줄 + 연결된 사업자(왼쪽 조인) — 관리자 광고주 읽기도 쓴다 */
    static JPAQuery<AdvertiserRow> advertisers(JPAQueryFactory query) {
        return query.select(ROW).from(A).leftJoin(P).on(P.id.eq(A.businessProfileId));
    }

    public void insert(NewAdvertiser a) {
        JpaStoreWrites.insert(entityManager, AdAdvertiserEntity.create(a.id(), a.name(), a.businessProfileId(), a.contactNameEnc(), a.contactPhoneEnc(), a.emailEnc(),
                a.contactNameMasked(), a.contactPhoneMasked(), a.emailMasked(), a.memo(), a.createdBy(), a.now()));
    }

    public Optional<AdvertiserRow> find(long id) {
        return Optional.ofNullable(advertisers(query).where(A.id.eq(id)).fetchFirst());
    }

    public Map<Long, AdvertiserRow> findAll(Collection<Long> ids) {
        Map<Long, AdvertiserRow> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        advertisers(query).where(A.id.in(ids)).fetch().forEach(row -> out.put(row.id(), row));
        return out;
    }

    /** 사업자 신원에 연결된 광고주 */
    public Optional<AdvertiserRow> byProfile(long profileId) {
        return Optional.ofNullable(advertisers(query).where(A.businessProfileId.eq(profileId)).fetchFirst());
    }

    /** 이름이 같은 광고주가 있나(대소문자·앞뒤 공백 무시 — 유일 인덱스와 같은 기준) */
    public boolean nameTaken(String name) {
        return query.selectOne().from(A).where(sameName(name)).fetchFirst() != null;
    }

    /** 이름이 같은 다른 광고주가 있나(자기 자신 빼고) */
    public boolean nameTakenByOther(String name, long selfId) {
        return query.selectOne().from(A).where(sameName(name), A.id.ne(selfId)).fetchFirst() != null;
    }

    /** 광고주 검색 — 이름(연결된 사업자면 상호·등록번호 끝 4자리도), 이름 차례, 최대 {@code limit}. 보관한 광고주는 빠진다(ad-advertiser-contract 규칙 6) */
    public List<AdvertiserRow> search(String q, int limit) {
        JPAQuery<AdvertiserRow> select = advertisers(query).where(A.archivedAt.isNull());
        if (q != null && !q.isBlank()) {
            String term = q.strip();
            select.where(Ilike.contains(A.name, term).or(Ilike.contains(P.companyName, term)).or(P.bizNoLast4.eq(term))); // 예전 ILIKE 그대로
        }
        return select.orderBy(A.name.asc(), A.id.asc()).limit(limit).fetch();
    }

    /** 광고주와 연결되지 않은 승인 사업자 검색 — 상호·등록번호 끝 4자리 */
    public List<BusinessHit> searchBusinesses(String q, int limit) {
        QAdAdvertiserEntity linked = new QAdAdvertiserEntity("linked");
        JPAQuery<BusinessHit> select = query.select(HIT).from(P)
                .where(P.status.eq(BusinessStatus.APPROVED), JPAExpressions.selectOne().from(linked).where(linked.businessProfileId.eq(P.id)).notExists());
        if (q != null && !q.isBlank()) {
            String term = q.strip();
            select.where(Ilike.contains(P.companyName, term).or(P.bizNoLast4.eq(term))); // 예전 ILIKE 그대로
        }
        return select.orderBy(P.companyName.asc(), P.id.asc()).limit(limit).fetch();
    }

    /** 승인 사업자 한 곳(수동 등록 — 상호·끝 4자리 스냅샷). 승인 상태가 아니면 비어 있다 */
    public Optional<BusinessHit> approvedBusiness(long profileId) {
        return Optional.ofNullable(query.select(HIT).from(P).where(P.id.eq(profileId), P.status.eq(BusinessStatus.APPROVED)).fetchFirst());
    }

    /** 사업자 신원의 상호·끝 4자리(상태 무관 — 연결 확인용) */
    public Optional<BusinessHit> business(long profileId) {
        return Optional.ofNullable(query.select(HIT).from(P).where(P.id.eq(profileId)).fetchFirst());
    }

    public Optional<Secret> secret(long id) {
        return Optional.ofNullable(query.select(Projections.constructor(Secret.class, A.contactNameEnc, A.contactPhoneEnc, A.emailEnc)).from(A).where(A.id.eq(id))
                .fetchFirst());
    }

    /** 사업자 신원 연락처 암호문 — 없는 신원은 비어 있다(파기된 신원은 칸이 null) */
    public Optional<BusinessSecret> businessSecret(long profileId) {
        Tuple t = query.select(P.id, P.ownerNameEnc, P.phoneEnc, P.emailEnc, P.erasedAt).from(P).where(P.id.eq(profileId)).fetchFirst();
        if (t == null) {
            return Optional.empty();
        }
        return Optional.of(t.get(P.erasedAt) != null ? new BusinessSecret(t.get(P.id), null, null, null)
                : new BusinessSecret(t.get(P.id), t.get(P.ownerNameEnc), t.get(P.phoneEnc), t.get(P.emailEnc)));
    }

    /** 사업자 신원의 이메일 암호문(긴급 내림 메일 — 지운 신원은 비어 있다) */
    public Optional<String> businessEmailEnc(long profileId) {
        return Optional.ofNullable(query.select(P.emailEnc).from(P).where(P.id.eq(profileId), P.emailEnc.isNotNull()).fetchFirst());
    }

    /** 평문 열람 기록 — 광고주 줄(직접 등록) */
    public void insertView(long id, long advertiserId, long operatorId, String reason, String ip, Instant now) {
        JpaStoreWrites.insert(entityManager, AdAdvertiserViewEntity.direct(id, advertiserId, operatorId, reason, ip, now));
    }

    /** 평문 열람 기록 — 사업자 광고주(키 = 사업자 신원), {@code source} ADVERTISER(광고 담당자 칸)·BUSINESS(신원 대표자·연락처·이메일) */
    public void insertBusinessView(long id, long profileId, String source, long operatorId, String reason, String ip, Instant now) {
        JpaStoreWrites.insert(entityManager, AdAdvertiserViewEntity.business(id, profileId, source, operatorId, reason, ip, now));
    }

    public int countViews(long advertiserId) {
        Long n = query.select(V.count()).from(V).where(V.advertiserId.eq(advertiserId)).fetchOne();
        return n == null ? 0 : Math.toIntExact(n);
    }

    /** 열람 기록 최근 {@code limit} — 광고주 줄(예전 기록 포함)·사업자 신원 중 준 것(둘 다 주면 합친다) */
    public List<ViewRow> views(Long advertiserId, Long profileId, int limit) {
        return query.select(Projections.constructor(ViewRow.class, V.createdAt, V.operatorId, O.operatorName, V.reason)).from(V).leftJoin(O).on(O.id.eq(V.operatorId))
                .where(V.advertiserId.eq(advertiserId == null ? -1L : advertiserId).or(V.businessProfileId.eq(profileId == null ? -1L : profileId)))
                .orderBy(V.createdAt.desc(), V.id.desc()).limit(limit).fetch();
    }

    // ── 광고주 관리(V131 — 고치기 · 보관 · 삭제 · 처리 기록) ──

    /** 광고주 줄 잠그기(고치기·보관·삭제를 한 줄씩) — 없으면 false */
    public boolean lock(long id) {
        return RowLocks.forUpdate(entityManager, AdAdvertiserEntity.class, id);
    }

    /** 사업자 신원 줄 잠그기(사업자 광고주의 광고주 줄을 처음 만들 때 둘이 겹치지 않게) — 없으면 false */
    public boolean lockProfile(long profileId) {
        return RowLocks.forUpdate(entityManager, BusinessProfileEntity.class, profileId);
    }

    public void update(long id, Change c, Instant now) {
        JPAUpdateClause update = query.update(A).set(A.updatedAt, now).set(A.name, c.name());
        if (!c.keepContactName()) {
            update.set(A.contactNameEnc, c.contactNameEnc()).set(A.contactNameMasked, c.contactNameMasked());
        }
        if (!c.keepPhone()) {
            update.set(A.contactPhoneEnc, c.contactPhoneEnc()).set(A.contactPhoneMasked, c.contactPhoneMasked());
        }
        if (!c.keepEmail()) {
            update.set(A.emailEnc, c.emailEnc()).set(A.emailMasked, c.emailMasked());
        }
        if (!c.keepMemo()) {
            update.set(A.memo, c.memo());
        }
        update.where(A.id.eq(id)).execute();
    }

    /** 보관(직접 등록만 — CHECK {@code ck_aa_archive_direct}) · {@code at} null 이면 보관 풀기 */
    public void setArchived(long id, Instant at, Long by, Instant now) {
        query.update(A).set(A.archivedAt, at).set(A.archivedBy, at == null ? null : by).set(A.updatedAt, now).where(A.id.eq(id)).execute();
    }

    /** 광고주 줄과 딸린 열람 기록·처리 기록을 지운다(신청 건이 없을 때만 부른다 — 남아 있으면 신청 건 외래키가 막는다) */
    public int delete(long id) {
        query.delete(V).where(V.advertiserId.eq(id)).execute();
        query.delete(E).where(E.advertiserId.eq(id)).execute();
        return (int) query.delete(A).where(A.id.eq(id)).execute();
    }

    /**
     * 그 광고주 줄을 가리키는 신청 건 — [0] 전체(상태 무관) · [1] 끝나지 않은 건(대기 {@code SUBMITTED}·{@code AWAITING_PAYMENT}·{@code AWAITING_DEPOSIT} + 진행·예약 =
     * {@code SCHEDULED} 이고 끝 &gt; 지금)
     */
    public long[] bookingCounts(long advertiserId, Instant now) {
        BooleanExpression open = APP.status.in(BizApplicationStatus.SUBMITTED, BizApplicationStatus.AWAITING_PAYMENT, BizApplicationStatus.AWAITING_DEPOSIT)
                .or(APP.status.eq(BizApplicationStatus.SCHEDULED).and(APP.endAt.gt(now)));
        NumberExpression<Long> openCount = Expressions.numberTemplate(Long.class, "sum({0})", new CaseBuilder().when(open).then(1L).otherwise(0L));
        Tuple t = query.select(APP.count(), openCount).from(APP).where(APP.advertiserId.eq(advertiserId)).fetchOne();
        long all = t == null || t.get(0, Long.class) == null ? 0 : t.get(0, Long.class);
        Long live = t == null ? null : t.get(1, Long.class);
        return new long[] {all, live == null ? 0 : live};
    }

    public void insertEvent(long id, long advertiserId, String action, Long actorId, List<String> fields, Instant now) {
        JpaStoreWrites.insert(entityManager, AdAdvertiserEventEntity.create(id, advertiserId, action, actorId,
                fields == null || fields.isEmpty() ? null : String.join(",", fields), now));
    }

    /** 처리 기록(최근 차례, 최대 {@code limit}) */
    public List<EventRow> events(long advertiserId, int limit) {
        return query.select(E.id, E.createdAt, E.action, E.actorId, O.operatorName, E.fields).from(E).leftJoin(O).on(O.id.eq(E.actorId))
                .where(E.advertiserId.eq(advertiserId)).orderBy(E.createdAt.desc(), E.id.desc()).limit(limit).fetch().stream()
                .map(t -> new EventRow(t.get(E.id), t.get(E.createdAt), t.get(E.action), t.get(E.actorId), t.get(O.operatorName),
                        t.get(E.fields) == null ? List.of() : List.of(t.get(E.fields).split(","))))
                .toList();
    }

    /** 이름 비교 — {@code lower(btrim(name))}(유일 인덱스 ux_ad_advertisers_name 과 같은 식 — HQL {@code trim(both from …)} 은 공백만 깎는 {@code btrim} 이다. {@code trim(?)} 꼴은 HQL 이 매개변수를 깎을 글자로 읽는다) */
    private static BooleanExpression sameName(String name) {
        return Expressions.stringTemplate("lower(trim(both from {0}))", A.name).eq(Expressions.stringTemplate("lower(trim(both from {0}))", name));
    }
}
