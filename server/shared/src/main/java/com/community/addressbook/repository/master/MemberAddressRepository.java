package com.community.addressbook.repository.master;

import com.community.addressbook.entity.MemberAddressEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 회원 주소록({@code member_addresses}) — 넣기는 {@code persist}. 질의·고치기는 {@link MemberAddressStore} */
public interface MemberAddressRepository extends RoboyamRepository<MemberAddressEntity, Long> {
}
