package com.community.address.service;

import com.community.address.entity.AddressDbLoadEntity;
import com.community.address.entity.AddressImportPhase;
import com.community.address.entity.AddressLoadStatus;
import com.community.address.exception.AddressImportFailure;
import com.community.address.repository.master.AddressDbLoadQueries;
import com.community.address.repository.master.AddressDbLoadRepository;
import com.community.address.repository.master.AddressImportStagingQueries;
import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주소 DB 적재 본체 — {@link AddressImportService#start} 가 가상 스레드에서 부른다(pionworld2 {@code AddressImportWorker} 를 옮겼다).
 *
 * <p>한 트랜잭션 안에서: 스테이징 비우기 → 줄 수 세기 → 도로명·지번·영문을 스테이징에 넣기 → (지번·영문이 있으면) 관리번호로 덩어리 UPDATE 해 붙이기 → 색인 만들기 →
 * 이름 세 번 바꾸기로 {@code address_entries} 와 맞바꾸기 → 적재 줄 {@code DONE} + 감사. 어디서 멈추든 트랜잭션이 되돌려 지금 주소 DB 는 그대로다.
 * 실패는 따로 트랜잭션에서 적재 줄 {@code FAILED}(사유 문구 키) + 감사(실패)로 남긴다. 올린 임시 파일은 끝나면 지운다(서버 폴더의 파일은 그대로 둔다).
 *
 * <p>바꾼 것(pionworld2 대비): 스테이징 색인을 지우고 다 넣은 뒤 새로 만든다(수백만 줄을 GIN 색인을 단 채로 넣지 않게) · 진행 단계를 적재 줄에도 남긴다 ·
 * 새 건수가 지금의 절반 아래면 멈추는 규칙은 같다({@code address.import.dropped}). DB 문장(DDL·배치 넣기·붙이기·맞바꾸기)은 저장소 {@link AddressImportStagingQueries} 다.
 */
@Component
public class AddressImportWorker {

    private static final Logger log = LoggerFactory.getLogger(AddressImportWorker.class);

    /** 붙이기 UPDATE 한 번이 맡는 스테이징 id 범위 — 진행률 단위 */
    static final long MERGE_CHUNK = 200_000;
    /** 새 건수가 지금 건수의 이 비율보다 적으면 잘린 파일로 보고 멈춘다 */
    static final double DROP_RATIO = 0.5;

    public static final String TARGET_TYPE = "ADDRESS_SEARCH";

    private final AddressImportStagingQueries staging;
    private final TransactionTemplate main;
    private final TransactionTemplate separate;
    private final AddressDbLoadRepository loads;
    private final AddressDbLoadQueries loadQueries;
    private final AddressImportJobs jobs;
    private final AuditLogService audit;

    public AddressImportWorker(AddressImportStagingQueries staging, PlatformTransactionManager transactions, AddressDbLoadRepository loads, AddressDbLoadQueries loadQueries,
                               AddressImportJobs jobs, AuditLogService audit) {
        this.staging = staging;
        this.main = new TransactionTemplate(transactions);
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.loads = loads;
        this.loadQueries = loadQueries;
        this.jobs = jobs;
        this.audit = audit;
    }

    /** 적재 한 번 — 예외를 밖으로 던지지 않는다(결과는 적재 줄에 남는다) */
    void run(long loadId, List<AddressFileReader.Source> sources, Charset charset, long operatorId, String ip, AddressImportJobs.Live live) {
        try {
            main.executeWithoutResult(status -> load(loadId, sources, charset, operatorId, ip, live));
            log.info("주소 DB 를 적재했다 loadId={} files={}", loadId, sources.stream().map(AddressFileReader.Source::name).toList());
        } catch (AddressImportFailure failure) {
            log.warn("주소 DB 적재를 멈췄다 loadId={} reason={} args={}", loadId, failure.messageKey(), failure.args());
            fail(loadId, operatorId, ip, failure);
        } catch (RuntimeException e) {
            log.error("주소 DB 적재 중 오류 loadId={}", loadId, e);
            fail(loadId, operatorId, ip, new AddressImportFailure("address.import.unknown"));
        } finally {
            jobs.end(loadId);
            for (AddressFileReader.Source source : sources) {
                if (!source.temporary()) {
                    continue; // 서버 폴더의 파일은 운영자 것 — 지우지 않는다
                }
                try {
                    Files.deleteIfExists(source.path());
                } catch (IOException e) {
                    log.warn("주소 DB 임시 파일을 지우지 못했다 path={}", source.path());
                }
            }
        }
    }

    private void load(long loadId, List<AddressFileReader.Source> sources, Charset charset, long operatorId, String ip, AddressImportJobs.Live live) {
        staging.truncateAll();
        staging.dropStagingIndexes();

        phase(loadId, live, AddressImportPhase.COUNTING);
        live.restart(AddressFileReader.countLines(sources, charset));

        phase(loadId, live, AddressImportPhase.PARSING);
        AddressFileReader.Counts counts = AddressFileReader.read(sources, charset, new StagingSink(live));

        Long current = staging.countEntries();
        if (current != null && current > 0 && counts.road() < current * DROP_RATIO) {
            throw new AddressImportFailure("address.import.dropped", "%,d".formatted(counts.road()), "%,d".formatted(current));
        }

        String suffix = Long.toString(loadId);
        staging.createMgmtNoIndex(loadId);
        long english = 0;
        if (counts.jibun() > 0 || counts.english() > 0) {
            // 방금 넣은 표는 통계가 없어 계획기가 「0줄」로 보고 붙이기를 중첩 루프로 짠다 — 붙이기 전에 꼭 ANALYZE(pionworld2 2026-08-04 실사용에서 12분 넘게 안 끝났던 원인)
            staging.analyzeEntries();
            phase(loadId, live, AddressImportPhase.MERGING);
            long[] range = staging.idRange();
            int sweeps = (counts.jibun() > 0 ? 1 : 0) + (counts.english() > 0 ? 1 : 0);
            live.restart(sweeps * (range[1] - range[0] + 1));
            if (counts.jibun() > 0) {
                staging.prepareJibunMerge();
                merge(staging::mergeJibun, range, live);
            }
            if (counts.english() > 0) {
                staging.prepareEnglishMerge();
                english = merge(staging::mergeEnglish, range, live);
            }
            staging.truncateSide();
        }

        phase(loadId, live, AddressImportPhase.SWAPPING);
        live.restart(-1); // 검색 색인 만들기는 줄 수로 셀 수 없다 — 화면은 진행 막대를 「도는 중」으로 그린다
        staging.createSearchIndex(loadId);
        staging.analyzeEntries();
        // 이름 바꾸기는 검색 중인 질의가 끝나기를 기다린다 — 너무 오래 막히면 멈추고 실패로 남긴다(지금 DB 는 그대로)
        staging.swapIntoPlace();

        AddressDbLoadEntity row = loads.findById(loadId).orElseThrow(() -> new IllegalStateException("적재 줄이 없다 loadId=" + loadId));
        row.succeed(counts.road(), counts.jibun(), english, "address.import.done", List.of("%,d".formatted(counts.road()), "%,d".formatted(english)), now());
        audit.record(AuditEvent.byOperator(operatorId, ip, AuditActions.ADDRESS_DB_IMPORT, AuditResult.SUCCESS).target(TARGET_TYPE, suffix)
                .with("files", row.getFileNames()).with("entries", counts.road()).with("jibun", counts.jibun()).with("english", english));
    }

    /** 실패를 따로 트랜잭션에서 남긴다 — 본 트랜잭션은 이미 되돌려졌다 */
    private void fail(long loadId, long operatorId, String ip, AddressImportFailure failure) {
        try {
            separate.executeWithoutResult(status -> loads.findById(loadId).filter(row -> row.getStatus() == AddressLoadStatus.RUNNING)
                    .ifPresent(row -> row.fail(failure.messageKey(), failure.args(), now())));
            audit.recordIsolated(AuditEvent.byOperator(operatorId, ip, AuditActions.ADDRESS_DB_IMPORT, AuditResult.FAILURE)
                    .target(TARGET_TYPE, Long.toString(loadId)).with("reason", failure.messageKey()));
        } catch (RuntimeException e) {
            log.error("주소 DB 적재 실패를 기록하지 못했다 loadId={}", loadId, e);
        }
    }

    /** 단계를 화면(메모리)과 적재 줄에 남긴다 — 줄은 따로 트랜잭션(본 트랜잭션이 끝나기 전에도 보이게) */
    private void phase(long loadId, AddressImportJobs.Live live, AddressImportPhase phase) {
        live.phase(phase);
        separate.executeWithoutResult(status -> loadQueries.markPhase(loadId, phase));
    }

    /** id 범위를 덩어리로 나눠 UPDATE — 매칭 줄이 아니라 훑은 범위로 진행률을 올린다(지번·영문이 없는 주소도 있어서). 바뀐 줄 수를 돌려준다 */
    private long merge(RangeMerge sweep, long[] range, AddressImportJobs.Live live) {
        long changed = 0;
        for (long start = range[0]; start <= range[1]; start += MERGE_CHUNK) {
            long end = Math.min(start + MERGE_CHUNK - 1, range[1]);
            changed += sweep.merge(start, end);
            live.add(end - start + 1);
        }
        return changed;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** id 범위 하나를 붙이는 문장(지번 또는 영문) — 바뀐 줄 수 */
    @FunctionalInterface
    private interface RangeMerge {
        int merge(long fromId, long toId);
    }

    /** 덩어리를 스테이징 표에 JDBC 배치로 넣는다 */
    private final class StagingSink implements AddressFileReader.Sink {

        private final AddressImportJobs.Live live;

        private StagingSink(AddressImportJobs.Live live) {
            this.live = live;
        }

        @Override
        public void roads(List<AddressFileParser.RoadRow> batch) {
            insert(staging::insertRoads, batch, row -> new Object[] {row.mgmtNo(), row.zipcode(), row.sido(), row.sigungu(), row.eupmyeondong(), row.roadName(),
                    row.buildingNoMain(), row.buildingNoSub(), row.buildingName(), row.roadAddress(), row.searchText()});
        }

        @Override
        public void jibuns(List<AddressFileParser.JibunRow> batch) {
            insert(staging::insertJibuns, batch, row -> new Object[] {row.mgmtNo(), row.beopjeongDong(), row.beopjeongRi(), row.mountain(), row.jibunMain(), row.jibunSub()});
        }

        @Override
        public void englishes(List<AddressFileParser.EnglishRow> batch) {
            insert(staging::insertEnglishes, batch, row -> new Object[] {row.mgmtNo(), row.roadAddressEn()});
        }

        private <T> void insert(Consumer<List<Object[]>> sink, List<T> batch, Function<T, Object[]> values) {
            sink.accept(batch.stream().map(values).toList());
            live.add(batch.size());
        }
    }
}
