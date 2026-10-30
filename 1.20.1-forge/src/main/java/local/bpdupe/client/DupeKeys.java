package local.bpdupe.client;

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
import net.minecraftforge.event.TickEvent;
import net.p3pp3rf1y.sophisticatedcore.common.gui.IFilterSlot;
import net.p3pp3rf1y.sophisticatedcore.common.gui.StorageContainerMenuBase;
import net.p3pp3rf1y.sophisticatedcore.compat.recipeviewers.common.SetGhostSlotMessage;
import net.p3pp3rf1y.sophisticatedcore.network.PacketHandler;
import net.p3pp3rf1y.sophisticatedcore.network.TransferFullSlotMessage;
import org.lwjgl.glfw.GLFW;

/**
 * Raw GLFW polling on purpose: vanilla only feeds {@link KeyMapping} state while no screen is open,
 * so a normal binding can never fire inside a container GUI. The key bound in Controls is still
 * honoured, it is just read directly.
 *
 * <ul>
 *   <li><b>K</b> - one {@code transfer_full_slot} per filter slot that holds a sample.</li>
 *   <li><b>Shift+K</b> (held) - same, every 3 ticks.</li>
 *   <li><b>Ctrl+K</b> - write the item id in the clipboard into the first filter slot as a ghost
 *       sample ({@code set_ghost_slot}) and immediately transfer it, i.e. create an item you never
 *       owned. Needs the server to have registered the recipe-viewer message (JEI/EMI/REI there).</li>
 * </ul>
 */
public final class DupeKeys {
    public static final KeyMapping DUPE =
            new KeyMapping("key.bpdupe.dupe", GLFW.GLFW_KEY_K, "key.categories.bpdupe");

    private static final int REPEAT_TICKS = 3;

    private static boolean wasDown;
    private static int repeatCountdown;

    private DupeKeys() {
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
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
                PacketHandler.INSTANCE.sendToServer(new TransferFullSlotMessage(slot.index));
                sent++;
            }
        }
        message(minecraft, sent == 0
                ? "§c[bpdupe] 没有装着样板的过滤槽（先打开带过滤槽的升级页，把物品点进过滤槽）"
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
            message(minecraft, "§c[bpdupe] 当前菜单里没有过滤槽");
            return;
        }

        String text = minecraft.keyboardHandler.getClipboard();
        ResourceLocation id = text == null ? null : ResourceLocation.tryParse(text.trim());
        Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            message(minecraft, "§c[bpdupe] 剪贴板里不是有效物品 ID（例： minecraft:diamond）");
            return;
        }

        ItemStack sample = new ItemStack(item, 1);
        try {
            PacketHandler.INSTANCE.sendToServer(new SetGhostSlotMessage(sample, filter.index));
            PacketHandler.INSTANCE.sendToServer(new TransferFullSlotMessage(filter.index));
            message(minecraft, "§a[bpdupe] set_ghost_slot + transfer_full_slot -> " + id);
        } catch (RuntimeException e) {
            message(minecraft, "§c[bpdupe] 服务端没有注册 set_ghost_slot（服务端需装 JEI/EMI/REI）");
        }
    }

    private static StorageContainerMenuBase<?> menuOf(Minecraft minecraft) {
        if (!(minecraft.player.containerMenu instanceof StorageContainerMenuBase<?> menu)) {
            message(minecraft, "§e[bpdupe] 先打开精妙背包");
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
