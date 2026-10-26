package com.music.resource.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.music.resource.model.AudioResource;

@Repository
public interface ResourcesRepository extends JpaRepository<AudioResource, Long> {

}
