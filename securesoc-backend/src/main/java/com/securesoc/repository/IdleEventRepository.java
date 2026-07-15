package com.securesoc.repository;

import com.securesoc.entity.IdleEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IdleEventRepository extends JpaRepository<IdleEvent, UUID> {
}
