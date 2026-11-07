package com.community.avatarupload.repository.master;

import com.community.avatarupload.entity.AvatarUploadRequestEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;

/** 아바타 올리기 신청({@code avatar_upload_requests}) — 넣기는 {@code persist}. 질의·상태 전이는 {@link AvatarUploadStore} */
public interface AvatarUploadRequestRepository extends RoboyamRepository<AvatarUploadRequestEntity, Long> {
}
