package com.community.media.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ServiceClientConfiguration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * 시험용 메모리 버킷 — S3Client 의 「요청 객체」 메서드만 구현한다(묶음 빌더 메서드는 SDK 의 기본 구현이 이쪽을 부른다).
 * {@link #failWith} 를 주면 모든 호출이 그 예외를 던진다.
 */
public class FakeS3Client implements S3Client {

    public final Map<String, byte[]> objects = new LinkedHashMap<>();
    public final List<PutObjectRequest> puts = new ArrayList<>();
    public final List<DeleteObjectRequest> deletes = new ArrayList<>();
    public final List<GetObjectRequest> gets = new ArrayList<>();
    public Instant lastModified = Instant.parse("2026-11-09T00:00:00Z");
    public RuntimeException failWith;

    private void maybeFail() {
        if (failWith != null) {
            throw failWith;
        }
    }

    @Override
    public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
        puts.add(request);
        maybeFail();
        try (var in = body.contentStreamProvider().newStream()) {
            objects.put(request.key(), in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return PutObjectResponse.builder().build();
    }

    @Override
    public DeleteObjectResponse deleteObject(DeleteObjectRequest request) {
        deletes.add(request);
        maybeFail();
        objects.remove(request.key());
        return DeleteObjectResponse.builder().build();
    }

    @Override
    public ResponseBytes<GetObjectResponse> getObjectAsBytes(GetObjectRequest request) {
        gets.add(request);
        maybeFail();
        byte[] data = objects.get(request.key());
        if (data == null) {
            throw NoSuchKeyException.builder().statusCode(404).message("missing").build();
        }
        return ResponseBytes.fromByteArray(GetObjectResponse.builder().lastModified(lastModified).build(), data);
    }

    @Override
    public HeadBucketResponse headBucket(HeadBucketRequest request) {
        maybeFail();
        return HeadBucketResponse.builder().build();
    }

    @Override
    public S3ServiceClientConfiguration serviceClientConfiguration() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String serviceName() {
        return "s3";
    }

    @Override
    public void close() {
    }
}
