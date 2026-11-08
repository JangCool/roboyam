package com.community.installation.service;

import com.community.installation.dto.TablePack;
import com.community.zconfiguration.jpa.IsolatedSideWork;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
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

    private final TablePackRestorer restorer;
    private final IsolatedSideWork sideWork;

    public PresetStructureApplier(TablePackRestorer restorer, IsolatedSideWork sideWork) {
        this.restorer = restorer;
        this.sideWork = sideWork;
    }

    /** @return 넣었으면 true. 실패하면 넣은 것을 되돌리고 false */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean apply(String presetId, TablePack snapshot, long rootOperatorId, Instant now) {
        String root = Long.toString(rootOperatorId);
        String at = now.truncatedTo(ChronoUnit.MICROS).toString();
        return sideWork.run("preset-structure", () -> {
            try {
                TablePackRestorer.Result result = restorer.restore(snapshot, false, false, (table, row, columns) -> {
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
}
