package com.community.installation.service;

import com.community.installation.dto.TablePack;
import com.community.installation.repository.master.TablePackRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 표 단위 행 묶음({@link TablePack})을 부르는 쪽의 트랜잭션 안에서 DB 에 넣는다 — 구조 스냅숏({@link PresetStructureApplier})과 데모 팩({@link DemoDataInstaller})이 같이 쓴다.
 *
 * <ol>
 *   <li>표마다 이 DB 의 칸을 찾는다. 팩의 칸이 DB 에 없으면(뒤 마이그레이션이 지웠거나 이름을 바꿨으면) {@code strict} 일 때 {@link PackMismatchException},
 *       아니면 그 칸만 빼고 경고한다. DB 에만 있는 칸(뒤 마이그레이션이 더한 칸)은 넣지 않으므로 기본값이 된다. 생성 칸은 넣지 않는다. 표가 없으면 같은 규칙.</li>
 *   <li>{@code REPLACE} 표를 <b>뒤 차례부터</b> 비운다(뒤 표가 앞 표를 FK 로 가리키므로).</li>
 *   <li>앞 차례부터 행을 넣는다 — 행마다 {@code transform} 을 거친 뒤 {@link #CHUNK} 행씩 한 문장으로. 같은 문장 안의 FK(자기 표 부모 등)는 문장 끝에 확인된다.
 *       표의 {@code late} 칸(뒤 차례 표를 가리키는 칸 — 서로 가리키는 표)은 비운 채 넣고, 모든 표를 넣은 뒤 채운다.</li>
 *   <li>시퀀스 칸을 맞춘다.</li>
 * </ol>
 * 실패하면 예외가 그대로 올라간다 — 되돌리기는 부르는 쪽(세이브포인트·트랜잭션)이 한다.
 */
@Component
public class TablePackRestorer {

    private static final Logger log = LoggerFactory.getLogger(TablePackRestorer.class);
    /** 한 문장에 넣는 행 수 — JSON 인자 크기를 적당히 */
    static final int CHUNK = 1000;
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TablePackRepository repository;

    public TablePackRestorer(TablePackRepository repository) {
        this.repository = repository;
    }

    /** 행 하나를 넣기 전에 바꾼다(운영자 ID·시각·ID 옮기기). 칸 정보는 이 DB 의 것이다 */
    @FunctionalInterface
    public interface RowTransform {
        Map<String, Object> apply(String table, Map<String, Object> row, Map<String, TablePackRepository.Column> columns);
    }

    /** 팩의 칸·표가 이 DB 와 맞지 않는다 — 데모 팩은 이때 넣지 않고 건너뛴다 */
    public static class PackMismatchException extends RuntimeException {
        public PackMismatchException(String message) {
            super(message);
        }
    }

    /** 넣은 결과 — 표 이름 → 넣거나 고친 행 수 */
    public record Result(Map<String, Integer> written) {

        public int total() {
            return written.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /** 표마다 이 DB 에서 쓸 칸 — 확인을 마친 것 */
    private record Plan(TablePack.Table table, TablePack.Mode mode, Map<String, TablePackRepository.Column> columns, List<String> write) {
    }

    /**
     * 팩을 넣는다.
     *
     * @param strict      팩의 표·칸이 DB 에 없으면 실패(데모) / 빼고 경고(구조)
     * @param skipTriggers 사용자 트리거를 끈 채 넣는다(데모 — 트리거가 이미 만든 결과를 담고 있다)
     */
    public Result restore(TablePack pack, boolean strict, boolean skipTriggers, RowTransform transform) {
        List<Plan> plans = plan(pack, strict);
        List<String> triggered = skipTriggers ? repository.withUserTriggers(plans.stream().map(p -> p.table().table()).toList()) : List.of();
        triggered.forEach(table -> repository.setUserTriggers(table, false));

        for (int i = plans.size() - 1; i >= 0; i--) {
            Plan plan = plans.get(i);
            if (plan.mode() == TablePack.Mode.REPLACE) {
                repository.deleteAll(plan.table().table());
            }
        }
        Map<String, Integer> written = new LinkedHashMap<>();
        Map<Plan, List<Map<String, Object>>> lateRows = new java.util.IdentityHashMap<>(); // 계획(행 목록이 든 레코드)은 같음 비교가 무겁다 — 같은 객체로만
        for (Plan plan : plans) {
            String table = plan.table().table();
            List<Map<String, Object>> rows = plan.table().rows();
            List<String> late = plan.table().late().stream().filter(plan.write()::contains).toList();
            int count = 0;
            for (int from = 0; from < rows.size(); from += CHUNK) {
                List<Map<String, Object>> chunk = new ArrayList<>(Math.min(CHUNK, rows.size() - from));
                for (Map<String, Object> row : rows.subList(from, Math.min(rows.size(), from + CHUNK))) {
                    Map<String, Object> kept = new LinkedHashMap<>();
                    for (String column : plan.write()) {
                        kept.put(column, row.get(column));
                    }
                    Map<String, Object> moved = transform.apply(table, kept, plan.columns());
                    if (!late.isEmpty()) {
                        // 나중에 채울 칸 — 지금은 비우고, 열쇠와 그 값을 모아 둔다
                        Map<String, Object> later = new LinkedHashMap<>();
                        plan.table().key().forEach(k -> later.put(k, moved.get(k)));
                        late.forEach(c -> later.put(c, moved.get(c)));
                        lateRows.computeIfAbsent(plan, p -> new ArrayList<>()).add(later);
                        late.forEach(c -> moved.put(c, null));
                    }
                    chunk.add(moved);
                }
                count += repository.write(table, plan.mode(), plan.table().key(), plan.write(), MAPPER.writeValueAsString(chunk));
            }
            repository.resetSequences(table, plan.write());
            written.merge(table, count, Integer::sum);
        }

        for (Map.Entry<Plan, List<Map<String, Object>>> entry : lateRows.entrySet()) {
            Plan plan = entry.getKey();
            List<String> columns = new ArrayList<>(plan.table().key());
            plan.table().late().stream().filter(plan.write()::contains).forEach(columns::add);
            List<Map<String, Object>> rows = entry.getValue();
            for (int from = 0; from < rows.size(); from += CHUNK) {
                repository.write(plan.table().table(), TablePack.Mode.UPDATE, plan.table().key(), columns,
                        MAPPER.writeValueAsString(rows.subList(from, Math.min(rows.size(), from + CHUNK))));
            }
        }

        if (!triggered.isEmpty()) {
            repository.checkDeferredNow(); // 미룬 FK 확인이 남아 있으면 트리거를 다시 켜는 DDL 이 막힌다
            triggered.forEach(table -> repository.setUserTriggers(table, true));
        }
        return new Result(written);
    }

    private List<Plan> plan(TablePack pack, boolean strict) {
        List<Plan> plans = new ArrayList<>();
        for (TablePack.Table table : pack.tables()) {
            TablePack.Mode mode = table.modeOrNull();
            if (mode == null) {
                throw new PackMismatchException("모르는 방식이다: " + table.table() + " " + table.mode());
            }
            Map<String, TablePackRepository.Column> columns = repository.columns(table.table());
            if (columns.isEmpty()) {
                mismatch(strict, "표가 없다: " + table.table());
                continue;
            }
            if (!columns.keySet().containsAll(table.key())) {
                mismatch(strict, "열쇠 칸이 없다: " + table.table() + " " + table.key());
                continue;
            }
            List<String> write = new ArrayList<>();
            for (String column : table.columns().keySet()) {
                TablePackRepository.Column found = columns.get(column);
                if (found == null) {
                    mismatch(strict, "칸이 없다: " + table.table() + "." + column);
                } else if (!found.generated()) {
                    write.add(column);
                }
            }
            if (!write.containsAll(table.key())) {
                mismatch(strict, "열쇠 칸을 담지 않았다: " + table.table());
                continue;
            }
            plans.add(new Plan(table, mode, columns, write));
        }
        return plans;
    }

    private static void mismatch(boolean strict, String message) {
        if (strict) {
            throw new PackMismatchException(message);
        }
        log.warn("팩이 이 DB 와 맞지 않아 그 부분을 뺐다 — {}", message);
    }
}
