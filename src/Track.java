import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

public class Track {

    public static final int WIDTH  = 800;
    public static final int HEIGHT = 600;

    // Road half-width in pixels
    public static final int ROAD_WIDTH = 80;

    // Waypoints define the centre-line of the track (closed loop)
    private final List<Point2D.Double> waypoints = new ArrayList<>();

    // Checkpoints – axis-aligned rectangles the player must cross in order
    private final List<Rectangle> checkpoints = new ArrayList<>();

    // The track centre-line as a smooth path (for drawing & AI)
    private Path2D.Double roadPath;
    // Outer / inner boundary paths (for collision)
    private Path2D.Double outerPath;
    private Path2D.Double innerPath;

    // Item-box positions
    private final List<Point2D.Double> itemBoxPositions = new ArrayList<>();

    public Track() {
        buildWaypoints();
        buildPaths();
        buildCheckpoints();
        buildItemBoxes();
    }

    // ---------------------------------------------------------------
    // Build the oval-ish track waypoints (centre-line)
    // ---------------------------------------------------------------
    private void buildWaypoints() {
        // A rounded rectangle track centred on the panel
        int cx = WIDTH / 2, cy = HEIGHT / 2;
        int rx = 300, ry = 200;
        int steps = 48;
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            double x = cx + rx * Math.cos(angle);
            double y = cy + ry * Math.sin(angle);
            waypoints.add(new Point2D.Double(x, y));
        }
    }

    // ---------------------------------------------------------------
    // Build the three Path2D objects from waypoints
    // ---------------------------------------------------------------
    private void buildPaths() {
        roadPath  = buildOffsetPath(0);
        outerPath = buildOffsetPath(ROAD_WIDTH);
        innerPath = buildOffsetPath(-ROAD_WIDTH);
    }

    /** Creates a closed path offset by `offset` pixels from the centre-line. */
    private Path2D.Double buildOffsetPath(double offset) {
        Path2D.Double path = new Path2D.Double();
        int n = waypoints.size();
        for (int i = 0; i < n; i++) {
            Point2D.Double prev = waypoints.get((i - 1 + n) % n);
            Point2D.Double curr = waypoints.get(i);
            Point2D.Double next = waypoints.get((i + 1) % n);

            // Normal = perpendicular to the tangent at this point
            double tx = next.x - prev.x;
            double ty = next.y - prev.y;
            double len = Math.sqrt(tx * tx + ty * ty);
            double nx = -ty / len;
            double ny =  tx / len;

            double px = curr.x + nx * offset;
            double py = curr.y + ny * offset;

            if (i == 0) path.moveTo(px, py);
            else        path.lineTo(px, py);
        }
        path.closePath();
        return path;
    }

    // ---------------------------------------------------------------
    // Checkpoints (thin rectangles crossing the road)
    // ---------------------------------------------------------------
    private void buildCheckpoints() {
        // Place 4 checkpoints evenly around the track
        int[] indices = {0, 12, 24, 36};
        for (int idx : indices) {
            Point2D.Double p = waypoints.get(idx);
            // Make a wide thin rectangle centred on the waypoint
            checkpoints.add(new Rectangle(
                (int) p.x - ROAD_WIDTH - 10,
                (int) p.y - 8,
                (ROAD_WIDTH + 10) * 2,
                16
            ));
        }
    }

    // ---------------------------------------------------------------
    // Item box positions
    // ---------------------------------------------------------------
    private void buildItemBoxes() {
        int[] indices = {6, 18, 30, 42};
        for (int idx : indices) {
            Point2D.Double p = waypoints.get(idx);
            itemBoxPositions.add(new Point2D.Double(p.x, p.y));
        }
    }

    // ---------------------------------------------------------------
    // Public helpers
    // ---------------------------------------------------------------

    /** Returns true if the point is on the road (between inner and outer). */
    public boolean isOnRoad(double x, double y) {
        boolean insideOuter = outerPath.contains(x, y);
        boolean insideInner = innerPath.contains(x, y);
        return insideOuter && !insideInner;
    }

    /** Returns the nearest waypoint index to the given position. */
    public int nearestWaypointIndex(double x, double y) {
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < waypoints.size(); i++) {
            Point2D.Double wp = waypoints.get(i);
            double d = Math.hypot(wp.x - x, wp.y - y);
            if (d < bestDist) { bestDist = d; best = i; }
        }
        return best;
    }

    /** Returns the angle (radians) the kart should face to head toward waypoint `idx`. */
    public double angleToWaypoint(double x, double y, int idx) {
        Point2D.Double wp = waypoints.get(idx);
        return Math.atan2(wp.y - y, wp.x - x);
    }

    public List<Point2D.Double> getWaypoints()      { return waypoints; }
    public List<Rectangle>      getCheckpoints()    { return checkpoints; }
    public List<Point2D.Double> getItemBoxPositions(){ return itemBoxPositions; }
    public Path2D.Double        getRoadPath()        { return roadPath; }
    public Path2D.Double        getOuterPath()       { return outerPath; }
    public Path2D.Double        getInnerPath()       { return innerPath; }

    // ---------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------
    public void draw(Graphics2D g) {
        // Grass background
        g.setColor(new Color(34, 139, 34));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // Road surface
        g.setColor(new Color(80, 80, 80));
        g.fill(outerPath);

        // Inner grass
        g.setColor(new Color(34, 139, 34));
        g.fill(innerPath);

        // Road edge lines (white)
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3f));
        g.draw(outerPath);
        g.draw(innerPath);

        // Centre dashed line
        g.setColor(new Color(255, 255, 0, 160));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{12f, 12f}, 0f));
        g.draw(roadPath);
        g.setStroke(new BasicStroke(1f));

        // Start/finish line (checkpoint 0)
        if (!checkpoints.isEmpty()) {
            Rectangle sf = checkpoints.get(0);
            // Checkered pattern
            int tileW = 8, tileH = sf.height / 2;
            for (int col = 0; col < sf.width / tileW; col++) {
                for (int row = 0; row < 2; row++) {
                    g.setColor((col + row) % 2 == 0 ? Color.WHITE : Color.BLACK);
                    g.fillRect(sf.x + col * tileW, sf.y + row * tileH, tileW, tileH);
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // Mini-map drawing (scaled down, top-right corner)
    // ---------------------------------------------------------------
    public void drawMiniMap(Graphics2D g, List<Kart> karts, Kart player) {
        int mmX = WIDTH - 170, mmY = 10;
        int mmW = 160, mmH = 120;
        double scaleX = (double) mmW / WIDTH;
        double scaleY = (double) mmH / HEIGHT;

        // Background
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(mmX - 4, mmY - 4, mmW + 8, mmH + 8, 8, 8);

        // Scale transform
        Graphics2D mg = (Graphics2D) g.create();
        mg.translate(mmX, mmY);
        mg.scale(scaleX, scaleY);

        // Grass
        mg.setColor(new Color(34, 139, 34));
        mg.fillRect(0, 0, WIDTH, HEIGHT);

        // Road
        mg.setColor(new Color(80, 80, 80));
        mg.fill(outerPath);
        mg.setColor(new Color(34, 139, 34));
        mg.fill(innerPath);

        // Karts
        for (Kart k : karts) {
            mg.setColor(k == player ? Color.YELLOW : Color.RED);
            mg.fillOval((int) k.x - 6, (int) k.y - 6, 12, 12);
        }

        mg.dispose();

        // Border
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(mmX - 4, mmY - 4, mmW + 8, mmH + 8, 8, 8);
        g.setStroke(new BasicStroke(1f));
    }
}
