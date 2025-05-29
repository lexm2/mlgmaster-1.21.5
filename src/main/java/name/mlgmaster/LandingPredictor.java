package name.mlgmaster;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;

public class LandingPredictor {
    
    public static class HitboxLandingResult {
        private final BlockPos primaryLandingBlock;
        private final Vec3d landingPosition;
        private final List<BlockPos> allHitBlocks;
        private final Vec3d lookTarget;
        private final Box originalHitbox;
        private final SafeLandingBlockChecker.SafetyResult safetyResult;
        
        public HitboxLandingResult(BlockPos primaryLandingBlock, Vec3d landingPosition, 
                                  List<BlockPos> allHitBlocks, Vec3d lookTarget, Box originalHitbox, 
                                  SafeLandingBlockChecker.SafetyResult safetyResult) {
            this.primaryLandingBlock = primaryLandingBlock;
            this.landingPosition = landingPosition;
            this.allHitBlocks = allHitBlocks;
            this.lookTarget = lookTarget;
            this.originalHitbox = originalHitbox;
            this.safetyResult = safetyResult;
        }
        
        public BlockPos getPrimaryLandingBlock() { return primaryLandingBlock; }
        public Vec3d getLandingPosition() { return landingPosition; }
        public List<BlockPos> getAllHitBlocks() { return allHitBlocks; }
        public Vec3d getLookTarget() { return lookTarget; }
        public Box getOriginalHitbox() { return originalHitbox; }
        public SafeLandingBlockChecker.SafetyResult getSafetyResult() { return safetyResult; }
        public boolean isSafeLanding() { return safetyResult.isSafe(); }
    }
    
    public static HitboxLandingResult predictHitboxLanding(MinecraftClient client, ClientPlayerEntity player, Vec3d currentPos, Vec3d velocity) {
        MLGMaster.LOGGER.info("🎭 HITBOX LANDING: Starting hitbox-aware landing prediction...");
        MLGMaster.LOGGER.info("📍 Current pos: {} | Velocity: {}", currentPos, velocity);
        
        // Get player hitbox and dimensions
        Box playerHitbox = player.getBoundingBox();
        double hitboxWidth = playerHitbox.getLengthX();
        double hitboxDepth = playerHitbox.getLengthZ();
        double hitboxHeight = playerHitbox.getLengthY();
        
        MLGMaster.LOGGER.info("📦 Player hitbox dimensions: width={:.3f} | depth={:.3f} | height={:.3f}", 
            hitboxWidth, hitboxDepth, hitboxHeight);
        MLGMaster.LOGGER.info("📦 Hitbox bounds: {}", playerHitbox);
        
        // Generate test points across the hitbox bottom
        List<Vec3d> testPoints = generateHitboxTestPoints(playerHitbox, currentPos);
        MLGMaster.LOGGER.info("📌 Generated {} test points across player hitbox", testPoints.size());
        
        List<LandingPoint> landingPoints = new ArrayList<>();
        
        // Test each point for landing using shared physics
        for (int i = 0; i < testPoints.size(); i++) {
            final int index = i;
            Vec3d testPoint = testPoints.get(index);
            
            MLGMaster.LOGGER.info("🔍 TESTING POINT {}: {} (relative to hitbox)", index + 1, testPoint);
            
            // Use shared physics simulation for each point
            PhysicsSimulator.SimulationResult simulation = PhysicsSimulator.simulatePointFall(
                client, player, testPoint, velocity, step -> {
                    // Custom collision checker: check if this specific point hits a block
                    boolean raycastHit = step.hasCollision();
                    boolean blockSolid = PhysicsSimulator.isBlockSolid(client, BlockPos.ofFloored(step.getPosition()));
                    
                    if (raycastHit || blockSolid) {
                        MLGMaster.LOGGER.info("  🎯 Point {} collision: raycast={} | solid_block={} at {}", 
                            index + 1, raycastHit, blockSolid, step.getPosition());
                    }
                    
                    return raycastHit || blockSolid;
                });
            
            if (simulation.foundImpact()) {
                PhysicsSimulator.SimulationStep impactStep = simulation.getImpactStep();
                BlockPos landingBlock = impactStep.hasCollision() ? 
                    impactStep.getCollision().getBlockPos() : 
                    BlockPos.ofFloored(impactStep.getPosition());
                
                landingPoints.add(new LandingPoint(testPoint, impactStep.getPosition(), landingBlock, i));
                MLGMaster.LOGGER.info("✅ Point {} LANDED: {} → block {} (Y={})", 
                    i + 1, testPoint, landingBlock, landingBlock.getY());
            } else {
                MLGMaster.LOGGER.info("❌ Point {} NO LANDING: {} (no collision found)", i + 1, testPoint);
            }
        }
        
        if (landingPoints.isEmpty()) {
            MLGMaster.LOGGER.warn("⚠️ HITBOX LANDING: No landing points found for any part of hitbox");
            return null;
        }
        
        MLGMaster.LOGGER.info("📊 LANDING SUMMARY: {}/{} points found landings", 
            landingPoints.size(), testPoints.size());
        
        // Choose the best landing block - ALWAYS use the HIGHEST block
        LandingPoint bestLanding = chooseBestLandingPoint(landingPoints);
        
        // Check comprehensive safety of the primary landing block
        SafeLandingBlockChecker.SafetyResult safetyResult = 
            SafeLandingBlockChecker.checkLandingSafety(client, player, bestLanding.landingBlock, currentPos);
        
        MLGMaster.LOGGER.info("🛡️ Landing safety check: {} - {}", 
            safetyResult.isSafe() ? "SAFE" : "UNSAFE", safetyResult.getReason());
        
        // Collect all unique blocks the player will hit, SORTED BY HEIGHT (highest first)
        List<BlockPos> allHitBlocks = new ArrayList<>();
        for (LandingPoint lp : landingPoints) {
            if (!allHitBlocks.contains(lp.landingBlock)) {
                allHitBlocks.add(lp.landingBlock);
            }
        }
        
        // Sort all hit blocks by Y coordinate (highest first)
        allHitBlocks.sort((a, b) -> Integer.compare(b.getY(), a.getY()));
        
        MLGMaster.LOGGER.info("🧱 ALL HIT BLOCKS (sorted by height):");
        for (int i = 0; i < allHitBlocks.size(); i++) {
            BlockPos block = allHitBlocks.get(i);
            MLGMaster.LOGGER.info("  {}. {} (Y={})", i + 1, block, block.getY());
        }
        
        // Calculate optimal look target (center of the water placement location on the HIGHEST block)
        Vec3d waterPlacementCenter = Vec3d.ofCenter(bestLanding.landingBlock.up());
        
        MLGMaster.LOGGER.info("🎯 FINAL SELECTION:");
        MLGMaster.LOGGER.info("  Primary landing: {} (Y={})", bestLanding.landingBlock, bestLanding.landingBlock.getY());
        MLGMaster.LOGGER.info("  Water target: {}", waterPlacementCenter);
        MLGMaster.LOGGER.info("  Safety: {}", safetyResult.isSafe() ? "SAFE" : "UNSAFE");
        
        return new HitboxLandingResult(bestLanding.landingBlock, bestLanding.landingPosition, 
            allHitBlocks, waterPlacementCenter, playerHitbox, safetyResult);
    }
    
