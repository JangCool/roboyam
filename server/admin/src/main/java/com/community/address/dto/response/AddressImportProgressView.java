package com.community.address.dto.response;

import com.community.address.entity.AddressImportPhase;
import java.time.Instant;
import java.util.List;

/**
 * 적재 진행 — admin-web {@code AddressImportProgress} 와 같은 모양.
 *
 * @param totalRows     -1 = 아직 세는 중(진행률을 낼 수 없다). 다른 서버가 돌리는 적재·다시 뜬 서버도 -1
 * @param message       끝났으면 결과 한 줄, 실패면 사유(요청 언어) — 진행 중이면 null
 * @param failedPhase   실패했으면 멈춘 단계({@code COUNTING}~{@code SWAPPING}), 아니면 null
 * @param startedBy     시작한 운영자 이름(지워졌으면 null)
 */
public record AddressImportProgressView(String jobId, AddressImportPhase phase, long totalRows, long processedRows, boolean done, boolean failed,
                                        AddressImportPhase failedPhase, String message, List<String> fileNames, Instant startedAt, String startedBy) {
}
