package com.community.emoticon.repository.master;

import com.community.emoticon.entity.EmoticonEntity;
import com.community.emoticon.entity.EmoticonPackEntity;
import com.community.emoticon.entity.EmoticonPackRevisionEntity;
import com.community.emoticon.entity.EmoticonPackSource;
import com.community.emoticon.entity.EmoticonPackStatus;
import com.community.emoticon.entity.EmoticonRevisionItemEntity;
import com.community.emoticon.entity.EmoticonSaleWish;
import com.community.emoticon.entity.EmoticonSuspendKind;
import com.community.emoticon.entity.QEmoticonEntity;
import com.community.emoticon.entity.QEmoticonImageEntity;
import com.community.emoticon.entity.QEmoticonPackEntity;
import com.community.emoticon.entity.QEmoticonPackRevisionEntity;
import com.community.emoticon.entity.QEmoticonRevisionItemEntity;
import com.community.operator.entity.QOperatorEntity;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;

/**
 * 로보티콘 팩·판·로보티콘(토큰 ID)·판의 차례 읽기·쓰기({@code emoticon_packs}·{@code emoticon_pack_revisions}·{@code emoticons}·{@code emoticon_revision_items}, V142).
 * {@code com.community.emoticon.service} 만 부른다. 트랜잭션은 부르는 서비스가 연다.
 *
 * <p>QueryDSL 이다 — 읽기는 한 줄 모양, 상태 전이·카운터는 조건부 UPDATE, 넣기는 {@code persist} + {@code flush}({@link EmoticonStoreSupport}). 팩 행 잠금은 팩 표만 고르는
 * {@code PESSIMISTIC_WRITE}({@code FOR NO KEY UPDATE} — 받기의 받은 수 · 심사 처리 · 저장 · 작가 탈퇴가 같은 팩에서 줄을 선다. 팩을 FK 로 가리키는 줄 — 판·로보티콘·심사 건·받기·
 * 구매·가격 기록·계약 동의 — 은 그 팩을 먼저 잠그거나({@code PESSIMISTIC_WRITE}·{@code PESSIMISTIC_READ}) 팩을 만드는 트랜잭션이 넣는다) · {@code PESSIMISTIC_READ}(결제 준비가 가격·판매 상태를 읽는 동안).
 * 판·팩이 서로 가리키는 외래 키는 커밋 때 확인하고(지연), 낸 판 고치기 금지·유료 가격 단계는 DB 트리거가 그대로 막는다. Native SQL 로 남긴 것은 없다.
 */
@Repository
public class EmoticonPackStore extends EmoticonStoreSupport {

    /** 팩 한 줄(판매 중단한 운영자 이름 포함 — 없으면 null). {@code suspendKind} = 판매 중단의 종류(판매 중단일 때만 — V147) */
    public record PackRow(long id, EmoticonPackSource source, Long creatorId, EmoticonPackStatus status, int price, Long liveRevisionId, int acquireCount,
                          Instant publishedAt, String suspendReason, Instant suspendedAt, Long suspendedBy, String suspendedByName, Instant createdAt, Instant updatedAt,
                          EmoticonSuspendKind suspendKind) {

        /** 작가 탈퇴로 멈춘 팩 — 되살리지 못한다(후속 결정 6) */
        public boolean creatorWithdrawn() {
            return suspendKind == EmoticonSuspendKind.CREATOR_WITHDRAWN;
        }
    }

    /** 작가 탈퇴로 멈춘 팩 한 줄 — 멈추기 전 상태·종류·사유(감사) */
    public record StoppedRow(long id, EmoticonPackStatus previousStatus, EmoticonSuspendKind previousKind, String previousReason) {
    }

    /** 판 한 줄 — {@code version} 이 null 이면 아직 내지 않은 임시 판 */
    public record RevisionRow(long id, long packId, Integer version, String name, String description, List<String> tags, Long coverImageId, EmoticonSaleWish saleWish,
                              String priceRequestMemo, Instant createdAt, Instant submittedAt) {

        public boolean sealed() {
            return version != null;
        }
    }

    /** 판의 차례 한 줄 — 로보티콘(토큰 ID)·그림·이름표 */
    public record ItemRow(long revisionId, int position, long emoticonId, long imageId, String label) {
    }

