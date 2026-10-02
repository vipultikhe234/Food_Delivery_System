package com.fooddelivery.identity.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** A TransactionTemplate stand-in that runs the callback directly. */
final class Transactions {

  private Transactions() {}

  static TransactionTemplate direct() {
    TransactionTemplate template = mock(TransactionTemplate.class);
    when(template.execute(any()))
        .thenAnswer(call -> call.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
    doCallRealMethod().when(template).executeWithoutResult(any());
    return template;
  }
}
