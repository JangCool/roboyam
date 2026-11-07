package com.community.withdrawal.repository.master;

import com.community.withdrawal.entity.IdentityWithdrawalEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 재가입·재인증 제한 해시 넣기(persist) — 읽기는 {@link IdentityWithdrawalStore} */
public interface IdentityWithdrawalRepository extends RoboyamRepository<IdentityWithdrawalEntity, Long> {
}
