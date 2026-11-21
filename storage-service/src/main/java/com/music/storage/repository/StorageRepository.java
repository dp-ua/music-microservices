package com.music.storage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.music.storage.model.Storage;

@Repository
public interface StorageRepository extends JpaRepository<Storage, Long> {

    boolean existsByBucketAndPath(String bucket, String path);

}
