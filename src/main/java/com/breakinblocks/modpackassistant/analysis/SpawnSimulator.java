package com.breakinblocks.modpackassistant.analysis;

import com.breakinblocks.modpackassistant.report.CsvWriter;
import com.breakinblocks.modpackassistant.report.ReportWriter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SpawnSimulator {
    public static final int TICKS_PER_JOB = 50;
    private static final int PASSIVE_INTERVAL = 400;
    private static final int MAGIC_CHUNKS = 17 * 17;
    private static final double MIN_PLAYER_DISTANCE_SQ = 24.0D * 24.0D;
    private static final int DESPAWN_AGE = 600;
    private static final int DESPAWN_CHANCE = 800;
    private static final int GROUP_ATTEMPTS = 3;
    private static final int DEFAULT_CLUSTER = 4;
    public static final List<String> SKIPPED_RULES = List.of("MobSpawnEvent.PositionCheck", "MobSpawnEvent.FinalizeSpawn", "structure spawn overrides outside nether fortresses");

    public static final class TypeStat {
        public final EntityType<?> type;
        public int attempts;
        public int individuals;
        public int packs;

        TypeStat(EntityType<?> type) {
            this.type = type;
        }
    }

    public static final class CategoryStat {
        public int attempts;
        public int individuals;
        public int capFullTicks;
        public int peak;
        public int current;
    }

    private record VirtualEntity(EntityType<?> type, MobCategory category, int bornTick) {
    }

    private final ServerLevel level;
    private final Holder<Biome> biome;
    private final List<ChunkPos> chunks;
    private final Vec3 playerPos;
    private final int totalTicks;
    private final RandomSource random = RandomSource.create();
    private final Map<EntityType<?>, TypeStat> typeStats = new HashMap<>();
    private final EnumMap<MobCategory, CategoryStat> categoryStats = new EnumMap<>(MobCategory.class);
    private final List<VirtualEntity> population = new ArrayList<>();
    private final ChunkGenerator generator;
    private int tick;

    public SpawnSimulator(ServerLevel level, Holder<Biome> biome, List<ChunkPos> chunks, Vec3 playerPos, int totalTicks) {
        this.level = level;
        this.biome = biome;
        this.chunks = chunks;
        this.playerPos = playerPos;
        this.totalTicks = totalTicks;
        this.generator = level.getChunkSource().getGenerator();
        for (MobCategory category : MobCategory.values()) {
            categoryStats.put(category, new CategoryStat());
        }
    }

    public int totalTicks() {
        return totalTicks;
    }

    public int jobCount() {
        return (totalTicks + TICKS_PER_JOB - 1) / TICKS_PER_JOB;
    }

    public int chunkCount() {
        return chunks.size();
    }

    public void simulateBatch() {
        int end = Math.min(totalTicks, tick + TICKS_PER_JOB);
        while (tick < end) {
            tickOnce();
            tick++;
        }
    }

    private void tickOnce() {
        boolean passive = tick % PASSIVE_INTERVAL == 0;
        for (MobCategory category : MobCategory.values()) {
            if (category == MobCategory.MISC || (category.isPersistent() && !passive)) {
                continue;
            }
            CategoryStat stat = categoryStats.get(category);
            int cap = category.getMaxInstancesPerChunk() * chunks.size() / MAGIC_CHUNKS;
            if (stat.current >= cap) {
                stat.capFullTicks++;
                continue;
            }
            for (ChunkPos chunk : chunks) {
                spawnCategoryForChunk(category, chunk, stat);
                if (stat.current >= cap) {
                    break;
                }
            }
        }
        despawn();
    }

    private void spawnCategoryForChunk(MobCategory category, ChunkPos chunk, CategoryStat categoryStat) {
        BlockPos pos = randomPositionWithin(chunk);
        if (level.getBlockState(pos).isRedstoneConductor(level, pos)) {
            return;
        }
        double distanceSq = playerPos.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (distanceSq <= MIN_PLAYER_DISTANCE_SQ) {
            return;
        }
        int spawnedThisChunk = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < GROUP_ATTEMPTS; attempt++) {
            WeightedList<MobSpawnSettings.SpawnerData> potential = generator.getMobsAt(biome, level.structureManager(), category, pos);
            Optional<MobSpawnSettings.SpawnerData> picked = potential.getRandom(random);
            if (picked.isEmpty()) {
                return;
            }
            MobSpawnSettings.SpawnerData data = picked.get();
            int groupSize = Mth.ceil(random.nextFloat() * 4.0F);
            int clusterLimit = DEFAULT_CLUSTER;
            boolean packSpawned = false;
            cursor.set(pos);
            for (int member = 0; member < groupSize; member++) {
                cursor.set(cursor.getX() + random.nextInt(6) - random.nextInt(6), cursor.getY(), cursor.getZ() + random.nextInt(6) - random.nextInt(6));
                TypeStat typeStat = typeStats.computeIfAbsent(data.type(), TypeStat::new);
                typeStat.attempts++;
                categoryStat.attempts++;
                double memberDistanceSq = playerPos.distanceToSqr(cursor.getX() + 0.5D, cursor.getY(), cursor.getZ() + 0.5D);
                if (!isValidPosition(category, data, cursor, memberDistanceSq)) {
                    continue;
                }
                Mob mob = throwawayMob(data.type(), cursor);
                if (mob == null) {
                    continue;
                }
                clusterLimit = mob.getMaxSpawnClusterSize();
                boolean passes = mob.checkSpawnRules(level, EntitySpawnReason.NATURAL) && mob.checkSpawnObstruction(level);
                mob.discard();
                if (!passes) {
                    continue;
                }
                typeStat.individuals++;
                categoryStat.individuals++;
                categoryStat.current++;
                categoryStat.peak = Math.max(categoryStat.peak, categoryStat.current);
                population.add(new VirtualEntity(data.type(), category, tick));
                packSpawned = true;
                if (++spawnedThisChunk >= clusterLimit) {
                    typeStat.packs++;
                    return;
                }
            }
            if (packSpawned) {
                typeStats.get(data.type()).packs++;
            }
        }
    }

    private boolean isValidPosition(MobCategory category, MobSpawnSettings.SpawnerData data, BlockPos pos, double distanceSq) {
        EntityType<?> type = data.type();
        if (type.getCategory() == MobCategory.MISC || !type.canSummon()) {
            return false;
        }
        if (!type.canSpawnFarFromPlayer() && distanceSq > (double) (category.getDespawnDistance() * category.getDespawnDistance())) {
            return false;
        }
        if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) {
            return false;
        }
        if (!SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, pos, random)) {
            return false;
        }
        return level.noCollision(type.getSpawnAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D));
    }

    private Mob throwawayMob(EntityType<?> type, BlockPos pos) {
        Entity entity = type.create(level, EntitySpawnReason.NATURAL);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            return null;
        }
        mob.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        return mob;
    }

    private BlockPos randomPositionWithin(ChunkPos chunk) {
        int x = chunk.getMinBlockX() + random.nextInt(16);
        int z = chunk.getMinBlockZ() + random.nextInt(16);
        LevelChunk loaded = level.getChunk(chunk.x(), chunk.z());
        int highest = loaded.getHighestFilledSectionIndex();
        int top = highest == -1
                ? level.getMinY()
                : SectionPos.sectionToBlockCoord(loaded.getSectionYFromSectionIndex(highest)) + 15;
        int y = Mth.randomBetweenInclusive(random, level.getMinY(), Math.max(level.getMinY(), top));
        return new BlockPos(x, y, z);
    }

    private void despawn() {
        Iterator<VirtualEntity> iterator = population.iterator();
        while (iterator.hasNext()) {
            VirtualEntity entity = iterator.next();
            if (entity.category().isPersistent()) {
                continue;
            }
            if (tick - entity.bornTick() > DESPAWN_AGE && random.nextInt(DESPAWN_CHANCE) == 0) {
                iterator.remove();
                categoryStats.get(entity.category()).current--;
            }
        }
    }

    public List<TypeStat> rankedTypes() {
        List<TypeStat> list = new ArrayList<>(typeStats.values());
        list.sort(Comparator.comparingInt((TypeStat stat) -> stat.individuals).reversed().thenComparing(stat -> BuiltInRegistries.ENTITY_TYPE.getKey(stat.type).toString()));
        return list;
    }

    public int totalIndividuals() {
        int total = 0;
        for (CategoryStat stat : categoryStats.values()) {
            total += stat.individuals;
        }
        return total;
    }

    public String csv(ReportWriter.Context context) {
        CsvWriter csv = new CsvWriter().comments(context.headerLines())
                .comment("assumptions: the caller is the only player; sampled chunks are the loaded chunks whose surface biome matches; despawning uses the vanilla 1 in 800 per tick rule after 600 ticks for non-persistent categories")
                .comment("skipped rules: " + String.join("; ", SKIPPED_RULES));
        csv.row("section", "entity_type", "category", "attempts", "individuals", "share_of_category_percent", "mean_pack_size");
        for (TypeStat stat : rankedTypes()) {
            MobCategory category = stat.type.getCategory();
            int categoryTotal = categoryStats.get(category).individuals;
            csv.row("type", BuiltInRegistries.ENTITY_TYPE.getKey(stat.type), category.getName(), stat.attempts, stat.individuals,
                    String.format("%.4f", categoryTotal == 0 ? 0.0D : stat.individuals * 100.0D / categoryTotal),
                    String.format("%.3f", stat.packs == 0 ? 0.0D : (double) stat.individuals / stat.packs));
        }
        csv.blank();
        csv.row("section", "category", "attempts", "individuals", "cap_full_ticks", "peak_population", "cap");
        for (Map.Entry<MobCategory, CategoryStat> entry : categoryStats.entrySet()) {
            if (entry.getKey() == MobCategory.MISC) {
                continue;
            }
            CategoryStat stat = entry.getValue();
            csv.row("category", entry.getKey().getName(), stat.attempts, stat.individuals, stat.capFullTicks, stat.peak,
                    entry.getKey().getMaxInstancesPerChunk() * chunks.size() / MAGIC_CHUNKS);
        }
        return csv.content();
    }
}
