package com.securesoc.repository;

import com.securesoc.entity.UsbEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UsbEventRepository extends JpaRepository<UsbEvent, UUID> {
}
