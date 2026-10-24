package com.malek.upgraderwheel.client;

import com.malek.upgraderwheel.ModConfig;
import com.malek.upgraderwheel.inventory.ContainerUpgrader;
import com.malek.upgraderwheel.logic.Codes;
import com.malek.upgraderwheel.logic.UpgradeMath;
import com.malek.upgraderwheel.network.Network;
import com.malek.upgraderwheel.network.PacketRequestCatalog;
import com.malek.upgraderwheel.network.PacketRoll;
import com.malek.upgraderwheel.network.PacketRollResult;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.client.config.GuiButtonExt;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The upgrade wheel: bet slot on the left, the wheel in the middle, the chosen result on the right,
 * and the searchable result list in the right-hand panel.
 * Spin flow adapted from Item Upgrader by viniHNS (MIT): the needle spins freely until the server answers,
 * then eases out onto a point inside the winning or the losing arc.
 */
public class GuiUpgrader extends GuiContainer {

    // Window
    private static final int WIDTH = 320;
    private static final int HEIGHT = 190;
    private static final int LEFT_WIDTH = 176;
    private static final int RIGHT_X = 178;
    private static final int RIGHT_WIDTH = 142;

    // Left panel
    private static final int BET_X = ContainerUpgrader.BET_X;
    private static final int BET_Y = ContainerUpgrader.BET_Y;
    private static final int RESULT_X = 140;
    private static final int RESULT_Y = BET_Y;
    private static final int WHEEL_X = 88;
    private static final int WHEEL_Y = 44;
    private static final int WHEEL_R = 27;
    private static final int HUB_R = 15;
    private static final int STEP_Y = 63;
    private static final int SPIN_X = 58;
    private static final int SPIN_Y = 77;
    private static final int SPIN_W = 60;
    private static final int SPIN_H = 16;
    private static final int STATUS_Y = 97;

    // Right panel
    private static final int SEARCH_X = 185;
    private static final int SEARCH_Y = 8;
    private static final int SEARCH_W = 128;
    private static final int SEARCH_H = 12;
    private static final int FILTER_X = 184;
    private static final int FILTER_Y = 24;
    private static final int FILTER_W = 31;
    private static final int FILTER_GAP = 2;
    private static final int FILTER_H = 14;
    private static final int LIST_X = 184;
    private static final int LIST_Y = 42;
    private static final int LIST_W = 124;
    private static final int ROW_H = 18;
    private static final int ROWS = 7;
    private static final int SCROLL_X = LIST_X + LIST_W + 1;
    private static final int SCROLL_W = 5;
    private static final int COUNT_Y = LIST_Y + ROWS * ROW_H + 5;

    // Buttons
    private static final int BUTTON_FILTER = 10;
    private static final int BUTTON_MINUS = 20;
    private static final int BUTTON_PLUS = 21;
    private static final int BUTTON_SPIN = 30;

    private static final double[] BAND_CENTER = {75, 50, 25, 10};
    private static final double[] BAND_LOW = {62.5, 37.5, 17.5, 0};
    private static final double[] BAND_HIGH = {Double.MAX_VALUE, 62.5, 37.5, 17.5};

    private static final double FREE_SPIN_SPEED = 540;
    private static final long TIMEOUT_NANOS = 10_000_000_000L;
    private static final long CONFIRM_NANOS = 3_000_000_000L;
    private static final long FLASH_NANOS = 1_500_000_000L;

    private enum Phase {
        IDLE, ARMED, WAITING, LANDING
    }

    private final ContainerUpgrader container;
    private final int catalogVersion;
    private final Random random = new Random();

    private GuiTextField search;
    private final GuiButton[] filterButtons = new GuiButton[4];
    private GuiButton minusButton;
    private GuiButton plusButton;
    private GuiButton spinButton;

    private boolean loading;
    private boolean catalogRequested;
    private final List<ClientCatalog.Entry> visible = new ArrayList<>();
    private final List<Integer> visibleAmounts = new ArrayList<>();
    private int scroll;
    private boolean draggingScrollbar;
    private int filterBand = -1;
    private int seenBetRevision = Integer.MIN_VALUE;

    private ClientCatalog.Entry selected;
    private int amount = 1;

    private Phase phase = Phase.IDLE;
    private double angle;
    private int lastTick;
    private long lastFrameNanos = System.nanoTime();
    private long phaseStartNanos;
    private double landStart;
    private double landEnd;
    private boolean rollWon;
    private double spinChance;
    private double frozenBetValue;
    private ItemStack frozenBet = ItemStack.EMPTY;
    private ClientCatalog.Entry spinTarget;
    private int spinAmount;

