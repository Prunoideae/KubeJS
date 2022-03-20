package dev.latvian.mods.kubejs.mixin.marker;

import net.minecraft.world.level.material.Material;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Material.class)
public interface MaterialMarker {
	// Materials will be auto-injected into here by Gradle
}
