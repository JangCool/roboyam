package com.community.withdrawal.repository.master;

import com.community.withdrawal.entity.MemberWithdrawalEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 탈퇴 신청 줄 넣기(persist) — 읽기·상태 전이는 {@link MemberWithdrawalStore} */
public interface MemberWithdrawalRepository extends RoboyamRepository<MemberWithdrawalEntity, Long> {
}
