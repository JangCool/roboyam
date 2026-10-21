package com.community.user.repository.master;

import com.community.user.entity.ProfileEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 닉네임 중복 확인은 {@link ProfileRepositoryQDSL} 에 있다 */
public interface ProfileRepository extends RoboyamRepository<ProfileEntity, Long> {
}
