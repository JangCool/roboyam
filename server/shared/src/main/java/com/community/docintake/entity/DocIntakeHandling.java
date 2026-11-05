package com.community.docintake.entity;

/** 인쇄본 처리 — 계속 보관 · 스캔 후 폐기 예정 · 폐기함(폐기 처리로만, 되돌릴 수 없다) */
public enum DocIntakeHandling {
    KEEP, DISPOSE_PENDING, DISPOSED;

    /** 고를 수 있는 처리인가(폐기함은 [폐기 처리] 로만) */
    public boolean open() {
        return this != DISPOSED;
    }
}