    private static final QEmoticonPackEntity P = QEmoticonPackEntity.emoticonPackEntity;
    private static final QEmoticonPackRevisionEntity R = QEmoticonPackRevisionEntity.emoticonPackRevisionEntity;
    private static final QEmoticonPackRevisionEntity R2 = new QEmoticonPackRevisionEntity("r2");
    private static final QEmoticonEntity E = QEmoticonEntity.emoticonEntity;
    private static final QEmoticonRevisionItemEntity I = QEmoticonRevisionItemEntity.emoticonRevisionItemEntity;
    private static final QEmoticonImageEntity M = QEmoticonImageEntity.emoticonImageEntity;
    private static final QOperatorEntity O = QOperatorEntity.operatorEntity;

    public EmoticonPackStore(JPAQueryFactory query, EntityManager entityManager, DataSource dataSource) {
        super(query, entityManager, dataSource);
    }

    // ── 팩 ──────────────────────────────────────────────────────────────────

    private JPAQuery<Tuple> packs() {
        return query.select(P.id, P.source, P.creatorId, P.status, P.price, P.liveRevisionId, P.acquireCount, P.publishedAt, P.suspendReason, P.suspendedAt, P.suspendedBy,
                O.operatorName, P.createdAt, P.updatedAt, P.suspendKind).from(P).leftJoin(O).on(O.id.eq(P.suspendedBy));
    }

    public Optional<PackRow> find(long id) {
        return Optional.ofNullable(packs().where(P.id.eq(id)).fetchFirst()).map(EmoticonPackStore::packOf);
    }

    /** 바꾸기 전에 — 팩 행을 잠근다(받기의 받은 수 · 심사 처리 · 저장이 같은 팩에서 줄을 선다). 잠금은 팩 표만 고르는 문장, 칸은 그 뒤에 읽는다 */
    public Optional<PackRow> lock(long id) {
        if (query.select(P.id).from(P).where(P.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchFirst() == null) {
            return Optional.empty();
        }
        return find(id);
    }

    /** 읽는 동안 바뀌지 않게 — 팩 행 공유 잠금(유료 결제 준비가 가격·판매 상태를 읽는다 — 가격 바꾸기·판매 중단과 줄을 선다) */
    public Optional<PackRow> lockShare(long id) {
        if (query.select(P.id).from(P).where(P.id.eq(id)).setLockMode(LockModeType.PESSIMISTIC_READ).fetchFirst() == null) {
            return Optional.empty();
        }
        return find(id);
    }

    public List<PackRow> byIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return packs().where(P.id.in(ids)).fetch().stream().map(EmoticonPackStore::packOf).collect(Collectors.toList());
    }

    /** 판매 중(APPROVED)인 무료 운영 팩(아이로빗콘 등) — 가입 때 주는 팩({@code EmoticonAcquireService#grantSignupPacks}), 팩 ID 차례 */
    public List<Long> officialFreeOnSale() {
        return query.select(P.id).from(P).where(P.source.eq(EmoticonPackSource.OFFICIAL), P.status.eq(EmoticonPackStatus.APPROVED), P.price.eq(0))
                .orderBy(P.id.asc()).fetch();
    }

    /** 작가의 팩 — 최근 고친 것 먼저 */
    public List<PackRow> byCreator(long creatorId) {
        return packs().where(P.creatorId.eq(creatorId)).orderBy(P.updatedAt.desc(), P.id.desc()).fetch().stream().map(EmoticonPackStore::packOf).collect(Collectors.toList());
    }

    /** 새 팩 — 상점의 판({@code liveRevisionId})은 같은 트랜잭션에서 넣는다(외래 키는 커밋 때 본다) */
    public void insert(long id, EmoticonPackSource source, Long creatorId, EmoticonPackStatus status, Long liveRevisionId, Instant publishedAt, Instant now) {
        insert(EmoticonPackEntity.create(id, source, creatorId, status, liveRevisionId, publishedAt, now));
    }

    public void setStatus(long id, EmoticonPackStatus status, Instant now) {
        query.update(P).set(P.status, status).set(P.updatedAt, now).where(P.id.eq(id)).execute();
    }

    /** 고친 시각만 */
    public void touch(long id, Instant now) {
        query.update(P).set(P.updatedAt, now).where(P.id.eq(id)).execute();
    }

    /**
     * 상점에 판을 건다(승인·운영 팩 고치기) — 판매 중으로, 처음 나온 시각은 처음 한 번만, 판매 중단 기록은 지운다(판매 중단 팩의 새 판을 승인하면 다시 판매).
     */
    public void publish(long id, long liveRevisionId, Instant now) {
        query.update(P).set(P.liveRevisionId, liveRevisionId).set(P.status, EmoticonPackStatus.APPROVED)
                .set(P.publishedAt, Expressions.dateTimeTemplate(Instant.class, "coalesce({0}, {1})", P.publishedAt, now)).setNull(P.suspendReason).setNull(P.suspendedAt)
                .setNull(P.suspendedBy).setNull(P.suspendKind).set(P.updatedAt, now).where(P.id.eq(id)).execute();
    }

