package com.community.datakey.batch;

import com.community.batch.core.BatchCatalog;
import com.community.batch.core.BatchContext;
import com.community.batch.core.BatchDefinition;
import com.community.batch.core.BatchItemException;
import com.community.batch.core.BatchJob;
import com.community.batch.core.BatchResult;
import com.community.batch.core.BatchStep;
import com.community.batch.core.BatchStepDefinition;
import com.community.batch.core.ChunkProcessor;
import com.community.batch.core.ChunkSource;
import com.community.zconfiguration.crypto.DataCipher;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.message.MessageRef;
import java.sql.PreparedStatement;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 데이터 키 교체 Java 배치 {@code data-key-rotation}(docs/features/commerce-foundation-contract.md 「0-1」 키 교체 절차 · 「0-4」, commerce.md 10-9) —
 * {@link DataCipher} 로 암호화한 칸({@link DataKeyColumns#ALL})마다 단계 하나, <b>지금 키가 아닌 암호문</b>(DB 에서 {@code NOT LIKE 'd<지금 버전>:%'})을 키셋으로 덩어리 처리해
 * {@link DataCipher#reencrypt} 로 지금 키로 다시 잠근다.
 *
 * <ul>
 *   <li>키 교체 절차: 새 키를 버전을 올려 {@code ROBOYAM_DATA_KEY} 에, 옛 키를 {@code ROBOYAM_DATA_KEY_PREVIOUS} 에 두고 재기동 → <b>이 배치를 「지금 실행」</b> →
 *       실패 건이 없으면 예전 키를 비운다. 그래서 기본은 <b>예약 꺼짐</b>(수동 실행 위주 — 켜면 매일 05:00 KST, 지금 키인 행은 고르지 않으므로 할 일이 없으면 곧 끝난다).</li>
 *   <li>저장은 <b>조건부 UPDATE</b>(읽은 암호문 그대로일 때만) — 그 사이 다른 곳(탈퇴 기능 등)이 값을 바꿨으면 덮어쓰지 않고 「건너뜀」으로 센다(다음 실행이 새 값을 본다).</li>
 *   <li>풀 수 없는 행(예전 키가 설정에 없음·용도가 다름·깨진 암호문)은 그 건만 실패({@code DATA_KEY_UNREADABLE}, 문구에 값·암호문 없음)로 남기고 나머지는 계속한다.
 *       이미 지금 키인 행은 고르지도, 바꾸지도 않는다.</li>
 *   <li>덩어리마다 저장 확정·체크포인트(행 키) — 「실패 지점부터 다시」는 체크포인트 다음부터, 「실패 건만 다시」는 실패 건만(예전 키를 다시 넣고 재기동한 뒤).
 *       처음부터 다시 돌려도 안전하다(멱등).</li>
 *   <li>지금 키가 없으면 실행 전체가 실패한다(503 {@code DATA_KEY_MISSING} 사유).</li>
 * </ul>
 * 이 배치는 분리 보관 표({@code withdrawn_member_contacts})를 읽는 몇 안 되는 곳이다 — 값을 풀어도 평문을 어디에도 남기지 않는다.
 */
@Component
public class DataKeyRotationJob implements BatchJob {

    /** 실패 건 오류 코드 — 풀 수 없는 암호문 */
    public static final String UNREADABLE = "DATA_KEY_UNREADABLE";

    private final BatchDefinition definition;
    private final List<EncryptedColumn> columns;
    private final JdbcTemplate jdbc;
    private final DataCipher cipher;

    @Autowired
    public DataKeyRotationJob(DataSource dataSource, DataCipher cipher) {
        this(BatchCatalog.DATA_KEY_ROTATION, DataKeyColumns.ALL, dataSource, cipher);
    }

    /** 시험용 — 다른 정의·칸 목록으로 */
    DataKeyRotationJob(BatchDefinition definition, List<EncryptedColumn> columns, DataSource dataSource, DataCipher cipher) {
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("교체할 칸이 없다");
        }
        this.definition = definition;
        this.columns = List.copyOf(columns);
        this.jdbc = new JdbcTemplate(dataSource);
        this.cipher = cipher;
    }

    @Override
    public BatchDefinition definition() {
        return definition;
    }

    @Override
    public List<BatchStepDefinition> steps() {
        return columns.stream().map(EncryptedColumn::stepDefinition).toList();
    }

    @Override
    public boolean resumable() {
        return true;
    }

    @Override
    public BatchResult run(BatchContext context) {
        cipher.requireConfigured();
        int version = cipher.activeVersion();
        long rotated = 0;
        for (EncryptedColumn column : columns) {
            BatchStep step = context.step(column.step());
            step.chunks(source(column, version), processor(column));
            rotated += step.processedCount();
        }
        return BatchResult.of(rotated == 0 ? new MessageRef("batch.result.dataKeyRotation.none", String.valueOf(version))
                : new MessageRef("batch.result.dataKeyRotation", String.valueOf(rotated), String.valueOf(version)));
    }

    /** 한 행 — {@code toString} 은 암호문을 싣지 않는다 */
    record Row(long id, String cipherText) {

        @Override
        public String toString() {
            return "Row[id=" + id + "]";
        }
    }

    /** 지금 키가 아닌 암호문만, 행 키 오름차순(키셋) */
    ChunkSource<Row> source(EncryptedColumn column, int version) {
        String notCurrent = "d" + version + ":%";
        String select = "SELECT " + column.idColumn() + ", " + column.cipherColumn() + " FROM " + column.table() + " WHERE " + column.cipherColumn() + " NOT LIKE ? ";
        return new ChunkSource<>() {
            @Override
            public String targetType() {
                return column.targetType();
            }

            @Override
            public List<Row> next(String afterKey, int size) {
                long after = afterKey == null ? Long.MIN_VALUE : Long.parseLong(afterKey);
                return jdbc.query(select + "AND " + column.idColumn() + " > ? ORDER BY " + column.idColumn() + " LIMIT ?",
                        (rs, i) -> new Row(rs.getLong(1), rs.getString(2)), notCurrent, after, size);
            }

            @Override
            public List<Row> byIds(List<String> targetIds) {
                return jdbc.query(connection -> {
                    PreparedStatement statement = connection.prepareStatement(select + "AND " + column.idColumn() + " = ANY (?) ORDER BY " + column.idColumn());
                    statement.setString(1, notCurrent);
                    statement.setArray(2, connection.createArrayOf("bigint", targetIds.stream().map(Long::valueOf).toArray()));
                    return statement;
                }, (rs, i) -> new Row(rs.getLong(1), rs.getString(2)));
            }

            @Override
            public String keyOf(Row item) {
                return Long.toString(item.id());
            }

            @Override
            public String targetIdOf(Row item) {
                return Long.toString(item.id());
            }
        };
    }

    /** 다시 잠가 조건부 UPDATE — 읽은 암호문 그대로인 행만 바꾼다. 바꾼 건수 */
    ChunkProcessor<Row> processor(EncryptedColumn column) {
        String update = "UPDATE " + column.table() + " SET " + column.cipherColumn() + " = ? WHERE " + column.idColumn() + " = ? AND " + column.cipherColumn() + " = ?";
        return items -> {
            long changed = 0;
            for (Row row : items) {
                String next = reencrypt(column, row);
                if (next.equals(row.cipherText())) {
                    continue; // 이미 지금 키(고른 뒤 다른 곳이 바꿨다)
                }
                changed += jdbc.update(update, next, row.id(), row.cipherText());
            }
            return changed;
        };
    }

    private String reencrypt(EncryptedColumn column, Row row) {
        try {
            return cipher.reencrypt(column.purpose(), row.cipherText());
        } catch (CommonException | IllegalArgumentException e) {
            // 예전 키가 설정에 없거나(503 DATA_KEY_MISSING) 풀리지 않는 암호문 — 값·암호문은 싣지 않고 키 버전만
            throw new BatchItemException(UNREADABLE, "풀 수 없는 암호문이다(키 버전 " + versionOf(row.cipherText()) + " — 예전 키가 ROBOYAM_DATA_KEY_PREVIOUS 에 있는지 확인)");
        }
    }

    private static String versionOf(String cipherText) {
        int colon = cipherText == null ? -1 : cipherText.indexOf(':');
        return colon > 1 && colon <= 4 ? cipherText.substring(1, colon) : "?";
    }
}
