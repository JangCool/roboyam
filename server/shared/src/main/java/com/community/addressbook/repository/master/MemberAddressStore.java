package com.community.addressbook.repository.master;

import com.community.addressbook.entity.MemberAddressEntity;
import com.community.addressbook.entity.QMemberAddressEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.types.ConstructorExpression;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 회원 주소록 표 {@code member_addresses}(V139) — 행 레코드는 암호문 그대로. 기본 배송지는 부분 유일 색인 {@code ux_member_addresses_default} 로 막으므로
 * 「앞 기본 끄기 → 새 기본 켜기」 차례를 문장 순서로 정한다: 기본 바꾸기는 대량 UPDATE(바로 나간다)이고 새 줄은 넣자마자 내보낸다(flush) — 변경 감지에 맡기면
 * Hibernate 가 INSERT 를 UPDATE 보다 먼저 내보낸다. 읽기는 프로젝션(대량 UPDATE 뒤 낡은 1차 캐시가 보이지 않게).
 * 회원 단위 잠금({@link #lockMember})은 부르는 서비스의 트랜잭션 안에서만 뜻이 있다. 암호문 칸은 {@code toString} 에 싣지 않는다.
 */
@Repository
public class MemberAddressStore extends RoboyamRepositoryQDSL {

    /** 저장된 한 줄(암호문 그대로) */
    public record Row(long id, long userId, String label, String recipientEnc, String phoneEnc, String postcode, String address1Enc, String address2Enc, String memoEnc,
                      boolean isDefault, Instant createdAt, Instant updatedAt) {

        @Override
        public String toString() {
            return "MemberAddressStore.Row[id=" + id + ", userId=" + userId + ", default=" + isDefault + "]";
        }
    }

    /** 넣거나 고칠 값(암호문) */
    public record Values(String label, String recipientEnc, String phoneEnc, String postcode, String address1Enc, String address2Enc, String memoEnc) {

        @Override
        public String toString() {
            return "MemberAddressStore.Values[가림]";
        }
    }

    private static final QMemberAddressEntity A = QMemberAddressEntity.memberAddressEntity;
    private static final ConstructorExpression<Row> ROW = Projections.constructor(Row.class, A.id, A.userId, A.label, A.recipientEnc, A.phoneEnc, A.postcode,
            A.address1Enc, A.address2Enc, A.memoEnc, A.isDefault, A.createdAt, A.updatedAt);

    private final MemberAddressRepository addresses;
    private final EntityManager entityManager;

    public MemberAddressStore(JPAQueryFactory query, MemberAddressRepository addresses, EntityManager entityManager) {
        super(query);
        this.addresses = addresses;
        this.entityManager = entityManager;
    }

    /**
     * 회원 하나의 주소록을 줄 세우는 어드바이저리 잠금(트랜잭션 범위) — 10개 한도·기본 하나를 동시 요청에도 지킨다. 키 = {@code hashtext('ab:<회원>')}.
     * 공구 주문 트랜잭션은 공구·주문 행을 잡은 뒤 이 잠금을 잡는다 — 이 잠금을 쥔 쪽은 주소록 행만 만지므로 거꾸로 기다리는 일이 없다.
     *
     * <p>Native SQL 인 이유: {@code pg_advisory_xact_lock} 은 PostgreSQL 함수다(처음 넣을 때는 잠글 행이 없다).
     */
    public void lockMember(long userId) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(?1))::text").setParameter(1, "ab:" + userId).getSingleResult();
    }

    /** 내 목록 — 기본 → 최근 고친 순 */
    public List<Row> list(long userId) {
        return query.select(ROW).from(A).where(A.userId.eq(userId)).orderBy(A.isDefault.desc(), A.updatedAt.desc(), A.id.desc()).fetch();
    }

    public int count(long userId) {
        Long n = query.select(A.count()).from(A).where(A.userId.eq(userId)).fetchOne();
        return n == null ? 0 : Math.toIntExact(n);
    }

    /** 내 배송지 하나(남의 것이면 비어 있다) */
    public Optional<Row> find(long userId, long id) {
        return Optional.ofNullable(query.select(ROW).from(A).where(A.id.eq(id), A.userId.eq(userId)).fetchOne());
    }

    public void insert(long id, long userId, Values v, boolean isDefault, Instant now) {
        MemberAddressEntity row = MemberAddressEntity.create(id, userId, v.label(), v.recipientEnc(), v.phoneEnc(), v.postcode(), v.address1Enc(), v.address2Enc(),
                v.memoEnc(), isDefault, now);
        addresses.persist(row);
        entityManager.flush();
        entityManager.detach(row);
    }

    public void update(long userId, long id, Values v, Instant now) {
        query.update(A).set(A.label, v.label()).set(A.recipientEnc, v.recipientEnc()).set(A.phoneEnc, v.phoneEnc()).set(A.postcode, v.postcode())
                .set(A.address1Enc, v.address1Enc()).set(A.address2Enc, v.address2Enc()).set(A.memoEnc, v.memoEnc()).set(A.updatedAt, now)
                .where(A.id.eq(id), A.userId.eq(userId)).execute();
    }

    /** 이 배송지만 기본으로 — 앞 기본을 먼저 끄고 켠다(부분 유일 색인) */
    public void makeDefault(long userId, long id) {
        query.update(A).set(A.isDefault, false).where(A.userId.eq(userId), A.isDefault.isTrue(), A.id.ne(id)).execute();
        query.update(A).set(A.isDefault, true).where(A.id.eq(id), A.userId.eq(userId), A.isDefault.isFalse()).execute();
    }

    /** 회원의 기본을 끈다(새 기본 배송지를 넣기 전) */
    public void clearDefault(long userId) {
        query.update(A).set(A.isDefault, false).where(A.userId.eq(userId), A.isDefault.isTrue()).execute();
    }

    public void delete(long userId, long id) {
        query.delete(A).where(A.id.eq(id), A.userId.eq(userId)).execute();
    }

    /**
     * 기본이 없으면 남은 것 중 가장 최근({@code updated_at}) 것을 기본으로 — 기본을 지운 뒤. 가장 최근 줄을 고르고 「기본이 없을 때만」 켠다
     * (예전 한 문장과 같은 결과 — {@link #lockMember} 아래라 두 문장 사이에 같은 회원의 주소록이 바뀌지 않는다).
     */
    public void promoteNewestIfNoDefault(long userId) {
        Long newest = query.select(A.id).from(A).where(A.userId.eq(userId)).orderBy(A.updatedAt.desc(), A.id.desc()).limit(1).fetchFirst();
        if (newest == null) {
            return;
        }
        QMemberAddressEntity other = new QMemberAddressEntity("other");
        query.update(A).set(A.isDefault, true)
                .where(A.id.eq(newest), JPAExpressions.selectOne().from(other).where(other.userId.eq(userId), other.isDefault.isTrue()).notExists()).execute();
    }
}
