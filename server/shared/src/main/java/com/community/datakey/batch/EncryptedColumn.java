package com.community.datakey.batch;

import com.community.batch.core.BatchStepDefinition;
import com.community.zconfiguration.crypto.DataPurpose;
import java.util.regex.Pattern;

/**
 * {@code DataCipher} 로 암호화한 칸 하나 — 데이터 키 교체 배치({@link DataKeyRotationJob})의 단계 하나가 된다. 표·칸 이름은 코드의 상수에서만 오므로 SQL 에 그대로 쓴다
 * (만들 때 식별자 규칙을 확인한다). 키 칸은 {@code bigint} 이어야 한다(키셋 순서).
 *
 * @param step       단계 이름(소문자·숫자·하이픈 — 이름 문구는 번들 {@code batch.job.data-key-rotation.step.<단계>})
 * @param table      표
 * @param idColumn   행 키 칸(bigint, 유일) — 키셋·체크포인트·실패 건의 대상 ID
 * @param cipherColumn 암호문 칸({@code d<버전>:…}, NOT NULL)
 * @param purpose    그 칸의 암호화 용도(AAD) — 쓰는 쪽과 같은 상수를 쓴다
 * @param targetType 실패 건의 대상 종류 코드(이름 문구는 번들 {@code batch.target.<코드>})
 */
public record EncryptedColumn(String step, String table, String idColumn, String cipherColumn, DataPurpose purpose, String targetType) {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-z0-9_]{0,62}");
    private static final Pattern TARGET_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");

    public EncryptedColumn {
        new BatchStepDefinition(step, true); // 단계 이름 규칙 확인
        for (String identifier : new String[] {table, idColumn, cipherColumn}) {
            if (identifier == null || !IDENTIFIER.matcher(identifier).matches()) {
                throw new IllegalArgumentException("식별자 규칙에 맞지 않는다: " + identifier);
            }
        }
        if (purpose == null || targetType == null || !TARGET_TYPE.matcher(targetType).matches()) {
            throw new IllegalArgumentException("용도·대상 종류가 없거나 규칙에 맞지 않는다: " + step);
        }
    }

    public BatchStepDefinition stepDefinition() {
        return new BatchStepDefinition(step, true);
    }
}
