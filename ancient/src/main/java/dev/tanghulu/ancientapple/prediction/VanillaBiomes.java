package dev.tanghulu.ancientapple.prediction;

import kludwisz.ancientcity.AncientCityGenerator;
import net.minecraft.registry.*;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.noise.NoiseConfig;

public final class VanillaBiomes {
    private final NoiseConfig noise;
    private final MultiNoiseBiomeSource source;
    public VanillaBiomes(long seed) {
        var registries=BuiltinRegistries.createWrapperLookup();
        noise=NoiseConfig.create(registries.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(ChunkGeneratorSettings.OVERWORLD).value(),registries.getOrThrow(RegistryKeys.NOISE_PARAMETERS),seed);
        source=MultiNoiseBiomeSource.create(registries.getOrThrow(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST).getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
    }
    public boolean valid(AncientCityGenerator layout) {
        var box=layout.pieces[0].box;
        int x=(box.minX+box.maxX)/2,z=(box.minZ+box.maxZ)/2;
        return source.getBiome(Math.floorDiv(x,4),Math.floorDiv(-27,4),Math.floorDiv(z,4),noise.getMultiNoiseSampler()).matchesKey(BiomeKeys.DEEP_DARK);
    }
}
