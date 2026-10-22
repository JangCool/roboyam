package com.community.user.repository.master;

import com.community.user.entity.MemberDisplayPreferenceEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/**
 * 회원 표시 설정({@code member_display_preferences}). 회원 탈퇴 흐름이 생기면 그 트랜잭션에서 {@code deleteById(userId)} 로 이 행을 지운다
 * (회원 행 자체를 지우면 외래키 {@code ON DELETE CASCADE} 로 함께 지워진다).
 */
public interface MemberDisplayPreferenceRepository extends RoboyamRepository<MemberDisplayPreferenceEntity, Long> {
}
