import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class GamePanel extends JPanel implements Runnable, KeyListener {

    // ── Constants ──────────────────────────────────────────────────
    public static final int WIDTH  = 800;
    public static final int HEIGHT = 600;
    private static final int TARGET_FPS = 60;
    private static final int TOTAL_LAPS = 3;

    // ── Game state ─────────────────────────────────────────────────
    private enum State { START, COUNTDOWN, RACING, FINISHED }
    private State state = State.START;

    // ── Core objects ───────────────────────────────────────────────
    private Track track;
    private Kart  player;
    private List<OpponentAI> opponents = new ArrayList<>();
    private List<Kart>       allKarts  = new ArrayList<>();
    private List<PowerUp>    powerUps  = new ArrayList<>();

    // ── Input ──────────────────────────────────────────────────────
    private boolean keyUp, keyDown, keyLeft, keyRight;

    // ── Timing ────────────────────────────────────────────────────
    private Thread gameThread;
    private long   tick = 0;
    private int    countdownTimer = 0; // frames
    private long   raceStartTime  = 0;

    // ── Double buffer ─────────────────────────────────────────────
    private BufferedImage buffer;
    private Graphics2D    bufferG;

    // ── HUD messages ──────────────────────────────────────────────
    private String hudMessage    = "";
    private int    hudMsgTimer   = 0;

    // ── Item roulette ─────────────────────────────────────────────
    private boolean playerHasItem  = false;
    private PowerUp.Type playerItem = null;
    private int rouletteTimer = 0;
    private static final int ROULETTE_FRAMES = 40;

    // ── Banana peels dropped on track ─────────────────────────────
    private List<PowerUp> droppedBananas = new ArrayList<>();

    // ── Finish ────────────────────────────────────────────────────
    private String finishMessage = "";
    private long   finishTime    = 0;

    // ──────────────────────────────────────────────────────────────
    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        addKeyListener(this);
        setFocusable(true);

        buffer  = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        bufferG = buffer.createGraphics();
        bufferG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    // ──────────────────────────────────────────────────────────────
    public void start() {
        initGame();
        gameThread = new Thread(this);
        gameThread.setDaemon(true);
        gameThread.start();
    }

    private void initGame() {
        track = new Track();

        // Starting grid positions (spread along start/finish straight)
        List<Point2D.Double> wps = track.getWaypoints();
        Point2D.Double startWP = wps.get(0);
        double startAngle = track.angleToWaypoint(startWP.x, startWP.y, 1);

        // Perpendicular offset for grid
        double perpX = -Math.sin(startAngle);
        double perpY =  Math.cos(startAngle);
        double backX = -Math.cos(startAngle);
        double backY = -Math.sin(startAngle);

        player = new Kart(
            startWP.x + perpX * 0  + backX * 0,
            startWP.y + perpY * 0  + backY * 0,
            startAngle,
            new Color(30, 144, 255), new Color(0, 80, 180), "YOU"
        );
        player.lapStartTime = System.currentTimeMillis();

        opponents.clear();
        allKarts.clear();

        Color[] bodyColors   = { new Color(220, 50, 50),  new Color(50, 200, 50),  new Color(220, 150, 0) };
        Color[] accentColors = { new Color(140, 0, 0),    new Color(0, 120, 0),    new Color(140, 80, 0)  };
        String[] names       = { "RED", "GRN", "ORG" };
        double[] difficulties = { 0.75, 0.85, 0.65 };

        for (int i = 0; i < 3; i++) {
            double ox = startWP.x + perpX * (i % 2 == 0 ? 40 : -40) + backX * (30 + (i / 2) * 40);
            double oy = startWP.y + perpY * (i % 2 == 0 ? 40 : -40) + backY * (30 + (i / 2) * 40);
            OpponentAI ai = new OpponentAI(ox, oy, startAngle,
                bodyColors[i], accentColors[i], names[i],
                track, difficulties[i]);
            ai.lapStartTime = System.currentTimeMillis();
            opponents.add(ai);
        }

        allKarts.add(player);
        allKarts.addAll(opponents);

        // Power-ups at item box positions
        powerUps.clear();
        droppedBananas.clear();
        List<Point2D.Double> ibPos = track.getItemBoxPositions();
        for (int i = 0; i < ibPos.size(); i++) {
            Point2D.Double p = ibPos.get(i);
            // Alternate mushroom / banana
            PowerUp.Type t = (i % 2 == 0) ? PowerUp.Type.MUSHROOM : PowerUp.Type.BANANA;
            powerUps.add(new PowerUp(t, p.x, p.y));
        }

        playerHasItem = false;
        playerItem    = null;
        rouletteTimer = 0;
        hudMessage    = "";
        hudMsgTimer   = 0;
        tick          = 0;
        state         = State.START;
    }

    // ── Game loop ─────────────────────────────────────────────────
    @Override
    public void run() {
        long lastTime  = System.nanoTime();
        long nsPerFrame = 1_000_000_000L / TARGET_FPS;

        while (true) {
            long now   = System.nanoTime();
            long delta = now - lastTime;

            if (delta >= nsPerFrame) {
                lastTime = now - (delta % nsPerFrame);
                tick++;
                update();
                render();
                repaint();
            } else {
                try { Thread.sleep(1); } catch (InterruptedException ignored) {}
            }
        }
    }

    // ── Update ────────────────────────────────────────────────────
    private void update() {
        switch (state) {
            case START:
                // wait for Enter
                break;
            case COUNTDOWN:
                updateCountdown();
                break;
            case RACING:
                updateRacing();
                break;
            case FINISHED:
                // show results
                break;
        }
    }

    private void updateCountdown() {
        countdownTimer--;
        if (countdownTimer <= 0) {
            state = State.RACING;
            raceStartTime = System.currentTimeMillis();
            for (Kart k : allKarts) k.lapStartTime = raceStartTime;
            showHudMessage("GO!", 90);
        }
    }

    private void updateRacing() {
        // ── Player input ──────────────────────────────────────────
        if (keyUp)    player.accelerate();
        if (keyDown)  player.brake();
        if (keyLeft)  player.steerLeft();
        if (keyRight) player.steerRight();

        boolean playerOnRoad = track.isOnRoad(player.x, player.y);
        player.update(playerOnRoad);

        // ── AI update ─────────────────────────────────────────────
        for (OpponentAI ai : opponents) ai.update();

        // ── Power-up updates ──────────────────────────────────────
        for (PowerUp pu : powerUps) pu.update();
        for (PowerUp bp : droppedBananas) bp.update();

        // ── Roulette ──────────────────────────────────────────────
        if (rouletteTimer > 0) rouletteTimer--;

        // ── HUD message timer ─────────────────────────────────────
        if (hudMsgTimer > 0) hudMsgTimer--;

        // ── Checkpoint & lap logic ────────────────────────────────
        checkCheckpoints(player);
        for (OpponentAI ai : opponents) checkCheckpoints(ai);

        // ── Collision: kart vs. power-ups ─────────────────────────
        checkPowerUpCollisions();

        // ── Collision: kart vs. dropped bananas ───────────────────
        checkBananaCollisions();

        // ── Collision: kart vs. kart ──────────────────────────────
        checkKartCollisions();

        // ── Boundary push-back ────────────────────────────────────
        pushBackFromWalls(player);
        for (OpponentAI ai : opponents) pushBackFromWalls(ai);

        // ── Race positions ────────────────────────────────────────
        updatePositions();
    }

    // ── Checkpoint / lap logic ────────────────────────────────────
    // checkpoint field = index of the last checkpoint the kart crossed.
    // Starts at -1 so the first crossing of CP0 (start/finish) is valid.
    private void checkCheckpoints(Kart k) {
        List<java.awt.Rectangle> cps = track.getCheckpoints();
        int n = cps.size();
        // Next expected checkpoint index
        int nextCP = (k.checkpoint + 1) % n;
        java.awt.Rectangle cpRect = cps.get(nextCP);

        if (cpRect.contains(k.x, k.y)) {
            if (nextCP == 0 && k.checkpoint == n - 1) {
                // Completed a full loop through all checkpoints → new lap
                k.lap++;
                long now = System.currentTimeMillis();
                k.lastLapTime = now - k.lapStartTime;
                if (k.lastLapTime < k.bestLapTime) k.bestLapTime = k.lastLapTime;
                k.lapStartTime = now;

                if (k == player) {
                    if (k.lap >= TOTAL_LAPS) {
                        k.lap = TOTAL_LAPS;
                        endRace();
                    } else {
                        showHudMessage("LAP " + (k.lap + 1) + "/" + TOTAL_LAPS + "!", 90);
                    }
                } else {
                    if (k.lap > TOTAL_LAPS) k.lap = TOTAL_LAPS;
                }
            }
            k.checkpoint = nextCP;
        }
    }

    private void endRace() {
        state = State.FINISHED;
        finishTime = System.currentTimeMillis() - raceStartTime;
        int pos = player.position;
        String[] ordinals = {"1st", "2nd", "3rd", "4th"};
        finishMessage = "FINISHED " + ordinals[Math.min(pos - 1, 3)] + "!";
    }

    // ── Power-up collisions ───────────────────────────────────────
    private void checkPowerUpCollisions() {
        for (PowerUp pu : powerUps) {
            if (!pu.active) continue;
            // Player picks up item box → roulette
            if (pu.overlaps(player)) {
                pu.collect();
                if (!playerHasItem) {
                    startRoulette();
                }
            }
            // AI picks up item box
            for (OpponentAI ai : opponents) {
                if (pu.overlaps(ai)) {
                    pu.collect();
                    // 50% chance AI uses it immediately
                    if (Math.random() < 0.5) {
                        if (Math.random() < 0.5) ai.applyBoost();
                    }
                }
            }
        }
    }

    private void startRoulette() {
        playerHasItem = true;
        rouletteTimer = ROULETTE_FRAMES;
        // Random item
        playerItem = Math.random() < 0.5 ? PowerUp.Type.MUSHROOM : PowerUp.Type.BANANA;
        showHudMessage(playerItem == PowerUp.Type.MUSHROOM ? "MUSHROOM!" : "BANANA!", 90);
    }

    // ── Banana collisions ─────────────────────────────────────────
    private void checkBananaCollisions() {
        List<PowerUp> toRemove = new ArrayList<>();
        for (PowerUp bp : droppedBananas) {
            if (!bp.active) { toRemove.add(bp); continue; }
            // Check all karts
            for (Kart k : allKarts) {
                if (bp.overlaps(k)) {
                    k.applySpinOut();
                    bp.active = false;
                    toRemove.add(bp);
                    if (k == player) showHudMessage("SPIN OUT!", 90);
                    break;
                }
            }
        }
        droppedBananas.removeAll(toRemove);
    }

    // ── Kart vs. kart collisions ──────────────────────────────────
    private void checkKartCollisions() {
        for (int i = 0; i < allKarts.size(); i++) {
            for (int j = i + 1; j < allKarts.size(); j++) {
                Kart a = allKarts.get(i);
                Kart b = allKarts.get(j);
                double dx = b.x - a.x;
                double dy = b.y - a.y;
                double dist = Math.sqrt(dx * dx + dy * dy);
                double minDist = Kart.KART_WIDTH + 4;
                if (dist < minDist && dist > 0.01) {
                    // Push apart
                    double overlap = (minDist - dist) / 2.0;
                    double nx = dx / dist;
                    double ny = dy / dist;
                    a.x -= nx * overlap;
                    a.y -= ny * overlap;
                    b.x += nx * overlap;
                    b.y += ny * overlap;
                    // Exchange some speed
                    double relSpeed = a.speed - b.speed;
                    a.speed -= relSpeed * 0.3;
                    b.speed += relSpeed * 0.3;
                }
            }
        }
    }

    // ── Wall push-back ────────────────────────────────────────────
    private void pushBackFromWalls(Kart k) {
        if (!track.isOnRoad(k.x, k.y)) {
            // Slow down on grass
            k.speed *= 0.85;
        }
    }

    // ── Race positions ────────────────────────────────────────────
    private void updatePositions() {
        // Score = lap * 1000 + (checkpoint+1) * 100
        allKarts.sort((a, b) -> {
            int scoreA = a.lap * 1000 + (a.checkpoint + 1) * 100;
            int scoreB = b.lap * 1000 + (b.checkpoint + 1) * 100;
            return Integer.compare(scoreB, scoreA);
        });
        for (int i = 0; i < allKarts.size(); i++) {
            allKarts.get(i).position = i + 1;
        }
    }

    // ── HUD helper ────────────────────────────────────────────────
    private void showHudMessage(String msg, int frames) {
        hudMessage  = msg;
        hudMsgTimer = frames;
    }

    // ── Render ────────────────────────────────────────────────────
    private void render() {
        Graphics2D g = bufferG;
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        switch (state) {
            case START:
                renderStartScreen(g);
                break;
            case COUNTDOWN:
                renderGame(g);
                renderCountdown(g);
                break;
            case RACING:
                renderGame(g);
                renderHUD(g);
                break;
            case FINISHED:
                renderGame(g);
                renderHUD(g);
                renderFinishScreen(g);
                break;
        }
    }

    private void renderGame(Graphics2D g) {
        track.draw(g);

        // Item boxes
        for (Point2D.Double ibp : track.getItemBoxPositions()) {
            PowerUp.drawItemBox(g, ibp.x, ibp.y, tick);
        }

        // Power-ups
        for (PowerUp pu : powerUps) pu.draw(g);

        // Dropped bananas
        for (PowerUp bp : droppedBananas) bp.draw(g);

        // Karts (draw player last so it's on top)
        for (OpponentAI ai : opponents) ai.draw(g);
        player.draw(g);

        // Mini-map
        track.drawMiniMap(g, allKarts, player);
    }

    private void renderHUD(Graphics2D g) {
        // ── Lap counter ───────────────────────────────────────────
        g.setFont(new Font("Arial", Font.BOLD, 22));
        g.setColor(Color.WHITE);
        // lap field counts completed laps; display current lap (1-based)
        int displayLap = Math.min(player.lap + 1, TOTAL_LAPS);
        drawShadowString(g, "LAP " + displayLap + "/" + TOTAL_LAPS, 10, 30);

        // ── Position ──────────────────────────────────────────────
        String[] ordinals = {"1ST", "2ND", "3RD", "4TH"};
        int posIndex = Math.max(0, Math.min(player.position - 1, ordinals.length - 1));
        String posStr = ordinals[posIndex];
        drawShadowString(g, posStr, 10, 58);

        // ── Speed ─────────────────────────────────────────────────
        int kmh = (int)(Math.abs(player.speed) / Kart.MAX_SPEED * 200);
        drawShadowString(g, kmh + " km/h", 10, 86);

        // ── Lap time ──────────────────────────────────────────────
        long elapsed = System.currentTimeMillis() - player.lapStartTime;
        drawShadowString(g, "TIME " + formatTime(elapsed), 10, 114);

        if (player.bestLapTime < Long.MAX_VALUE) {
            g.setFont(new Font("Arial", Font.PLAIN, 14));
            drawShadowString(g, "BEST " + formatTime(player.bestLapTime), 10, 134);
        }

        // ── Item slot ─────────────────────────────────────────────
        drawItemSlot(g);

        // ── HUD message ───────────────────────────────────────────
        if (hudMsgTimer > 0) {
            float alpha = Math.min(1f, hudMsgTimer / 30f);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setFont(new Font("Arial", Font.BOLD, 36));
            g.setColor(Color.YELLOW);
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(hudMessage);
            drawShadowString(g, hudMessage, WIDTH / 2 - tw / 2, HEIGHT / 2 - 40);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }
    }

    private void drawItemSlot(Graphics2D g) {
        int sx = 10, sy = HEIGHT - 70;
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(sx, sy, 60, 60, 8, 8);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(sx, sy, 60, 60, 8, 8);
        g.setStroke(new BasicStroke(1f));

        if (playerHasItem && playerItem != null) {
            if (rouletteTimer > 0) {
                // Spinning roulette
                PowerUp.Type display = (tick % 6 < 3) ? PowerUp.Type.MUSHROOM : PowerUp.Type.BANANA;
                drawItemIcon(g, display, sx + 30, sy + 30);
            } else {
                drawItemIcon(g, playerItem, sx + 30, sy + 30);
            }
        }

        g.setFont(new Font("Arial", Font.PLAIN, 10));
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("ITEM", sx + 18, sy + 72);
    }

    private void drawItemIcon(Graphics2D g, PowerUp.Type type, int cx, int cy) {
        Graphics2D ig = (Graphics2D) g.create();
        ig.translate(cx, cy);
        if (type == PowerUp.Type.MUSHROOM) {
            ig.setColor(new Color(220, 50, 50));
            ig.fillOval(-12, -12, 24, 14);
            ig.setColor(Color.WHITE);
            ig.fillOval(-7, -10, 5, 5);
            ig.fillOval(2, -10, 5, 5);
            ig.setColor(new Color(240, 200, 150));
            ig.fillRoundRect(-5, 2, 10, 10, 3, 3);
        } else {
            ig.setColor(new Color(255, 220, 0));
            ig.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            java.awt.geom.Path2D.Double banana = new java.awt.geom.Path2D.Double();
            banana.moveTo(-8, 6);
            banana.curveTo(-10, -4, 4, -10, 8, -4);
            ig.draw(banana);
        }
        ig.dispose();
    }

    private void renderStartScreen(Graphics2D g) {
        // Dark overlay
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Title
        g.setFont(new Font("Arial", Font.BOLD, 56));
        g.setColor(new Color(255, 220, 0));
        String title = "KART RACER";
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, WIDTH / 2 - fm.stringWidth(title) / 2, HEIGHT / 2 - 60);

        // Subtitle
        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.setColor(Color.WHITE);
        String sub = "Press ENTER to Start";
        fm = g.getFontMetrics();
        // Blink
        if ((tick / 30) % 2 == 0)
            g.drawString(sub, WIDTH / 2 - fm.stringWidth(sub) / 2, HEIGHT / 2 + 10);

        // Controls
        g.setFont(new Font("Arial", Font.PLAIN, 15));
        g.setColor(Color.LIGHT_GRAY);
        String[] controls = {
            "↑ Accelerate   ↓ Brake",
            "← / → Steer",
            "SPACE – Use Item"
        };
        for (int i = 0; i < controls.length; i++) {
            fm = g.getFontMetrics();
            g.drawString(controls[i], WIDTH / 2 - fm.stringWidth(controls[i]) / 2, HEIGHT / 2 + 60 + i * 22);
        }
    }

    private void renderCountdown(Graphics2D g) {
        int seconds = (countdownTimer / TARGET_FPS) + 1;
        String txt = seconds > 0 ? String.valueOf(seconds) : "GO!";
        g.setFont(new Font("Arial", Font.BOLD, 80));
        g.setColor(Color.YELLOW);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(txt);
        // Shadow
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString(txt, WIDTH / 2 - tw / 2 + 4, HEIGHT / 2 + 4);
        g.setColor(Color.YELLOW);
        g.drawString(txt, WIDTH / 2 - tw / 2, HEIGHT / 2);
    }

    private void renderFinishScreen(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 180));
        g.fillRoundRect(WIDTH / 2 - 200, HEIGHT / 2 - 100, 400, 220, 20, 20);

        g.setFont(new Font("Arial", Font.BOLD, 42));
        g.setColor(Color.YELLOW);
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(finishMessage);
        g.drawString(finishMessage, WIDTH / 2 - tw / 2, HEIGHT / 2 - 50);

        g.setFont(new Font("Arial", Font.BOLD, 20));
        g.setColor(Color.WHITE);
        String timeStr = "Race Time: " + formatTime(finishTime);
        fm = g.getFontMetrics();
        g.drawString(timeStr, WIDTH / 2 - fm.stringWidth(timeStr) / 2, HEIGHT / 2);

        if (player.bestLapTime < Long.MAX_VALUE) {
            String bestStr = "Best Lap:  " + formatTime(player.bestLapTime);
            g.drawString(bestStr, WIDTH / 2 - fm.stringWidth(bestStr) / 2, HEIGHT / 2 + 30);
        }

        g.setFont(new Font("Arial", Font.PLAIN, 16));
        g.setColor(Color.LIGHT_GRAY);
        String restart = "Press ENTER to play again";
        fm = g.getFontMetrics();
        if ((tick / 30) % 2 == 0)
            g.drawString(restart, WIDTH / 2 - fm.stringWidth(restart) / 2, HEIGHT / 2 + 80);
    }

    // ── Utility ───────────────────────────────────────────────────
    private void drawShadowString(Graphics2D g, String s, int x, int y) {
        Color orig = g.getColor();
        g.setColor(new Color(0, 0, 0, 180));
        g.drawString(s, x + 2, y + 2);
        g.setColor(orig);
        g.drawString(s, x, y);
    }

    private String formatTime(long ms) {
        long secs  = ms / 1000;
        long millis = ms % 1000;
        long mins  = secs / 60;
        secs = secs % 60;
        return String.format("%d:%02d.%03d", mins, secs, millis);
    }

    // ── Paint ─────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(buffer, 0, 0, null);
    }

    // ── KeyListener ───────────────────────────────────────────────
    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                keyUp = true;
                break;
            case KeyEvent.VK_DOWN:
                keyDown = true;
                break;
            case KeyEvent.VK_LEFT:
                keyLeft = true;
                break;
            case KeyEvent.VK_RIGHT:
                keyRight = true;
                break;
            case KeyEvent.VK_ENTER:
                if (state == State.START || state == State.FINISHED) {
                    if (state == State.FINISHED) initGame();
                    state = State.COUNTDOWN;
                    countdownTimer = TARGET_FPS * 3; // 3-second countdown
                }
                break;
            case KeyEvent.VK_SPACE:
                useItem();
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                keyUp = false;
                break;
            case KeyEvent.VK_DOWN:
                keyDown = false;
                break;
            case KeyEvent.VK_LEFT:
                keyLeft = false;
                break;
            case KeyEvent.VK_RIGHT:
                keyRight = false;
                break;
        }
    }

    @Override public void keyTyped(KeyEvent e) {}

    // ── Item use ──────────────────────────────────────────────────
    private void useItem() {
        if (!playerHasItem || playerItem == null || rouletteTimer > 0) return;
        if (playerItem == PowerUp.Type.MUSHROOM) {
            player.applyBoost();
            showHudMessage("BOOST!", 60);
        } else {
            // Drop banana behind the kart
            double bx = player.x - Math.cos(player.angle) * 30;
            double by = player.y - Math.sin(player.angle) * 30;
            PowerUp banana = new PowerUp(PowerUp.Type.BANANA, bx, by);
            droppedBananas.add(banana);
            showHudMessage("BANANA DROPPED!", 60);
        }
        playerHasItem = false;
        playerItem    = null;
    }
}
