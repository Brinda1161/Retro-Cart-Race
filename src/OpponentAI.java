import java.awt.*;
import java.util.List;
import java.awt.geom.Point2D;

public class OpponentAI extends Kart {

    private final Track track;
    private int targetWaypointIdx;

    // How far ahead the AI looks for its target waypoint
    private static final int LOOKAHEAD = 3;

    // Difficulty: 0.0 (easy) – 1.0 (hard)
    private final double difficulty;

    // Random wobble to make AI look less robotic
    private double wobble = 0;
    private int wobbleTimer = 0;

    public OpponentAI(double x, double y, double angle,
                      Color body, Color accent, String name,
                      Track track, double difficulty) {
        super(x, y, angle, body, accent, name);
        this.track      = track;
        this.difficulty = difficulty;
        // Start targeting the nearest waypoint ahead
        targetWaypointIdx = track.nearestWaypointIndex(x, y);
    }

    public void update() {
        List<Point2D.Double> waypoints = track.getWaypoints();
        int n = waypoints.size();

        // Check if we've reached the target waypoint
        Point2D.Double target = waypoints.get(targetWaypointIdx);
        double dist = Math.hypot(target.x - x, target.y - y);
        if (dist < 30) {
            targetWaypointIdx = (targetWaypointIdx + LOOKAHEAD) % n;
            target = waypoints.get(targetWaypointIdx);
        }

        // Desired angle toward target
        double desiredAngle = Math.atan2(target.y - y, target.x - x);

        // Angle difference (normalised to [-π, π])
        double diff = normaliseAngle(desiredAngle - angle);

        // Wobble (makes AI imperfect)
        wobbleTimer--;
        if (wobbleTimer <= 0) {
            wobble = (Math.random() - 0.5) * 0.3 * (1.0 - difficulty);
            wobbleTimer = 20 + (int)(Math.random() * 40);
        }
        diff += wobble;

        // Steer
        if (!spunOut) {
            double turnAmount = Math.min(Math.abs(diff), Kart.TURN_SPEED) * Math.signum(diff);
            angle += turnAmount;
        }

        // Accelerate (difficulty affects top speed)
        boolean onRoad = track.isOnRoad(x, y);
        double maxSpd = (boosted ? MAX_SPEED * 1.6 : MAX_SPEED) * (0.7 + 0.3 * difficulty);

        if (!spunOut) {
            if (speed < maxSpd) speed = Math.min(maxSpd, speed + ACCELERATION * difficulty);
        }

        super.update(onRoad);
    }

    private double normaliseAngle(double a) {
        while (a >  Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }
}
