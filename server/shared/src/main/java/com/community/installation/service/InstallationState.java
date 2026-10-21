package com.community.installation.service;

import com.community.installation.dto.InstallationCompleted;
import com.community.installation.entity.InstallationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 이 서버가 아는 <b>설치 상태</b>(메모리 캐시). 모든 {@code /api/**} 요청이 이것을 본다 — 요청마다 DB 를 읽지 않는다.
 *
 * <ul>
 *   <li><b>{@code COMPLETED} 가 되면 다시 읽지 않는다</b> — 한 번 완료된 설치는 되돌아가지 않으므로 캐시는 한 방향({@code false → true})으로만 바뀐다.</li>
 *   <li>설치 전에는 <b>다른 앱 서버가 설치를 끝내는 것</b>을 따라가려고 {@code roboyam.install.refresh-millis}(기본 15초)마다 DB 를 읽는다.
 *       이 서버가 설치를 끝내면 <b>커밋 직후</b> 즉시 완료로 바꾼다({@link #onCompleted}) — 롤백되면 이벤트가 오지 않아 메모리는 처음 그대로다.</li>
 *   <li>읽지 못하면(DB 일시 장애) <b>설치 전으로 본다</b>(닫힌 쪽으로 실패한다) — 다음 주기에 다시 읽는다.</li>
 * </ul>
 * api 와 admin 이 같이 쓴다(관리자 앱은 설치 완료를 이 주기 확인으로 따라온다).
 */
@Component
public class InstallationState {

    private static final Logger log = LoggerFactory.getLogger(InstallationState.class);

    private final InstallationStore store;
    private volatile boolean completed;
    private volatile boolean loaded;

    public InstallationState(InstallationStore store) {
        this.store = store;
    }

    /** 설치가 끝났는가 */
    public boolean isInstalled() {
        if (completed) {
            return true;
        }
        if (!loaded) {
            refresh(); // 기동 직후 첫 요청이 주기 확인보다 먼저 올 때
        }
        return completed;
    }

    /** 이 서버가 설치를 끝냈다(커밋 직후) — 곧바로 완료로 바꾼다. 다른 서버는 주기 확인으로 따라온다. 메모리만 바꾸므로 실패하지 않는다 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(InstallationCompleted event) {
        markCompleted();
    }

    /** 다른 서버가 설치를 끝낸 것을 알았을 때(토큰을 만들려는 순간 등) 완료로 바꾼다 */
    public void markCompleted() {
        loaded = true;
        if (!completed) {
            completed = true;
            log.info("설치가 끝났다 — 이제 설치 마법사는 닫히고 서비스가 열린다");
        }
    }

    /** 설치 전인 동안 DB 를 다시 읽어 다른 서버가 끝낸 설치를 따라간다 */
    @Scheduled(fixedDelayString = "${roboyam.install.refresh-millis:15000}")
    public void poll() {
        if (!completed) {
            refresh();
        }
    }

    /** DB 의 상태를 읽는다. 완료면 캐시를 완료로 바꾼다. 읽지 못하면 던지지 않고 지금 상태를 유지한다 */
    void refresh() {
        try {
            boolean done = store.status().map(status -> status == InstallationStatus.COMPLETED).orElse(false);
            loaded = true;
            if (done) {
                markCompleted();
            }
        } catch (RuntimeException e) {
            log.error("설치 상태를 읽지 못했다 — 설치 전으로 보고 다음 주기에 다시 읽는다", e);
        }
    }
}
