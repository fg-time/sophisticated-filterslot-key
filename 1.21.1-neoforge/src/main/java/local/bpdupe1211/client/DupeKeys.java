package local.bpdupe1211.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedcore.common.gui.IFilterSlot;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import net.p3pp3rf1y.sophisticatedcore.compat.recipeviewers.common.SetGhostSlotPayload;
import net.p3pp3rf1y.sophisticatedcore.network.TransferFullSlotPayload;
import org.lwjgl.glfw.GLFW;

/**
 * Raw GLFW polling on purpose - vanilla only feeds {@link KeyMapping} state while no screen is open,
 * so a regular binding cannot fire inside a container GUI. The key bound in Controls is still
 * honoured (it is read back through {@link KeyMapping#getKey()}), so rebinding works.
 *
 * <ul>
 *   <li><b>K</b> - one {@code transfer_full_slot} per filter slot holding a sample.</li>
 *   <li><b>Shift+K</b> (held) - the same every 3 ticks.</li>
 *   <li><b>Ctrl+K</b> - plant the clipboard item id as a ghost sample ({@code set_ghost_slot}) and
 *       transfer it immediately: creates an item the player never owned. Needs the server to have
 *       registered the recipe-viewer payload (JEI / EMI / REI on the server).</li>
 * </ul>
 */
public final class DupeKeys {
    public static final KeyMapping DUPE =
            new KeyMapping("key.bpdupe1211.dupe", GLFW.GLFW_KEY_K, "key.categories.bpdupe1211");

    private static final int REPEAT_TICKS = 3;

    private static boolean wasDown;
    private static int repeatCountdown;

    private DupeKeys() {
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !(minecraft.screen instanceof AbstractContainerScreen<?> screen)) {
            wasDown = false;
            return;
        }
        if (screen.getFocused() instanceof EditBox) {
            return;
        }

        boolean down = InputConstants.isKeyDown(minecraft.getWindow().getWindow(), DUPE.getKey().getValue());
        boolean shift = down && Screen.hasShiftDown();
        boolean ctrl = down && Screen.hasControlDown();

        if (down && !wasDown) {
            if (ctrl) {
                createFromClipboard(minecraft);
            } else {
                transfer(minecraft);
            }
            repeatCountdown = REPEAT_TICKS;
        } else if (shift && !ctrl && --repeatCountdown <= 0) {
            transfer(minecraft);
            repeatCountdown = REPEAT_TICKS;
        }
        wasDown = down;
    }

    private static void transfer(Minecraft minecraft) {
        StorageContainerMenuBase<?> menu = menuOf(minecraft);
        if (menu == null) {
            return;
        }
        int sent = 0;
        for (Slot slot : menu.upgradeSlots) {
            if (slot instanceof IFilterSlot && slot.hasItem() && slot.isActive()) {
                PacketDistributor.sendToServer(new TransferFullSlotPayload(slot.index));
                sent++;
            }
        }
        message(minecraft, sent == 0
                ? "§c[bpdupe] no filter slot holding a sample (open the upgrade's filter tab and click an item in)"
                : "§a[bpdupe] sent transfer_full_slot x" + sent);
    }

    private static void createFromClipboard(Minecraft minecraft) {
        StorageContainerMenuBase<?> menu = menuOf(minecraft);
        if (menu == null) {
            return;
        }
        Slot filter = null;
        for (Slot slot : menu.upgradeSlots) {
            if (slot instanceof IFilterSlot && slot.isActive()) {
                filter = slot;
                break;
            }
        }
        if (filter == null) {
            message(minecraft, "§c[bpdupe] no filter slot in this menu");
            return;
        }

        String text = minecraft.keyboardHandler.getClipboard();
        ResourceLocation id = text == null ? null : ResourceLocation.tryParse(text.trim());
        Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            message(minecraft, "§c[bpdupe] clipboard is not a valid item id (e.g. minecraft:diamond)");
            return;
        }

        ItemStack sample = new ItemStack(item);
        try {
            PacketDistributor.sendToServer(new SetGhostSlotPayload(sample, filter.index));
            PacketDistributor.sendToServer(new TransferFullSlotPayload(filter.index));
            message(minecraft, "§a[bpdupe] set_ghost_slot + transfer_full_slot -> " + id);
        } catch (RuntimeException e) {
            message(minecraft, "§c[bpdupe] set_ghost_slot is not registered here (server needs JEI/EMI/REI)");
        }
    }

    private static StorageContainerMenuBase<?> menuOf(Minecraft minecraft) {
        if (!(minecraft.player.containerMenu instanceof StorageContainerMenuBase<?> menu)) {
            message(minecraft, "§e[bpdupe] open a Sophisticated backpack first");
            return null;
        }
        return menu;
    }

    private static void message(Minecraft minecraft, String text) {
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.literal(text), true);
        }
    }
}
