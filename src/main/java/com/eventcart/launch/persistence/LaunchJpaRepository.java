package com.eventcart.launch.persistence;

import com.eventcart.launch.LaunchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LaunchJpaRepository extends JpaRepository<LaunchEntity, UUID> {

    List<LaunchEntity> findByStatusInOrderByOpensAtAsc(List<LaunchStatus> statuses);
}
