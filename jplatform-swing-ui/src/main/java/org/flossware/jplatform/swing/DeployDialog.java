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

package org.flossware.jplatform.swing;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog for collecting deployment information.
 *
 * <p>Prompts the user for:</p>
 * <ul>
 *   <li>Application ID - unique identifier for the application</li>
 *   <li>Main Class - fully-qualified main class name</li>
 * </ul>
 *
 * <p>The dialog displays the JAR path for reference and provides OK/Cancel buttons.</p>
 *
 * @since 2.0
 */
class DeployDialog extends Dialog {

    private final TextField applicationIdField;
    private final TextField mainClassField;
    private boolean confirmed = false;

    /**
     * Creates a new deploy dialog.
     *
     * @param parent the parent frame
     * @param jarPath the path to the JAR file being deployed
     */
    public DeployDialog(Frame parent, String jarPath) {
        super(parent, "Deploy Application", true);

        setLayout(new BorderLayout(10, 10));
        setSize(500, 250);
        setLocationRelativeTo(parent);

        // Form panel
        Panel formPanel = new Panel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // JAR path (read-only)
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.3;
        formPanel.add(new Label("JAR File:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        TextField jarPathField = new TextField(jarPath);
        jarPathField.setEditable(false);
        jarPathField.setBackground(Color.LIGHT_GRAY);
        formPanel.add(jarPathField, gbc);

        // Application ID
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.3;
        formPanel.add(new Label("Application ID:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        applicationIdField = new TextField();
        formPanel.add(applicationIdField, gbc);

        // Main class
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.3;
        formPanel.add(new Label("Main Class:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.7;
        mainClassField = new TextField();
        formPanel.add(mainClassField, gbc);

        add(formPanel, BorderLayout.CENTER);

        // Button panel
        Panel buttonPanel = new Panel(new FlowLayout(FlowLayout.RIGHT));
        Button okButton = new Button("Deploy");
        Button cancelButton = new Button("Cancel");

        okButton.addActionListener(e -> {
            if (validateInput()) {
                confirmed = true;
                dispose();
            }
        });

        cancelButton.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Set default button
        getRootPane().setDefaultButton(okButton);
    }

    /**
     * Validates the input fields.
     *
     * @return true if input is valid, false otherwise
     */
    private boolean validateInput() {
        String appId = applicationIdField.getText().trim();
        String mainClass = mainClassField.getText().trim();

        if (appId.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Application ID is required",
                    "Validation Error",
                    JOptionPane.ERROR_MESSAGE);
            applicationIdField.requestFocus();
            return false;
        }

        if (mainClass.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Main Class is required",
                    "Validation Error",
                    JOptionPane.ERROR_MESSAGE);
            mainClassField.requestFocus();
            return false;
        }

        // Basic validation of main class format
        if (!mainClass.matches("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$")) {
            JOptionPane.showMessageDialog(this,
                    "Main Class must be a valid Java class name (e.g., com.example.MyApp)",
                    "Validation Error",
                    JOptionPane.ERROR_MESSAGE);
            mainClassField.requestFocus();
            return false;
        }

        return true;
    }

    /**
     * Checks if the user confirmed the deployment.
     *
     * @return true if Deploy was clicked, false if Cancel
     */
    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Gets the entered application ID.
     *
     * @return the application ID
     */
    public String getApplicationId() {
        return applicationIdField.getText().trim();
    }

    /**
     * Gets the entered main class.
     *
     * @return the main class
     */
    public String getMainClass() {
        return mainClassField.getText().trim();
    }
}
