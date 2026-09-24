package com.flashsale.service;

import com.flashsale.dto.OrderResponse;
import com.flashsale.dto.PurchaseRequest;

public interface FlashSaleService {

    OrderResponse purchaseWithPessimisticLock(PurchaseRequest request);

    OrderResponse purchaseWithOptimisticLock(PurchaseRequest request);

    OrderResponse purchaseWithDistributedLock(PurchaseRequest request);

    OrderResponse purchaseWithRedisLua(PurchaseRequest request);
}