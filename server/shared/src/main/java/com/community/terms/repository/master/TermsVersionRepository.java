package com.community.terms.repository.master;

import com.community.terms.entity.TermsKind;
import com.community.terms.entity.TermsVersionEntity;
import com.community.zconfiguration.jpa.RoboyamRepository;
import java.util.Optional;

public interface TermsVersionRepository extends RoboyamRepository<TermsVersionEntity, Long> {

    /** 종류·버전으로 한 게시본(본문 포함) */
    Optional<TermsVersionEntity> findByKindAndVersion(TermsKind kind, Integer version);
}
