package com.tittow.panel.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.tittow.panel.Erdspur;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

/** Sieht jeden gesetzten Block: So weiß der Mauerbau genau, welche Erde sein eigenes Gerüst ist. */
@Mixin(MultiPlayerGameMode.class)
public abstract class GeruestMerkenMixin {
	@Inject(method = "useItemOn", at = @At("HEAD"), require = 0)
	private void tittow_vorSetzen(LocalPlayer p, InteractionHand hand, BlockHitResult treffer,
			CallbackInfoReturnable<InteractionResult> cir) {
		Erdspur.vorSetzen(p, hand, treffer);
	}

	@Inject(method = "useItemOn", at = @At("RETURN"), require = 0)
	private void tittow_nachSetzen(LocalPlayer p, InteractionHand hand, BlockHitResult treffer,
			CallbackInfoReturnable<InteractionResult> cir) {
		Erdspur.nachSetzen();
	}
}
