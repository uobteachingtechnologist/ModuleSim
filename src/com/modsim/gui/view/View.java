package com.modsim.gui.view;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.text.DecimalFormat;

import javax.swing.*;

import com.modsim.modules.BaseModule;
import com.modsim.modules.Link;
import com.modsim.res.Colors;
import com.modsim.Main;
import com.modsim.tools.BaseTool;
import com.modsim.tools.PlaceTool;
import com.modsim.util.Vec2;

/**
 * The main viewport for the simulator
 * @author aw12700
 *
 */
public class View extends JPanel {

    public int zoomI = 3;
    public double zoom = zoomI * ZOOM_MULTIPLIER;

    public static final double ZOOM_MULTIPLIER = 0.2;
    public static final int ZOOM_LIMIT = 12;

    public double camX = 0, camY = 0;
    public AffineTransform wToV = new AffineTransform();

    private static final long serialVersionUID = 1L;
    public BaseTool curTool = null;

    public boolean useAA = true;

    public View() {
        setFocusable(true);
        ViewUtil listener = new ViewUtil();
        addMouseListener(listener);
        addMouseMotionListener(listener);
        addMouseWheelListener(listener);
        addKeyListener(listener);
    }

    public void calcXForm() {
        wToV = new AffineTransform();

        wToV.translate(camX, camY);
        wToV.translate((getWidth() / 2), (getHeight() / 2));

        wToV.scale(zoom, zoom);
    }

    @Override
    public void paintComponent(Graphics oldG) {
        Graphics2D g = (Graphics2D) oldG;

        // Antialiasing
        if (useAA) {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        else {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        }

        // View transform
        calcXForm();

        AffineTransform old = new AffineTransform(g.getTransform());

        // Fill back
        g.setColor(Colors.background);
        g.fillRect(0, 0, getWidth(), getHeight());

        // Grid
        g.setColor(Colors.grid);
        double xD = (camX + getWidth()/2);
        double yD = (camY + getHeight()/2);
        double xOff = (int)xD % (int)(Main.sim.grid * zoom);
        double yOff = (int)yD % (int)(Main.sim.grid * zoom);
        g.translate(xOff, yOff);
        drawGrid(g);

        g.setTransform(old);

        // Draw links
        g.transform(wToV);
        synchronized (Main.sim) {
            for (Link l : Main.sim.getLinks()) {
                if (l == null) {
                    System.err.println("Warning: Null link encountered while drawing");
                    continue;
                }
                l.draw(g);
            }

            g.setTransform(old);

            // Draw modules
            for (BaseModule m : Main.sim.getModules()) {
                m.updateXForm();
                g.transform(m.toView);
                m.paint(g);

                if (m.error) {
                    drawError(g);
                }

                g.setTransform(old);
            }

            for (BaseModule m : Main.sim.getModules()) {
                g.transform(m.toView);
                m.drawLabel(g);
                g.setTransform(old);
            }

            for (BaseModule m : Main.sim.getModules()) {
                if (m.selected) {
                    g.transform(m.toView);
                    m.drawBounds(g);
                    g.setTransform(old);
                }
            }
        }

        // Draw the tool
        if (curTool != null) {
            g.transform(wToV);
            curTool.paintWorld(g);
            g.setTransform(old);
            curTool.paintScreen(g);
        }

        g.setTransform(old);

        // Draw iterations per second
        g.setColor(Color.BLACK);
        g.setFont(new Font("Monospaced", Font.BOLD, 10));
        DecimalFormat df = new DecimalFormat("#.##");
        String num = df.format(Main.sim.itrPerSec);
        int pad = 20 - num.length();
        for (int i=0; i < pad; i++) num = " " + num;
        g.drawString(num + " iterations/s", 10, 10);
    }

    /**
     * Draws an error flag
     */
    public void drawError(Graphics2D g) {
        g.setColor(Colors.errorEdge);
        g.drawOval(-30, -30, 60, 60);
        g.setColor(Colors.errorFill);
        g.fillOval(-27, -27, 54, 54);
        g.setColor(Colors.errorText);
        g.fillRect(-3, -16, 6, 20);
        g.fillOval(-3, 8, 6, 6);
    }

    /**
     * Draws a grid
     */
    public void drawGrid(Graphics2D g) {
        double grid = zoom * Main.sim.grid;

        int xNum = (int)(getWidth() / grid);
        int yNum = (int)(getHeight() / grid);

        for (int i = 0; i <= xNum + 1; i++) {
            g.drawLine((int)(i * grid), (int)-grid, (int)(i*grid), getHeight() + (int)grid);
        }

        for (int i = 0; i <= yNum + 1; i++) {
            g.drawLine((int)-grid, (int)(i * grid), getWidth() + (int)grid, (int)(i*grid));
        }
    }

    /**
     * Whether or not a tool is currently in use
     * @return True if tool is in use
     */
    public boolean hasTool() {
        return curTool != null;
    }

    /**
     * Cancels the current tool usage (if any)
     */
    public void cancelTool() {
        if (curTool != null) curTool.cancel();
        curTool = null;
    }

    /**
     * Sets the current tool
     * @param newTool New tool to use
     */
    public void setTool(BaseTool newTool) {
        if (curTool != null) curTool.cancel();
        curTool = newTool;
    }

    /**
     * Zooms the viewport in on the specified screen point
     * @param x X-coordinate
     * @param y Y-coordinate
     */
    public void zoomIn(int x, int y) {
        Vec2 zmPt = ViewUtil.screenToWorld(new Vec2(x, y));

        zoomI++;
        if (zoomI > ZOOM_LIMIT) {
            zoomI --;
        }
        else {
            zoom = zoomI * ZOOM_MULTIPLIER;
            calcXForm();
            Vec2 newScreenPt = ViewUtil.worldToScreen(zmPt);
            camX -= newScreenPt.x - x;
            camY -= newScreenPt.y - y;
        }
    }

    /**
     * Zooms the view out form the specified screen point
     * @param x X-coordinate
     * @param y Y-coordinate
     */
    public void zoomOut(int x, int y) {
        Vec2 zmPt = ViewUtil.screenToWorld(new Vec2(x, y));

        zoomI--;
        if (zoomI < 1) {
            zoomI = 1;
        }
        else {
            zoom = zoomI * ZOOM_MULTIPLIER;
            calcXForm();
            Vec2 newScreenPt = ViewUtil.worldToScreen(zmPt);
            camX -= newScreenPt.x - x;
            camY -= newScreenPt.y - y;
        }
    }

}
