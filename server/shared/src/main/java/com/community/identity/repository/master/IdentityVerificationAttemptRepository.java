package com.community.identity.repository.master;

import com.community.identity.entity.IdentityVerificationAttemptEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 본인인증 시도 넣기(persist) — 읽기·끝내기는 {@link IdentityStore} */
public interface IdentityVerificationAttemptRepository extends RoboyamRepository<IdentityVerificationAttemptEntity, String> {
}
