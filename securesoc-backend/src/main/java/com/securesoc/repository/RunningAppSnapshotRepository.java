package com.securesoc.repository;

import com.securesoc.entity.RunningAppSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RunningAppSnapshotRepository extends JpaRepository<RunningAppSnapshot, UUID> {
}