    /** 상점의 판만 바꾼다(운영 팩 고치기 — 판매 중단이면 그대로 판매 중단) */
    public void replaceLive(long id, long liveRevisionId, Instant now) {
        query.update(P).set(P.liveRevisionId, liveRevisionId).set(P.updatedAt, now).where(P.id.eq(id)).execute();
    }

    public void suspend(long id, String reason, long operatorId, Instant now) {
        query.update(P).set(P.status, EmoticonPackStatus.SUSPENDED).set(P.suspendKind, EmoticonSuspendKind.OPERATOR).set(P.suspendReason, reason).set(P.suspendedAt, now)
                .set(P.suspendedBy, operatorId).set(P.updatedAt, now).where(P.id.eq(id)).execute();
    }

    public void resume(long id, Instant now) {
        query.update(P).set(P.status, EmoticonPackStatus.APPROVED).setNull(P.suspendReason).setNull(P.suspendedAt).setNull(P.suspendedBy).setNull(P.suspendKind)
                .set(P.updatedAt, now).where(P.id.eq(id)).execute();
    }

    /**
     * 작가 탈퇴 — 그 작가의 상점에 나온 팩(판매 중 · 운영자 판매 중단)을 작가 탈퇴 판매 중단으로 바꾼다(후속 결정 6). 팩 행을 팩 ID 차례로 잠근 뒤 바꾼다
     * (받기·결제 준비·결제 확정·가격 바꾸기·심사 승인과 줄을 선다). 이미 작가 탈퇴로 멈춘 팩은 그대로(다시 불러도 같다). 멈춘 팩과 멈추기 전 값.
     * 잠금은 팩 표만 고르는 ID 문장(차례 = ID), 멈추기 전 값은 잠근 뒤 읽는다.
     */
    public List<StoppedRow> stopForCreatorWithdrawal(long creatorId, String reason, Instant now) {
        BooleanExpression stoppable = P.creatorId.eq(creatorId).and(P.status.in(EmoticonPackStatus.APPROVED, EmoticonPackStatus.SUSPENDED))
                .and(P.suspendKind.isNull().or(P.suspendKind.ne(EmoticonSuspendKind.CREATOR_WITHDRAWN)));
        // 조건 잠금(id 차례, PESSIMISTIC_WRITE) — PostgreSQL 이 잠근 뒤 조건을 다시 본다. 칸은 조건을 넣어 다시 읽는다
        List<Long> ids = query.select(P.id).from(P).where(stoppable).orderBy(P.id.asc()).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetch();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<StoppedRow> locked = query.select(P.id, P.status, P.suspendKind, P.suspendReason).from(P).where(P.id.in(ids), stoppable).orderBy(P.id.asc()).fetch().stream()
                .map(t -> new StoppedRow(t.get(P.id), t.get(P.status), t.get(P.suspendKind), t.get(P.suspendReason))).toList();
        List<StoppedRow> out = new ArrayList<>();
        for (StoppedRow row : locked) {
            long n = query.update(P).set(P.status, EmoticonPackStatus.SUSPENDED).set(P.suspendKind, EmoticonSuspendKind.CREATOR_WITHDRAWN).set(P.suspendReason, reason)
                    .set(P.suspendedAt, now).setNull(P.suspendedBy).set(P.updatedAt, now)
                    .where(P.id.eq(row.id()), P.status.in(EmoticonPackStatus.APPROVED, EmoticonPackStatus.SUSPENDED)).execute();
            if (n == 1) {
                out.add(row);
            }
        }
        return out;
    }

    /** 가격을 바꾼다(승인·운영자 가격 바꾸기 — 팩 행을 잠근 트랜잭션에서). 유료 가격은 트리거가 설정의 열린 가격 단계인지 한 번 더 본다(V143) */
    public void setPrice(long id, int price, Instant now) {
        query.update(P).set(P.price, price).set(P.updatedAt, now).where(P.id.eq(id)).execute();
    }

    /**
     * 받은 수 + 1(처음 받기 — 팩 행을 잠근 트랜잭션에서) — 올린 뒤의 받은 수. 팩 행을 잠근 채라 올리기와 읽기 사이에 끼는 작업자가 없다(예전 {@code RETURNING} 과 같은 값).
     * 팩이 없으면 예전처럼 {@link EmptyResultDataAccessException}.
     */
    public int incrementAcquire(long id) {
        if (query.update(P).set(P.acquireCount, P.acquireCount.add(1)).where(P.id.eq(id)).execute() != 1) {
            throw new EmptyResultDataAccessException(1);
        }
        return query.select(P.acquireCount).from(P).where(P.id.eq(id)).fetchFirst();
    }

