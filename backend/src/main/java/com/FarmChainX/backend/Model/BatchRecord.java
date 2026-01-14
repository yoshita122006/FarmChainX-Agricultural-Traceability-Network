package com.FarmChainX.backend.Model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "batch_records")
public class BatchRecord {

    @Id
    @Column(name = "batch_id", nullable = false, unique = true)
    private String batchId;
    @Column(length = 500)
    private String rejectionReason;
    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }


    // ❌ Removed invalid @ManyToOne crop relationship

    @Column(name = "farmer_id")
    private String farmerId;

    @Column(name = "distributor_id")
    private String distributorId;

    @Column(name = "crop_id")
    private Long cropId;

    @Column(name = "crop_type")
    private String cropType;

    @Column(name = "total_quantity")
    private Double totalQuantity;

    @Column(name = "avg_quality_score")
    private Double avgQualityScore;

    @Column(name = "harvest_date")
    private LocalDate harvestDate;

    @Column(name = "status")
    private String status = "PLANTED";

    @Column(name = "is_blocked")
    private Boolean blocked = false;

    @Column(name = "rejected_by")
    private String rejectedBy;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "qr_code_url")
    private String qrCodeUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Getters & Setters

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }

    public String getFarmerId() { return farmerId; }
    public void setFarmerId(String farmerId) { this.farmerId = farmerId; }

    public String getDistributorId() { return distributorId; }
    public void setDistributorId(String distributorId) { this.distributorId = distributorId; }

    public String getCropType() { return cropType; }
    public void setCropType(String cropType) { this.cropType = cropType; }

    public Double getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(Double totalQuantity) { this.totalQuantity = totalQuantity; }

    public Double getAvgQualityScore() { return avgQualityScore; }
    public void setAvgQualityScore(Double avgQualityScore) { this.avgQualityScore = avgQualityScore; }

    public LocalDate getHarvestDate() { return harvestDate; }
    public void setHarvestDate(LocalDate harvestDate) { this.harvestDate = harvestDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Boolean getBlocked() { return blocked; }
    public void setBlocked(Boolean blocked) { this.blocked = blocked; }

    public String getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(String rejectedBy) { this.rejectedBy = rejectedBy; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }

    public String getQrCodeUrl() { return qrCodeUrl; }
    public void setQrCodeUrl(String qrCodeUrl) { this.qrCodeUrl = qrCodeUrl; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}