package local.bpdupe1211;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

/**
 * Client-side key for the Sophisticated Core filter-slot transfer defect (1.21.1 / NeoForge).
 *
 * <p>{@code TransferFullSlotPayload.handlePayload} takes the slot id from the client and feeds it
 * straight to {@code quickMoveStack}; that method never asks the source slot {@code mayPickup()},
 * and {@code FilterSlotItemHandler} returns false there. A filter (ghost) slot is therefore
 * accepted as a real item source, and {@code mergeSlotStack}'s upgrade-settings branch merges the
 * sample into real storage: +1 real item per packet, sample cleared.
 */
@Mod(BpDupe.MODID)
public final class BpDupe {
    public static final String MODID = "bpdupe1211";

    public BpDupe(IEventBus modBus) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            local.bpdupe1211.client.ClientSetup.init(modBus);
        }
    }
}