    // ── 판 ──────────────────────────────────────────────────────────────────

    private JPAQuery<Tuple> revisionRows() {
        return query.select(R.id, R.packId, R.version, R.name, R.description, R.tags, R.coverImageId, R.saleWish, R.priceRequestMemo, R.createdAt, R.submittedAt).from(R);
    }

    public Optional<RevisionRow> revision(long id) {
        return Optional.ofNullable(revisionRows().where(R.id.eq(id)).fetchFirst()).map(EmoticonPackStore::revisionOf);
    }

    public Map<Long, RevisionRow> revisions(Collection<Long> ids) {
        Map<Long, RevisionRow> out = new LinkedHashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        for (Tuple t : revisionRows().where(R.id.in(ids)).fetch()) {
            RevisionRow row = revisionOf(t);
            out.put(row.id(), row);
        }
        return out;
    }

    /** 아직 내지 않은 임시 판(팩마다 하나) */
    public Optional<RevisionRow> draftOf(long packId) {
        return Optional.ofNullable(revisionRows().where(R.packId.eq(packId), R.version.isNull()).fetchFirst()).map(EmoticonPackStore::revisionOf);
    }

    /** 마지막으로 낸 판 */
    public Optional<RevisionRow> latestSealed(long packId) {
        return Optional.ofNullable(revisionRows().where(R.packId.eq(packId), R.version.isNotNull()).orderBy(R.version.desc()).fetchFirst())
                .map(EmoticonPackStore::revisionOf);
    }

    /**
     * 팩마다 마지막으로 낸 판 — 판 번호가 그 팩의 가장 큰 번호인 줄(팩 × 번호 유일 — 예전 {@code DISTINCT ON} 과 같은 줄, 팩 ID 차례).
     */
    public Map<Long, RevisionRow> latestSealed(Collection<Long> packIds) {
        Map<Long, RevisionRow> out = new LinkedHashMap<>();
        if (packIds.isEmpty()) {
            return out;
        }
        for (Tuple t : revisionRows().where(R.packId.in(packIds), R.version.eq(JPAExpressions.select(R2.version.max()).from(R2).where(R2.packId.eq(R.packId))))
                .orderBy(R.packId.asc()).fetch()) {
            RevisionRow row = revisionOf(t);
            out.put(row.packId(), row);
        }
        return out;
    }

    /** 팩마다 임시 판 */
    public Map<Long, RevisionRow> drafts(Collection<Long> packIds) {
        Map<Long, RevisionRow> out = new LinkedHashMap<>();
        if (packIds.isEmpty()) {
            return out;
        }
        for (Tuple t : revisionRows().where(R.packId.in(packIds), R.version.isNull()).fetch()) {
            RevisionRow row = revisionOf(t);
            out.put(row.packId(), row);
        }
        return out;
    }

    public int maxVersion(long packId) {
        Integer max = query.select(R.version.max()).from(R).where(R.packId.eq(packId)).fetchFirst();
        return max == null ? 0 : max;
    }

    /** 임시 판을 넣는다(판을 내려면 차례를 넣은 뒤 {@link #seal}) */
    public void insertDraft(long id, long packId, String name, String description, List<String> tags, Long coverImageId, EmoticonSaleWish saleWish, String memo,
                            Instant now) {
        insert(EmoticonPackRevisionEntity.draft(id, packId, name, description, tags, coverImageId, saleWish, memo, now));
    }

    /** 임시 판 칸을 바꾼다(낸 판은 트리거가 막는다) */
    public void updateDraft(long id, String name, String description, List<String> tags, Long coverImageId, EmoticonSaleWish saleWish, String memo) {
        query.update(R).set(R.name, name).set(R.description, description).set(R.tags, tags.toArray(String[]::new)).set(R.coverImageId, coverImageId)
                .set(R.saleWish, saleWish).set(R.priceRequestMemo, memo).where(R.id.eq(id)).execute();
    }

    /** 판을 낸다 — 그 뒤로 판 칸·차례를 고치지 못한다(트리거) */
    public void seal(long id, int version, Instant at) {
        query.update(R).set(R.version, version).set(R.submittedAt, at).where(R.id.eq(id), R.version.isNull()).execute();
    }

    // ── 로보티콘 · 차례 ─────────────────────────────────────────────────────

    public void insertEmoticon(long id, long packId, Instant now) {
        insert(EmoticonEntity.create(id, packId, now));
    }

