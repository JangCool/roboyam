package com.community.title.repository.master;

import com.community.title.entity.MemberTitlePrefEntity;
import com.community.zconfiguration.jpa.IrobitRepository;

/** 회원 대표 칭호({@code member_title_prefs}, V240) — 묶어 읽기는 {@code findAllById} */
public interface MemberTitlePrefRepository extends IrobitRepository<MemberTitlePrefEntity, Long> {
}
