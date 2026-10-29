package com.community.address.repository.master;

import com.community.address.entity.AddressDbLoadEntity;
import com.community.address.entity.AddressLoadStatus;
import com.community.address.entity.QAddressDbLoadEntity;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 적재 이력 쪽 읽기 — 끝난 적재({@code DONE})만, 최신이 위. 색인 {@code ix_address_db_loads_done} 을 탄다 */
@Repository
public class AddressDbLoadQueries extends RoboyamRepositoryQDSL {

    private static final QAddressDbLoadEntity LOAD = QAddressDbLoadEntity.addressDbLoadEntity;

    public AddressDbLoadQueries(JPAQueryFactory query) {
        super(query);
    }

    public List<AddressDbLoadEntity> donePage(int page, int size) {
        return query.selectFrom(LOAD).where(LOAD.status.eq(AddressLoadStatus.DONE)).orderBy(LOAD.finishedAt.desc(), LOAD.id.desc())
                .offset((long) page * size).limit(size).fetch();
    }

    public long doneCount() {
        Long count = query.select(LOAD.count()).from(LOAD).where(LOAD.status.eq(AddressLoadStatus.DONE)).fetchOne();
        return count == null ? 0 : count;
    }
}
