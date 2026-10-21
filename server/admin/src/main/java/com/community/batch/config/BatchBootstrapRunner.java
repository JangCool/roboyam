package com.community.batch.config;

import com.community.batch.service.BatchCatalogSynchronizer;
import com.community.batch.service.BatchRunner;
import com.community.batch.service.BatchScheduler;
import com.community.batch.service.DbBatchGateway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 서버가 뜰 때 ① Java 배치 카탈로그를 DB 와 맞추고 ② DB 배치(pg_cron)를 준비하고 ③ 죽은 서버가 남긴 「실행 중」 로그를 실패로 닫고 ④ 예약 실행을 시작한다. 순서가 중요하다 — 예약이
 * 읽을 설정 행이 먼저 있어야 한다. ①이 실패하면 기동을 멈춘다(설정이 없는 채로 요청을 받지 않게). ②는 실패해도 멈추지 않는다 — pg_cron 이 없는 DB 에서도 서버와 Java 배치는 떠야 하고
 * DB 배치만 화면에 「사용 불가」로 보인다.
 */
@Component
public class BatchBootstrapRunner implements ApplicationRunner {

    private final BatchCatalogSynchronizer synchronizer;
    private final DbBatchGateway database;
    private final BatchRunner runner;
    private final BatchScheduler scheduler;

    public BatchBootstrapRunner(BatchCatalogSynchronizer synchronizer, DbBatchGateway database, BatchRunner runner, BatchScheduler scheduler) {
        this.synchronizer = synchronizer;
        this.database = database;
        this.runner = runner;
        this.scheduler = scheduler;
    }

    @Override
    public void run(ApplicationArguments args) {
        synchronizer.sync();
        database.prepare();
        runner.sweepInterrupted();
        scheduler.start();
    }
}
