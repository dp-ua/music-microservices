package com.music.resource.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.music.resource.model.AudioResource;

@Repository
public interface ResourcesRepository extends JpaRepository<AudioResource, Long> {

    @Query("SELECT r FROM AudioResource r WHERE r.id IN :ids AND r.isDeleted = false")
    List<AudioResource> findAllByIdInAndNotDeleted(@Param("ids") List<Long> ids);

    @Modifying
    @Transactional
    @Query("UPDATE AudioResource r SET r.isDeleted = true WHERE r.id IN :ids")
    void softDeleteByIdIn(@Param("ids") List<Long> ids);

}
