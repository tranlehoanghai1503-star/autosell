package com.hai.selldonutsmp.modules;

import com.hai.selldonutsmp.SellDonutSMPAddon;
import com.hai.selldonutsmp.util.Money;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoPayModule extends Module {
    private enum State { IDLE, STOPPING, WAIT_BAL, PAYING, RESTART_WAIT }

    private static final int RETRY_TICKS = 2 * 60 * 20; // thu lai sau 2 phut neu loi

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Nang cao");

    // ---------- Chung ----------
    private final Setting<String> username = sgGeneral.add(new StringSetting.Builder()
        .name("ten-nguoi-nhan")
        .description("Ten nguoi choi nhan tien.")
        .defaultValue("")
        .build()
    );

    private final Setting<Integer> intervalMinutes = sgGeneral.add(new IntSetting.Builder()
        .name("chu-ky-pay-phut")
        .description("Cu bao nhieu phut thi pay mot lan.")
        .defaultValue(60)
        .min(1)
        .sliderMax(240)
        .build()
    );

    private final Setting<Double> taxPercent = sgGeneral.add(new DoubleSetting.Builder()
        .name("tru-thue-phan-tram")
        .description("So phan tram tru di truoc khi /pay de chua cho cho thue (nen de 3 den 5).")
        .defaultValue(3.0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Double> minBalance = sgGeneral.add(new DoubleSetting.Builder()
        .name("so-du-toi-thieu")
        .description("So du phai lon hon muc nay thi moi pay.")
        .defaultValue(1000)
        .min(0)
        .sliderMax(1000000)
        .build()
    );

    // ---------- Nang cao ----------
    private final Setting<Integer> stopDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("tat-sellaxe-truoc-khi-bal-giay")
        .description("Tat SellAxe bao nhieu giay roi moi go /bal.")
        .defaultValue(5)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> resumeDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("bat-lai-sellaxe-sau-pay-giay")
        .description("Sau khi /pay bao nhieu giay thi bat lai SellAxe.")
        .defaultValue(5)
        .min(1)
        .sliderMax(30)
        .build()
    );

    private final Setting<Integer> failsafeSeconds = sgAdvanced.add(new IntSetting.Builder()
        .name("tu-bat-lai-sellaxe-sau-giay")
        .description("Neu SellAxe bi AutoPay tat qua so giay nay ma chua pay xong thi tu bat lai.")
        .defaultValue(60)
        .min(15)
        .sliderMax(300)
        .build()
    );

    private final Setting<Integer> restartWait = sgAdvanced.add(new IntSetting.Builder()
        .name("cho-khi-restart-region-giay")
        .description("Khi thay canh bao restart region giua luc pay, cho bao nhieu giay roi bat lai SellAxe.")
        .defaultValue(30)
        .min(5)
        .sliderMax(120)
        .build()
    );

    private final Setting<String> balanceRegex = sgAdvanced.add(new StringSetting.Builder()
        .name("regex-doc-so-du")
        .description("Regex doc dong chat tra ve cua /bal. Nhom so 1 phai la so tien.")
        .defaultValue("(?i)(?:balance|bal|money|coins)\\D{0,20}\\$?\\s?([\\d,]+(?:\\.\\d+)?[kmbt]?)(?![a-z])")
        .build()
    );

    private final Setting<Boolean> debug = sgAdvanced.add(new BoolSetting.Builder()
        .name("go-loi-chat")
        .description("In ra moi dong chat nhan duoc khi dang cho /bal (de chinh regex).")
        .defaultValue(false)
        .build()
    );

    private State state = State.IDLE;
    private int timer = 0;
    private int cycleTicks = -1;
    private int stoppedTicks = 0;
    private boolean stoppedByUs = false;
    private String lastRegex = null;
    private Pattern lastPattern = null;

    public AutoPayModule() {
        super(SellDonutSMPAddon.CATEGORY, "AutoPay", "Tu /pay toan bo tien (da tru thue) cho mot nguoi choi theo chu ky, tam dung SellAxe khi pay.");
    }

    @Override
    public void onActivate() {
        if (cycleTicks < 0) cycleTicks = intervalMinutes.get() * 60 * 20;
    }

    @Override
    public void onDeactivate() {
        resumeSellAxe(false);
        state = State.IDLE;
    }

    /** AutoSell dung ham nay de khong ban do giua luc dang pay. */
    public boolean isBusy() {
        return state != State.IDLE;
    }

    private Pattern balancePattern() {
        String r = balanceRegex.get();
        if (!r.equals(lastRegex)) {
            try {
                lastPattern = Pattern.compile(r);
            } catch (Exception e) {
                warning("Regex doc so du khong hop le.");
                lastPattern = Pattern.compile("a^");
            }
            lastRegex = r;
        }
        return lastPattern;
    }

    private SellAxeModule sellAxe() {
        return Modules.get().get(SellAxeModule.class);
    }

    // skipHome = true khi nhan vat van dung dung cho (pay xong binh thuong) -> SellAxe khong can /home
    private void resumeSellAxe(boolean skipHome) {
        if (!stoppedByUs) return;
        stoppedByUs = false;
        stoppedTicks = 0;
        SellAxeModule sa = sellAxe();
        if (sa != null && !sa.isActive()) {
            if (skipHome) sa.skipHomeNextStart();
            sa.toggle();
        }
    }

    private void abort(int nextCycleTicks, boolean skipHome) {
        resumeSellAxe(skipHome);
        state = State.IDLE;
        cycleTicks = nextCycleTicks;
    }

    private void beginPay() {
        if (username.get().isBlank()) {
            warning("Chua nhap ten nguoi nhan cho AutoPay.");
            cycleTicks = intervalMinutes.get() * 60 * 20;
            return;
        }
        SellAxeModule sa = sellAxe();
        if (sa != null && sa.isActive()) {
            sa.toggle();
            stoppedByUs = true;
        }
        stoppedTicks = 0;
        state = State.STOPPING;
        timer = stopDelay.get() * 20;
        info("Den chu ky pay, tam dung SellAxe.");
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        // Vao lai server giua chung -> huy luot pay dang do va bat lai SellAxe
        if (state != State.IDLE || stoppedByUs) abort(RETRY_TICKS, false);
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        String msg = event.getMessage().getString();
        String low = msg.toLowerCase();

        if (low.contains("server are updating") || low.contains("server is updating")) {
            if (state != State.IDLE || stoppedByUs) {
                state = State.RESTART_WAIT;
                timer = restartWait.get() * 20;
            }
            return;
        }

        if (state != State.WAIT_BAL) return;
        if (debug.get()) info("[chat] %s", msg);

        try {
            Matcher m = balancePattern().matcher(msg);
            if (!m.find()) return;

            double balance = Money.parse(m.group(1));
            if (balance < minBalance.get()) {
                info("So du " + Money.format(balance) + " qua thap, bo qua luot nay.");
                abort(intervalMinutes.get() * 60 * 20, true);
                return;
            }

            long pay = (long) Math.floor(balance * (1.0 - taxPercent.get() / 100.0));
            ChatUtils.sendPlayerMsg("/pay " + username.get() + " " + pay);
            info("Pay " + username.get() + " " + Money.format(pay) + " (so du " + Money.format(balance) + ").");

            state = State.PAYING;
            timer = resumeDelay.get() * 20;
        } catch (Exception e) {
            warning("Khong doc duoc so du: %s", String.valueOf(e.getMessage()));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Chot an toan: SellAxe bi minh tat qua lau -> bat lai
        if (stoppedByUs && ++stoppedTicks >= failsafeSeconds.get() * 20) {
            warning("Qua thoi gian cho, tu bat lai SellAxe.");
            abort(RETRY_TICKS, false);
            return;
        }

        int max = intervalMinutes.get() * 60 * 20;
        if (cycleTicks > max) cycleTicks = max;

        switch (state) {
            case IDLE -> {
                if (--cycleTicks <= 0) {
                    AutoSellModule sell = Modules.get().get(AutoSellModule.class);
                    if (sell != null && sell.isBusy()) cycleTicks = 20; // cho AutoSell ban xong roi moi pay
                    else beginPay();
                }
            }
            case STOPPING -> {
                if (--timer <= 0) {
                    ChatUtils.sendPlayerMsg("/bal");
                    state = State.WAIT_BAL;
                    timer = 10 * 20;
                }
            }
            case WAIT_BAL -> {
                if (--timer <= 0) {
                    warning("Khong nhan duoc so du tu /bal, bat lai SellAxe.");
                    abort(RETRY_TICKS, true);
                }
            }
            case PAYING -> {
                if (--timer <= 0) abort(max, true);
            }
            case RESTART_WAIT -> {
                if (--timer <= 0) abort(RETRY_TICKS, false);
            }
        }
    }
}
