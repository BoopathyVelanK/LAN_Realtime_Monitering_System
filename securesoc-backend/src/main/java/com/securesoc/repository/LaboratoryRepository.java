package com.securesoc.repository;

import com.securesoc.entity.Laboratory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LaboratoryRepository extends JpaRepository<Laboratory, UUID> {
}
