/*
 * Copyright (C) 2024-2026 FlossWare
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.flossware.platform.swing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import org.flossware.platform.api.ApplicationState;
import org.flossware.platform.api.PlatformManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Swing-based desktop UI for platform-java management.
 *
 * <p>Provides a graphical interface for:
 *
 * <ul>
 *   <li>Deploying applications via file chooser
 *   <li>Starting, stopping, and undeploying applications
 *   <li>Viewing application status and metrics
 *   <li>Real-time metrics updates
 * </ul>
 *
 * <p>The UI consists of:
 *
 * <ul>
 *   <li>Application list table with ID, state, and basic metrics
 *   <li>Control buttons (Deploy, Start, Stop, Undeploy, Refresh)
 *   <li>Status bar showing platform status
 *   <li>Auto-refresh every 2 seconds for real-time updates
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * PlatformManager manager = new ApplicationManager();
 * SwingConsole console = new SwingConsole(manager);
 * console.show();
 * }</pre>
 *
 * @since 2.0
 */
public class SwingConsole {

  private static final Logger LOGGER = LoggerFactory.getLogger(SwingConsole.class);

  private final PlatformManager platformManager;
  private final JFrame frame;
  private final JTable applicationTable;
  private final DefaultTableModel tableModel;
  private final JLabel statusLabel;
  private final ScheduledExecutorService refreshExecutor;

