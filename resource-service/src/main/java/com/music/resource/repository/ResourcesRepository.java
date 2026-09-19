package com.music.resource.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.music.resource.model.Resource;

@Repository
public interface ResourcesRepository extends JpaRepository<Resource, Long> {

    @Query("SELECT r FROM Resource r WHERE r.id IN :ids AND r.isDeleted = false")
    List<Resource> findAllByIdInAndNotDeleted(@Param("ids") List<Long> ids);

    @Modifying
    @Transactional
    @Query("UPDATE Resource r SET r.isDeleted = true WHERE r.id IN :ids")
    void softDeleteByIdIn(@Param("ids") List<Long> ids);

}
