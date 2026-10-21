package com.community.installation.service;

import com.community.installation.entity.InstallationStatus;
import com.community.installation.repository.master.InstallationRepositoryQDSL;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설치 상태·토큰 해시를 DB 에서 읽고 쓰는 길.
 *
 * <p>항상 <b>새 트랜잭션</b>이다({@code REQUIRES_NEW}) — 커밋 직후에 불려도 방금 커밋한 값을 읽고, 부른 요청의 영속성 컨텍스트에 남은 옛 값을 보지 않는다.
 */
@Service
public class InstallationStore {

    private final InstallationRepositoryQDSL installation;

    public InstallationStore(InstallationRepositoryQDSL installation) {
        this.installation = installation;
    }

    /** 지금 상태. 행이 없으면 비어 있다(설치 전으로 본다) */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<InstallationStatus> status() {
        return installation.findStatus();
    }

    /** 저장된 설치 토큰 해시 */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<String> tokenHash() {
        return installation.findTokenHash();
    }

    /**
     * 설치 토큰 해시를 저장한다.
     *
     * @return 저장했는가 — 설치가 이미 끝났으면 false
     * @throws IllegalStateException {@code installation} 행이 없을 때(V4 마이그레이션이 만든다)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean saveTokenHash(String tokenHash) {
        if (installation.updateTokenHash(tokenHash) > 0) {
            return true;
        }
        if (installation.findStatus().isEmpty()) {
            throw new IllegalStateException("installation 행이 없다 — V4__installation.sql 이 적용됐는지 확인하세요");
        }
        return false;
    }
}
