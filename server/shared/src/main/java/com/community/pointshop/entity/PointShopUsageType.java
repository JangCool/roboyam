package com.community.pointshop.entity;

/** 사용 방식 — 영구(기본, 가졌으면 다시 못 산다) · 소모형(쓰면 사라지고 여러 장 — 변경권) · 기간제(산 순간부터 N일, 보유 중에 또 사면 앞 끝에서 이어 붙는다) */
public enum PointShopUsageType {
    PERMANENT,
    CONSUMABLE,
    PERIOD
}
