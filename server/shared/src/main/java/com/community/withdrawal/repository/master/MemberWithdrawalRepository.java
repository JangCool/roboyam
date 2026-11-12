package com.community.withdrawal.repository.master;

import com.community.withdrawal.entity.MemberWithdrawalEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 탈퇴 신청 줄 넣기(persist) — 읽기·상태 전이는 {@link MemberWithdrawalStore} */
public interface MemberWithdrawalRepository extends IrobitRepository<MemberWithdrawalEntity, Long> {
}