    private static List<Vec3d> generateHitboxTestPoints(Box hitbox, Vec3d currentPos) {
        List<Vec3d> points = new ArrayList<>();
        
        // Get hitbox bounds relative to current position
        double minX = hitbox.getMin(Direction.Axis.X);
        double maxX = hitbox.getMax(Direction.Axis.X);
        double minZ = hitbox.getMin(Direction.Axis.Z);
        double maxZ = hitbox.getMax(Direction.Axis.Z);
        double bottomY = hitbox.getMin(Direction.Axis.Y);
        
        // Use the actual hitbox bounds for testing
        double centerX = (minX + maxX) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        
        MLGMaster.LOGGER.info("📦 HITBOX BOUNDS: X=[{:.3f}, {:.3f}] Z=[{:.3f}, {:.3f}] BottomY={:.3f}", 
            minX, maxX, minZ, maxZ, bottomY);
        
        // Test points at the bottom face of the hitbox
        Vec3d[] testPoints = {
            new Vec3d(centerX, bottomY, centerZ),           // Center point
            new Vec3d(minX, bottomY, minZ),                 // Back-left corner
            new Vec3d(maxX, bottomY, minZ),                 // Back-right corner
            new Vec3d(minX, bottomY, maxZ),                 // Front-left corner
            new Vec3d(maxX, bottomY, maxZ),                 // Front-right corner
            new Vec3d(minX, bottomY, centerZ),              // Left edge
            new Vec3d(maxX, bottomY, centerZ),              // Right edge
            new Vec3d(centerX, bottomY, minZ),              // Back edge
            new Vec3d(centerX, bottomY, maxZ),              // Front edge
        };
        
        
        String[] pointNames = {
            "Center", "Back-Left Corner", "Back-Right Corner", "Front-Left Corner", 
            "Front-Right Corner", "Left Edge", "Right Edge", "Back Edge", "Front Edge"
        };
        
        MLGMaster.LOGGER.info("📌 GENERATING TEST POINTS:");
        for (int i = 0; i < testPoints.length; i++) {
            points.add(testPoints[i]);
            MLGMaster.LOGGER.info("  {}. {}: {} (X={:.3f}, Y={:.3f}, Z={:.3f})", 
                i + 1, pointNames[i], testPoints[i], 
                testPoints[i].x, testPoints[i].y, testPoints[i].z);
        }
        
        // Additional points for better coverage (quarter points)
        double quarterWidth = (maxX - minX) / 4.0;
        double quarterDepth = (maxZ - minZ) / 4.0;
        
        Vec3d[] quarterPoints = {
            new Vec3d(minX + quarterWidth, bottomY, minZ + quarterDepth),
            new Vec3d(maxX - quarterWidth, bottomY, minZ + quarterDepth),
            new Vec3d(minX + quarterWidth, bottomY, maxZ - quarterDepth),
            new Vec3d(maxX - quarterWidth, bottomY, maxZ - quarterDepth)
        };
        
        String[] quarterNames = {
            "Quarter Back-Left", "Quarter Back-Right", 
            "Quarter Front-Left", "Quarter Front-Right"
        };
        
        MLGMaster.LOGGER.info("📌 ADDING QUARTER POINTS:");
        for (int i = 0; i < quarterPoints.length; i++) {
            points.add(quarterPoints[i]);
            MLGMaster.LOGGER.info("  {}. {}: {} (X={:.3f}, Y={:.3f}, Z={:.3f})", 
                testPoints.length + i + 1, quarterNames[i], quarterPoints[i],
                quarterPoints[i].x, quarterPoints[i].y, quarterPoints[i].z);
        }
        
        MLGMaster.LOGGER.info("✅ Generated {} total test points for hitbox coverage", points.size());
        return points;
    }
    
