package com.community.user.repository.master;

import com.community.user.entity.ProfileNicknameHistoryEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.List;

/** 닉네임 변경 이력(비공개 — 관리자 회원 상세만 읽는다). 색인 {@code ix_profile_nickname_history_user (user_id, id DESC)} */
public interface ProfileNicknameHistoryRepository extends RoboyamRepository<ProfileNicknameHistoryEntity, Long> {

    /** 회원의 최근 이력 20줄(최신이 위) */
    List<ProfileNicknameHistoryEntity> findTop20ByUserIdOrderByIdDesc(Long userId);
}
