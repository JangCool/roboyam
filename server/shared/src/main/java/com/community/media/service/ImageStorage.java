package com.community.media.service;

/**
 * 이미지 파일 저장소. 지금 구현은 로컬 디스크({@link LocalDiskImageStorage}) 하나다 — S3 호환 저장소는 같은 인터페이스로 후속에 더한다.
 * 어느 것을 쓸지는 설정값({@code roboyam.storage.type})이고 나머지 코드는 이 인터페이스만 안다.
 *
 * <p>키(key)는 서버가 만든 경로({@code site-icon/3f9a…/icon-512.png})다. 키에 <b>버전 토큰</b>이 들어 있어 내용을 바꾸면 키(주소)가 바뀐다 —
 * 그래서 파일을 한 번 쓰면 고치지 않고, 브라우저·CDN 이 1년 캐시를 걸 수 있다.
 */
public interface ImageStorage {

    /** 파일을 저장한다. 같은 키가 있으면 덮어쓴다. 쓰지 못하면 {@code StorageUnavailableException}(503) */
    void put(String key, byte[] data);

    /** 파일을 지운다. 없어도 오류가 아니다(멱등). 지우지 못하면 예외를 던지므로 부르는 쪽이 로그만 남기고 넘어간다 */
    void delete(String key);

    /** 브라우저가 이 파일을 가져가는 주소(로컬은 {@code /media/…}, S3 는 CDN 주소 전체) */
    String publicUrl(String key);
}