    private String message;
    private int messageColor;
    private long flashUntilNanos;
    private boolean flashWon;

    public GuiUpgrader(ContainerUpgrader container, int catalogVersion) {
        super(container);
        this.container = container;
        this.catalogVersion = catalogVersion;
        this.xSize = WIDTH;
        this.ySize = HEIGHT;
    }

    // ------------------------------------------------------------------ setup

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        String previousSearch = search == null ? "" : search.getText();
        search = new GuiTextField(0, fontRenderer, guiLeft + SEARCH_X, guiTop + SEARCH_Y, SEARCH_W, SEARCH_H);
        search.setMaxStringLength(64);
        search.setText(previousSearch);

        buttonList.clear();
        String[] labels = {"~75%", "~50%", "~25%", "~10%"};
        for (int i = 0; i < 4; i++) {
            filterButtons[i] = new GuiButtonExt(BUTTON_FILTER + i, guiLeft + FILTER_X + i * (FILTER_W + FILTER_GAP), guiTop + FILTER_Y,
                    FILTER_W, FILTER_H, labels[i]);
            buttonList.add(filterButtons[i]);
        }
        minusButton = new GuiButtonExt(BUTTON_MINUS, guiLeft + RESULT_X - 14, guiTop + STEP_Y, 12, 12, "-");
        plusButton = new GuiButtonExt(BUTTON_PLUS, guiLeft + RESULT_X + 18, guiTop + STEP_Y, 12, 12, "+");
        spinButton = new GuiButtonExt(BUTTON_SPIN, guiLeft + SPIN_X, guiTop + SPIN_Y, SPIN_W, SPIN_H, "");
        buttonList.add(minusButton);
        buttonList.add(plusButton);
        buttonList.add(spinButton);

