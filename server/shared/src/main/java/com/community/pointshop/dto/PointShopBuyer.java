package com.community.pointshop.dto;

/**
 * 구매하려는 회원 — 회원 권한({@code pointshop.use})은 회원 역할을 푸는 api 가 판정해 넘긴다({@code BoardActor.can}). 나머지 자격(정상 회원·이메일 인증·포인트 동결)은 서비스가 DB 에서 본다.
 *
 * @param pointShopPermitted 회원 권한 {@code pointshop.use} 가 있다
 */
public record PointShopBuyer(long userId, boolean pointShopPermitted) {
}