    /** 임시 판의 차례를 통째로 바꾼다 — 지우고 차례대로 넣는다(낸 판의 차례는 트리거가 막는다) */
    public void replaceItems(long revisionId, long packId, List<ItemRow> items) {
        query.delete(I).where(I.revisionId.eq(revisionId)).execute();
        if (!items.isEmpty()) {
            insertAll(items.stream().map(item -> EmoticonRevisionItemEntity.create(revisionId, item.position(), packId, item.emoticonId(), item.imageId(), item.label()))
                    .toList());
        }
    }

    /** 판마다 차례(차례대로) */
    public Map<Long, List<ItemRow>> items(Collection<Long> revisionIds) {
        Map<Long, List<ItemRow>> out = new LinkedHashMap<>();
        if (revisionIds.isEmpty()) {
            return out;
        }
        revisionIds.forEach(id -> out.put(id, new ArrayList<>()));
        for (Tuple t : query.select(I.revisionId, I.position, I.emoticonId, I.imageId, I.label).from(I).where(I.revisionId.in(revisionIds))
                .orderBy(I.revisionId.asc(), I.position.asc()).fetch()) {
            ItemRow row = new ItemRow(t.get(I.revisionId), t.get(I.position), t.get(I.emoticonId), t.get(I.imageId), t.get(I.label));
            out.computeIfAbsent(row.revisionId(), k -> new ArrayList<>()).add(row);
        }
        return out;
    }

    public List<ItemRow> items(long revisionId) {
        return items(List.of(revisionId)).getOrDefault(revisionId, List.of());
    }

    /** 이 팩의 어느 판에서 이 그림을 쓰던 로보티콘(새 판에서도 같은 ID — 계약 S1) */
    public Optional<Long> emoticonByImage(long packId, long imageId) {
        return Optional.ofNullable(query.select(I.emoticonId).from(I).where(I.packId.eq(packId), I.imageId.eq(imageId)).orderBy(I.revisionId.desc()).fetchFirst());
    }

    /** 이 팩의 어느 판에서 같은 지문의 그림을 쓰던 로보티콘(같은 파일을 다시 올려도 같은 ID) */
    public Optional<Long> emoticonByHash(long packId, String contentHash) {
        return Optional.ofNullable(query.select(I.emoticonId).from(I).join(M).on(M.id.eq(I.imageId)).where(I.packId.eq(packId), M.contentHash.eq(contentHash))
                .orderBy(I.revisionId.desc()).fetchFirst());
    }

    /** 이 팩의 로보티콘 ID 들 */
    public Set<Long> emoticonIds(long packId) {
        return new HashSet<>(query.select(E.id).from(E).where(E.packId.eq(packId)).fetch());
    }

    /** 이 팩의 판들이 쓰는 그림(로보티콘·대표) */
    public Set<Long> imageIds(long packId) {
        Set<Long> out = new HashSet<>(query.select(I.imageId).from(I).where(I.packId.eq(packId)).fetch());
        out.addAll(query.select(R.coverImageId).from(R).where(R.packId.eq(packId), R.coverImageId.isNotNull()).fetch());
        return out;
    }

    // ── 안쪽 ────────────────────────────────────────────────────────────────

    /** 상점에 나온(판매 중 · 판매 중단) 팩 — 별칭 {@code p} */
    static BooleanExpression published(QEmoticonPackEntity p) {
        return p.status.in(EmoticonPackStatus.APPROVED, EmoticonPackStatus.SUSPENDED);
    }

    static List<String> tags(String[] values) {
        if (values == null) {
            return List.of();
        }
        return Arrays.stream(values).map(String::valueOf).collect(Collectors.toList());
    }

    private static PackRow packOf(Tuple t) {
        return new PackRow(t.get(P.id), t.get(P.source), t.get(P.creatorId), t.get(P.status), t.get(P.price), t.get(P.liveRevisionId), t.get(P.acquireCount),
                t.get(P.publishedAt), t.get(P.suspendReason), t.get(P.suspendedAt), t.get(P.suspendedBy), t.get(O.operatorName), t.get(P.createdAt), t.get(P.updatedAt),
                t.get(P.suspendKind));
    }

    private static RevisionRow revisionOf(Tuple t) {
        return new RevisionRow(t.get(R.id), t.get(R.packId), t.get(R.version), t.get(R.name), t.get(R.description), tags(t.get(R.tags)), t.get(R.coverImageId),
                t.get(R.saleWish), t.get(R.priceRequestMemo), t.get(R.createdAt), t.get(R.submittedAt));
    }
}
