package com.tittow.panel.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.OptionInstance;

/** Erlaubt Helligkeit über 100 % (für "Hell"), ohne die normale Begrenzung. */
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
	@Accessor("value")
	void tittow_setValue(Object value);
}
