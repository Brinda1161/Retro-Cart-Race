import java.awt.*;
import java.awt.geom.*;

public class PowerUp {

    public enum Type { MUSHROOM, BANANA }

    public Type type;
    public double x, y;
    public boolean active = true;
    public int respawnTimer = 0;

    private static final int RESPAWN_FRAMES = 300; // 5 seconds
    private static final int SIZE = 18;

    // Animation
    private float animAngle = 0;

    public PowerUp(Type type, double x, double y) {
        this.type = type;
        this.x    = x;
        this.y    = y;
    }

    public void update() {
        if (!active) {
            respawnTimer--;
            if (respawnTimer <= 0) active = true;
        }
        animAngle += 0.05f;
    }

    /** Returns true if the kart overlaps this power-up. */
    public boolean overlaps(Kart k) {
        if (!active) return false;
        double dx = k.x - x;
        double dy = k.y - y;
        return Math.sqrt(dx * dx + dy * dy) < SIZE + 8;
    }

    public void collect() {
        active       = false;
        respawnTimer = RESPAWN_FRAMES;
    }

    public void draw(Graphics2D g) {
        if (!active) return;

        Graphics2D pg = (Graphics2D) g.create();
        pg.translate(x, y);
        pg.rotate(animAngle);

        if (type == Type.MUSHROOM) {
            drawMushroom(pg);
        } else {
            drawBanana(pg);
        }

        pg.dispose();
    }

    private void drawMushroom(Graphics2D g) {
        // Cap
        g.setColor(new Color(220, 50, 50));
        g.fillOval(-SIZE / 2, -SIZE / 2, SIZE, SIZE / 2 + 4);
        // White spots
        g.setColor(Color.WHITE);
        g.fillOval(-6, -SIZE / 2 + 2, 5, 5);
        g.fillOval(2,  -SIZE / 2 + 2, 5, 5);
        // Stem
        g.setColor(new Color(240, 200, 150));
        g.fillRoundRect(-5, -2, 10, 10, 3, 3);
    }

    private void drawBanana(Graphics2D g) {
        g.setColor(new Color(255, 220, 0));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Curved banana shape
        Path2D.Double banana = new Path2D.Double();
        banana.moveTo(-8, 6);
        banana.curveTo(-10, -4, 4, -10, 8, -4);
        g.draw(banana);
        g.setStroke(new BasicStroke(1f));
    }

    // ── Item box (roulette box on track) ──────────────────────────
    public static void drawItemBox(Graphics2D g, double bx, double by, long tick) {
        int sz = 20;
        Graphics2D bg = (Graphics2D) g.create();
        bg.translate(bx, by);
        bg.rotate(tick * 0.03);

        // Outer box
        bg.setColor(new Color(255, 255, 255, 200));
        bg.fillRect(-sz / 2, -sz / 2, sz, sz);
        bg.setColor(new Color(180, 180, 255));
        bg.setStroke(new BasicStroke(2f));
        bg.drawRect(-sz / 2, -sz / 2, sz, sz);

        // Question mark
        bg.setColor(new Color(80, 80, 200));
        bg.setFont(new Font("Arial", Font.BOLD, 14));
        bg.drawString("?", -4, 6);

        bg.dispose();
    }
}
