package com.community.installation.service;

import com.community.installation.dto.TablePack;
import com.community.installation.repository.master.InstallationRepositoryQDSL;
import com.community.installation.repository.master.TablePackRepository;
import com.community.business.service.BusinessPii;
import com.community.datakey.batch.DataKeyColumns;
import com.community.datakey.batch.EncryptedColumn;
import com.community.media.service.ImageStorage;
import com.community.zconfiguration.crypto.BlindIndex;
import com.community.zconfiguration.crypto.DataCipher;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설치 직후 <b>데모 팩</b>({@link DemoPackCatalog})을 넣는다 — 「데모 데이터 포함」으로 설치했을 때만, 설치가 커밋된 <b>뒤</b> 따로 한 트랜잭션으로.
 * 정본은 docs/features/install-contract.md 「데모 데이터 선택 (2026-11-08)」 3.
 *
 * <h2>복원 규칙</h2>
 * <ul>
 *   <li><b>시각</b>: 모든 시각·날짜 칸을 「팩 만든 시각 → 지금 − 1분」(분 단위로 내림)만큼 옮긴다 — 팩을 몇 달 전에 만들었어도 글이 방금 쓴 것처럼(지난 21일에 흩어져) 보인다.
 *       칸 이름에 {@code birth} 가 든 날짜(생년월일)는 옮기지 않는다.</li>
 *   <li><b>ID</b>: 팩 행의 ID(한 칸짜리 bigint 기본 키)를 같은 시간만큼 옮긴다(Snowflake 의 시각 부분 + 차이) — 글 월 조각·「오늘 글」 판정이 ID 범위로 자르므로 ID 와 시각이 같이 움직여야 한다.
 *       bigint 칸, bigint 배열, 글자·JSON 칸 안의 숫자(본문의 {@code data-image-id}, 로보티콘 {@code :emo-ID:} 등)에서 그 ID 를 모두 바꾼다. 구조(게시판·말머리) ID 는 팩 밖이라 그대로다.
 *       「지금 − 1분」으로 맞추므로 옮긴 ID 는 설치가 이미 매긴 ID(루트 운영자)보다 앞이고 앞으로 매길 ID 와 겹치지 않는다.</li>
 *   <li><b>운영자</b>: 팩을 만든 DB 의 운영자 ID(루트·데모 운영자 — 목록의 {@code operatorIds})는 이 설치의 루트 운영자 ID 가 된다.</li>
 *   <li><b>칸</b>: 팩의 칸·표가 이 DB 에 없으면(팩을 만든 뒤 마이그레이션이 지우거나 이름을 바꿨으면) 넣지 않고 실패한다({@link TablePackRestorer.PackMismatchException}).
 *       DB 에만 있는 칸은 기본값이 된다. 사용자 트리거는 끈 채 넣는다(팩의 행은 트리거가 이미 만든 결과다).</li>
 *   <li><b>개인정보 칸</b>(DataCipher 암호문 — {@link DataKeyColumns#ALL} 의 칸: 사업자 신원·광고주 연락처·등록증·계약서 파일 …): 팩의 키(목록의 {@code dataKey})로 풀어
 *       설치한 곳의 키로 다시 잠근다. 사업자등록번호 찾기 해시({@code business_profiles.biz_no_hash})도 설치한 곳의 찾기 키로 다시 만든다. 이 칸들은 ID·시각을 옮기지 않는다.
 *       설치한 곳에 키({@code ROBOYAM_DATA_KEY}·{@code ROBOYAM_INDEX_KEY})가 없으면 실패(데모만 건너뜀).</li>
 *   <li><b>파일</b>: 팩의 올린 파일을 같은 키(안의 ID 도 같은 규칙으로 옮김)로 저장소에 쓴다. 실패하면 쓴 파일을 지운다.</li>
 * </ul>
 * 실패하면 트랜잭션이 롤백돼 데모는 하나도 남지 않고, 부르는 쪽({@link InstallationService})이 {@link #markFailed} 로 적는다 — 설치(구조)는 그대로 쓸 수 있다.
 */
@Service
public class DemoDataInstaller {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInstaller.class);
    /** 옮긴 시각·ID 를 지금보다 이만큼 앞에 둔다 — 설치가 매긴 ID 와 겹치지 않게 */
    static final long MARGIN_MS = 60_000L;
    /** Snowflake 의 시각 부분 아래 비트 수(서버 번호 10 + 순번 12 — SnowflakeIdGenerator) */
    private static final int TIME_SHIFT_BITS = 22;
    private static final Pattern ID_IN_TEXT = Pattern.compile("(?<![0-9])[0-9]{15,19}(?![0-9])");
    public static final String APPLIED = "APPLIED";
    public static final String FAILED = "FAILED";

    private final DemoPackCatalog catalog;
    private final TablePackRestorer restorer;
    private final TablePackRepository tables;
    private final ImageStorage storage;
    private final InstallationRepositoryQDSL installation;
    private final DataCipher cipher;
    private final BlindIndex index;

    public DemoDataInstaller(DemoPackCatalog catalog, TablePackRestorer restorer, TablePackRepository tables, ImageStorage storage,
                             InstallationRepositoryQDSL installation, DataCipher cipher, BlindIndex index) {
        this.cipher = cipher;
        this.index = index;
        this.catalog = catalog;
        this.restorer = restorer;
        this.tables = tables;
        this.storage = storage;
        this.installation = installation;
    }

    /** 넣은 결과 */
    public record Outcome(int rows, int files) {
    }

    /** 데모 팩을 넣는다 — 실패하면 예외(트랜잭션 롤백, 쓴 파일은 지운다) */
    @Transactional
    public Outcome install(long rootOperatorId, Instant now) {
        DemoPackCatalog.Manifest manifest = catalog.manifest().orElseThrow(() -> new IllegalStateException("데모 팩이 없다"));
        TablePack data;
        try {
            data = catalog.readData();
        } catch (IOException e) {
            throw new UncheckedIOException("데모 팩 데이터를 읽지 못했다", e);
        }
        // 분 단위로 내림 — 분에 맞춰야 하는 칸(사업자 신청 기간 ck_ba_period 등)이 옮긴 뒤에도 맞게
        long deltaMs = Math.max(0L, Duration.between(Instant.parse(manifest.builtAt()), now).toMillis() - MARGIN_MS) / 60_000L * 60_000L;
        Rekey rekey = new Rekey(manifest.dataKey() == null ? null : new DataCipher(manifest.dataKey(), ""), cipher, index);
        Shift shift = new Shift(deltaMs, ids(data, deltaMs << TIME_SHIFT_BITS, manifest.operatorIds(), Long.toString(rootOperatorId)));

        tables.ensurePostPartitions(); // 글은 ID 범위로 월 조각에 들어간다 — 옮긴 ID 의 달 조각이 있어야 한다
        TablePackRestorer.Result result = restorer.restore(data, true, true, (table, row, columns) -> shift.row(table, rekey.row(table, row), columns));

        List<String> written = new ArrayList<>();
        try {
            for (DemoPackCatalog.FileEntry file : manifest.files()) {
                String key = shift.text(file.key());
                storage.put(key, catalog.readFile(file.key()));
                written.add(key);
            }
        } catch (IOException | RuntimeException e) {
            written.forEach(this::deleteQuietly);
            throw e instanceof IOException io ? new UncheckedIOException("데모 팩 파일을 쓰지 못했다", io) : (RuntimeException) e;
        }
        installation.markDemoResult(APPLIED, now);
        log.info("데모 데이터를 넣었다 — 표 {}개 · 행 {}개 · 파일 {}개 · 시각 {}시간 옮김", result.written().size(), result.total(), written.size(), deltaMs / 3_600_000L);
        return new Outcome(result.total(), written.size());
    }

    /** 데모를 넣지 못했다고 적는다(설치는 그대로) */
    @Transactional
    public void markFailed(Instant now) {
        installation.markDemoResult(FAILED, now);
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("데모 파일을 지우지 못했다 key={}", key);
        }
    }

    /** 팩 ID → 옮긴 ID, 만든 DB 의 운영자 ID → 루트 운영자 ID */
    private static Map<String, String> ids(TablePack data, long idShift, List<String> operatorIds, String root) {
        Map<String, String> map = new HashMap<>();
        for (TablePack.Table table : data.tables()) {
            if (table.key().size() != 1 || !"bigint".equals(table.columns().get(table.key().getFirst()))) {
                continue;
            }
            String key = table.key().getFirst();
            for (Map<String, Object> row : table.rows()) {
                Object id = row.get(key);
                // Snowflake 모양(15자리 이상)만 — serial 번호(1, 2, …)를 옮기면 같은 숫자의 다른 칸(개수·금액)까지 바뀐다
                if (id != null && id.toString().length() >= 15) {
                    map.put(id.toString(), Long.toString(Long.parseLong(id.toString()) + idShift));
                }
            }
        }
        operatorIds.forEach(id -> map.put(id, root));
        return map;
    }

    /** 개인정보 칸을 팩의 키에서 설치한 곳의 키로 — 칸 목록은 데이터 키 교체 배치와 같은 정본({@link DataKeyColumns#ALL}) */
    private record Rekey(DataCipher from, DataCipher to, BlindIndex index) {

        private static final Map<String, List<EncryptedColumn>> BY_TABLE = byTable();
        /** 다시 잠근 칸 — {@link Shift} 가 건드리지 않는다 */
        static final Set<String> COLUMNS = columns();

        private static Map<String, List<EncryptedColumn>> byTable() {
            Map<String, List<EncryptedColumn>> map = new HashMap<>();
            DataKeyColumns.ALL.forEach(c -> map.computeIfAbsent(c.table(), t -> new ArrayList<>()).add(c));
            return map;
        }

        private static Set<String> columns() {
            Set<String> set = new HashSet<>();
            DataKeyColumns.ALL.forEach(c -> set.add(c.table() + "." + c.cipherColumn()));
            set.add("business_profiles.biz_no_hash");
            return set;
        }

        Map<String, Object> row(String table, Map<String, Object> row) {
            List<EncryptedColumn> list = BY_TABLE.get(table);
            if (list == null) {
                return row;
            }
            Map<String, Object> out = new LinkedHashMap<>(row);
            for (EncryptedColumn column : list) {
                Object value = out.get(column.cipherColumn());
                if (value == null) {
                    continue;
                }
                if (from == null) {
                    throw new IllegalStateException("팩에 암호문이 있는데 팩의 키(dataKey)가 없다: " + table + "." + column.cipherColumn());
                }
                if (column.bytes()) {
                    String hex = value.toString();
                    byte[] plain = from.decryptBytes(column.purpose(), HexFormat.of().parseHex(hex.startsWith("\\x") ? hex.substring(2) : hex));
                    out.put(column.cipherColumn(), "\\x" + HexFormat.of().formatHex(to.encryptBytes(column.purpose(), plain)));
                } else {
                    String plain = from.decrypt(column.purpose(), value.toString());
                    out.put(column.cipherColumn(), to.encrypt(column.purpose(), plain));
                    if ("business_profiles".equals(table) && "biz_no_enc".equals(column.cipherColumn()) && out.get("biz_no_hash") != null) {
                        out.put("biz_no_hash", index.hash(BusinessPii.BIZ_NO, plain)); // 찾기 해시도 이 설치의 찾기 키로
                    }
                }
            }
            return out;
        }
    }

    /** 행·글자 안의 시각·ID 를 옮긴다 */
    private record Shift(long deltaMs, Map<String, String> ids) {

        Map<String, Object> row(String table, Map<String, Object> row, Map<String, TablePackRepository.Column> columns) {
            Map<String, Object> out = new LinkedHashMap<>(row);
            for (Map.Entry<String, Object> entry : out.entrySet()) {
                Object value = entry.getValue();
                TablePackRepository.Column column = columns.get(entry.getKey());
                if (value == null || column == null || Rekey.COLUMNS.contains(table + "." + entry.getKey())) {
                    continue; // 다시 잠근 개인정보 칸은 그대로(암호문 안의 숫자를 바꾸면 풀 수 없다)
                }
                if (column.isBigint()) {
                    entry.setValue(ids.getOrDefault(value.toString(), value.toString()));
                } else if (column.isTemporal()) {
                    if (!entry.getKey().contains("birth")) {
                        entry.setValue(time(value.toString()));
                    }
                } else if (column.isTextual() || column.type().endsWith("[]")) {
                    entry.setValue(deep(value)); // 글자·JSON·배열 — 안의 ID. bytea(16진 글자) 등 다른 형식은 건드리지 않는다
                }
            }
            return out;
        }

        private Object deep(Object value) {
            if (value instanceof String text) {
                return text(text);
            }
            if (value instanceof Map<?, ?> map) {
                Map<Object, Object> out = new LinkedHashMap<>();
                map.forEach((k, v) -> out.put(k, deep(v)));
                return out;
            }
            if (value instanceof List<?> list) {
                return list.stream().map(this::deep).toList();
            }
            if (value instanceof Number number && !(value instanceof Double) && !(value instanceof Float)) {
                String moved = ids.get(number.toString());
                return moved == null ? value : Long.valueOf(moved);
            }
            return value;
        }

        String text(String text) {
            Matcher m = ID_IN_TEXT.matcher(text);
            if (!m.find()) {
                return text;
            }
            StringBuilder out = new StringBuilder();
            do {
                m.appendReplacement(out, ids.getOrDefault(m.group(), m.group()));
            } while (m.find());
            m.appendTail(out);
            return out.toString();
        }

        /** 내보낸 시각 글자(row_to_json 모양) — 시간대 있는 시각 · 없는 시각 · 날짜 */
        private String time(String value) {
            if (value.length() == 10) {
                return LocalDate.parse(value).plusDays(Math.round(deltaMs / 86_400_000.0)).toString();
            }
            try {
                return OffsetDateTime.parse(value).plus(Duration.ofMillis(deltaMs)).toString();
            } catch (RuntimeException e) {
                return LocalDateTime.parse(value).plus(Duration.ofMillis(deltaMs)).toString();
            }
        }
    }
}
