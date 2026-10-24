package kludwisz.ancientcity;

import com.seedfinding.mcbiome.biome.Biome;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mccore.state.Dimension;
import com.seedfinding.mccore.version.MCVersion;
import com.seedfinding.mccore.version.VersionMap;
import com.seedfinding.mcfeature.structure.RegionStructure;
import com.seedfinding.mcfeature.structure.UniformStructure;

public class AncientCity extends UniformStructure<AncientCity> {
	public static final VersionMap<RegionStructure.Config> CONFIGS = new VersionMap<RegionStructure.Config>()
		.add(MCVersion.v1_19, new RegionStructure.Config(24, 8, 20083232));


	public AncientCity(MCVersion version) {
		this(CONFIGS.getAsOf(version), version);
	}

	public AncientCity(RegionStructure.Config config, MCVersion version) {
		super(config, version);
	}

	public static String name() {
		return "ancient_city";
	}

	@Override
	public boolean canStart(Data<AncientCity> data, long structureSeed, ChunkRand rand) {
		return super.canStart(data, structureSeed, rand);
	}

	@Override
	public Dimension getValidDimension() {
		return Dimension.OVERWORLD;
	}

	@Override
	public boolean isValidBiome(Biome biome) {
		return false;
	}
}
