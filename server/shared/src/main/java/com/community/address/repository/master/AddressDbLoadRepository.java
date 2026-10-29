package com.community.address.repository.master;

import com.community.address.entity.AddressDbLoadEntity;
import com.community.address.entity.AddressLoadStatus;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;
import java.util.Optional;

public interface AddressDbLoadRepository extends RoboyamRepository<AddressDbLoadEntity, Long> {

    /** 돌고 있는 적재(사이트에 하나) */
    Optional<AddressDbLoadEntity> findFirstByStatus(AddressLoadStatus status);

    /** 지금 올라 있는 주소 DB = 가장 최근에 끝난 적재 */
    Optional<AddressDbLoadEntity> findFirstByStatusOrderByFinishedAtDesc(AddressLoadStatus status);

    /** 서버가 다시 떴을 때 — 이 서버가 돌리다 멈춘 적재 */
    List<AddressDbLoadEntity> findByStatusAndNodeId(AddressLoadStatus status, int nodeId);
}
