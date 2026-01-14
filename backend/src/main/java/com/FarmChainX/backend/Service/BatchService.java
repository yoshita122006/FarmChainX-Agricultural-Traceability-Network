package com.FarmChainX.backend.Service;

import com.FarmChainX.backend.Model.BatchRecord;
import com.FarmChainX.backend.Model.BatchTrace;
import com.FarmChainX.backend.Model.Crop;
import com.FarmChainX.backend.Model.Listing;
import com.FarmChainX.backend.Repository.BatchRecordRepository;
import com.FarmChainX.backend.Repository.BatchTraceRepository;
import com.FarmChainX.backend.Repository.CropRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BatchService {

    private final BatchRecordRepository batchRecordRepository;
    private final CropRepository cropRepository;
    private final ListingService listingService;
    private final BatchTraceRepository batchTraceRepository;
    private final NotificationEventService notificationEventService;

    public BatchService(BatchRecordRepository batchRecordRepository,
                        CropRepository cropRepository,
                        ListingService listingService,
                        BatchTraceRepository batchTraceRepository, NotificationEventService notificationEventService) {
        this.batchRecordRepository = batchRecordRepository;
        this.cropRepository = cropRepository;
        this.listingService = listingService;
        this.batchTraceRepository = batchTraceRepository;
        this.notificationEventService = notificationEventService;
    }

    // ------------------- CREATE NEW BATCH -------------------
    public BatchRecord createBatch(BatchRecord batch) {
        if (batch.getFarmerId() == null || batch.getFarmerId().isEmpty())
            throw new RuntimeException("Farmer ID is required");
        if (batch.getCropType() == null || batch.getCropType().isEmpty())
            throw new RuntimeException("Crop type is required");

        if (batch.getBatchId() == null || batch.getBatchId().isEmpty())
            batch.setBatchId(generateBatchId(batch.getCropType()));

        batch.setCreatedAt(LocalDateTime.now());
        if (batch.getStatus() == null)
            batch.setStatus("PLANTED");

        if ("HARVESTED".equalsIgnoreCase(batch.getStatus()) && batch.getHarvestDate() == null)
            batch.setHarvestDate(LocalDate.now());

        return batchRecordRepository.save(batch);
    }

    // ------------------- GET SINGLE BATCH -------------------
    public Optional<BatchRecord> getBatch(String batchId) {
        return batchRecordRepository.findById(batchId);
    }

    // ------------------- GET BATCHES BY FARMER -------------------
    public List<BatchRecord> getBatchesByFarmer(String farmerId) {
        return batchRecordRepository.findByFarmerId(farmerId);
    }

    // ------------------- GET CROPS FOR BATCH -------------------
    public List<Crop> getCropsForBatch(String batchId) {
        return cropRepository.findByBatchId(batchId);
    }

    // ------------------- DISTRIBUTOR PENDING BATCHES -------------------
    public List<BatchRecord> getPendingBatchesForDistributor() {
        List<BatchRecord> batches = batchRecordRepository.findByStatusIn(
                List.of("HARVESTED", "SUBMITTED_FOR_APPROVAL"));

        return batches.stream()
                .filter(b -> !Boolean.TRUE.equals(b.getBlocked()))
                .filter(b -> {
                    List<Crop> activeCrops = cropRepository.findByBatchId(b.getBatchId())
                            .stream()
                            .filter(c -> !Boolean.TRUE.equals(c.getBlocked()))
                            .toList();
                    return !activeCrops.isEmpty();
                })
                .toList();
    }

    // ------------------- DISTRIBUTOR APPROVED BATCHES -------------------
    public List<BatchRecord> getApprovedBatches(String distributorId) {
        List<BatchRecord> batches = batchRecordRepository.findByDistributorIdAndStatus(
                distributorId, "APPROVED");

        return batches.stream()
                .filter(b -> !Boolean.TRUE.equals(b.getBlocked()))
                .filter(b -> {
                    List<Crop> activeCrops = cropRepository.findByBatchId(b.getBatchId())
                            .stream()
                            .filter(c -> !Boolean.TRUE.equals(c.getBlocked()))
                            .toList();
                    return !activeCrops.isEmpty();
                })
                .toList();
    }

    // ------------------- APPROVE BATCH -------------------
    @Transactional
    public BatchRecord approveBatch(String batchId, String distributorId) {

        BatchRecord batch = batchRecordRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        if ("APPROVED".equals(batch.getStatus())) {
            return batch;
        }

        batch.setStatus("APPROVED");
        batch.setDistributorId(distributorId);
        batch.setUpdatedAt(LocalDateTime.now());

        List<Crop> crops = cropRepository.findByBatchId(batchId);

        for (Crop crop : crops) {

            double farmerPrice = crop.getPrice() != null ? crop.getPrice() : 0.0;

            // 💰 Profit calculation
            double farmerProfit = farmerPrice * 0.10;
            double distributorProfit = farmerPrice * 0.10;

            double finalMarketPrice =
                    farmerPrice + farmerProfit + distributorProfit;

            Listing listing = new Listing();
            listing.setBatchId(batchId);
            listing.setFarmerId(batch.getFarmerId());
            listing.setCropId(crop.getCropId());
            listing.setDistributorId(distributorId);

            listing.setQuantity(
                    crop.getQuantity() != null
                            ? Double.parseDouble(crop.getQuantity())
                            : 0.0
            );

            listing.setPrice(finalMarketPrice);
            listing.setFarmerProfit(farmerProfit);
            listing.setDistributorProfit(distributorProfit);

            listingService.createOrActivateListing(listing);
        }

        batchRecordRepository.save(batch);

        // 🔔 NOTIFY FARMER – APPROVAL
        notificationEventService.notifyUser(
                batch.getFarmerId(),
                "FARMER",
                "Batch Approved ✅",
                "Your batch " + batchId + " has been approved and listed in the marketplace.",
                "BATCH_APPROVED",
                batchId
        );

        return batch;
    }

    private Double parseDoubleSafe(String number) {
        try {
            return Double.parseDouble(number);
        } catch (Exception e) {
            return 0.0;
        }
    }

    // ------------------- REJECT BATCH -------------------
    // ------------------- REJECT BATCH -------------------
    @Transactional
    public BatchRecord rejectBatch(
            String batchId,
            String distributorId,
            String reason
    ) {

        BatchRecord batch = batchRecordRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        batch.setStatus("REJECTED");
        batch.setRejectedBy(distributorId);
        batch.setRejectionReason(reason);
        batch.setBlocked(true);
        batch.setUpdatedAt(LocalDateTime.now());

        batchRecordRepository.save(batch);

        // 🧾 Trace (optional but good)
        saveTrace(
                batch,
                "REJECTED - Reason: " + (reason != null ? reason : "N/A"),
                distributorId
        );

        // 🔔 NOTIFY FARMER – REJECTION
        notificationEventService.notifyUser(
                batch.getFarmerId(),
                "FARMER",
                "Batch Rejected ❌",
                "Your batch " + batchId + " was rejected. Reason: "
                        + (reason != null ? reason : "Not specified"),
                "BATCH_REJECTED",
                batchId
        );

        return batch;
    }


    @Transactional
    public BatchRecord updateStatus(String batchId, String status, String userId) {

        BatchRecord batch = batchRecordRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        // 1️⃣ Update batch
        batch.setStatus(status);
        batch.setUpdatedAt(LocalDateTime.now());

        if ("HARVESTED".equalsIgnoreCase(status) && batch.getHarvestDate() == null) {
            batch.setHarvestDate(LocalDate.now());
        }

        // 2️⃣ Update all crops under this batch
        List<Crop> crops = cropRepository.findByBatchId(batchId);
        for (Crop crop : crops) {
            crop.setStatus(status);
        }
        cropRepository.saveAll(crops);

        // 3️⃣ Save batch
        batchRecordRepository.save(batch);

        // 4️⃣ Trace
        saveTrace(batch, status, userId);

        // 🔔 5️⃣ AUTO NOTIFY DISTRIBUTOR WHEN HARVESTED
        if ("HARVESTED".equalsIgnoreCase(status)) {
            String distributorId = batch.getDistributorId();
            System.out.println(distributorId);

            notificationEventService.notifyUser(
                     "ALL",                      // all distributors
                    "DISTRIBUTOR",
                    "New Batch Ready for Approval",
                    "Batch " + batch.getBatchId() + " is harvested and waiting for approval",
                    "BATCH_SUBMITTED",
                    batch.getBatchId()           // entity reference
            );
        }

        return batch;
    }

    // ------------------- UPDATE QUALITY GRADE -------------------
    @Transactional
    public BatchRecord updateQualityGrade(String batchId, String grade, Integer confidence, String userId) {
        BatchRecord batch = batchRecordRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));

        // Update each crop's quality grade
        List<Crop> crops = cropRepository.findByBatchId(batchId);
        if (crops.isEmpty()) {
            // still save grade on batch even if no crops found (frontend expects this)
            if (grade != null) {
                // try to set average quality/confidence on batch if fields exist
                try {
                    batch.setAvgQualityScore(
                            confidence != null ? confidence.doubleValue() : batch.getAvgQualityScore());
                } catch (Throwable ignored) {
                }
            }
            batch.setUpdatedAt(LocalDateTime.now());
            batchRecordRepository.save(batch);
            saveTrace(batch, "QUALITY_UPDATED", userId);
            return batch;
        }

        for (Crop c : crops) {
            c.setQualityGrade(grade);
        }
        cropRepository.saveAll(crops);

        // store confidence/avgQualityScore on batch record as well so frontend can read
        // it easily
        if (confidence != null) {
            try {
                batch.setAvgQualityScore(confidence.doubleValue());
            } catch (Throwable ignored) {
            }
        }
        batch.setUpdatedAt(LocalDateTime.now());
        batchRecordRepository.save(batch);

        saveTrace(batch, "QUALITY_UPDATED", userId);
        return batch;
    }

    // ------------------- SPLIT BATCH -------------------
    @Transactional
    public BatchRecord splitBatch(String parentBatchId, double splitQuantity, String userId) {

        BatchRecord parent = batchRecordRepository.findById(parentBatchId)
                .orElseThrow(() -> new RuntimeException("Parent batch not found"));

        double parentTotal = parent.getTotalQuantity() != null ? parent.getTotalQuantity() : 0.0;
        if (splitQuantity <= 0 || splitQuantity > parentTotal) {
            throw new RuntimeException("Invalid split quantity");
        }

        List<Crop> parentCrops = cropRepository.findByBatchId(parentBatchId);
        if (parentCrops.isEmpty()) {
            throw new RuntimeException("No crops to split");
        }

        double ratio = splitQuantity / parentTotal;

        BatchRecord child = new BatchRecord();
        child.setBatchId(parent.getBatchId() + "-S" +
                UUID.randomUUID().toString().replace("-", "").substring(0, 3).toUpperCase());
        child.setFarmerId(parent.getFarmerId());
        child.setCropType(parent.getCropType());
        child.setStatus(parent.getStatus());
        child.setTotalQuantity(roundToTwoDecimals(splitQuantity));
        child.setCreatedAt(LocalDateTime.now());
        batchRecordRepository.save(child);

        List<Crop> parentUpdates = new ArrayList<>();
        List<Crop> childCrops = new ArrayList<>();

        for (Crop pc : parentCrops) {

            double parentQty = parseQuantity(pc.getQuantity());
            double childQty = roundToTwoDecimals(parentQty * ratio);
            double remainingQty = roundToTwoDecimals(parentQty - childQty);

            // update parent
            pc.setQuantity(String.valueOf(remainingQty));
            parentUpdates.add(pc);

            if (childQty > 0) {
                Crop childCrop = new Crop();
                childCrop.setBatchId(child.getBatchId());
                childCrop.setFarmerId(parent.getFarmerId());
                childCrop.setCropName(pc.getCropName());
                childCrop.setQuantity(String.valueOf(childQty));
                childCrop.setLocation(pc.getLocation());
                childCrop.setExpectedHarvestDate(pc.getExpectedHarvestDate());
                childCrop.setQualityGrade(pc.getQualityGrade());

                // ✅ CRITICAL LINE
                childCrop.setPrice(pc.getPrice());

                // 🔍 debug (remove later)
                System.out.println("Copied price: " + pc.getPrice());

                childCrops.add(childCrop);
            }
        }

        cropRepository.saveAll(parentUpdates);
        cropRepository.saveAll(childCrops);

        parent.setTotalQuantity(roundToTwoDecimals(parentTotal - splitQuantity));
        parent.setUpdatedAt(LocalDateTime.now());
        batchRecordRepository.save(parent);

        saveTrace(parent, "SPLIT", userId);
        saveTrace(child, "CREATED_BY_SPLIT", userId);

        return child;
    }

    // ------------------- MERGE BATCHES -------------------
    @Transactional
    public List<BatchRecord> mergeBatches(String targetBatchId, List<String> sourceBatchIds, String userId) {

        if (sourceBatchIds == null || sourceBatchIds.isEmpty())
            throw new RuntimeException("No source batches provided");

        // Fetch target batch
        BatchRecord target = batchRecordRepository.findById(targetBatchId)
                .orElseThrow(() -> new RuntimeException("Target batch not found"));

        // Fetch source batches
        List<BatchRecord> sources = sourceBatchIds.stream()
                .filter(id -> !id.equals(targetBatchId))
                .map(id -> batchRecordRepository.findById(id)
                        .orElseThrow(() -> new RuntimeException("Source batch not found: " + id)))
                .collect(Collectors.toList());

        double totalAdded = 0.0;
        List<Crop> cropsToSave = new ArrayList<>();

        for (BatchRecord s : sources) {

            if (!Objects.equals(s.getCropType(), target.getCropType()))
                throw new RuntimeException("Cannot merge batches of different crop types");

            List<Crop> scrops = cropRepository.findByBatchId(s.getBatchId());

            for (Crop c : scrops) {
                c.setBatchId(target.getBatchId());
                cropsToSave.add(c);
                totalAdded += parseQuantity(c.getQuantity());
            }

            // Block and mark source as merged
            s.setBlocked(true);
            s.setStatus("MERGED");
            s.setUpdatedAt(LocalDateTime.now());
            batchRecordRepository.save(s);

            saveTrace(s, "MERGED_INTO -> " + targetBatchId, userId);
        }

        // Save all crops under target batch
        cropRepository.saveAll(cropsToSave);

        // Update target batch quantity
        double targetTotal = target.getTotalQuantity() != null ? target.getTotalQuantity() : 0.0;
        target.setTotalQuantity(roundToTwoDecimals(targetTotal + totalAdded));

        // IMPORTANT: target batch should remain ACTIVE
        target.setStatus("ACTIVE");
        target.setUpdatedAt(LocalDateTime.now());
        batchRecordRepository.save(target);

        saveTrace(target, "MERGED_FROM_SOURCES", userId);

        // Return updated batch list for farmer/distributor
        return batchRecordRepository.findByFarmerIdAndBlockedFalse(target.getFarmerId());

    }

    // ------------------- GET BATCH TRACES -------------------
    public List<BatchTrace> getBatchTrace(String batchId) {
        return batchTraceRepository.findByBatchIdOrderByTimestampAsc(batchId);
    }

    // ------------------- HELPERS -------------------
    private double parseQuantity(String q) {
        if (q == null || q.isEmpty())
            return 0.0;
        try {
            return Double.parseDouble(q);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private double roundToTwoDecimals(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private Map<String, Object> toBatchResponse(BatchRecord batch) {
        Map<String, Object> data = new HashMap<>();

        data.put("batchId", batch.getBatchId());
        data.put("farmerId", batch.getFarmerId());
        data.put("cropType", batch.getCropType());
        data.put("totalQuantity", batch.getTotalQuantity());
        data.put("harvestDate", batch.getHarvestDate());
        data.put("status", batch.getStatus());
        data.put("distributorId", batch.getDistributorId());
        data.put("avgQualityScore", batch.getAvgQualityScore());
        data.put("rejectionReason", batch.getRejectionReason());

        List<Crop> crops = cropRepository.findByBatchId(batch.getBatchId());
        data.put("crops", crops);

        if (!crops.isEmpty()) {
            Crop c = crops.get(0);
            data.put("location", c.getLocation());
            data.put("expectedHarvestDate", c.getExpectedHarvestDate());
            data.put("cropName", c.getCropName());
            data.put("qualityGrade", c.getQualityGrade());
        }

        // ✅ PRICE FIX (MOST IMPORTANT)
        Listing listing = listingService.getListingByBatchId(batch.getBatchId());
        if (listing != null) {
            data.put("price", listing.getPrice());
            data.put("listingQuantity", listing.getQuantity());
            data.put("listingStatus", listing.getStatus());
        } else {
            data.put("price", 0);
            data.put("listingQuantity", 0);
            data.put("listingStatus", "N/A");
        }

        return data;
    }

    private String generateBatchId(String cropType) {
        String prefix = cropType != null && cropType.length() >= 3
                ? cropType.substring(0, 3).toUpperCase()
                : "CRP";
        String date = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMdd"));
        String random = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "FCX-" + prefix + "-" + date + "-" + random;
    }

    private void saveTrace(BatchRecord batch, String status, String userId) {
        BatchTrace trace = new BatchTrace();
        trace.setBatchId(batch.getBatchId());
        trace.setFarmerId(batch.getFarmerId());
        trace.setStatus(status);
        trace.setChangedBy(userId);

        trace.setTimestamp(LocalDateTime.now());
        batchTraceRepository.save(trace);
    }

    public BatchRecord submitForApproval(String batchId, String userId) {
        BatchRecord batch = batchRecordRepository.findById(batchId)
                .orElseThrow(() -> new RuntimeException("Batch not found"));
        batch.setStatus("SUBMITTED_FOR_APPROVAL");
        batch.setUpdatedAt(LocalDateTime.now());
        batchRecordRepository.save(batch);

        saveTrace(batch, "SUBMITTED_FOR_APPROVAL", userId);
        return batch;
    }

}