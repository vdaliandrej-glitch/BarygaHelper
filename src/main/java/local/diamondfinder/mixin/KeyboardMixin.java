package local.diamondfinder.mixin;

import local.diamondfinder.DiamondFinder;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void diamondFinder$menuKey(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action == 1 && DiamondFinder.menuKey(event.key(), event.modifiers())) {
            ci.cancel();
        }
    }
}
