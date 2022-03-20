package dev.latvian.mods.kubejs.mixin.marker;

import net.minecraft.world.level.block.SoundType;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SoundType.class)
public interface SoundTypeMarker {
	// SoundTypes will be auto-injected into here by Gradle
}
