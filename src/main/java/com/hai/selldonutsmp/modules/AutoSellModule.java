package com.hai.selldonutsmp.modules;

import com.hai.selldonutsmp.SellDonutSMPAddon;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AutoSellModule extends Module {
    public enum Mode {
        CO_DINH("Setting 1 - Tick co dinh"),
        NGAU_NHIEN("Setting 2 - 4 chu ky ngau nhien");

        private final String title;

        Mode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    private enum State { IDLE, WAIT_GUI, PICK, PLACE, AFTER_CONFIRM }

    private static final Random RNG = new Random();
    private static final int MAX_ROUNDS = 10;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCycles = settings.createGroup("Setting 2 - 4 chu ky");
    private final SettingGroup sgSell = settings.createGroup("Cho /sell");
    private final SettingGroup sgHuman = settings.createGroup("Chong flag");

    // ---------- Chung ----------
    private final Setting<List<Item>> items = sgGeneral.add(new ItemListSetting.Builder()
        .name("chon-item")
        .description("Chi nhung item nay moi duoc day len /sell (de khong ban nham do khac).")
        .defaultValue(new ArrayList<>())
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("che-do")
        .description("Setting 1: tick co dinh. Setting 2: 4 chu ky tick rieng, lap lai lien tuc.")
        .defaultValue(Mode.CO_DINH)
        .build()
    );

    private final Setting<Integer> fixedTicks = sgGeneral.add(new IntSetting.Builder()
        .name("setting-1-tick-day-do")
        .description("Cu bao nhieu tick thi day het item da chon len /sell (20 tick = 1 giay).")
        .defaultValue(200)
        .min(1)
        .sliderMax(1200)
        .build()
    );

    private final Setting<Integer> minStacks = sgGeneral.add(new IntSetting.Builder()
        .name("toi-thieu-o-item")
        .description("Trong tui do phai co it nhat bay nhieu o item da chon thi moi mo /sell.")
        .defaultValue(1)
        .min(1)
        .sliderMax(36)
        .build()
    );

    // ---------- Setting 2: 4 chu ky ----------
    private final Setting<Integer> cycle1 = cycleSetting("chu-ky-1-tick");
    private final Setting<Integer> cycle2 = cycleSetting("chu-ky-2-tick");
    private final Setting<Integer> cycle3 = cycleSetting("chu-ky-3-tick");
    private final Setting<Integer> cycle4 = cycleSetting("chu-ky-4-tick");

    private final Setting<Boolean> reroll = sgCycles.add(new BoolSetting.Builder()
        .name("random-lai-4-chu-ky")
        .description("Bat len de random lai 4 chu ky tu 10 den 100 tick (tu tat sau khi random).")
        .defaultValue(false)
        .build()
    );

    // ---------- Cho /sell ----------
    private final Setting<String> sellCommand = sgSell.add(new StringSetting.Builder()
        .name("lenh-sell")
        .description("Lenh mo giao dien ban do.")
        .defaultValue("/sell")
        .build()
    );

    private final Setting<Integer> topSlots = sgSell.add(new IntSetting.Builder()
        .name("so-o-tren-dung-duoc")
        .description("So o phia tren cua /sell dung duoc (DonutSMP: 45, hang duoi khong tinh).")
        .defaultValue(45)
        .min(1)
        .sliderMax(54)
        .build()
    );

    private final Setting<Integer> confirmSlot = sgSell.add(new IntSetting.Builder()
        .name("o-xac-nhan")
        .description("O de bam xac nhan ban (tinh tu 1). DonutSMP: o 54 la kinh xanh lime.")
        .defaultValue(54)
        .min(1)
        .sliderMax(54)
        .build()
    );

    private final Setting<Boolean> confirmWhenFull = sgSell.add(new BoolSetting.Builder()
        .name("bam-xac-nhan-khi-day")
        .description("Khi day het cac o phia tren thi tu bam o xac nhan.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> confirmWhenDone = sgSell.add(new BoolSetting.Builder()
        .name("bam-xac-nhan-khi-het-do")
        .description("Khi da day het item da chon (chua day o) cung tu bam o xac nhan.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> checkLime = sgSell.add(new BoolSetting.Builder()
        .name("chi-bam-neu-la-kinh-lime")
        .description("Chi bam o xac nhan neu dung la kinh xanh lime (tranh bam nham).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> waitAfterConfirm = sgSell.add(new IntSetting.Builder()
        .name("cho-sau-khi-bam-xac-nhan-tick")
        .description("Cho bao nhieu tick sau khi bam xac nhan roi moi lam tiep.")
        .defaultValue(15)
        .min(1)
        .sliderMax(100)
        .build()
    );

    private final Setting<Integer> waitGuiSeconds = sgSell.add(new IntSetting.Builder()
        .name("cho-mo-gui-toi-da-giay")
        .description("Gui lenh /sell roi cho toi da bao nhieu giay de giao dien mo ra.")
        .defaultValue(5)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Boolean> closeAfter = sgSell.add(new BoolSetting.Builder()
        .name("dong-gui-sau-khi-ban")
        .description("Ban xong thi tu dong giao dien /sell.")
        .defaultValue(true)
        .build()
    );

    // ---------- Chong flag ----------
    private final Setting<Integer> minDelay = sgHuman.add(new IntSetting.Builder()
        .name("tre-thao-tac-toi-thieu-tick")
        .description("Moi lan bam o trong /sell se cho ngau nhien tu muc nay...")
        .defaultValue(2)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> maxDelay = sgHuman.add(new IntSetting.Builder()
        .name("tre-thao-tac-toi-da-tick")
        .description("...den muc nay (tick).")
        .defaultValue(5)
        .min(1)
        .sliderMax(40)
        .build()
    );

    private final Setting<Boolean> debug = sgHuman.add(new BoolSetting.Builder()
        .name("go-loi")
        .description("In ra cac buoc AutoSell dang lam.")
        .defaultValue(false)
        .build()
    );

    private State state = State.IDLE;
    private int wait = 100;      // dem nguoc den lan ban ke tiep
    private int timer = 0;       // dem nguoc giua cac buoc
    private int cycleIdx = 0;
    private int pickedFrom = -1;
    private int pushed = 0;
    private int rounds = 0;
    private boolean warnedEmpty = false;

    public AutoSellModule() {
        super(SellDonutSMPAddon.CATEGORY, "AutoSell", "Tu dong /sell: day het item da chon len giao dien ban theo chu ky.");
    }

    private Setting<Integer> cycleSetting(String name) {
        return sgCycles.add(new IntSetting.Builder()
            .name(name)
            .description("So tick cho chu ky nay (20 tick = 1 giay). Mac dinh random tu 10 den 100 luc moi cai dat.")
            .defaultValue(10 + RNG.nextInt(91))
            .min(1)
            .sliderMax(1200)
            .build()
        );
    }

    /** AutoPay goi ham nay de khong pay giua luc dang ban do. */
    public boolean isBusy() {
        return state != State.IDLE;
    }

    @Override
    public void onActivate() {
        state = State.IDLE;
        rounds = 0;
        warnedEmpty = false;
        wait = nextInterval();
    }

    @Override
    public void onDeactivate() {
        if (state != State.IDLE && mc.player != null && mc.currentScreen instanceof GenericContainerScreen) {
            mc.player.closeHandledScreen();
        }
        state = State.IDLE;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        state = State.IDLE;
        rounds = 0;
        wait = Math.max(100, nextInterval());
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        state = State.IDLE;
    }

    // ---------- Lich ----------

    private int nextInterval() {
        if (mode.get() == Mode.CO_DINH) return Math.max(1, fixedTicks.get());
        int v = switch (cycleIdx) {
            case 0 -> cycle1.get();
            case 1 -> cycle2.get();
            case 2 -> cycle3.get();
            default -> cycle4.get();
        };
        cycleIdx = (cycleIdx + 1) % 4;
        return Math.max(1, v);
    }

    private int humanDelay() {
        int lo = Math.max(1, minDelay.get());
        int hi = Math.max(lo, maxDelay.get());
        return lo + RNG.nextInt(hi - lo + 1);
    }

    private void rerollCycles() {
        cycle1.set(10 + RNG.nextInt(91));
        cycle2.set(10 + RNG.nextInt(91));
        cycle3.set(10 + RNG.nextInt(91));
        cycle4.set(10 + RNG.nextInt(91));
        info("Da random lai 4 chu ky: " + cycle1.get() + ", " + cycle2.get() + ", " + cycle3.get() + ", " + cycle4.get() + " tick.");
    }

    // ---------- Tien trinh ----------

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        if (reroll.get()) {
            rerollCycles();
            reroll.set(false);
        }

        switch (state) {
            case IDLE -> idle();
            case WAIT_GUI -> waitGui();
            case PICK -> {
                if (--timer <= 0) pick();
            }
            case PLACE -> {
                if (--timer <= 0) place();
            }
            case AFTER_CONFIRM -> {
                if (--timer <= 0) afterConfirm();
            }
        }
    }

    private void idle() {
        if (--wait > 0) return;

        AutoPayModule pay = Modules.get().get(AutoPayModule.class);
        if (pay != null && pay.isBusy()) {
            wait = 20; // dang pay thi doi
            return;
        }
        if (mc.currentScreen != null) {
            wait = 20; // dang mo giao dien khac
            return;
        }

        if (items.get().isEmpty()) {
            if (!warnedEmpty) {
                warning("Chua chon item nao trong chon-item, AutoSell khong ban gi ca.");
                warnedEmpty = true;
            }
            wait = nextInterval();
            return;
        }
        warnedEmpty = false;

        if (countSelected() < minStacks.get()) {
            wait = nextInterval();
            return;
        }

        String cmd = sellCommand.get().trim();
        if (!cmd.startsWith("/")) cmd = "/" + cmd;
        ChatUtils.sendPlayerMsg(cmd);
        if (debug.get()) info("Gui lenh " + cmd);

        state = State.WAIT_GUI;
        timer = waitGuiSeconds.get() * 20;
        pushed = 0;
    }

    private void waitGui() {
        if (sellHandler() != null) {
            state = State.PICK;
            timer = humanDelay();
            return;
        }
        if (--timer <= 0) {
            warning("Khong mo duoc giao dien /sell, bo qua luot nay.");
            state = State.IDLE;
            wait = nextInterval();
        }
    }

    // Nhat 1 stack item da chon trong tui do
    private void pick() {
        GenericContainerScreenHandler h = sellHandler();
        if (h == null) {
            finish();
            return;
        }

        int src = findSelected(h);
        if (src < 0 || firstEmptyTop(h) < 0) {
            donePushing(h);
            return;
        }

        click(h, src);
        pickedFrom = src;
        state = State.PLACE;
        timer = humanDelay();
    }

    // Tha stack do vao o trong phia tren
    private void place() {
        GenericContainerScreenHandler h = sellHandler();
        if (h == null) {
            finish();
            return;
        }

        if (h.getCursorStack().isEmpty()) { // nhat that bai -> thu lai
            state = State.PICK;
            timer = humanDelay();
            return;
        }

        int dst = firstEmptyTop(h);
        if (dst < 0) { // het cho -> tra lai tui do
            click(h, pickedFrom);
            donePushing(h);
            return;
        }

        click(h, dst);
        pushed++;
        if (debug.get()) info("Day len o " + (dst + 1) + " (da day " + pushed + " o).");
        state = State.PICK;
        timer = humanDelay();
    }

    private void donePushing(GenericContainerScreenHandler h) {
        boolean full = firstEmptyTop(h) < 0;
        boolean noMore = findSelected(h) < 0;
        boolean confirm = (full && confirmWhenFull.get()) || (noMore && pushed > 0 && confirmWhenDone.get());

        if (!confirm) {
            finish();
            return;
        }

        int idx = confirmSlot.get() - 1;
        if (idx < 0 || idx >= topSize(h)) {
            warning("O xac nhan khong hop le.");
            finish();
            return;
        }
        if (checkLime.get() && !h.getSlot(idx).getStack().isOf(Items.LIME_STAINED_GLASS_PANE)) {
            warning("O so " + confirmSlot.get() + " khong phai kinh xanh lime, khong bam.");
            finish();
            return;
        }

        click(h, idx);
        if (debug.get()) info("Bam o xac nhan " + confirmSlot.get() + ".");
        rounds++;
        state = State.AFTER_CONFIRM;
        timer = waitAfterConfirm.get();
    }

    private void afterConfirm() {
        GenericContainerScreenHandler h = sellHandler();

        if (h == null) { // giao dien tu dong sau khi ban
            if (countSelected() > 0 && rounds < MAX_ROUNDS) {
                state = State.IDLE; // con do -> mo lai /sell sau 1 giay
                wait = 20;
            } else {
                state = State.IDLE;
                wait = nextInterval();
                rounds = 0;
            }
            pushed = 0;
            return;
        }

        if (findSelected(h) >= 0 && firstEmptyTop(h) >= 0 && rounds < MAX_ROUNDS) {
            pushed = 0;
            state = State.PICK;
            timer = humanDelay();
        } else {
            finish();
        }
    }

    private void finish() {
        if (closeAfter.get() && mc.player != null && mc.currentScreen instanceof GenericContainerScreen) {
            mc.player.closeHandledScreen();
        }
        state = State.IDLE;
        wait = nextInterval();
        rounds = 0;
        pushed = 0;
        pickedFrom = -1;
    }

    // ---------- Tien ich ----------

    private GenericContainerScreenHandler sellHandler() {
        if (mc.currentScreen instanceof GenericContainerScreen s && s.getScreenHandler().getRows() == 6) {
            return s.getScreenHandler();
        }
        return null;
    }

    private int topSize(GenericContainerScreenHandler h) {
        return h.getRows() * 9;
    }

    private int firstEmptyTop(GenericContainerScreenHandler h) {
        int limit = Math.min(topSlots.get(), topSize(h));
        for (int i = 0; i < limit; i++) {
            if (h.getSlot(i).getStack().isEmpty()) return i;
        }
        return -1;
    }

    private int findSelected(GenericContainerScreenHandler h) {
        for (int i = topSize(h); i < h.slots.size(); i++) {
            ItemStack s = h.getSlot(i).getStack();
            if (!s.isEmpty() && items.get().contains(s.getItem())) return i;
        }
        return -1;
    }

    // Dem so o item da chon trong tui do (o 9-35 la tui, 36-44 la hotbar)
    private int countSelected() {
        int n = 0;
        for (int i = 9; i <= 44; i++) {
            ItemStack s = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (!s.isEmpty() && items.get().contains(s.getItem())) n++;
        }
        return n;
    }

    private void click(GenericContainerScreenHandler h, int slot) {
        mc.interactionManager.clickSlot(h.syncId, slot, 0, SlotActionType.PICKUP, mc.player);
    }
}
