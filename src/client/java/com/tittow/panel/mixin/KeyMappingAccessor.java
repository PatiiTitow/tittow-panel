package com.tittow.panel.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.KeyMapping;

/** Erlaubt dem Bot, einen echten Tastendruck (z. B. Linksklick) auszulösen. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int tittow_getClickCount();

	@Accessor("clickCount")
	void tittow_setClickCount(int count);
}
