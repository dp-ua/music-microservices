package com.music.resource.storage;

import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import org.springframework.stereotype.Component;

import com.music.resource.exception.StorageException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3FileStorage implements FileStorage {

    private static final String CONTENT_TYPE = "audio/mpeg";
    private static final String KEY_SUFFIX = ".mp3";

    private final S3Client s3Client;
    private final S3Properties properties;

    @Override
    public StorageLocation upload(String bucket, String path, byte[] data) {
        var location = StorageLocation.builder()
                .bucket(bucket)
                .key(buildKey(path, UUID.randomUUID() + KEY_SUFFIX))
                .build();
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(location.getBucket())
                            .key(location.getKey())
                            .contentType(CONTENT_TYPE)
                            .build(),
                    RequestBody.fromBytes(data));
            log.info("File uploaded to storage: {}", location);
            return location;
        } catch (SdkException e) {
            log.error("Can't upload file to storage: {}", location, e);
            throw StorageException.failed("upload", location.getKey(), e);
        }
    }

    @Override
    public StorageLocation copy(StorageLocation source, String bucket, String path) {
        var target = StorageLocation.builder()
                .bucket(bucket)
                .key(buildKey(path, fileName(source.getKey())))
                .build();
        try {
            s3Client.copyObject(CopyObjectRequest.builder()
                    .sourceBucket(source.getBucket())
                    .sourceKey(source.getKey())
                    .destinationBucket(target.getBucket())
                    .destinationKey(target.getKey())
                    .build());
            log.info("File copied in storage: {} -> {}", source, target);
            return target;
        } catch (NoSuchKeyException e) {
            log.error("File not found in storage: {}", source);
            throw StorageException.notFound(source.getKey(), e);
        } catch (SdkException e) {
            log.error("Can't copy file in storage: {} -> {}", source, target, e);
            throw StorageException.failed("copy", source.getKey(), e);
        }
    }

    @Override
    public byte[] download(StorageLocation location) {
        try {
            return s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(location.getBucket())
                            .key(location.getKey())
                            .build()
            ).asByteArray();
        } catch (NoSuchKeyException e) {
            log.error("File not found in storage: {}", location);
            throw StorageException.notFound(location.getKey(), e);
        } catch (SdkException e) {
            log.error("Can't download file from storage: {}", location, e);
            throw StorageException.failed("download", location.getKey(), e);
        }
    }

    @Override
    public void delete(StorageLocation location) {
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(location.getBucket())
                            .key(location.getKey())
                            .build());
            log.info("File deleted from storage: {}", location);
        } catch (SdkException e) {
            log.error("Can't delete file from storage: {}", location, e);
            throw StorageException.failed("delete", location.getKey(), e);
        }
    }

    /** The path of a storage ("/files") is a folder: key "files/name.mp3". */
    private static String buildKey(String path, String fileName) {
        var folder = StringUtils.strip(path, "/");
        return StringUtils.isEmpty(folder) ? fileName : folder + "/" + fileName;
    }

    private static String fileName(String key) {
        return key.substring(key.lastIndexOf('/') + 1);
    }

}
