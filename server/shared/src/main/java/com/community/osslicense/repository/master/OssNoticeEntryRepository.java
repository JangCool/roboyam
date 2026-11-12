package com.community.osslicense.repository.master;

import com.community.osslicense.entity.OssNoticeEntryEntity;
import com.community.zconfiguration.jpa.IrobitRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 오픈소스 라이선스 고지의 직접 추가 항목(V181) — 이름순 목록은 {@link OssNoticeEntryRepositoryQDSL} */
public interface OssNoticeEntryRepository extends IrobitRepository<OssNoticeEntryEntity, Long> {

    /** 고치기·숨기기·지우기를 한 줄씩 줄 세운다(이 행을 FK 로 가리키는 표가 없다) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OssNoticeEntryEntity> findWithLockById(Long id);
}
