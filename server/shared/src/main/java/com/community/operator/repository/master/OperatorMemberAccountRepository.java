package com.community.operator.repository.master;

import com.community.operator.entity.OperatorMemberAccountEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 운영자 회원 계정 연결 넣기(persist + flush) — 읽기·끊기는 {@link OperatorMemberAccountStore} */
public interface OperatorMemberAccountRepository extends RoboyamRepository<OperatorMemberAccountEntity, Long> {
}
