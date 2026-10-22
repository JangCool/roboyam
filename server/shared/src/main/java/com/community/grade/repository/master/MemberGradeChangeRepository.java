package com.community.grade.repository.master;

import com.community.grade.entity.MemberGradeChangeEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 등급 변경 이력의 한 줄 기록({@code persist}). 조회는 admin 의 회원 조회 쪽에 있다 */
public interface MemberGradeChangeRepository extends RoboyamRepository<MemberGradeChangeEntity, Long> {
}
