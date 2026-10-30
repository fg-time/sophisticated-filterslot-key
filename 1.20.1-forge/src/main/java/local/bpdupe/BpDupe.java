package local.bpdupe;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Client-side key for the Sophisticated Core filter-slot transfer defect (1.20.1 / Forge).
 *
 * <p>The server handler {@code TransferFullSlotMessage.handleMessage} takes the slot id from the
 * client and calls {@code quickMoveStack} without ever asking the source slot {@code mayPickup()}.
 * A filter (ghost) slot answers false, so the sample is not a real item source - but this path
 * merges it into real storage anyway. Sending the index of a filter slot therefore yields one real
 * item per packet while the sample is simply cleared.
 */
@Mod(BpDupe.MODID)
public final class BpDupe {
    public static final String MODID = "bpdupe";

    public BpDupe() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> local.bpdupe.client.ClientSetup.init(modBus));
    }
}
