package local.diamondfinder.mixin;

import local.diamondfinder.DiamondFinder;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void diamondFinder$capture(Screen screen, CallbackInfo ci) {
        if (DiamondFinder.scanner != null && DiamondFinder.scanner.capture(screen)) {
            ci.cancel();
        }
    }
}
