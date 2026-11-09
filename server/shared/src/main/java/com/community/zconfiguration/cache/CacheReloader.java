package com.community.zconfiguration.cache;

/**
 * 「이전 값 유지」 캐시의 다시 읽기 — 설정 사본·내부 규칙 캐시({@code CacheDefinition#keepsLastValue()})를 읽는 서비스가 빈으로 구현한다.
 *
 * <ul>
 *   <li>항목이 TTL 보다 오래되면 다음 읽기가 이것으로 다시 읽는다. 다시 읽다 실패하면 <b>이전 값을 그대로 쓰고</b> 그다음 읽기에서 다시 시도한다(예전 메모리 사본의 동작).</li>
 *   <li>비우기(세대 올리기 · 「지금 갱신」 · 끄기)는 이전 값까지 지운다 — 그 뒤 첫 읽기는 부르는 쪽의 로더({@code Cache#get(key, loader)})가 읽고, 실패하면 부르는 쪽 규칙(처음부터 못 읽은 경우)을 따른다.</li>
 *   <li>캐시에 처음 담는 로더와 같은 값을 돌려줘야 한다(보통 같은 메서드를 부른다). 항목 키만으로 다시 읽을 수 있어야 한다.</li>
 * </ul>
 * 등록은 {@link CacheConfig} 가 띄운 뒤 한꺼번에 한다({@link CacheControl#registerReloader}). 업무 코드는 이 인터페이스만 구현하고 캐시 구현(Caffeine)은 모른다.
 */
public interface CacheReloader {

    /** 다시 읽을 캐시 이름({@code CacheNames}) */
    String cacheName();

    /** 그 항목 키의 값을 원본(DB)에서 다시 읽는다 — 실패하면 던진다(캐시가 이전 값을 쓴다) */
    Object reload(Object key) throws Exception;
}
