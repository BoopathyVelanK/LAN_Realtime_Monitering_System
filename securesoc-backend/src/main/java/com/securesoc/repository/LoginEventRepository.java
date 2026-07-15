package com.securesoc.repository;

import com.securesoc.entity.LoginEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoginEventRepository extends JpaRepository<LoginEvent, UUID> {
}
