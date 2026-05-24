package org.flossware.jplatform.jcurses;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import org.flossware.jplatform.api.ApplicationState;
import org.flossware.jplatform.api.PlatformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Terminal-based (curses-like) UI for JPlatform management using Lanterna.
 *
 * <p>Provides a text-based interface for managing applications in a terminal window.
 * Similar to tools like htop, top, or vim - uses the full terminal screen with keyboard navigation.</p>
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Full-screen terminal UI with application table</li>
 *   <li>Keyboard shortcuts for all operations</li>
 *   <li>Real-time metrics updates (auto-refresh every 2 seconds)</li>
 *   <li>Color-coded application states</li>
 *   <li>Status bar with help text</li>
 *   <li>Interactive selection and action menus</li>
 * </ul>
 *
 * <p>Keyboard Controls:</p>
 * <ul>
 *   <li>↑/↓ (or k/j) - Navigate application list</li>
 *   <li>d - Deploy new application</li>
 *   <li>s - Start selected application</li>
 *   <li>t - Stop selected application</li>
 *   <li>u - Undeploy selected application</li>
 *   <li>r - Refresh display</li>
 *   <li>q - Quit application</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * PlatformManager manager = new ApplicationManager();
 * TerminalConsole console = new TerminalConsole(manager);
 * console.start(); // Blocks until user presses 'q'
 * }</pre>
 *
 * @since 1.1
 */
public class TerminalConsole {

    private static final Logger logger = LoggerFactory.getLogger(TerminalConsole.class);

    private final PlatformManager platformManager;
    private final ScheduledExecutorService refreshExecutor;
    private Screen screen;
    private Terminal terminal;
    private volatile boolean running;
    private int selectedIndex = 0;
    private List<String> applicationIds = new ArrayList<>();
    private String statusMessage = "Ready";

