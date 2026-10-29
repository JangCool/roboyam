package com.community.address.service;

import com.community.address.dto.response.AddressDbLoadPage;
import com.community.address.dto.response.AddressDbLoadRowView;
import com.community.address.dto.response.AddressImportProgressView;
import com.community.address.dto.response.AddressImportStartedResponse;
import com.community.address.dto.response.AddressServerFilesView;
import com.community.address.entity.AddressDbLoadEntity;
import com.community.address.entity.AddressImportPhase;
import com.community.address.entity.AddressLoadStatus;
import com.community.address.exception.AddressImportJobNotFoundException;
import com.community.address.exception.AddressImportRunningException;
import com.community.address.repository.master.AddressDbLoadQueries;
import com.community.address.repository.master.AddressDbLoadRepository;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.permission.dto.AdminActor;
import com.community.zconfiguration.exception.ValidationException;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.message.MessageRef;
import com.community.zconfiguration.message.Messages;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 주소 DB 올리기(docs/features/address-search-contract.md) — 조회 {@code site.read}, 올리기 {@code site.manage}(컨트롤러가 선언).
 *
 * <ul>
 *   <li><b>시작</b>: 파일 1~2개(zip · 빈 파일 아님 · 한 개 {@value #FILE_MAX_MB}MB 이하)와 글자 집합(MS949 | UTF-8)을 검사해 400 {@code VALIDATION}
 *       ({@code fields.files}·{@code fields.charset}) → 돌고 있는 적재가 있으면 409 {@code ADDRESS_IMPORT_RUNNING}(DB 부분 유일 색인이 최종 판정) →
 *       파일을 서버 임시 파일로 옮기고 적재 줄({@code RUNNING})을 넣은 뒤 가상 스레드로 {@link AddressImportWorker} 를 돌리고 작업 번호를 바로 준다.</li>
 *   <li><b>서버 폴더에서 시작</b>: 올리지 않고 {@link AddressServerFiles} 폴더의 zip 을 이름으로 고른다(크기 한도 없음, 파일은 지우지 않는다). 나머지는 같다.</li>
 *   <li><b>진행</b>: 이 서버가 돌리는 중이면 메모리의 단계·줄 수, 아니면 적재 줄(끝났으면 결과·사유 문구를 요청 언어로).</li>
 *   <li><b>서버가 다시 뜨면</b> 이 서버가 돌리다 멈춘 {@code RUNNING} 줄을 「중단」으로 닫는다(트랜잭션이 되돌려져 주소 DB 는 그대로다).</li>
 * </ul>
 * Java 배치({@code BatchJob})로 만들지 않은 까닭: 운영자가 올린 파일을 받은 서버에서만 돌 수 있는 한 번짜리 작업이고 예약·재실행이 없다 — 배치 요청 표는 배치를 켠
 * 다른 서버가 집을 수 있어 파일이 없는 서버로 갈 수 있다.
 */
@Service
public class AddressImportService {

    private static final Logger log = LoggerFactory.getLogger(AddressImportService.class);

    static final int FILE_MAX_MB = 300;
    static final long FILE_MAX_BYTES = FILE_MAX_MB * 1024L * 1024L;
    static final int MAX_FILES = 2;
    /** 화면이 고르는 글자 집합 — 행정안전부 파일은 CP949(MS949) */
    static final Set<String> CHARSETS = Set.of("MS949", "UTF-8");
    static final int HISTORY_SIZE_MAX = 50;

    private final AddressDbLoadRepository loads;
    private final AddressDbLoadQueries loadQueries;
    private final OperatorRepository operators;
    private final AddressImportJobs jobs;
    private final AddressImportWorker worker;
    private final AddressServerFiles serverFiles;
    private final SnowflakeIdGenerator ids;
    private final Messages messages;
    private final TransactionTemplate tx;
    private final int nodeId;

    public AddressImportService(AddressDbLoadRepository loads, AddressDbLoadQueries loadQueries, OperatorRepository operators, AddressImportJobs jobs,
                                AddressImportWorker worker, AddressServerFiles serverFiles, SnowflakeIdGenerator ids, Messages messages, PlatformTransactionManager transactions,
                                @Value("${roboyam.node-id}") int nodeId) {
        this.loads = loads;
        this.loadQueries = loadQueries;
        this.operators = operators;
        this.jobs = jobs;
        this.worker = worker;
        this.serverFiles = serverFiles;
        this.ids = ids;
        this.messages = messages;
        this.tx = new TransactionTemplate(transactions);
        this.nodeId = nodeId;
    }

    public AddressImportStartedResponse start(AdminActor actor, List<MultipartFile> files, String charset) {
        List<MultipartFile> chosen = files == null ? List.of() : files;
        check(chosen, charset);
        if (loads.findFirstByStatus(AddressLoadStatus.RUNNING).isPresent()) {
            throw new AddressImportRunningException();
        }

        List<AddressFileReader.Source> sources = new ArrayList<>();
        try {
            for (MultipartFile file : chosen) {
                sources.add(copyToTemp(file));
            }
            return launch(actor, sources, charset);
        } catch (RuntimeException e) {
            deleteAll(sources);
            throw e;
        }
    }

    /** 서버 폴더의 파일로 적재 — 검사 순서: 글자 집합 · 파일 이름(400) → 돌고 있는 적재(409). 파일은 지우지 않는다 */
    public AddressImportStartedResponse startFromServer(AdminActor actor, List<String> fileNames, String charset) {
        if (charset == null || !CHARSETS.contains(charset)) {
            throw ValidationException.of("charset", new MessageRef("address.import.charsetUnknown"));
        }
        List<AddressFileReader.Source> sources = serverFiles.resolve(fileNames);
        if (loads.findFirstByStatus(AddressLoadStatus.RUNNING).isPresent()) {
            throw new AddressImportRunningException();
        }
        return launch(actor, sources, charset);
    }

    public AddressServerFilesView serverFiles() {
        return new AddressServerFilesView(serverFiles.enabled(), serverFiles.list());
    }

    /** 적재 줄({@code RUNNING})을 넣고 가상 스레드로 적재를 시작한다 — 이미 돌고 있으면(부분 유일 색인) 409 */
    private AddressImportStartedResponse launch(AdminActor actor, List<AddressFileReader.Source> sources, String charset) {
        try {
            long loadId = ids.nextId();
            List<String> names = sources.stream().map(AddressFileReader.Source::name).toList();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            tx.executeWithoutResult(status -> {
                loads.persist(AddressDbLoadEntity.start(loadId, names, charset, nodeId, actor.operatorId(), now));
                loads.flush();
            });
            AddressImportJobs.Live live = jobs.begin(loadId);
            Charset decoding = Charset.forName(charset);
            Thread.ofVirtual().name("address-import-" + loadId).start(() -> worker.run(loadId, sources, decoding, actor.operatorId(), actor.ip(), live));
            log.info("주소 DB 적재를 시작했다 loadId={} files={} by operatorId={}", loadId, names, actor.operatorId());
            return new AddressImportStartedResponse(Long.toString(loadId));
        } catch (DataIntegrityViolationException e) {
            throw new AddressImportRunningException(); // 같은 때 다른 운영자가 먼저 시작했다(부분 유일 색인)
        }
    }

    /** 파일·글자 집합 검사 — 틀린 칸을 모아 400 */
    static void check(List<MultipartFile> files, String charset) {
        Map<String, MessageRef> fields = new LinkedHashMap<>();
        if (files.isEmpty() || files.size() > MAX_FILES) {
            fields.put("files", new MessageRef("address.import.filesCount", MAX_FILES));
        } else {
            for (MultipartFile file : files) {
                MessageRef problem = fileProblem(file.getOriginalFilename(), file.getSize());
                if (problem != null) {
                    fields.put("files", problem);
                    break;
                }
            }
        }
        if (charset == null || !CHARSETS.contains(charset)) {
            fields.put("charset", new MessageRef("address.import.charsetUnknown"));
        }
        if (!fields.isEmpty()) {
            throw new ValidationException(fields);
        }
    }

    /** 파일 하나의 문제 — zip · 빈 파일 아님 · 300MB 이하(화면 {@code UtilAddressSettings.fileProblem} 과 같은 순서) */
    static MessageRef fileProblem(String name, long size) {
        if (name == null || !name.toLowerCase().endsWith(".zip")) {
            return new MessageRef("address.import.fileNotZip");
        }
        if (size <= 0) {
            return new MessageRef("address.import.fileEmpty");
        }
        if (size > FILE_MAX_BYTES) {
            return new MessageRef("address.import.fileTooLarge", FILE_MAX_MB);
        }
        return null;
    }

    public AddressImportProgressView progress(String jobId) {
        long id;
        try {
            id = Long.parseLong(jobId);
        } catch (NumberFormatException e) {
            throw new AddressImportJobNotFoundException();
        }
        return loads.findById(id).map(this::view).orElseThrow(AddressImportJobNotFoundException::new);
    }

    /** 돌고 있는 적재 — 없으면 null */
    public AddressImportProgressView current() {
        return loads.findFirstByStatus(AddressLoadStatus.RUNNING).map(this::view).orElse(null);
    }

    public AddressDbLoadPage history(int page, int size) {
        int safeSize = Math.clamp(size, 1, HISTORY_SIZE_MAX);
        int safePage = Math.max(page, 0);
        List<AddressDbLoadEntity> rows = loadQueries.donePage(safePage, safeSize);
        Map<Long, String> names = operatorNames(rows.stream().map(AddressDbLoadEntity::getStartedBy).collect(Collectors.toSet()));
        List<AddressDbLoadRowView> items = rows.stream()
                .map(row -> new AddressDbLoadRowView(Long.toString(row.getId()), row.getFinishedAt(), String.join(", ", row.getFileNames()),
                        nonNull(row.getEntryCount()), nonNull(row.getEnglishCount()), names.get(row.getStartedBy())))
                .toList();
        return new AddressDbLoadPage(items, loadQueries.doneCount(), safePage, safeSize);
    }

    /** 서버가 다시 떴다 — 이 서버가 돌리다 멈춘 적재를 닫는다 */
    @EventListener(ApplicationReadyEvent.class)
    public void closeInterrupted() {
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            for (AddressDbLoadEntity row : loads.findByStatusAndNodeId(AddressLoadStatus.RUNNING, nodeId)) {
                if (jobs.live(row.getId()).isEmpty()) {
                    row.fail("address.import.interrupted", List.of(), now);
                    log.warn("서버가 다시 떠 멈춘 주소 DB 적재를 닫았다 loadId={}", row.getId());
                }
            }
        });
    }

    private AddressImportProgressView view(AddressDbLoadEntity row) {
        String startedBy = row.getStartedBy() == null ? null : operators.findById(row.getStartedBy()).map(OperatorEntity::getOperatorName).orElse(null);
        String jobId = Long.toString(row.getId());
        return switch (row.getStatus()) {
            case RUNNING -> jobs.live(row.getId())
                    .map(live -> new AddressImportProgressView(jobId, live.phase(), live.total(), live.processed(), false, false, null, null, row.getFileNames(),
                            row.getStartedAt(), startedBy))
                    // 다른 관리자 서버가 돌리는 적재 — 줄 수는 모르고 단계만 안다
                    .orElseGet(() -> new AddressImportProgressView(jobId, row.getPhase(), -1, 0, false, false, null, null, row.getFileNames(),
                            row.getStartedAt(), startedBy));
            case DONE -> new AddressImportProgressView(jobId, AddressImportPhase.DONE, nonNull(row.getEntryCount()), nonNull(row.getEntryCount()), true, false,
                    null, message(row), row.getFileNames(), row.getStartedAt(), startedBy);
            case FAILED -> new AddressImportProgressView(jobId, AddressImportPhase.FAILED, -1, 0, false, true, row.getPhase(), message(row), row.getFileNames(),
                    row.getStartedAt(), startedBy);
        };
    }

    private String message(AddressDbLoadEntity row) {
        return row.getMessageKey() == null ? null : messages.get(row.getMessageKey(), row.getMessageArgs());
    }

    private Map<Long, String> operatorNames(Set<Long> operatorIds) {
        Set<Long> wanted = new HashSet<>(operatorIds);
        wanted.remove(null);
        return operators.findAllById(wanted).stream().collect(Collectors.toMap(OperatorEntity::getId, OperatorEntity::getOperatorName, (a, b) -> a));
    }

    private static AddressFileReader.Source copyToTemp(MultipartFile file) {
        try {
            Path temp = Files.createTempFile("roboyam-address-", ".zip");
            file.transferTo(temp);
            return new AddressFileReader.Source(temp, baseName(file.getOriginalFilename()));
        } catch (IOException e) {
            throw new UncheckedIOException("주소 DB 파일을 임시 파일로 옮기지 못했다", e);
        }
    }

    /** 브라우저가 경로를 붙여 보내는 경우가 있다 — 이름만 */
    private static String baseName(String name) {
        String value = name == null ? "upload.zip" : name;
        return value.substring(Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\')) + 1);
    }

    private static void deleteAll(List<AddressFileReader.Source> sources) {
        for (AddressFileReader.Source source : sources) {
            if (!source.temporary()) {
                continue;
            }
            try {
                Files.deleteIfExists(source.path());
            } catch (IOException e) {
                log.warn("주소 DB 임시 파일을 지우지 못했다 path={}", source.path());
            }
        }
    }

    private static long nonNull(Long value) {
        return value == null ? 0 : value;
    }
}
