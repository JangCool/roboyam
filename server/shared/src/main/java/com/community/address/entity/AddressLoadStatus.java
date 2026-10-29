package com.community.address.entity;

/** 적재 한 번의 상태 — 돌고 있는 적재({@link #RUNNING})는 사이트에 하나(V109 부분 유일 색인) */
public enum AddressLoadStatus {
    RUNNING,
    DONE,
    FAILED
}
