package com.flashsale.controller;

import com.flashsale.dto.OrderResponse;
import com.flashsale.dto.PurchaseRequest;
import com.flashsale.service.FlashSaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flash-sale")
@RequiredArgsConstructor
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @PostMapping("/purchase/pessimistic")
    public ResponseEntity<OrderResponse> purchasePessimistic(@Valid @RequestBody PurchaseRequest request) {
        return ResponseEntity.ok(flashSaleService.purchaseWithPessimisticLock(request));
    }

    @PostMapping("/purchase/optimistic")
    public ResponseEntity<OrderResponse> purchaseOptimistic(@Valid @RequestBody PurchaseRequest request) {
        return ResponseEntity.ok(flashSaleService.purchaseWithOptimisticLock(request));
    }

    @PostMapping("/purchase/distributed")
    public ResponseEntity<OrderResponse> purchaseDistributed(@Valid @RequestBody PurchaseRequest request) {
        return ResponseEntity.ok(flashSaleService.purchaseWithDistributedLock(request));
    }

    @PostMapping("/purchase/redis-lua")
    public ResponseEntity<OrderResponse> purchaseRedisLua(@Valid @RequestBody PurchaseRequest request) {
        return ResponseEntity.ok(flashSaleService.purchaseWithRedisLua(request));
    }
}