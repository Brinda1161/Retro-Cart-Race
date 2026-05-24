import java.awt.*;
import java.awt.geom.*;

public class Kart {

    // ── Position & motion ──────────────────────────────────────────
    public double x, y;
    public double angle;       // radians, 0 = right
    public double speed;       // pixels per frame
    public double angularVel;  // radians per frame

    // ── Physics constants ──────────────────────────────────────────
    public static final double MAX_SPEED       = 5.0;
    public static final double ACCELERATION    = 0.18;
    public static final double BRAKE_FORCE     = 0.28;
    public static final double FRICTION        = 0.06;
    public static final double GRASS_FRICTION  = 0.14;
    public static final double TURN_SPEED      = 0.055;
    public static final double KART_WIDTH      = 20;
    public static final double KART_HEIGHT     = 28;

    // ── State ──────────────────────────────────────────────────────
    public int  lap          = 0;
    public int  checkpoint   = -1;  // last checkpoint crossed (-1 = none yet)
    public boolean finished  = false;
    public int  position     = 0;   // race position (1-based)

    // Power-up state
    public boolean boosted      = false;
    public int     boostTimer   = 0;
    public boolean spunOut      = false;
    public int     spinTimer    = 0;
    public int     spinOutCooldown = 0; // immunity frames after spin

    // Visual
    protected Color bodyColor;
    protected Color accentColor;
    protected String name;

    // Lap timing
    public long lapStartTime   = 0;
    public long bestLapTime    = Long.MAX_VALUE;
    public long lastLapTime    = 0;

    public Kart(double x, double y, double angle, Color body, Color accent, String name) {
        this.x = x;
        this.y = y;
        this.angle = angle;
        this.bodyColor   = body;
        this.accentColor = accent;
        this.name        = name;
    }

    // ── Physics update ─────────────────────────────────────────────
    public void update(boolean onRoad) {
        // Spin-out
        if (spunOut) {
            spinTimer--;
            angle += 0.18;
            speed *= 0.88;
            if (spinTimer <= 0) {
                spunOut = false;
                spinOutCooldown = 90;
            }
        }

        if (spinOutCooldown > 0) spinOutCooldown--;

        // Boost timer
        if (boosted) {
            boostTimer--;
            if (boostTimer <= 0) boosted = false;
        }

        // Apply friction
        double frictionVal = onRoad ? FRICTION : GRASS_FRICTION;
        if (speed > 0)      speed = Math.max(0, speed - frictionVal);
        else if (speed < 0) speed = Math.min(0, speed + frictionVal);

        // Move
        x += Math.cos(angle) * speed;
        y += Math.sin(angle) * speed;
    }

    public void accelerate() {
        if (spunOut) return;
        double maxSpd = boosted ? MAX_SPEED * 1.6 : MAX_SPEED;
        speed = Math.min(maxSpd, speed + ACCELERATION);
    }

    public void brake() {
        if (spunOut) return;
        speed = Math.max(-MAX_SPEED * 0.4, speed - BRAKE_FORCE);
    }

    public void steerLeft() {
        if (spunOut) return;
        if (Math.abs(speed) > 0.3)
            angle -= TURN_SPEED * (speed / MAX_SPEED);
    }

    public void steerRight() {
        if (spunOut) return;
        if (Math.abs(speed) > 0.3)
            angle += TURN_SPEED * (speed / MAX_SPEED);
    }

    public void applyBoost() {
        boosted    = true;
        boostTimer = 120; // 2 seconds at 60fps
        speed      = Math.min(MAX_SPEED * 1.6, speed + 2.0);
    }

    public void applySpinOut() {
        if (spinOutCooldown > 0) return;
        spunOut   = true;
        spinTimer = 60;
        speed    *= 0.3;
    }

    // ── Collision rectangle ────────────────────────────────────────
    public Rectangle2D.Double getBounds() {
        return new Rectangle2D.Double(
            x - KART_WIDTH / 2,
            y - KART_HEIGHT / 2,
            KART_WIDTH,
            KART_HEIGHT
        );
    }

    // ── Drawing ────────────────────────────────────────────────────
    public void draw(Graphics2D g) {
        Graphics2D kg = (Graphics2D) g.create();
        kg.translate(x, y);
        kg.rotate(angle + Math.PI / 2); // nose points "up" in local space

        // Shadow
        kg.setColor(new Color(0, 0, 0, 60));
        kg.fillRoundRect(-10, -14, 20, 28, 6, 6);

        // Body
        kg.setColor(bodyColor);
        kg.fillRoundRect(-9, -13, 18, 26, 5, 5);

        // Cockpit
        kg.setColor(accentColor);
        kg.fillRoundRect(-5, -8, 10, 12, 4, 4);

        // Wheels
        kg.setColor(Color.DARK_GRAY);
        kg.fillRect(-12, -11, 5, 7);  // front-left
        kg.fillRect(7,  -11, 5, 7);   // front-right
        kg.fillRect(-12,  6, 5, 7);   // rear-left
        kg.fillRect(7,    6, 5, 7);   // rear-right

        // Boost flame
        if (boosted) {
            kg.setColor(new Color(255, 140, 0, 200));
            int[] fx = {-4, 0, 4};
            int[] fy = {13, 22, 13};
            kg.fillPolygon(fx, fy, 3);
            kg.setColor(new Color(255, 255, 0, 160));
            int[] fx2 = {-2, 0, 2};
            int[] fy2 = {13, 19, 13};
            kg.fillPolygon(fx2, fy2, 3);
        }

        kg.dispose();

        // Name tag
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(name);
        g.drawString(name, (int)(x - tw / 2.0), (int)(y - 20));
    }
}
