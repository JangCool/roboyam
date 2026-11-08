package com.community.installation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 표 단위로 담은 행 묶음 — 설치 프리셋의 <b>구조 스냅숏</b>({@code install-presets/<프리셋>.structure.json}, {@code scripts/export-install-preset.mjs})과
 * <b>데모 팩</b>의 데이터({@code demo-pack/data.json.gz}, {@code scripts/build-demo-pack.mjs})가 같은 모양이다. 정본은 docs/features/install-contract.md 「데모 데이터 선택 (2026-11-08)」.
 *
 * <p>행은 칸 이름 → JSON 값이다. 큰 정수(bigint)·소수(numeric)는 글자로 담는다(18자리 ID 가 JSON 숫자로 끝자리를 잃지 않게) — 넣을 때 DB 가 칸 형식으로 다시 읽는다
 * ({@code json_populate_recordset}). 팩에 없는 칸은 넣지 않으므로 DB 기본값이 된다.
 *
 * @param format     형식 번호(지금 1)
 * @param preset     구조 스냅숏이면 그 프리셋 아이디, 데모 팩이면 그 팩을 쓸 수 있는 프리셋 아이디
 * @param exportedAt 내보낸 시각(ISO-8601) — 데모 팩은 시각을 옮기는 기준이다
 * @param source     내보낸 DB 의 정보(예: {@code sharedVersion} = shared 마이그레이션 마지막 번호)
 * @param tables     넣는 차례대로(앞 표를 뒤 표가 FK 로 가리킨다)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TablePack(int format, String preset, String exportedAt, Map<String, Object> source, List<Table> tables) {

    public TablePack {
        source = source == null ? Map.of() : source;
        tables = tables == null ? List.of() : List.copyOf(tables);
    }

    /**
     * 표 하나.
     *
     * @param table   표 이름
     * @param mode    {@link Mode} 이름
     * @param key     열쇠 칸(기본 키) — UPSERT·UPDATE 가 행을 맞추는 칸
     * @param columns 담은 칸 이름 → 내보낸 DB 의 형식({@code format_type}) — 데모 팩은 이것으로 시각·ID 칸을 가린다
     * @param rows    행들
     * @param late    나중에 채울 칸 — 뒤 차례 표를 FK 로 가리키는 칸(서로 가리키는 표: 사업자 신원 ↔ 등록증). 먼저 비운 채 넣고 모든 표를 넣은 뒤 채운다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Table(String table, String mode, List<String> key, LinkedHashMap<String, String> columns, List<Map<String, Object>> rows, List<String> late) {

        public Table {
            key = key == null ? List.of() : List.copyOf(key);
            late = late == null ? List.of() : List.copyOf(late);
            columns = columns == null ? new LinkedHashMap<>() : columns;
            rows = rows == null ? List.of() : rows;
        }

        public Mode modeOrNull() {
            try {
                return mode == null ? null : Mode.valueOf(mode);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** 넣는 방식 */
    public enum Mode {
        /** 그 표를 비우고 이 행들로 채운다 */
        REPLACE,
        /** 열쇠가 같은 행은 담은 칸을 고치고, 없으면 넣는다 */
        UPSERT,
        /** 열쇠가 같은 행의 담은 칸만 고친다(없으면 아무것도 하지 않는다) */
        UPDATE,
        /** 넣기만 한다(열쇠가 겹치면 실패) */
        INSERT
    }
}
