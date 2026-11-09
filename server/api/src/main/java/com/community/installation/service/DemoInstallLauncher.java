package com.community.installation.service;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * 설치가 커밋된 뒤 데모 팩을 <b>요청과 따로</b>(이 서버의 전용 스레드 하나에서) 넣는다.
 *
 * <p>데모는 행 1만여 개와 파일 수백 개를 저장소에 올리느라 몇 분 걸린다. 설치 요청 안에서 넣으면 앞단(Cloudflare 무료 플랜은 100초)이 응답을 기다리다 끊어
 * 화면이 「서버에 연결하지 못했어요」로 멈춘다 — 서버는 끝까지 넣는데 화면만 모른다. 그래서 설치 응답은 {@code demo: RUNNING} 으로 바로 돌려주고,
 * 화면은 {@code GET /api/system/installation/demo} 로 결과를 따라온다.
 *
 * <p>결과({@code APPLIED} · {@code FAILED})는 {@link DemoDataInstaller} 가 설치 표에 적는다. 넣던 중에 서버가 멈추면 트랜잭션이 롤백돼 데모 행은 남지 않고 결과도 비어 있는데,
 * 그 상태가 오래되면 상태 조회가 실패로 적는다({@link InstallationService#demoStatus}).
 */
@Component
public class DemoInstallLauncher implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DemoInstallLauncher.class);

    private final DemoDataInstaller demoInstaller;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("install-demo").factory());

    public DemoInstallLauncher(DemoDataInstaller demoInstaller) {
        this.demoInstaller = demoInstaller;
    }

    /** 데모 넣기를 시작한다 — 곧바로 돌아온다 */
    public void launch(long rootOperatorId) {
        executor.execute(() -> run(rootOperatorId, Instant.now()));
    }

    /** 데모를 넣고 결과를 적는다 — 실패해도 던지지 않는다(설치는 이미 끝났다) */
    void run(long rootOperatorId, Instant now) {
        try {
            DemoDataInstaller.Outcome outcome = demoInstaller.install(rootOperatorId, now);
            log.info("설치 직후 데모 데이터를 넣었다 rows={} files={}", outcome.rows(), outcome.files());
        } catch (RuntimeException e) {
            log.warn("데모 데이터를 넣지 못해 건너뛰었다 — 설치(구조)는 끝났다. 원인={}", e.getMessage(), e);
            try {
                demoInstaller.markFailed(Instant.now());
            } catch (RuntimeException markFailure) {
                log.warn("데모 실패를 적지 못했다 원인={}", markFailure.getClass().getSimpleName());
            }
        }
    }

    /** 끄는 중이면 새 일은 받지 않고, 넣던 데모는 잠깐 기다린다(못 끝내면 롤백된다) */
    @Override
    public void destroy() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
            log.warn("데모 데이터를 넣는 중에 서버가 꺼진다 — 데모는 롤백되고, 결과는 상태 조회가 시간이 지난 뒤 실패로 적는다");
        }
    }
}
