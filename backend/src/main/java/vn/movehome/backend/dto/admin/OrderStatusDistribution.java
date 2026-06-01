package vn.movehome.backend.dto.admin;

import vn.movehome.backend.entity.OrderStatus;

import java.util.Map;

/**
 * Phan bo don hang theo trang thai — dung ve pie/donut chart (tuy chon Phase 4).
 * key = OrderStatus enum, value = so luong don.
 * Tat ca 6 status deu xuat hien du co = 0 (khong bo trong).
 */
public record OrderStatusDistribution(Map<OrderStatus, Long> distribution) {}
