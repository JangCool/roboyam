package com.community.batch.config;

import com.community.batch.service.BatchCatalogSynchronizer;
import com.community.batch.service.BatchRunner;
import com.community.batch.service.BatchScheduler;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 서버가 뜰 때 ① 배치 카탈로그를 DB 와 맞추고 ② 죽은 서버가 남긴 「실행 중」 로그를 실패로 닫고 ③ 예약 실행을 시작한다. 순서가 중요하다 — 예약이 읽을 설정 행이 먼저 있어야 한다.
 * ①이 실패하면 기동을 멈춘다(설정이 없는 채로 요청을 받지 않게).
 */
@Component
public class BatchBootstrapRunner implements ApplicationRunner {

    private final BatchCatalogSynchronizer synchronizer;
    private final BatchRunner runner;
    private final BatchScheduler scheduler;

    public BatchBootstrapRunner(BatchCatalogSynchronizer synchronizer, BatchRunner runner, BatchScheduler scheduler) {
        this.synchronizer = synchronizer;
        this.runner = runner;
        this.scheduler = scheduler;
    }

    @Override
    public void run(ApplicationArguments args) {
        synchronizer.sync();
        runner.sweepInterrupted();
        scheduler.start();
    }
}
