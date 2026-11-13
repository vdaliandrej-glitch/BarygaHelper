package local.diamondfinder.mixin;

import local.diamondfinder.DiamondFinder;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class MenuPacketMixin {
    @Inject(method = "handleContainerContent", at = @At("RETURN"))
    private void shintaro$content(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (DiamondFinder.scanner != null) {
            DiamondFinder.scanner.serverContent(packet.containerId());
        }
    }

    @Inject(method = "handleContainerSetSlot", at = @At("RETURN"))
    private void shintaro$slot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (DiamondFinder.scanner != null) {
            DiamondFinder.scanner.serverContent(packet.getContainerId());
        }
    }
}
