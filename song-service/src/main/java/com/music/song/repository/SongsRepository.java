package com.music.song.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.music.song.model.Song;

@Repository
public interface SongsRepository extends JpaRepository<Song, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE Song s SET s.isDeleted = true WHERE s.id IN :ids")
    void softDeleteByIdIn(@Param("ids") List<Long> ids);

    @Query("SELECT s FROM Song s WHERE s.id IN :ids AND s.isDeleted = false")
    List<Song> findAllByIdInAndNotDeleted(@Param("ids") List<Long> ids);

}