  /**
   * Creates a new Swing console for the given platform manager.
   *
   * @param platformManager the platform manager to control
   */
  public SwingConsole(PlatformManager platformManager) {
    if (platformManager == null) {
      throw new IllegalArgumentException("PlatformManager cannot be null");
    }

    this.platformManager = platformManager;
    this.frame = new JFrame("JPlatform Management Console");
    this.tableModel =
        new DefaultTableModel(
            new String[] {"Application ID", "State", "CPU Time (ms)", "Heap (MB)", "Threads"}, 0) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return false; // Read-only table
          }
        };
    this.applicationTable = new JTable(tableModel);
    this.statusLabel = new JLabel("Ready");
    this.refreshExecutor =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread thread = new Thread(r, "swing-ui-refresh");
              thread.setDaemon(true);
              return thread;
            });

    initializeUI();
    startAutoRefresh();

    LOGGER.info("Swing console initialized");
  }

  /** Initializes the Swing UI components and layout. */
  private void initializeUI() {
    frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    frame.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent e) {
            shutdown();
          }
        });
    frame.setSize(900, 600);
    frame.setLayout(new BorderLayout());

    // Title panel
    JPanel titlePanel = new JPanel();
    titlePanel.setBackground(new Color(41, 128, 185));
    JLabel titleLabel = new JLabel("JPlatform Management Console");
    titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
    titleLabel.setForeground(Color.WHITE);
    titlePanel.add(titleLabel);
    frame.add(titlePanel, BorderLayout.NORTH);

    // Center panel with table
    JScrollPane scrollPane = new JScrollPane(applicationTable);
    applicationTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    applicationTable.setRowHeight(25);
    applicationTable.getTableHeader().setFont(new Font("Arial", Font.BOLD, 12));
    frame.add(scrollPane, BorderLayout.CENTER);

    // Button panel
    JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

    JButton deployButton = new JButton("Deploy");
    JButton startButton = new JButton("Start");
    JButton stopButton = new JButton("Stop");
    JButton undeployButton = new JButton("Undeploy");
    JButton refreshButton = new JButton("Refresh");

    deployButton.addActionListener(e -> deployApplication());
    startButton.addActionListener(e -> startSelectedApplication());
    stopButton.addActionListener(e -> stopSelectedApplication());
    undeployButton.addActionListener(e -> undeploySelectedApplication());
    refreshButton.addActionListener(e -> refreshApplicationList());

    buttonPanel.add(deployButton);
    buttonPanel.add(startButton);
    buttonPanel.add(stopButton);
    buttonPanel.add(undeployButton);
    buttonPanel.add(new JSeparator(SwingConstants.VERTICAL));
    buttonPanel.add(refreshButton);

    frame.add(buttonPanel, BorderLayout.SOUTH);

    // Status bar
    JPanel statusPanel = new JPanel(new BorderLayout());
    statusPanel.setBorder(BorderFactory.createEtchedBorder());
    statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
    statusPanel.add(statusLabel, BorderLayout.WEST);
    frame.add(statusPanel, BorderLayout.PAGE_END);

    // Center the frame on screen
    frame.setLocationRelativeTo(null);

    // Initial data load
    refreshApplicationList();
  }

  /** Starts automatic refresh of the application list every 2 seconds. */
  private void startAutoRefresh() {
    refreshExecutor.scheduleAtFixedRate(
        () -> {
          try {
            SwingUtilities.invokeLater(this::refreshApplicationList);
          } catch (Exception e) {
            LOGGER.error("Error during auto-refresh", e);
          }
        },
        2,
        2,
        TimeUnit.SECONDS);

    LOGGER.debug("Auto-refresh started (2-second interval)");
  }

  /** Refreshes the application list from the platform manager. */
  private void refreshApplicationList() {
    try {
      Map<String, ApplicationState> applications = platformManager.listApplications();

      // Clear existing rows
      tableModel.setRowCount(0);

      // Add application rows
      for (Map.Entry<String, ApplicationState> entry : applications.entrySet()) {
        String appId = entry.getKey();
        ApplicationState state = entry.getValue();

        // Get metrics if available
        String cpuTime = "-";
        String heapUsed = "-";
        String threadCount = "-";

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
          LOGGER.debug("Could not get metrics for {}: {}", appId, e.getMessage());
        }

        tableModel.addRow(new Object[] {appId, state.name(), cpuTime, heapUsed, threadCount});
      }

      statusLabel.setText(String.format("Ready - %d application(s)", applications.size()));

    } catch (Exception e) {
      LOGGER.error("Error refreshing application list", e);
      statusLabel.setText("Error: " + e.getMessage());
    }
  }

  /** Shows a file chooser dialog to deploy an application. */
  private void deployApplication() {
    JFileChooser fileChooser = new JFileChooser();
    fileChooser.setDialogTitle("Select Application JAR");
    fileChooser.setFileFilter(
        new javax.swing.filechooser.FileNameExtensionFilter("JAR Files", "jar"));

    if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
      String jarPath = fileChooser.getSelectedFile().getAbsolutePath();

      // Show dialog to get application ID and main class
      DeployDialog dialog = new DeployDialog(frame, jarPath);
      dialog.setVisible(true);

      if (dialog.isConfirmed()) {
        try {
          statusLabel.setText("Deploying " + dialog.getApplicationId() + "...");

          var descriptor =
              org.flossware.platform.api.ApplicationDescriptor.builder()
                  .applicationId(dialog.getApplicationId())
                  .mainClass(dialog.getMainClass())
                  .addClasspathEntry(Paths.get(jarPath).toUri())
                  .build();

          platformManager.deploy(descriptor);

          statusLabel.setText("Deployed: " + dialog.getApplicationId());
          refreshApplicationList();

        } catch (Exception e) {
          LOGGER.error("Deployment failed", e);
          JOptionPane.showMessageDialog(
              frame,
              "Deployment failed: " + e.getMessage(),
              "Deployment Error",
              JOptionPane.ERROR_MESSAGE);
          statusLabel.setText("Deployment failed");
        }
      }
    }
  }

  /** Starts the selected application. */
  private void startSelectedApplication() {
    String appId = getSelectedApplicationId();
    if (appId == null) {
      JOptionPane.showMessageDialog(
          frame, "Please select an application", "No Selection", JOptionPane.WARNING_MESSAGE);
      return;
    }

    try {
      statusLabel.setText("Starting " + appId + "...");
      platformManager.start(appId);
      statusLabel.setText("Started: " + appId);
      refreshApplicationList();
    } catch (Exception e) {
      LOGGER.error("Start failed for {}", appId, e);
      JOptionPane.showMessageDialog(
          frame, "Start failed: " + e.getMessage(), "Start Error", JOptionPane.ERROR_MESSAGE);
      statusLabel.setText("Start failed");
    }
  }

  /** Stops the selected application. */
  private void stopSelectedApplication() {
    String appId = getSelectedApplicationId();
    if (appId == null) {
      JOptionPane.showMessageDialog(
          frame, "Please select an application", "No Selection", JOptionPane.WARNING_MESSAGE);
      return;
    }

    try {
      statusLabel.setText("Stopping " + appId + "...");
      platformManager.stop(appId);
      statusLabel.setText("Stopped: " + appId);
      refreshApplicationList();
    } catch (Exception e) {
      LOGGER.error("Stop failed for {}", appId, e);
      JOptionPane.showMessageDialog(
          frame, "Stop failed: " + e.getMessage(), "Stop Error", JOptionPane.ERROR_MESSAGE);
      statusLabel.setText("Stop failed");
    }
  }

  /** Undeploys the selected application. */
  private void undeploySelectedApplication() {
    String appId = getSelectedApplicationId();
    if (appId == null) {
      JOptionPane.showMessageDialog(
          frame, "Please select an application", "No Selection", JOptionPane.WARNING_MESSAGE);
      return;
    }

    int confirm =
        JOptionPane.showConfirmDialog(
            frame,
            "Are you sure you want to undeploy '" + appId + "'?",
            "Confirm Undeploy",
            JOptionPane.YES_NO_OPTION);

    if (confirm == JOptionPane.YES_OPTION) {
      try {
        statusLabel.setText("Undeploying " + appId + "...");
        platformManager.undeploy(appId);
        statusLabel.setText("Undeployed: " + appId);
        refreshApplicationList();
      } catch (Exception e) {
        LOGGER.error("Undeploy failed for {}", appId, e);
        JOptionPane.showMessageDialog(
            frame,
            "Undeploy failed: " + e.getMessage(),
            "Undeploy Error",
            JOptionPane.ERROR_MESSAGE);
        statusLabel.setText("Undeploy failed");
      }
    }
  }

  /**
   * Gets the application ID of the currently selected row.
   *
   * @return the application ID, or null if no selection
   */
  private String getSelectedApplicationId() {
    int selectedRow = applicationTable.getSelectedRow();
    if (selectedRow < 0) {
      return null;
    }
    return (String) tableModel.getValueAt(selectedRow, 0);
  }

  /** Shows the Swing console window. */
  public void show() {
    SwingUtilities.invokeLater(
        () -> {
          frame.setVisible(true);
          LOGGER.info("Swing console displayed");
        });
  }

  /** Hides the Swing console window. */
  public void hide() {
    SwingUtilities.invokeLater(
        () -> {
          frame.setVisible(false);
          LOGGER.info("Swing console hidden");
        });
  }

  /** Shuts down the console and releases resources. */
  public void shutdown() {
    refreshExecutor.shutdown();
    try {
      if (!refreshExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
        refreshExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      refreshExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }

    SwingUtilities.invokeLater(
        () -> {
          frame.dispose();
          LOGGER.info("Swing console shut down");
        });
  }

  /**
   * Returns the Frame for testing purposes. Package-private for unit tests.
   *
   * @return the main frame
   */
  JFrame getFrame() {
    return frame;
  }

  /**
   * Returns the table model for testing purposes. Package-private for unit tests.
   *
   * @return the table model
   */
  DefaultTableModel getTableModel() {
    return tableModel;
  }

  /**
   * Returns the status label for testing purposes. Package-private for unit tests.
   *
   * @return the status label
   */
  JLabel getStatusLabel() {
    return statusLabel;
  }
}