    private static LandingPoint chooseBestLandingPoint(List<LandingPoint> landingPoints) {
        MLGMaster.LOGGER.info("🏆 CHOOSING BEST LANDING from {} candidates:", landingPoints.size());
        
        // Log all candidates for debugging with detailed coordinates
        for (int i = 0; i < landingPoints.size(); i++) {
            LandingPoint point = landingPoints.get(i);
            MLGMaster.LOGGER.info("  Candidate {}: Point#{} → Block {} (X={}, Y={}, Z={})", 
                i + 1, point.pointIndex + 1, point.landingBlock, 
                point.landingBlock.getX(), point.landingBlock.getY(), point.landingBlock.getZ());
        }
        
        // Find the landing point with the HIGHEST Y value
        LandingPoint best = landingPoints.get(0);
        int highestY = best.landingBlock.getY();
        
        MLGMaster.LOGGER.info("🔍 SEARCHING FOR HIGHEST BLOCK:");
        MLGMaster.LOGGER.info("  Initial candidate: {} (Y={})", best.landingBlock, highestY);
        
        for (LandingPoint point : landingPoints) {
            int currentY = point.landingBlock.getY();
            MLGMaster.LOGGER.info("  Comparing: {} (Y={}) vs current highest Y={}", 
                point.landingBlock, currentY, highestY);
            
            if (currentY > highestY) {
                highestY = currentY;
                best = point;
                MLGMaster.LOGGER.info("    ⬆️ NEW HIGHEST: {} (Y={}) from point#{}", 
                    point.landingBlock, currentY, point.pointIndex + 1);
            } else if (currentY == highestY) {
                MLGMaster.LOGGER.info("    = EQUAL HEIGHT: {} (Y={}) - keeping current", 
                    point.landingBlock, currentY);
            } else {
                MLGMaster.LOGGER.info("    ⬇️ LOWER: {} (Y={}) - rejected", 
                    point.landingBlock, currentY);
            }
        }
        
        MLGMaster.LOGGER.info("🎯 FINAL SELECTION: Block {} (Y={}) from test point #{}", 
            best.landingBlock, highestY, best.pointIndex + 1);
        
        return best;
    }
    
    private static class LandingPoint {
        final Vec3d originalPoint;
        final Vec3d landingPosition;
        final BlockPos landingBlock;
        final int pointIndex;
        
        LandingPoint(Vec3d originalPoint, Vec3d landingPosition, BlockPos landingBlock, int pointIndex) {
            this.originalPoint = originalPoint;
            this.landingPosition = landingPosition;
            this.landingBlock = landingBlock;
            this.pointIndex = pointIndex;
        }
    }
}
