package com.securesoc.repository;

import com.securesoc.entity.LogoutEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LogoutEventRepository extends JpaRepository<LogoutEvent, UUID> {
}
