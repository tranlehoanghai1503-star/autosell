package com.hai.selldonutsmp.modules;

import com.hai.selldonutsmp.SellDonutSMPAddon;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Freecam;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.block.ChestBlock;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

import java.util.regex.Pattern;

public class SellAxeModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDeath = settings.createGroup("Khi chet");
    private final SettingGroup sgRestart = settings.createGroup("Khi restart region");
    private final SettingGroup sgIdle = settings.createGroup("Chong dung yen");

    // ---------- Chung ----------
    private final Setting<Boolean> sneak = sgGeneral.add(new BoolSetting.Builder()
        .name("giu-sneak")
        .description("Giu phim sneak o buoc dau tien.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> stepDelay = sgGeneral.add(new IntSetting.Builder()
        .name("tre-moi-buoc-tick")
        .description("Khoang cach giua cac buoc: nhin len, giu chuot phai, giu chuot trai (10 tick = 0,5 giay).")
        .defaultValue(10)
        .min(1)
        .sliderMax(40)
        .build()
    );

    private final Setting<Integer> reconnectDelay = sgGeneral.add(new IntSetting.Builder()
        .name("cho-sau-khi-vao-lai-giay")
        .description("Sau khi vao lai server (hoac het restart region) tat bao nhieu giay roi moi chay lai.")
        .defaultValue(5)
        .min(0)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> homeOnStart = sgGeneral.add(new BoolSetting.Builder()
        .name("tu-home-khi-bat")
        .description("Moi lan bat SellAxe (hoac chay lai sau khi vao lai server) se tu go /home truoc khi bat dau.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> homeName = sgGeneral.add(new StringSetting.Builder()
        .name("ten-home")
        .description("Ten home dung cho moi lenh /home cua SellAxe (vi du: 1).")
        .defaultValue("1")
        .build()
    );

    private final Setting<Integer> homeWait = sgGeneral.add(new IntSetting.Builder()
        .name("cho-sau-home-giay")
        .description("Sau khi go /home, cho bao nhieu giay cho dich chuyen xong roi moi chay.")
        .defaultValue(5)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Boolean> workInFreecam = sgGeneral.add(new BoolSetting.Builder()
        .name("chay-khi-bat-freecam")
        .description("Khi bat Freecam cua Meteor, SellAxe van chat/an theo huong nhin cua nhan vat (khong theo camera).")
        .defaultValue(true)
        .build()
    );

    // ---------- Khi chet ----------
    private final Setting<Boolean> deathHandling = sgDeath.add(new BoolSetting.Builder()
        .name("tu-xu-ly-khi-chet")
        .description("Khi nhan vat chet (can bat AutoRespawn cua Meteor): tu /home roi tat va bat lai SellAxe.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> deathHomeDelay = sgDeath.add(new IntSetting.Builder()
        .name("cho-home-sau-khi-chet-giay")
        .description("Sau khi chet bao nhieu giay thi go /home (tinh tu luc chet).")
        .defaultValue(5)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> deathRestartDelay = sgDeath.add(new IntSetting.Builder()
        .name("bat-lai-sau-home-giay")
        .description("Sau khi go /home, SellAxe tat va bat lai sau bao nhieu giay.")
        .defaultValue(3)
        .min(1)
        .sliderMax(30)
        .build()
    );

    // ---------- Khi restart region ----------
    private final Setting<String> restartBarRegex = sgRestart.add(new StringSetting.Builder()
        .name("regex-dong-chu-hotbar")
        .description("Regex nhan dong chu tren thanh hotbar khi server dang restart hoac xep hang.")
        .defaultValue("(?i)(restart|updat|queue|position|slot|region|server)")
        .build()
    );

    private final Setting<Integer> barGoneSeconds = sgRestart.add(new IntSetting.Builder()
        .name("hotbar-mat-chu-giay")
        .description("Dong chu tren hotbar bien mat lien tuc bao nhieu giay thi coi la restart xong.")
        .defaultValue(3)
        .min(1)
        .sliderMax(15)
        .build()
    );

    private final Setting<Integer> restartFallback = sgRestart.add(new IntSetting.Builder()
        .name("cho-du-phong-giay")
        .description("Neu khong thay dong chu nao tren hotbar thi cho bao nhieu giay roi chay lai.")
        .defaultValue(30)
        .min(5)
        .sliderMax(120)
        .build()
    );

    private final Setting<Integer> restartMaxWait = sgRestart.add(new IntSetting.Builder()
        .name("cho-toi-da-giay")
        .description("Cho toi da bao nhieu giay (phong khi dong chu khong bao gio mat).")
        .defaultValue(600)
        .min(30)
        .sliderMax(1800)
        .build()
    );

    private final Setting<Boolean> debug = sgRestart.add(new BoolSetting.Builder()
        .name("go-loi-hotbar")
        .description("In ra cac dong chu tren hotbar nhan duoc trong luc restart (de chinh regex).")
        .defaultValue(false)
        .build()
    );

    // ---------- Chong dung yen ----------
    private final Setting<Boolean> autoHome = sgIdle.add(new BoolSetting.Builder()
        .name("tu-home-khi-dung-yen")
        .description("Dung yen qua lau thi tu /home.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> idleSeconds = sgIdle.add(new IntSetting.Builder()
        .name("dung-yen-bao-lau-giay")
        .description("Dung yen bao nhieu giay thi /home.")
        .defaultValue(120)
        .min(10)
        .sliderMax(600)
        .build()
    );

    private final Setting<Boolean> noChestHome = sgIdle.add(new BoolSetting.Builder()
        .name("tu-home-khi-khong-chat-ruong")
        .description("Qua lau khong chat duoc ruong thi tu /home.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> noChestSeconds = sgIdle.add(new IntSetting.Builder()
        .name("khong-chat-ruong-bao-lau-giay")
        .description("Bao nhieu giay khong chat ruong thi /home.")
        .defaultValue(120)
        .min(10)
        .sliderMax(600)
        .build()
    );

    private final Setting<Boolean> anyBlock = sgIdle.add(new BoolSetting.Builder()
        .name("tinh-ca-block-khac")
        .description("Tinh ca block khac (barrel, shulker...) la dang chat, khong chi ruong.")
        .defaultValue(false)
        .build()
    );

    // 0 = cho (hoac cho /home), 1 = khoa nhin len (+sneak), 2 = giu chuot phai, 3 = giu ca chuot trai
    private int stage = 0;
    private int timer = 0;
    private boolean pendingHome = false;
    private boolean skipHomeOnce = false;

    private volatile int tick = 0;
    private volatile boolean restartMode = false;
    private volatile boolean barSeen = false;
    private volatile int lastBarTick = 0;
    private int restartTicks = 0;

    // Xu ly khi chet
    private boolean deathMode = false;
    private boolean wasDead = false;
    private boolean homeSentAfterDeath = false;
    private int deathTicks = 0;
    private int postHomeTicks = 0;

    private Object lastWorld = null;
    private Vec3d idleAnchor = null;
    private int idleTicks = 0;
    private int noChestTicks = 0;
    private boolean breakingChest = false;

    // Dong bo camera Freecam voi nhan vat trong luc game xu ly tick
    private Freecam syncedFreecam = null;
    private double savedX, savedY, savedZ;
    private float savedYaw, savedPitch;

    private String lastRegex = null;
    private Pattern lastPattern = null;

    public SellAxeModule() {
        super(SellDonutSMPAddon.CATEGORY, "SellAxe", "Tu dong chat ruong: nhin len, giu chuot phai roi giu chuot trai.");
    }

    /** AutoPay goi truoc khi bat lai SellAxe de khong /home thua (dang dung dung cho roi). */
    public void skipHomeNextStart() {
        skipHomeOnce = true;
    }

    @Override
    public void onActivate() {
        lastWorld = mc.world;
        restartMode = false;
        deathMode = false;
        wasDead = false;

        boolean justJoined = mc.player == null || mc.player.age < 40;
        boolean goHome = !skipHomeOnce;
        skipHomeOnce = false;
        start(justJoined ? reconnectDelay.get() * 20 : 0, goHome);
    }

    @Override
    public void onDeactivate() {
        restoreFreecam();
        releaseKeys();
        stage = 0;
        pendingHome = false;
        restartMode = false;
        deathMode = false;
    }

    private void start(int delayTicks, boolean goHome) {
        releaseKeys();
        stage = 0;
        timer = delayTicks;
        pendingHome = goHome && homeOnStart.get();
        idleAnchor = null;
        idleTicks = 0;
        noChestTicks = 0;
        breakingChest = false;
    }

    private void releaseKeys() {
        mc.options.sneakKey.setPressed(false);
        mc.options.useKey.setPressed(false);
        mc.options.attackKey.setPressed(false);
    }

    private void sendHomeCommand() {
        String name = homeName.get().trim();
        ChatUtils.sendPlayerMsg(name.isEmpty() ? "/home" : "/home " + name);
    }

    private Pattern barPattern() {
        String r = restartBarRegex.get();
        if (!r.equals(lastRegex)) {
            try {
                lastPattern = Pattern.compile(r);
            } catch (Exception e) {
                warning("Regex dong chu hotbar khong hop le.");
                lastPattern = Pattern.compile("a^");
            }
            lastRegex = r;
        }
        return lastPattern;
    }

    private void enterRestart() {
        if (restartMode || deathMode) return;
        releaseKeys();
        stage = 0;
        timer = 0;
        pendingHome = false;
        idleAnchor = null;
        idleTicks = 0;
        noChestTicks = 0;
        breakingChest = false;
        restartTicks = 0;
        barSeen = false;
        lastBarTick = tick;
        restartMode = true;
        info("Phat hien restart region, tam dung SellAxe.");
    }

    private void enterDeath() {
        releaseKeys();
        stage = 0;
        timer = 0;
        pendingHome = false;
        idleAnchor = null;
        idleTicks = 0;
        noChestTicks = 0;
        breakingChest = false;
        restartMode = false;
        deathMode = true;
        deathTicks = 0;
        postHomeTicks = 0;
        homeSentAfterDeath = false;
        info("Nhan vat da chet, tam dung SellAxe.");
    }

    // ---------- Su kien ----------

    // Vao lai server: tat (nha phim), cho roi /home va chay lai
    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        restartMode = false;
        deathMode = false;
        wasDead = false;
        lastWorld = mc.world;
        start(reconnectDelay.get() * 20, true);
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        releaseKeys();
        stage = 0;
        pendingHome = false;
        restartMode = false;
        deathMode = false;
        lastWorld = null;
    }

    // Dong canh bao restart region trong chat
    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString().toLowerCase();
        if (msg.contains("server are updating") || msg.contains("server is updating")) {
            enterRestart();
        }
    }

    // Dong chu tren thanh hotbar (action bar)
    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!restartMode) return;

        Text text = null;
        if (event.packet instanceof OverlayMessageS2CPacket p) text = p.text();
        else if (event.packet instanceof GameMessageS2CPacket p && p.overlay()) text = p.content();
        if (text == null) return;

        String s = text.getString();
        if (debug.get()) mc.execute(() -> info("[hotbar] %s", s));

        if (barPattern().matcher(s).find()) {
            barSeen = true;
            lastBarTick = tick;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tick++;
        if (mc.player == null || mc.world == null) return;

        // ----- Chet: /home sau 5 giay roi tat/bat lai SellAxe -----
        boolean dead = mc.player.isDead() || mc.currentScreen instanceof DeathScreen;
        if (deathHandling.get()) {
            if (dead && !wasDead) enterDeath();
            wasDead = dead;

            if (deathMode) {
                handleDeath(dead);
                return;
            }
        } else {
            wasDead = dead;
            if (deathMode) {
                deathMode = false;
                start(0, false);
            }
        }

        // Doi world ma khong co su kien join (bi chuyen region) -> tat roi chay lai
        if (mc.world != lastWorld) {
            boolean first = lastWorld == null;
            lastWorld = mc.world;
            idleAnchor = null;
            idleTicks = 0;
            if (!first && !restartMode) start(reconnectDelay.get() * 20, true);
        }

        // Dang cho restart region: doi dong chu tren hotbar bien mat
        if (restartMode) {
            restartTicks++;
            boolean barGone = barSeen && (tick - lastBarTick) >= barGoneSeconds.get() * 20;
            boolean noBar = !barSeen && restartTicks >= restartFallback.get() * 20;
            boolean tooLong = restartTicks >= restartMaxWait.get() * 20;
            if (barGone || noBar || tooLong) {
                restartMode = false;
                info("Restart xong, SellAxe se chay lai.");
                start(reconnectDelay.get() * 20, true);
            }
            return;
        }

        if (stage >= 1) mc.player.setPitch(-90f);

        if (timer > 0) {
            timer--;
            return;
        }

        switch (stage) {
            case 0 -> {
                if (pendingHome) {
                    pendingHome = false;
                    sendHomeCommand();
                    info("Tu /home truoc khi chay.");
                    timer = homeWait.get() * 20;
                } else {
                    mc.player.setPitch(-90f);
                    if (sneak.get()) mc.options.sneakKey.setPressed(true);
                    stage = 1;
                    timer = stepDelay.get();
                }
            }
            case 1 -> {
                mc.options.useKey.setPressed(true);
                stage = 2;
                timer = stepDelay.get();
            }
            case 2 -> {
                mc.options.attackKey.setPressed(true);
                stage = 3;
            }
            case 3 -> {
                if (sneak.get()) mc.options.sneakKey.setPressed(true);
                mc.options.useKey.setPressed(true);
                mc.options.attackKey.setPressed(true);
                checkIdle();
                checkChest();
            }
        }
    }

    // Sau khi chet: du thoi gian + da hoi sinh -> /home; sau do cho roi chay lai
    private void handleDeath(boolean dead) {
        deathTicks++;
        if (dead) return; // chua hoi sinh (doi AutoRespawn)

        if (!homeSentAfterDeath) {
            if (deathTicks >= deathHomeDelay.get() * 20) {
                sendHomeCommand();
                homeSentAfterDeath = true;
                postHomeTicks = 0;
                info("Da /home sau khi chet, SellAxe se bat lai sau " + deathRestartDelay.get() + " giay.");
            }
            return;
        }

        if (++postHomeTicks >= deathRestartDelay.get() * 20) {
            deathMode = false;
            start(0, false); // da /home roi nen khong /home lan nua
        }
    }

    // Freecam cua Meteor lam game ngam/chat theo camera chu khong theo nhan vat.
    // Dau moi tick: tam dua camera Freecam ve mat + huong nhin cua nhan vat de game
    // tinh tam ngam dung; cuoi tick: tra camera ve cho cu cho ban tiep tuc xem.
    @EventHandler(priority = EventPriority.LOWEST)
    private void onTickSync(TickEvent.Pre event) {
        restoreFreecam();

        if (!workInFreecam.get() || mc.player == null || mc.world == null) return;
        if (stage < 1 || restartMode || deathMode) return;

        Freecam fc = Modules.get().get(Freecam.class);
        if (fc == null || !fc.isActive()) return;

        savedX = fc.pos.x;
        savedY = fc.pos.y;
        savedZ = fc.pos.z;
        savedYaw = fc.yaw;
        savedPitch = fc.pitch;

        Vec3d eye = mc.player.getEyePos();
        fc.pos.set(eye.x, eye.y, eye.z);
        fc.yaw = mc.player.getYaw();
        fc.pitch = mc.player.getPitch();
        syncedFreecam = fc;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onTickRestore(TickEvent.Post event) {
        restoreFreecam();
    }

    private void restoreFreecam() {
        if (syncedFreecam == null) return;
        syncedFreecam.pos.set(savedX, savedY, savedZ);
        syncedFreecam.yaw = savedYaw;
        syncedFreecam.pitch = savedPitch;
        syncedFreecam = null;
    }

    // Dung yen qua lau (bi ket cuoi duong nuoc) -> /home
    private void checkIdle() {
        if (!autoHome.get()) {
            idleAnchor = null;
            idleTicks = 0;
            return;
        }

        Vec3d pos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (idleAnchor == null || pos.squaredDistanceTo(idleAnchor) > 1.0) {
            idleAnchor = pos;
            idleTicks = 0;
            return;
        }

        if (++idleTicks >= idleSeconds.get() * 20) {
            sendHome("Dung yen qua lau, tu /home.");
        }
    }

    // Kiem tra co dang chat ruong khong (dang dap block va tia ngam cua nhan vat tro vao ruong)
    private boolean isBreakingChest() {
        if (mc.interactionManager == null || mc.world == null) return false;
        if (!mc.interactionManager.isBreakingBlock()) return false;
        HitResult target = mc.player.raycast(4.5, 1.0F, false);
        if (!(target instanceof BlockHitResult hit)) return false;
        if (hit.getType() != HitResult.Type.BLOCK) return false;

        var block = mc.world.getBlockState(hit.getBlockPos()).getBlock();
        return anyBlock.get() || block instanceof ChestBlock;
    }

    // Qua lau khong chat ruong -> /home
    private void checkChest() {
        breakingChest = isBreakingChest();

        if (!noChestHome.get() || breakingChest) {
            noChestTicks = 0;
            return;
        }

        if (++noChestTicks >= noChestSeconds.get() * 20) {
            sendHome("Khong chat duoc ruong trong " + noChestSeconds.get() + " giay, tu /home.");
        }
    }

    private void sendHome(String reason) {
        sendHomeCommand();
        info("%s", reason);
        idleAnchor = null;
        idleTicks = 0;
        noChestTicks = 0;
    }

    // Hien trang thai ngay trong danh sach module cua Meteor
    @Override
    public String getInfoString() {
        if (deathMode) return "Vua chet";
        if (restartMode) return "Restart region";
        if (stage < 3) return "Dang khoi dong";
        return breakingChest ? "Dang chat ruong" : "Khong chat";
    }
}
