package com.community.terms.repository.master;

import com.community.terms.entity.TermsDraftEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;

/** 키는 종류 이름({@code TermsKind#name()}) */
public interface TermsDraftRepository extends RoboyamRepository<TermsDraftEntity, String> {

    /** 초안을 쓰기 잠금으로 읽는다 — 게시를 종류마다 한 번에 하나씩 줄 세운다. 트랜잭션 안에서만 의미가 있다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TermsDraftEntity> findWithLockByKind(String kind);
}
