package com.community.bizapplication.repository.master;

import com.community.bizapplication.entity.BizContractFileEntity;
import com.community.bizapplication.entity.QBizContractFileEntity;
import com.community.docintake.entity.DocFileChannel;
import com.community.operator.entity.QOperatorEntity;
import com.community.zconfiguration.jpa.JpaStoreWrites;
import com.community.zconfiguration.jpa.IrobitRepositoryQDSL;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 계약서 사본(V112 {@code biz_contract_files}) — 내용은 암호문 bytea 라 목록·정보 조회는 내용을 읽지 않는다(프로젝션). 줄을 지우지 않고, 바꾸면 옛 줄에 「대체됨」을 적는다.
 * QueryDSL 만 쓴다.
 */
@Repository
public class BizContractStore extends IrobitRepositoryQDSL {

    private static final QBizContractFileEntity CF = QBizContractFileEntity.bizContractFileEntity;
    private static final QOperatorEntity UPLOADER = new QOperatorEntity("uploader");
    private static final QOperatorEntity REPLACER = new QOperatorEntity("replacer");

    /** 계약서 한 장의 정보(내용 없음) — 올린 사람·대체한 사람 이름 포함. {@code channel} = 들어온 길(V140 — 올림 · 전자 팩스) */
    public record ContractRow(long id, long applicationId, String fileName, String contentType, int sizeBytes, String sha256, Long uploadedBy, String uploadedByName,
                              Instant uploadedAt, Instant replacedAt, Long replacedBy, String replacedByName, Long replacedByFileId, DocFileChannel channel) {

        public boolean active() {
            return replacedAt == null;
        }
    }

    /** 미리 올린 사본은 건이 없어 0 으로 읽는다(예전 {@code getLong} 과 같다) */
    private static final Expression<ContractRow> ROW = Projections.constructor(ContractRow.class, CF.id, CF.applicationId.coalesce(0L), CF.fileName, CF.contentType,
            CF.sizeBytes, CF.sha256, CF.uploadedBy, UPLOADER.operatorName, CF.uploadedAt, CF.replacedAt, CF.replacedBy, REPLACER.operatorName, CF.replacedByFileId,
            CF.channel);

    private final EntityManager entityManager;

    public BizContractStore(JPAQueryFactory query, EntityManager entityManager) {
        super(query);
        this.entityManager = entityManager;
    }

    public void insert(long id, long applicationId, String fileName, String contentType, int sizeBytes, byte[] encrypted, String sha256, long operatorId, Instant now) {
        insert(id, applicationId, fileName, contentType, sizeBytes, encrypted, sha256, DocFileChannel.UPLOAD, operatorId, now);
    }

    /** 들어온 길(올림 · 전자 팩스)을 적어 더한다 */
    public void insert(long id, long applicationId, String fileName, String contentType, int sizeBytes, byte[] encrypted, String sha256, DocFileChannel channel,
                       long operatorId, Instant now) {
        JpaStoreWrites.insert(entityManager, BizContractFileEntity.create(id, applicationId, fileName, contentType, sizeBytes, encrypted, sha256, channel, operatorId, now));
    }

    /** 건이 생기기 전에 올린 사본(광고 일정표 운영자 등록 서랍 — application_id 없음, V115). 들어온 길은 DB 기본값과 같은 올림 */
    public void insertDraft(long id, String fileName, String contentType, int sizeBytes, byte[] encrypted, String sha256, long operatorId, Instant now) {
        JpaStoreWrites.insert(entityManager, BizContractFileEntity.create(id, null, fileName, contentType, sizeBytes, encrypted, sha256, DocFileChannel.UPLOAD, operatorId,
                now));
    }

    /** 미리 올린 사본 한 장(아직 건에 붙지 않은 것) */
    public Optional<ContractRow> findDraft(long fileId) {
        return Optional.ofNullable(select().where(CF.applicationId.isNull(), CF.id.eq(fileId)).fetchFirst());
    }

    /** 미리 올린 사본을 건에 붙인다 — 아직 붙지 않은 것만. 붙인 장수 */
    public int attachDrafts(long applicationId, Collection<Long> fileIds) {
        int n = 0;
        for (Long fileId : fileIds) {
            n += (int) query.update(CF).set(CF.applicationId, applicationId).where(CF.id.eq(fileId), CF.applicationId.isNull()).execute();
        }
        return n;
    }

    /** 옛 파일을 「대체됨」으로 — 아직 대체되지 않았을 때만 */
    public boolean replace(long fileId, long applicationId, long newFileId, long operatorId, Instant now) {
        return query.update(CF).set(CF.replacedAt, now).set(CF.replacedBy, operatorId).set(CF.replacedByFileId, newFileId)
                .where(CF.id.eq(fileId), CF.applicationId.eq(applicationId), CF.replacedAt.isNull()).execute() == 1;
    }

    public List<ContractRow> list(long applicationId) {
        return select().where(CF.applicationId.eq(applicationId)).orderBy(CF.uploadedAt.asc(), CF.id.asc()).fetch();
    }

    public Optional<ContractRow> find(long applicationId, long fileId) {
        return Optional.ofNullable(select().where(CF.applicationId.eq(applicationId), CF.id.eq(fileId)).fetchFirst());
    }

    /** 대체되지 않은 사본 수 */
    public int activeCount(long applicationId) {
        Long n = query.select(CF.count()).from(CF).where(CF.applicationId.eq(applicationId), CF.replacedAt.isNull()).fetchOne();
        return n == null ? 0 : Math.toIntExact(n);
    }

    /** 암호문 내용 */
    public Optional<byte[]> content(long applicationId, long fileId) {
        return Optional.ofNullable(query.select(CF.content).from(CF).where(CF.applicationId.eq(applicationId), CF.id.eq(fileId)).fetchFirst());
    }

    private JPAQuery<ContractRow> select() {
        BooleanExpression uploader = UPLOADER.id.eq(CF.uploadedBy);
        return query.select(ROW).from(CF).leftJoin(UPLOADER).on(uploader).leftJoin(REPLACER).on(REPLACER.id.eq(CF.replacedBy));
    }
}
