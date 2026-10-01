package com.fooddelivery.platform.persistence.it;

import com.fooddelivery.platform.persistence.entity.BaseEntity;
import com.fooddelivery.platform.persistence.money.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "widgets")
class Widget extends BaseEntity {

  @Column(name = "name", nullable = false, length = 100)
  private String name;

  @Embedded private Money price;

  protected Widget() {}

  Widget(String name, Money price) {
    this.name = name;
    this.price = price;
  }

  String getName() {
    return name;
  }

  void rename(String name) {
    this.name = name;
  }

  Money getPrice() {
    return price;
  }
}
