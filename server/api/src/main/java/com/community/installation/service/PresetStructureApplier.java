package com.community.installation.service;

import com.community.installation.dto.TablePack;
import com.community.installation.repository.master.TablePackRepository;
import com.community.zconfiguration.jpa.IsolatedSideWork;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설치 때 프리셋의 <b>구조 스냅숏</b>({@code install-presets/<프리셋>.structure.json} — 게시판·말머리·소모임 분류·메뉴·가젯 페이지·테마별 홈 배치·게시판 기본값·사이트 표시 설정 …)을
 * 설치 트랜잭션 안에서 넣는다. 정본은 docs/features/install-contract.md 「데모 데이터 선택 (2026-11-08)」 1.
 *
 * <ul>
 *   <li>스냅숏의 ID 를 그대로 쓴다(게시판·메뉴 노드·가젯 ID 가 서로를, 데모 팩이 게시판·말머리를 가리킨다). 지난 시각의 Snowflake 라 앞으로 매길 ID 와 겹치지 않는다.</li>
 *   <li>운영자를 가리키는 칸의 {@code "$ROOT"} 는 루트 운영자 ID 가 되고, 시각 칸은 모두 설치 시각이 된다(설치 전 시각이 화면에 「고친 때」로 보이지 않게).</li>
 *   <li>운영자 역할(roles·role_permissions — 담당·총괄·부관리자·전체 열람 등)도 넣는다. 권한 줄이 가리키는 권한 카탈로그(permissions)를 먼저 채우고
 *       ({@link InstallPermissions#catalog}), 코드에 없는 권한(스냅숏을 내보낸 뒤 지운 권한)의 줄은 빼고 경고한다({@link #withoutUnknownPermissions}).</li>
 *   <li>스냅숏의 칸이 DB 에 없으면(스냅숏을 내보낸 뒤 마이그레이션이 칸을 지웠으면) 그 칸만 빼고 경고한다 — 설치를 깨지 않는다.</li>
 *   <li>넣다가 실패하면 JDBC 세이브포인트({@link IsolatedSideWork})로 그 부분만 되돌리고 {@code false} — 부르는 쪽({@link BoardPresetApplier})이 예전 방식
 *       (프리셋 {@code boards} 로 게시판만 만들기)으로 물러선다. 그래서 스냅숏이 어긋나도 설치는 끝난다.</li>
 * </ul>
 */
@Component
public class PresetStructureApplier {

    private static final Logger log = LoggerFactory.getLogger(PresetStructureApplier.class);
    /** 스냅숏에서 루트 운영자 자리 */
    static final String ROOT_MARK = "$ROOT";
    private static final String ROLE_PERMISSIONS = "role_permissions";

    private final TablePackRestorer restorer;
    private final TablePackRepository tables;
    private final IsolatedSideWork sideWork;

    public PresetStructureApplier(TablePackRestorer restorer, TablePackRepository tables, IsolatedSideWork sideWork) {
        this.restorer = restorer;
        this.tables = tables;
        this.sideWork = sideWork;
    }

    /** @return 넣었으면 true. 실패하면 넣은 것을 되돌리고 false */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean apply(String presetId, TablePack snapshot, long rootOperatorId, Instant now) {
        String root = Long.toString(rootOperatorId);
        String at = now.truncatedTo(ChronoUnit.MICROS).toString();
        List<TablePackRepository.PermissionRow> permissions = InstallPermissions.catalog();
        TablePack pack = withoutUnknownPermissions(presetId, snapshot, permissions);
        return sideWork.run("preset-structure", () -> {
            try {
                // 운영자 역할의 권한 줄(role_permissions)은 권한 카탈로그(permissions)를 가리킨다 — 카탈로그는 관리자 서버가 뜰 때 채우므로 설치 때(관리자 서버 전)엔 비어 있다
                tables.ensurePermissions(permissions);
                TablePackRestorer.Result result = restorer.restore(pack, false, false, (table, row, columns) -> {
                    Map<String, Object> out = new LinkedHashMap<>(row);
                    for (Map.Entry<String, Object> entry : out.entrySet()) {
                        Object value = entry.getValue();
                        if (ROOT_MARK.equals(value)) {
                            entry.setValue(root);
                        } else if (value != null && columns.get(entry.getKey()).isTemporal()) {
                            entry.setValue(at);
                        }
                    }
                    return out;
                });
                log.info("게시판 구성 {} 의 구조를 넣었다 — 표 {}개 · 행 {}개", presetId, result.written().size(), result.total());
                return true;
            } catch (RuntimeException e) {
                // 구조 데이터는 비밀이 아니라 원인 문구를 남긴다(세이브포인트 쪽 로그는 예외 이름만 남긴다)
                log.warn("게시판 구성 {} 의 구조를 넣지 못해 기본 게시판 만들기로 물러선다 — {}", presetId, e.getMessage());
                throw e;
            }
        }, false);
    }

    /**
     * 권한 줄(role_permissions) 중 코드 카탈로그에 없는 권한(스냅숏을 내보낸 뒤 코드에서 지운 권한)을 뺀 스냅숏 — 그대로 넣으면 FK 에 걸려 구조 전체가 기본 게시판 만들기로 물러선다.
     */
    static TablePack withoutUnknownPermissions(String presetId, TablePack snapshot, List<TablePackRepository.PermissionRow> catalog) {
        Set<String> known = new HashSet<>();
        catalog.forEach(row -> known.add(row.code()));
        Set<String> dropped = new TreeSet<>();
        List<TablePack.Table> tables = new ArrayList<>();
        for (TablePack.Table table : snapshot.tables()) {
            if (!ROLE_PERMISSIONS.equals(table.table())) {
                tables.add(table);
                continue;
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> row : table.rows()) {
                Object code = row.get("permission_code");
                if (code != null && known.contains(code.toString())) {
                    rows.add(row);
                } else {
                    dropped.add(row.get("role_id") + ":" + code);
                }
            }
            tables.add(new TablePack.Table(table.table(), table.mode(), table.key(), table.columns(), rows, table.late()));
        }
        if (dropped.isEmpty()) {
            return snapshot;
        }
        log.warn("게시판 구성 {} 의 운영자 역할 권한 줄 중 코드에 없는 권한 {}줄은 넣지 않는다 — {}", presetId, dropped.size(), dropped);
        return new TablePack(snapshot.format(), snapshot.preset(), snapshot.exportedAt(), snapshot.source(), tables);
    }
}
