package com.community.batch.entity;

import java.io.Serializable;
import java.util.Objects;

/** {@link BatchRunStepEntity} 의 복합 키 — (실행, 단계 이름) */
public class BatchRunStepId implements Serializable {

    private Long runId;
    private String stepName;

    protected BatchRunStepId() {
    }

    public BatchRunStepId(Long runId, String stepName) {
        this.runId = runId;
        this.stepName = stepName;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BatchRunStepId id && Objects.equals(runId, id.runId) && Objects.equals(stepName, id.stepName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(runId, stepName);
    }
}
