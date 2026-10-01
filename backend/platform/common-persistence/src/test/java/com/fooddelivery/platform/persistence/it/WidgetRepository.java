package com.fooddelivery.platform.persistence.it;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface WidgetRepository extends JpaRepository<Widget, UUID> {

  long countByName(String name);
}