        if (ClientCatalog.isReady(catalogVersion)) {
            loading = false;
        } else if (!catalogRequested) {
            loading = true;
            catalogRequested = true;
            Network.CHANNEL.sendToServer(new PacketRequestCatalog());
        }
        refilter();
        updateButtons();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        // The integrated server has to keep running to answer the spin
        return false;
    }

    void onCatalogReady() {
        loading = false;
        if (selected != null) {
            selected = findEntry(selected.itemId, selected.meta);
        }
        refilter();
        scrollToFirstUpgrade();
        updateButtons();
    }

    private ClientCatalog.Entry findEntry(int itemId, int meta) {
        for (ClientCatalog.Entry entry : ClientCatalog.entries()) {
            if (entry.itemId == itemId && entry.meta == meta) {
                return entry;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ state helpers

    private boolean spinning() {
        return phase == Phase.WAITING || phase == Phase.LANDING;
    }

    private Codes.Bet betStatus() {
        if (spinning()) {
            return Codes.Bet.OK;
        }
        return ClientState.betStatus(container.windowId);
    }

    private double betValue() {
        if (spinning()) {
            return frozenBetValue;
        }
        return ClientState.betStatus(container.windowId) == Codes.Bet.OK ? ClientState.betValue(container.windowId) : 0;
    }

    private int amountFor(ClientCatalog.Entry entry) {
        return entry == null ? 1 : Math.max(1, Math.min(entry.maxStack, amount));
    }

    private double rawChanceFor(ClientCatalog.Entry entry, int count) {
        return UpgradeMath.rawChance(betValue(), entry.value * count, ClientCatalog.houseEdge());
    }

    private boolean locked(double rawChance) {
        return rawChance < ClientCatalog.minChance();
    }

    /** Chance shown on the wheel right now. */
    private double wheelChance() {
        if (spinning()) {
            return spinChance;
        }
        if (selected == null || betStatus() != Codes.Bet.OK) {
            return 0;
        }
        return Math.min(ClientCatalog.maxChance(), rawChanceFor(selected, amountFor(selected)));
    }

    private boolean canSpin() {
        if (loading || spinning() || selected == null || betStatus() != Codes.Bet.OK || betValue() <= 0) {
            return false;
        }
        return !locked(rawChanceFor(selected, amountFor(selected)));
    }

    private void setMessage(String text, int color) {
        message = text;
        messageColor = color;
    }

    private void clearMessage() {
        message = null;
    }

    private void updateButtons() {
        if (spinButton == null) {
            return;
        }
        boolean stackable = selected != null && selected.stackable();
        minusButton.visible = stackable;
        plusButton.visible = stackable;
        minusButton.enabled = stackable && !spinning() && amountFor(selected) > 1;
        plusButton.enabled = stackable && !spinning() && amountFor(selected) < selected.maxStack;
        spinButton.enabled = phase == Phase.ARMED || canSpin();
        if (spinning()) {
            spinButton.displayString = I18n.format("gui.upgraderwheel.spinning");
            spinButton.enabled = false;
        } else if (phase == Phase.ARMED) {
            spinButton.displayString = TextFormatting.YELLOW + I18n.format("gui.upgraderwheel.confirm", GuiDraw.chance(wheelChance()));
        } else {
            spinButton.displayString = I18n.format("gui.upgraderwheel.upgrade");
        }
        for (int i = 0; i < filterButtons.length; i++) {
            String label = "~" + (int) BAND_CENTER[i] + "%";
            filterButtons[i].displayString = i == filterBand ? TextFormatting.YELLOW + label : label;
            filterButtons[i].enabled = !spinning();
        }
    }

    // ------------------------------------------------------------------ list

    private void refilter() {
        visible.clear();
        visibleAmounts.clear();
        if (loading) {
            return;
        }
        String query = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        String[] tokens = query.isEmpty() ? new String[0] : query.split("\\s+");
        double bet = betValue();
        boolean betOk = betStatus() == Codes.Bet.OK && bet > 0;
        double edge = ClientCatalog.houseEdge();
        for (ClientCatalog.Entry entry : ClientCatalog.entries()) {
            if (!matches(entry, tokens)) {
                continue;
            }
            int count = 1;
            if (filterBand >= 0) {
                if (!betOk) {
                    continue;
                }
                if (entry.stackable()) {
                    count = UpgradeMath.amountForChance(bet, entry.value, edge, BAND_CENTER[filterBand], entry.maxStack);
                }
                double raw = UpgradeMath.rawChance(bet, entry.value * count, edge);
                double high = Math.min(BAND_HIGH[filterBand], ClientCatalog.maxChance());
                if (raw < BAND_LOW[filterBand] || raw > high || locked(raw)) {
                    continue;
                }
            }
            visible.add(entry);
            visibleAmounts.add(count);
        }
        clampScroll();
    }

    private static boolean matches(ClientCatalog.Entry entry, String[] tokens) {
        for (String token : tokens) {
            if (token.startsWith("@")) {
                if (!entry.modId.startsWith(token.substring(1))) {
                    return false;
                }
            } else if (!entry.searchName.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private void clampScroll() {
        int max = Math.max(0, visible.size() - ROWS);
        scroll = Math.max(0, Math.min(max, scroll));
    }

    /** With no search and no filter, jump past the items that would be downgrades. */
    private void scrollToFirstUpgrade() {
        if (filterBand >= 0 || (search != null && !search.getText().trim().isEmpty())) {
            return;
        }
        double bet = betValue();
        if (bet <= 0) {
            return;
        }
        for (int i = 0; i < visible.size(); i++) {
            if (UpgradeMath.rawChance(bet, visible.get(i).value, ClientCatalog.houseEdge()) < ClientCatalog.maxChance()) {
                scroll = i;
                clampScroll();
                return;
            }
        }
        scroll = visible.size();
        clampScroll();
    }

    private void onBetChanged() {
        if (phase == Phase.ARMED) {
            phase = Phase.IDLE;
        }
        clearMessageIfStale();
        refilter();
        scrollToFirstUpgrade();
        updateButtons();
    }

    private boolean keepMessage;

    private void clearMessageIfStale() {
        // The bet update that follows a spin must not wipe the result message
        if (keepMessage) {
            keepMessage = false;
        } else {
            clearMessage();
        }
    }

    private void select(ClientCatalog.Entry entry, int suggestedAmount) {
        if (entry != selected) {
            clearMessage();
        }
        selected = entry;
        amount = entry == null ? 1 : Math.max(1, Math.min(entry.maxStack, suggestedAmount));
        if (phase == Phase.ARMED) {
            phase = Phase.IDLE;
        }
        updateButtons();
    }

    // ------------------------------------------------------------------ spinning

    private void startSpin() {
        if (!canSpin()) {
            return;
        }
        spinTarget = selected;
        spinAmount = amountFor(selected);
        spinChance = wheelChance();
        frozenBetValue = betValue();
        frozenBet = container.getBet().copy();
        container.getBetSlot().setHidden(true);
        clearMessage();
        phase = Phase.WAITING;
        phaseStartNanos = System.nanoTime();
        lastTick = (int) Math.floor(angle / 30.0);
        Network.CHANNEL.sendToServer(new PacketRoll(container.windowId, spinTarget.itemId, spinTarget.meta, spinAmount));
        updateButtons();
    }

    void onRollResult(PacketRollResult result) {
        if (result.windowId != container.windowId || phase != Phase.WAITING) {
            return;
        }
        if (!result.code.isResult()) {
            stopSpin();
            setMessage(I18n.format(result.code.langKey(), GuiDraw.chance(ClientCatalog.minChance())), 0xB02020);
            playSound(SoundEvents.BLOCK_NOTE_BASS, 0.6F);
            return;
        }
        rollWon = result.code == Codes.Roll.WIN;
        spinChance = result.chance;
        double arc = Math.max(0, Math.min(100, spinChance)) * 3.6;
        double landing = rollWon
                ? arc * (0.1 + 0.8 * random.nextDouble())
                : arc + (360 - arc) * (0.1 + 0.8 * random.nextDouble());
        double duration = ModConfig.spinSeconds;
        int turns = Math.max(2, (int) Math.round(duration * 0.8));
        landStart = angle;
        double current = ((angle % 360) + 360) % 360;
        landEnd = angle - current + turns * 360 + landing;
        phase = Phase.LANDING;
        phaseStartNanos = System.nanoTime();
        updateButtons();
    }

    private void stopSpin() {
        phase = Phase.IDLE;
        container.getBetSlot().setHidden(false);
        frozenBet = ItemStack.EMPTY;
        // Catch up on bet updates that arrived during the spin, without wiping the message set right after this
        seenBetRevision = Integer.MIN_VALUE;
        keepMessage = true;
        updateButtons();
    }

    private void reveal() {
        stopSpin();
        flashUntilNanos = System.nanoTime() + FLASH_NANOS;
        flashWon = rollWon;
        if (rollWon) {
            String name = spinTarget == null ? "?" : spinTarget.name;
            if (spinAmount > 1) {
                name = spinAmount + " x " + name;
            }
            setMessage(I18n.format("gui.upgraderwheel.won", name), 0x1E7A1E);
            playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.0F);
        } else {
            setMessage(I18n.format("gui.upgraderwheel.lost"), 0xB02020);
            playSound(SoundEvents.ENTITY_ITEM_BREAK, 0.8F);
        }
    }

    private void animate() {
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - lastFrameNanos) / 1.0E9);
        lastFrameNanos = now;
        switch (phase) {
            case WAITING:
                angle += FREE_SPIN_SPEED * dt;
                tick();
                if (now - phaseStartNanos > TIMEOUT_NANOS) {
                    stopSpin();
                    setMessage(I18n.format("gui.upgraderwheel.timeout"), 0xB02020);
                }
                break;
            case LANDING:
                double t = Math.min(1.0, (now - phaseStartNanos) / (ModConfig.spinSeconds * 1.0E9));
                double eased = 1 - Math.pow(1 - t, 3);
                angle = landStart + (landEnd - landStart) * eased;
                tick();
                if (t >= 1.0) {
                    angle = landEnd;
                    reveal();
                }
                break;
            case ARMED:
                if (now - phaseStartNanos > CONFIRM_NANOS) {
                    phase = Phase.IDLE;
                    updateButtons();
                }
                break;
            default:
                break;
        }
    }

    private void tick() {
        int current = (int) Math.floor(angle / 30.0);
        if (current != lastTick) {
            lastTick = current;
            playSound(SoundEvents.BLOCK_NOTE_HAT, 1.8F);
        }
    }

    private void playSound(SoundEvent sound, float pitch) {
        if (ModConfig.sounds) {
            mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(sound, pitch));
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= BUTTON_FILTER && button.id < BUTTON_FILTER + 4) {
            int band = button.id - BUTTON_FILTER;
            filterBand = filterBand == band ? -1 : band;
            scroll = 0;
            refilter();
            if (filterBand < 0) {
                scrollToFirstUpgrade();
            }
            updateButtons();
        } else if (button.id == BUTTON_MINUS || button.id == BUTTON_PLUS) {
            int step = isShiftKeyDown() ? 10 : 1;
            changeAmount(button.id == BUTTON_PLUS ? step : -step);
        } else if (button.id == BUTTON_SPIN) {
            if (phase == Phase.ARMED || !ModConfig.confirmUpgrade) {
                phase = Phase.IDLE;
                startSpin();
            } else if (canSpin()) {
                phase = Phase.ARMED;
                phaseStartNanos = System.nanoTime();
                updateButtons();
            }
        }
    }

    private void changeAmount(int delta) {
        if (selected == null || !selected.stackable() || spinning()) {
            return;
        }
        amount = Math.max(1, Math.min(selected.maxStack, amountFor(selected) + delta));
        if (phase == Phase.ARMED) {
            phase = Phase.IDLE;
        }
        clearMessage();
        updateButtons();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (spinning()) {
            // Nothing in the inventory moves while the wheel is spinning
            return;
        }
        int x = mouseX - guiLeft;
        int y = mouseY - guiTop;
        if (inside(x, y, SEARCH_X, SEARCH_Y, SEARCH_W, SEARCH_H) && mouseButton == 1) {
            search.setText("");
            search.setFocused(true);
            refilter();
            scrollToFirstUpgrade();
            return;
        }
        search.mouseClicked(mouseX, mouseY, mouseButton);

        if (inside(x, y, LIST_X, LIST_Y, LIST_W, ROWS * ROW_H)) {
            if (!spinning() && !loading) {
                int index = scroll + (y - LIST_Y) / ROW_H;
                if (index >= 0 && index < visible.size()) {
                    select(visible.get(index), visibleAmounts.get(index));
                    playSound(SoundEvents.UI_BUTTON_CLICK, 1.0F);
                }
            }
            return;
        }
        if (inside(x, y, SCROLL_X, LIST_Y, SCROLL_W, ROWS * ROW_H)) {
            draggingScrollbar = true;
            scrollFromMouse(y);
            return;
        }
        if (inside(x, y, RESULT_X - 1, RESULT_Y - 1, 18, 18) && mouseButton == 1 && !spinning()) {
            select(null, 1);
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingScrollbar) {
            scrollFromMouse(mouseY - guiTop);
            return;
        }
        if (spinning()) {
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        draggingScrollbar = false;
        if (spinning()) {
            return;
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    private void scrollFromMouse(int y) {
        int max = visible.size() - ROWS;
        if (max <= 0) {
            scroll = 0;
            return;
        }
        double t = (y - LIST_Y) / (double) (ROWS * ROW_H);
        scroll = (int) Math.round(t * max);
        clampScroll();
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        int x = Mouse.getEventX() * width / mc.displayWidth - guiLeft;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1 - guiTop;
        int direction = wheel > 0 ? -1 : 1;
        if (inside(x, y, LIST_X, LIST_Y, LIST_W + SCROLL_W + 1, ROWS * ROW_H)) {
            scroll += direction * (isShiftKeyDown() ? ROWS : 1);
            clampScroll();
        } else if (inside(x, y, RESULT_X - 14, RESULT_Y - 1, 46, STEP_Y + 12 - RESULT_Y + 1)) {
            changeAmount(-direction * (isShiftKeyDown() ? 10 : 1));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (spinning() && keyCode != Keyboard.KEY_ESCAPE) {
            // Hotbar number keys and the drop key would move items mid-spin
            return;
        }
        if (!search.isFocused() && keyCode != 0 && keyCode == ClientEvents.OPEN_KEY.getKeyCode()) {
            mc.player.closeScreen();
            return;
        }
        if (keyCode != Keyboard.KEY_ESCAPE && search.isFocused()) {
            if (search.textboxKeyTyped(typedChar, keyCode)) {
                scroll = 0;
                refilter();
                scrollToFirstUpgrade();
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private static boolean inside(int x, int y, int left, int top, int w, int h) {
        return x >= left && y >= top && x < left + w && y < top + h;
    }

    // ------------------------------------------------------------------ update and draw

    @Override
    public void updateScreen() {
        super.updateScreen();
        search.updateCursorCounter();
        if (!spinning() && seenBetRevision != ClientState.betRevision()) {
            seenBetRevision = ClientState.betRevision();
            onBetChanged();
        } else {
            updateButtons();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        animate();
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (!drawListTooltip(mouseX, mouseY) && !drawResultTooltip(mouseX, mouseY) && !drawWheelTooltip(mouseX, mouseY)) {
            renderHoveredToolTip(mouseX, mouseY);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int left = guiLeft;
        int top = guiTop;
        GlStateManager.color(1, 1, 1, 1);
        GuiDraw.panel(left, top, LEFT_WIDTH, HEIGHT);
        GuiDraw.panel(left + RIGHT_X, top, RIGHT_WIDTH, HEIGHT);

        // Slots
        GuiDraw.slot(left + BET_X, top + BET_Y);
        GuiDraw.slot(left + RESULT_X, top + RESULT_Y);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                GuiDraw.slot(left + 8 + col * 18, top + ContainerUpgrader.INVENTORY_TOP + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            GuiDraw.slot(left + 8 + col * 18, top + ContainerUpgrader.INVENTORY_TOP + 58);
        }

        drawWheel(left + WHEEL_X, top + WHEEL_Y);
        drawLeftItems(left, top);
        drawLeftTexts(left, top);
        drawList(left, top, mouseX, mouseY);

        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused()) {
            fontRenderer.drawString(I18n.format("gui.upgraderwheel.search_hint"), left + SEARCH_X + 4, top + SEARCH_Y + 2, 0x707070);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("gui.upgraderwheel.title"), 8, 6, GuiDraw.TEXT);
    }

    private void drawWheel(int cx, int cy) {
        double chance = wheelChance();
        boolean isLocked = !spinning() && selected != null && betStatus() == Codes.Bet.OK
                && locked(rawChanceFor(selected, amountFor(selected)));
        double arc = Math.max(0, Math.min(100, chance)) * 3.6;
        long now = System.nanoTime();

        GuiDraw.beginShapes();
        if (now < flashUntilNanos) {
            double fade = (flashUntilNanos - now) / (double) FLASH_NANOS;
            int glow = flashWon ? 0x55FF55 : 0xFF5555;
            GuiDraw.ring(cx, cy, WHEEL_R + 2, WHEEL_R + 6, 0, 360, GuiDraw.withAlpha(glow, fade), GuiDraw.withAlpha(glow, 0));
        }
        GuiDraw.circle(cx, cy, WHEEL_R + 2, 0xFF1E1E1E);
        GuiDraw.sector(cx, cy, WHEEL_R, arc, 360, 0xFF3A3A3A, 0xFF5E5E5E);
        if (arc > 0) {
            int inner = isLocked ? 0xFF6A6A6A : 0xFFFFE27A;
            int outer = isLocked ? 0xFF8A8A8A : 0xFFE0A000;
            GuiDraw.sector(cx, cy, WHEEL_R, 0, arc, inner, outer);
            GuiDraw.spoke(cx, cy, HUB_R, WHEEL_R, 0, 0.5, 0xFF1E1E1E);
            GuiDraw.spoke(cx, cy, HUB_R, WHEEL_R, arc, 0.5, 0xFF1E1E1E);
        }
        GuiDraw.circle(cx, cy, HUB_R + 1, 0xFF1E1E1E);
        GuiDraw.circle(cx, cy, HUB_R, 0xFFC6C6C6);
        GuiDraw.needle(cx, cy, HUB_R - 2, WHEEL_R + 4, angle, 4.0, 0xFF1E1E1E);
        GuiDraw.needle(cx, cy, HUB_R - 1, WHEEL_R + 2.5, angle, 2.6, 0xFFD62828);
        GuiDraw.endShapes();

        String text;
        int color;
        if (spinning() || (selected != null && betStatus() == Codes.Bet.OK)) {
            text = GuiDraw.chance(chance);
            color = isLocked ? 0x8A8A8A : GuiDraw.TEXT;
        } else {
            text = "?";
            color = 0x8A8A8A;
        }
        drawCentered(text, cx, cy - 4, color);
    }

    private void drawLeftItems(int left, int top) {
        RenderHelper.enableGUIStandardItemLighting();
        if (container.getBetSlot().isHidden() && !frozenBet.isEmpty()) {
            itemRender.renderItemAndEffectIntoGUI(frozenBet, left + BET_X, top + BET_Y);
            itemRender.renderItemOverlayIntoGUI(fontRenderer, frozenBet, left + BET_X, top + BET_Y, null);
        }
        ClientCatalog.Entry shown = spinning() ? spinTarget : selected;
        if (shown != null) {
            int count = spinning() ? spinAmount : amountFor(shown);
            ItemStack stack = shown.stack.copy();
            stack.setCount(count);
            itemRender.renderItemAndEffectIntoGUI(stack, left + RESULT_X, top + RESULT_Y);
            itemRender.renderItemOverlayIntoGUI(fontRenderer, stack, left + RESULT_X, top + RESULT_Y, null);
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();
        GlStateManager.color(1, 1, 1, 1);
    }

    private void drawLeftTexts(int left, int top) {
        drawCentered(I18n.format("gui.upgraderwheel.bet_label"), left + BET_X + 8, top + BET_Y - 11, GuiDraw.TEXT);
        drawCentered(I18n.format("gui.upgraderwheel.result_label"), left + RESULT_X + 8, top + RESULT_Y - 11, GuiDraw.TEXT);

        double bet = betValue();
        if (bet > 0) {
            drawCentered(GuiDraw.value(bet), left + BET_X + 8, top + BET_Y + 20, 0x5A5A5A);
        }
        ClientCatalog.Entry shown = spinning() ? spinTarget : selected;
        if (shown != null) {
            int count = spinning() ? spinAmount : amountFor(shown);
            drawCentered(GuiDraw.value(shown.value * count), left + RESULT_X + 8, top + RESULT_Y + 20, 0x5A5A5A);
            if (shown.stackable() && !spinning()) {
                drawCentered("x" + count, left + RESULT_X + 8, top + STEP_Y + 2, GuiDraw.TEXT);
            }
        }

        String status;
        int color = GuiDraw.TEXT;
        Codes.Bet betState = betStatus();
        if (loading) {
            status = I18n.format("gui.upgraderwheel.loading");
        } else if (spinning()) {
            status = I18n.format("gui.upgraderwheel.spinning");
        } else if (message != null) {
            status = message;
            color = messageColor;
        } else if (betState != Codes.Bet.OK) {
            status = I18n.format(betState.langKey());
            color = betState == Codes.Bet.EMPTY || betState == Codes.Bet.NOT_READY ? GuiDraw.TEXT : 0xB02020;
        } else if (selected == null) {
            status = I18n.format("gui.upgraderwheel.pick_result");
        } else if (locked(rawChanceFor(selected, amountFor(selected)))) {
            status = I18n.format("gui.upgraderwheel.locked", GuiDraw.chance(ClientCatalog.minChance()));
            color = 0xB02020;
        } else if (phase == Phase.ARMED) {
            status = I18n.format("gui.upgraderwheel.confirm_hint");
            color = 0x9A6A00;
        } else {
            status = I18n.format("gui.upgraderwheel.ready");
        }
        drawCentered(fontRenderer.trimStringToWidth(status, LEFT_WIDTH - 10), left + LEFT_WIDTH / 2, top + STATUS_Y, color);
    }

    private void drawList(int left, int top, int mouseX, int mouseY) {
        int listLeft = left + LIST_X;
        int listTop = top + LIST_Y;
        GuiDraw.sunken(listLeft - 1, listTop - 1, LIST_W + SCROLL_W + 3, ROWS * ROW_H + 2, 0xFF2A2A2A);

        if (loading || visible.isEmpty()) {
            String text = I18n.format(loading ? "gui.upgraderwheel.loading" : "gui.upgraderwheel.no_results");
            drawCentered(text, listLeft + LIST_W / 2, listTop + ROWS * ROW_H / 2 - 4, 0xA0A0A0);
            drawCount(left, top);
            return;
        }

        int hovered = -1;
        if (inside(mouseX - left, mouseY - top, LIST_X, LIST_Y, LIST_W, ROWS * ROW_H)) {
            hovered = scroll + (mouseY - listTop) / ROW_H;
        }

        int end = Math.min(visible.size(), scroll + ROWS);
        for (int i = scroll; i < end; i++) {
            int rowTop = listTop + (i - scroll) * ROW_H;
            ClientCatalog.Entry entry = visible.get(i);
            if (entry == selected) {
                drawRect(listLeft, rowTop, listLeft + LIST_W, rowTop + ROW_H, 0x60FFD24A);
            } else if (i == hovered) {
                drawRect(listLeft, rowTop, listLeft + LIST_W, rowTop + ROW_H, 0x28FFFFFF);
            }
        }

        RenderHelper.enableGUIStandardItemLighting();
        for (int i = scroll; i < end; i++) {
            int rowTop = listTop + (i - scroll) * ROW_H;
            itemRender.renderItemAndEffectIntoGUI(visible.get(i).stack, listLeft + 1, rowTop + 1);
        }
        RenderHelper.disableStandardItemLighting();
        GlStateManager.disableLighting();

        boolean betOk = betStatus() == Codes.Bet.OK && betValue() > 0;
        for (int i = scroll; i < end; i++) {
            int rowTop = listTop + (i - scroll) * ROW_H;
            ClientCatalog.Entry entry = visible.get(i);
            int count = visibleAmounts.get(i);
            String chanceText = "";
            int chanceColor = 0x777777;
            if (betOk) {
                double raw = rawChanceFor(entry, count);
                chanceText = GuiDraw.chance(Math.min(ClientCatalog.maxChance(), raw));
                chanceColor = GuiDraw.chanceColor(raw, locked(raw));
            }
            int chanceWidth = fontRenderer.getStringWidth(chanceText);
            String name = count > 1 ? count + "x " + entry.name : entry.name;
            int nameWidth = LIST_W - 20 - chanceWidth - 4;
            String trimmed = fontRenderer.trimStringToWidth(name, nameWidth);
            if (!trimmed.equals(name) && nameWidth > 8) {
                trimmed = fontRenderer.trimStringToWidth(name, nameWidth - fontRenderer.getStringWidth("..")) + "..";
            }
            fontRenderer.drawString(trimmed, listLeft + 20, rowTop + 5, 0xE0E0E0);
            if (!chanceText.isEmpty()) {
                fontRenderer.drawString(chanceText, listLeft + LIST_W - 2 - chanceWidth, rowTop + 5, chanceColor);
            }
        }

        // Scrollbar
        int trackTop = listTop;
        int trackHeight = ROWS * ROW_H;
        drawRect(left + SCROLL_X, trackTop, left + SCROLL_X + SCROLL_W, trackTop + trackHeight, 0xFF151515);
        if (visible.size() > ROWS) {
            int thumbHeight = Math.max(8, trackHeight * ROWS / visible.size());
            int maxScroll = visible.size() - ROWS;
            int thumbTop = trackTop + (trackHeight - thumbHeight) * scroll / maxScroll;
            drawRect(left + SCROLL_X, thumbTop, left + SCROLL_X + SCROLL_W, thumbTop + thumbHeight, 0xFFC6C6C6);
            drawRect(left + SCROLL_X, thumbTop, left + SCROLL_X + SCROLL_W - 1, thumbTop + thumbHeight - 1, 0xFFE8E8E8);
            drawRect(left + SCROLL_X + 1, thumbTop + 1, left + SCROLL_X + SCROLL_W - 1, thumbTop + thumbHeight - 1, 0xFFC6C6C6);
        }
        drawCount(left, top);
    }

    private void drawCount(int left, int top) {
        if (!loading) {
            fontRenderer.drawString(I18n.format("gui.upgraderwheel.count", visible.size()), left + LIST_X, top + COUNT_Y, 0x5A5A5A);
        }
    }

    private void drawCentered(String text, int centerX, int y, int color) {
        fontRenderer.drawString(text, centerX - fontRenderer.getStringWidth(text) / 2, y, color);
    }

    // ------------------------------------------------------------------ tooltips

    private boolean drawListTooltip(int mouseX, int mouseY) {
        int x = mouseX - guiLeft;
        int y = mouseY - guiTop;
        if (loading || !inside(x, y, LIST_X, LIST_Y, LIST_W, ROWS * ROW_H)) {
            return false;
        }
        int index = scroll + (y - LIST_Y) / ROW_H;
        if (index < 0 || index >= visible.size()) {
            return false;
        }
        drawEntryTooltip(visible.get(index), visibleAmounts.get(index), mouseX, mouseY);
        return true;
    }

    private boolean drawResultTooltip(int mouseX, int mouseY) {
        ClientCatalog.Entry shown = spinning() ? spinTarget : selected;
        if (shown == null || !inside(mouseX - guiLeft, mouseY - guiTop, RESULT_X - 1, RESULT_Y - 1, 18, 18)) {
            return false;
        }
        drawEntryTooltip(shown, spinning() ? spinAmount : amountFor(shown), mouseX, mouseY);
        return true;
    }

    private void drawEntryTooltip(ClientCatalog.Entry entry, int count, int mouseX, int mouseY) {
        ItemStack stack = entry.stack.copy();
        stack.setCount(count);
        List<String> lines = new ArrayList<>(getItemToolTip(stack));
        if (count > 1) {
            // The tooltip event already added the value of one
            lines.add(TextFormatting.GRAY + I18n.format("gui.upgraderwheel.value", GuiDraw.value(entry.value * count)));
        }
        if (betStatus() == Codes.Bet.OK && betValue() > 0) {
            double raw = rawChanceFor(entry, count);
            if (locked(raw)) {
                lines.add(TextFormatting.RED + I18n.format("gui.upgraderwheel.locked", GuiDraw.chance(ClientCatalog.minChance())));
            } else {
                lines.add(TextFormatting.GOLD + I18n.format("gui.upgraderwheel.chance", GuiDraw.chance(Math.min(ClientCatalog.maxChance(), raw))));
            }
        }
        drawHoveringText(lines, mouseX, mouseY);
    }

    private boolean drawWheelTooltip(int mouseX, int mouseY) {
        int dx = mouseX - (guiLeft + WHEEL_X);
        int dy = mouseY - (guiTop + WHEEL_Y);
        if (dx * dx + dy * dy > (WHEEL_R + 2) * (WHEEL_R + 2)) {
            return false;
        }
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format("gui.upgraderwheel.chance", GuiDraw.chance(wheelChance())));
        lines.add(TextFormatting.GRAY + I18n.format("gui.upgraderwheel.wheel.bet", GuiDraw.value(betValue())));
        ClientCatalog.Entry shown = spinning() ? spinTarget : selected;
        if (shown != null) {
            int count = spinning() ? spinAmount : amountFor(shown);
            lines.add(TextFormatting.GRAY + I18n.format("gui.upgraderwheel.wheel.target", GuiDraw.value(shown.value * count)));
        }
        lines.add(TextFormatting.DARK_GRAY + I18n.format("gui.upgraderwheel.wheel.edge",
                String.format(Locale.ROOT, "%.0f%%", ClientCatalog.houseEdge() * 100), GuiDraw.chance(ClientCatalog.maxChance())));
        drawHoveringText(lines, mouseX, mouseY);
        return true;
    }
}
