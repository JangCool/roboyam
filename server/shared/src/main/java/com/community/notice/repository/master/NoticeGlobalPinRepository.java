package com.community.notice.repository.master;

import com.community.notice.entity.NoticeGlobalPinEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 전체 공지 줄(V174) — 쓰기는 관리자 공지 관리, 공개 쪽 읽기는 {@link NoticeGlobalPinQueries} */
public interface NoticeGlobalPinRepository extends RoboyamRepository<NoticeGlobalPinEntity, Long> {
}
