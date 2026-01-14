package com.FarmChainX.backend.Service;

import com.FarmChainX.backend.Model.*;
import com.FarmChainX.backend.Repository.*;
import com.FarmChainX.backend.enums.OrderStatus;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class OrderService {

    @Autowired
    private ListingRepository listingRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CropRepository cropRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private NotificationEventService notificationEventService;


    /* ================= PLACE ORDER ================= */

    @Transactional
    public Order placeOrder(Long listingId, String consumerId,
                            Double requestedQty, String deliveryAddress,
                            String contactNumber) {

        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new RuntimeException("Listing not found"));

        if (listing.getQuantity() < requestedQty)
            throw new RuntimeException("Insufficient quantity");

        listing.setQuantity(listing.getQuantity() - requestedQty);
        listingRepository.save(listing);

        double basePrice = listing.getPrice();
        double total = basePrice * requestedQty;

        Order order = new Order();
        order.setListingId(listingId);
        order.setBatchId(listing.getBatchId());
        order.setConsumerId(consumerId);
        order.setDistributorId(listing.getDistributorId());
        order.setQuantity(requestedQty);
        order.setPricePerKg(basePrice);
        order.setTotalAmount(total);
        order.setFarmerProfit(basePrice * 0.1 * requestedQty);
        order.setDistributorProfit(basePrice * 0.1 * requestedQty);
        order.setDeliveryAddress(deliveryAddress);
        order.setContactNumber(contactNumber);
        order.setStatus(OrderStatus.ORDER_PLACED);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);

        // 🔔 NOTIFY DISTRIBUTOR
        notificationEventService.notifyUser(
                savedOrder.getDistributorId(),
                "DISTRIBUTOR",
                "New Order Received 🛒",
                "Order #" + savedOrder.getOrderId() + " has been placed.",
                "NEW_ORDER",
                String.valueOf(savedOrder.getOrderId())
        );

        return savedOrder;
    }


    /* ================= UPDATE STATUS ================= */

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus status,
                                   String distributorId, String location) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getDistributorId().equals(distributorId))
            throw new RuntimeException("Unauthorized");

        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());

        if (status == OrderStatus.IN_WAREHOUSE) {
            if (order.getWarehouseAt() == null)
                order.setWarehouseAt(LocalDateTime.now());
            if (location != null && !location.isBlank())
                order.setWarehouseLocation(location);
        }

        if (status == OrderStatus.IN_TRANSIT) {
            if (order.getInTransitAt() == null)
                order.setInTransitAt(LocalDateTime.now());
            if (location != null && !location.isBlank())
                order.setTransitLocation(location);
        }

        if (status == OrderStatus.DELIVERED && order.getDeliveredAt() == null) {
            order.setDeliveredAt(LocalDateTime.now());
            settlePayment(order);
        }

        Order updatedOrder = orderRepository.save(order);

        // 🔔 NOTIFY CONSUMER – STATUS UPDATE
        notificationEventService.notifyUser(
                updatedOrder.getConsumerId(),
                "BUYER",
                "Order Status Updated 📦",
                "Your order #" + updatedOrder.getOrderId()
                        + " is now " + status.name(),
                status == OrderStatus.DELIVERED
                        ? "ORDER_DELIVERED"
                        : "ORDER_STATUS_UPDATE",
                String.valueOf(updatedOrder.getOrderId())
        );

        return updatedOrder;
    }



    /* ================= CONSUMER CANCEL ================= */

    @Transactional
    public void cancelOrder(Long orderId, String reason) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getStatus() == OrderStatus.CANCELLED)
            return;

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason(reason);
        order.setCancelledAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        orderRepository.save(order);

        // 🔔 NOTIFY DISTRIBUTOR
        notificationEventService.notifyUser(
                order.getDistributorId(),
                "DISTRIBUTOR",
                "Order Cancelled ❌",
                "Order #" + order.getOrderId()
                        + " was cancelled. Reason: "
                        + (reason != null ? reason : "N/A"),
                "ORDER_CANCELLED",
                String.valueOf(order.getOrderId())
        );
    }


    /* ================= FETCH ================= */

    public List<OrderDetailsDTO> getOrdersByConsumerFull(String consumerId) {
        return orderRepository.findByConsumerId(consumerId)
                .stream()
                .map(this::buildSafeDTO)
                .toList();
    }

    public List<OrderDetailsDTO> getOrdersByDistributorFull(String distributorId) {
        return orderRepository.findByDistributorId(distributorId)
                .stream()
                .map(this::buildSafeDTO)
                .toList();
    }

    /* ================= DTO BUILDER ================= */

    private OrderDetailsDTO buildSafeDTO(Order order) {

        try {
            Listing listing = listingRepository.findById(order.getListingId()).orElse(null);
            if (listing == null) return null;

            Crop crop = listing.getCropId() != null
                    ? cropRepository.findById(listing.getCropId()).orElse(null)
                    : null;

            User farmer = listing.getFarmerId() != null
                    ? userRepository.findById(listing.getFarmerId()).orElse(null)
                    : null;

            User distributor = order.getDistributorId() != null
                    ? userRepository.findById(order.getDistributorId()).orElse(null)
                    : null;

            OrderDetailsDTO dto = new OrderDetailsDTO();

            dto.setOrderId(order.getOrderId());
            dto.setOrderCode("ORD-" + order.getOrderId());
            dto.setQuantity(order.getQuantity());
            dto.setPricePerKg(order.getPricePerKg());
            dto.setTotalAmount(order.getTotalAmount());
            dto.setStatus(order.getStatus());
            dto.setCancelReason(order.getCancelReason());

            dto.setExpectedDelivery(order.getExpectedDelivery());
            dto.setCreatedAt(order.getCreatedAt());
            dto.setWarehouseAt(order.getWarehouseAt());
            dto.setInTransitAt(order.getInTransitAt());
            dto.setDeliveredAt(order.getDeliveredAt());
            dto.setCancelledAt(order.getCancelledAt());
            dto.setWarehouseLocation(order.getWarehouseLocation());
            dto.setTransitLocation(order.getTransitLocation());
            dto.setConsumerId(order.getConsumerId());

            dto.setDeliveryAddress(order.getDeliveryAddress());
            dto.setContactNumber(order.getContactNumber());

            if (crop != null) {
                dto.setCropName(crop.getCropName());
                dto.setCropType(crop.getCropType());
                dto.setCropImageUrl(crop.getCropImageUrl());
            }

            if (farmer != null) {
                dto.setFarmerName(farmer.getName());
                dto.setFarmerContact(farmer.getPhone());
            }

            if (distributor != null) {
                dto.setDistributorName(distributor.getName());
                dto.setDistributorContact(distributor.getPhone());
            }

            return dto;

        } catch (Exception e) {
            System.err.println("❌ Failed to build OrderDetailsDTO for order " + order.getOrderId());
            e.printStackTrace();
            return null; // NEVER crash API
        }
    }

    /* ================= PAYMENT ================= */

    private void settlePayment(Order order) {
        userRepository.findById(order.getDistributorId()).ifPresent(d -> {
            d.setBalance((d.getBalance() == null ? 0 : d.getBalance())
                    + order.getDistributorProfit());
            userRepository.save(d);
        });
    }
    /* ================= BASIC FETCH (REQUIRED BY CONTROLLER) ================= */

    public List<Order> getOrdersByConsumer(String consumerId) {
        return orderRepository.findByConsumerId(consumerId);
    }

    public List<Order> getOrdersByFarmer(String farmerId) {
        return orderRepository.findByFarmerId(farmerId);
    }

    @Transactional
    public Order setExpectedDelivery(Long orderId, String distributorId, LocalDateTime expectedDelivery) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getDistributorId().equals(distributorId)) {
            throw new RuntimeException("Unauthorized distributor");
        }

        order.setExpectedDelivery(expectedDelivery);
        order.setUpdatedAt(LocalDateTime.now());

        return orderRepository.save(order);
    }

}