    /**
     * Creates a new terminal console for the given platform manager.
     *
     * @param platformManager the platform manager to control
     * @throws IllegalArgumentException if platformManager is null
     */
    public TerminalConsole(PlatformManager platformManager) {
        if (platformManager == null) {
            throw new IllegalArgumentException("PlatformManager cannot be null");
        }

        this.platformManager = platformManager;
        this.refreshExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "terminal-ui-refresh");
            thread.setDaemon(true);
            return thread;
        });

        logger.info("Terminal console initialized");
    }

    /**
     * Starts the terminal UI and enters the main event loop.
     * This method blocks until the user quits (presses 'q').
     *
     * @throws IOException if terminal initialization fails
     */
    public void start() throws IOException {
        initializeTerminal();
        running = true;

        // Start auto-refresh
        refreshExecutor.scheduleAtFixedRate(() -> {
            try {
                if (running) {
                    refreshApplicationList();
                    render();
                }
            } catch (Exception e) {
                logger.error("Error during auto-refresh", e);
            }
        }, 2, 2, TimeUnit.SECONDS);

        // Initial render
        refreshApplicationList();
        render();

        // Main event loop
        try {
            mainLoop();
        } finally {
            shutdown();
        }
    }

    /**
     * Initializes the terminal and screen.
     */
    private void initializeTerminal() throws IOException {
        DefaultTerminalFactory terminalFactory = new DefaultTerminalFactory();
        terminal = terminalFactory.createTerminal();
        screen = new TerminalScreen(terminal);
        screen.startScreen();
        screen.setCursorPosition(null); // Hide cursor

        logger.debug("Terminal initialized");
    }

    /**
     * Main event loop - processes keyboard input.
     */
    private void mainLoop() throws IOException {
        while (running) {
            KeyStroke keyStroke = screen.pollInput();
            if (keyStroke != null) {
                handleInput(keyStroke);
                render();
            }
            Thread.yield();
        }
    }

    /**
     * Handles keyboard input.
     */
    private void handleInput(KeyStroke keyStroke) {
        if (keyStroke.getKeyType() == KeyType.Character) {
            char c = keyStroke.getCharacter();
            switch (c) {
                case 'q':
                case 'Q':
                    running = false;
                    break;
                case 'r':
                case 'R':
                    refreshApplicationList();
                    statusMessage = "Refreshed";
                    break;
                case 's':
                case 'S':
                    startSelectedApplication();
                    break;
                case 't':
                case 'T':
                    stopSelectedApplication();
                    break;
                case 'u':
                case 'U':
                    undeploySelectedApplication();
                    break;
                case 'd':
                case 'D':
                    statusMessage = "Deploy: Use REST API or file watcher (not available in terminal UI)";
                    break;
                case 'j':
                    moveSelectionDown();
                    break;
                case 'k':
                    moveSelectionUp();
                    break;
            }
        } else if (keyStroke.getKeyType() == KeyType.ArrowDown) {
            moveSelectionDown();
        } else if (keyStroke.getKeyType() == KeyType.ArrowUp) {
            moveSelectionUp();
        }
    }

    /**
     * Moves selection down in the application list.
     */
    private void moveSelectionDown() {
        if (!applicationIds.isEmpty() && selectedIndex < applicationIds.size() - 1) {
            selectedIndex++;
        }
    }

    /**
     * Moves selection up in the application list.
     */
    private void moveSelectionUp() {
        if (selectedIndex > 0) {
            selectedIndex--;
        }
    }

    /**
     * Refreshes the application list from the platform manager.
     */
    private void refreshApplicationList() {
        try {
            Map<String, ApplicationState> apps = platformManager.listApplications();
            applicationIds = new ArrayList<>(apps.keySet());
            Collections.sort(applicationIds);

            // Adjust selection if needed
            if (selectedIndex >= applicationIds.size() && !applicationIds.isEmpty()) {
                selectedIndex = applicationIds.size() - 1;
            }
            if (applicationIds.isEmpty()) {
                selectedIndex = 0;
            }

        } catch (Exception e) {
            logger.error("Error refreshing application list", e);
            statusMessage = "Error: " + e.getMessage();
        }
    }

    /**
     * Starts the selected application.
     */
    private void startSelectedApplication() {
        if (selectedIndex >= 0 && selectedIndex < applicationIds.size()) {
            String appId = applicationIds.get(selectedIndex);
            try {
                platformManager.start(appId);
                statusMessage = "Started: " + appId;
                refreshApplicationList();
            } catch (Exception e) {
                logger.error("Failed to start {}", appId, e);
                statusMessage = "Error starting " + appId + ": " + e.getMessage();
            }
        } else {
            statusMessage = "No application selected";
        }
    }

    /**
     * Stops the selected application.
     */
    private void stopSelectedApplication() {
        if (selectedIndex >= 0 && selectedIndex < applicationIds.size()) {
            String appId = applicationIds.get(selectedIndex);
            try {
                platformManager.stop(appId);
                statusMessage = "Stopped: " + appId;
                refreshApplicationList();
            } catch (Exception e) {
                logger.error("Failed to stop {}", appId, e);
                statusMessage = "Error stopping " + appId + ": " + e.getMessage();
            }
        } else {
            statusMessage = "No application selected";
        }
    }

    /**
     * Undeploys the selected application.
     */
    private void undeploySelectedApplication() {
        if (selectedIndex >= 0 && selectedIndex < applicationIds.size()) {
            String appId = applicationIds.get(selectedIndex);
            try {
                platformManager.undeploy(appId);
                statusMessage = "Undeployed: " + appId;
                refreshApplicationList();
            } catch (Exception e) {
                logger.error("Failed to undeploy {}", appId, e);
                statusMessage = "Error undeploying " + appId + ": " + e.getMessage();
            }
        } else {
            statusMessage = "No application selected";
        }
    }

    /**
     * Renders the UI to the terminal screen.
     */
    private void render() throws IOException {
        screen.clear();
        TextGraphics graphics = screen.newTextGraphics();
        TerminalSize size = screen.getTerminalSize();

        int row = 0;

        // Title
        graphics.setForegroundColor(TextColor.ANSI.WHITE);
        graphics.setBackgroundColor(TextColor.ANSI.BLUE);
        String title = " JPlatform Terminal Console ";
        graphics.putString(0, row++, padRight(title, size.getColumns()));
        graphics.setBackgroundColor(TextColor.ANSI.BLACK);

        row++;

        // Column headers
        graphics.setForegroundColor(TextColor.ANSI.CYAN);
        String header = String.format("%-20s %-12s %-15s %-15s %-10s",
                "Application ID", "State", "CPU (ms)", "Heap (MB)", "Threads");
        graphics.putString(0, row++, header);

        // Separator
        graphics.setForegroundColor(TextColor.ANSI.WHITE);
        graphics.putString(0, row++, repeat("-", Math.min(80, size.getColumns())));

        // Application list
        int listStartRow = row;
        for (int i = 0; i < applicationIds.size() && row < size.getRows() - 3; i++) {
            String appId = applicationIds.get(i);
            renderApplicationRow(graphics, row++, appId, i == selectedIndex);
        }

        // Help text
        row = size.getRows() - 2;
        graphics.setForegroundColor(TextColor.ANSI.YELLOW);
        graphics.putString(0, row++, "Keys: ↑/↓=navigate  s=start  t=stop  u=undeploy  r=refresh  q=quit");

        // Status bar
        graphics.setForegroundColor(TextColor.ANSI.WHITE);
        graphics.setBackgroundColor(TextColor.ANSI.BLACK);
        String status = String.format(" %s | Apps: %d ",
                statusMessage, applicationIds.size());
        graphics.putString(0, row, padRight(status, size.getColumns()));

        screen.refresh();
    }

    /**
     * Renders a single application row.
     */
    private void renderApplicationRow(TextGraphics graphics, int row, String appId, boolean selected) {
        try {
            ApplicationState state = platformManager.listApplications().get(appId);
            String cpuTime = "-";
            String heapUsed = "-";
            String threadCount = "-";

            // Get metrics if available
            try {
                if (state == ApplicationState.RUNNING || state == ApplicationState.STOPPED) {
                    var context = platformManager.getApplicationContext(appId);
                    if (context != null && context.getResourceMonitor() != null) {
                        var snapshot = context.getResourceMonitor().getCurrentSnapshot();
                        cpuTime = String.format("%.2f", snapshot.getCpuTimeNanos() / 1_000_000.0);
                        heapUsed = String.format("%.2f", snapshot.getHeapUsedBytes() / (1024.0 * 1024.0));
                        threadCount = String.valueOf(snapshot.getThreadCount());
                    }
                }
            } catch (Exception e) {
                logger.debug("Could not get metrics for {}", appId);
            }

            // Color code by state
            if (selected) {
                graphics.setForegroundColor(TextColor.ANSI.BLACK);
                graphics.setBackgroundColor(TextColor.ANSI.WHITE);
            } else {
                graphics.setBackgroundColor(TextColor.ANSI.BLACK);
                switch (state) {
                    case RUNNING:
                        graphics.setForegroundColor(TextColor.ANSI.GREEN);
                        break;
                    case STOPPED:
                        graphics.setForegroundColor(TextColor.ANSI.YELLOW);
                        break;
                    case FAILED:
                        graphics.setForegroundColor(TextColor.ANSI.RED);
                        break;
                    default:
                        graphics.setForegroundColor(TextColor.ANSI.WHITE);
                }
            }

            String line = String.format("%-20s %-12s %-15s %-15s %-10s",
                    truncate(appId, 20),
                    state.name(),
                    cpuTime,
                    heapUsed,
                    threadCount);

            graphics.putString(0, row, line);

        } catch (Exception e) {
            logger.error("Error rendering row for {}", appId, e);
            graphics.setForegroundColor(TextColor.ANSI.RED);
            graphics.putString(0, row, "Error rendering: " + appId);
        }

        // Reset background
        graphics.setBackgroundColor(TextColor.ANSI.BLACK);
    }

    /**
     * Pads a string to the right with spaces.
     */
    private String padRight(String s, int length) {
        if (s.length() >= length) {
            return s.substring(0, length);
        }
        return s + repeat(" ", length - s.length());
    }

    /**
     * Repeats a string n times.
     */
    private String repeat(String s, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Truncates a string to max length.
     */
    private String truncate(String s, int maxLength) {
        if (s.length() <= maxLength) {
            return s;
        }
        return s.substring(0, maxLength - 3) + "...";
    }

    /**
     * Shuts down the console and releases resources.
     */
    public void shutdown() {
        running = false;
        refreshExecutor.shutdown();
        try {
            if (!refreshExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                refreshExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            refreshExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            if (screen != null) {
                screen.stopScreen();
            }
            if (terminal != null) {
                terminal.close();
            }
        } catch (IOException e) {
            logger.error("Error closing terminal", e);
        }

        logger.info("Terminal console shut down");
    }

    /**
     * Main method for standalone execution.
     */
    public static void main(String[] args) {
        System.out.println("JPlatform Terminal Console requires a running PlatformManager instance.");
        System.out.println("This is a demonstration stub. Integrate with jplatform-launcher for full functionality.");
        System.exit(1);
    }
}
