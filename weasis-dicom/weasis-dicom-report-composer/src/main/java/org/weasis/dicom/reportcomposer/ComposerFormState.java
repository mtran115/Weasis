/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.reportcomposer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JComboBox;
import javax.swing.JToggleButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/**
 * Captures pending form input separately from reviewed findings. Called only on the Swing thread.
 */
final class ComposerFormState {
  private ComposerFormState() {}

  static JsonNode capture(Component root) {
    ArrayNode values = JsonNodeFactory.instance.arrayNode();
    visit(
        root,
        "",
        (component, path) -> {
          ObjectNode value = JsonNodeFactory.instance.objectNode();
          value.put("path", path);
          value.put("class", component.getClass().getName());
          if (component instanceof JToggleButton button) {
            value.put("kind", "toggle");
            value.put("label", button.getText());
            value.put("selected", button.isSelected());
          } else if (component instanceof JComboBox<?> combo) {
            value.put("kind", "choice");
            value.put("value", choiceKey(combo.getSelectedItem()));
          } else if (component instanceof JTextComponent text) {
            value.put("kind", "text");
            value.put("value", text.getText());
          } else {
            return;
          }
          values.add(value);
        });
    return values;
  }

  static void restore(Component root, JsonNode values) {
    if (values == null || !values.isArray()) return;
    // Text goes last, after dependent choice listeners have refreshed any defaults.
    for (String kind : new String[] {"choice", "toggle", "text"}) {
      for (JsonNode value : values) {
        if (!kind.equals(value.path("kind").asText())) continue;
        Component component = atPath(root, value.path("path").asText());
        if (component == null
            || !component.getClass().getName().equals(value.path("class").asText())) continue;
        if (component instanceof JToggleButton button
            && java.util.Objects.equals(button.getText(), value.path("label").asText())) {
          boolean selected = value.path("selected").asBoolean();
          if (button.isSelected() != selected) {
            // Action listeners also update dependent enabled controls.
            boolean enabled = button.isEnabled();
            button.setEnabled(true);
            button.doClick(0);
            button.setEnabled(enabled);
          }
        } else if (component instanceof JComboBox<?> combo) {
          for (int i = 0; i < combo.getItemCount(); i++) {
            if (choiceKey(combo.getItemAt(i)).equals(value.path("value").asText())) {
              combo.setSelectedIndex(i);
              break;
            }
          }
        } else if (component instanceof JTextComponent text) {
          text.setText(value.path("value").asText());
        }
      }
    }
  }

  static void watch(Component root, Runnable changed) {
    visit(
        root,
        "",
        (component, path) -> {
          if (component instanceof JToggleButton button) {
            button.addItemListener(event -> changed.run());
          } else if (component instanceof JComboBox<?> combo) {
            combo.addActionListener(event -> changed.run());
          } else if (component instanceof JTextComponent text) {
            text.getDocument()
                .addDocumentListener(
                    new DocumentListener() {
                      public void insertUpdate(DocumentEvent event) {
                        changed.run();
                      }

                      public void removeUpdate(DocumentEvent event) {
                        changed.run();
                      }

                      public void changedUpdate(DocumentEvent event) {
                        changed.run();
                      }
                    });
          }
        });
  }

  private static String choiceKey(Object value) {
    return value instanceof Enum<?> e ? e.name() : java.util.Objects.toString(value, "");
  }

  private static Component atPath(Component root, String path) {
    Component current = root;
    if (path.isEmpty()) return current;
    for (String index : path.split("\\.")) {
      if (!(current instanceof Container container)) return null;
      try {
        int child = Integer.parseInt(index);
        if (child < 0 || child >= container.getComponentCount()) return null;
        current = container.getComponent(child);
      } catch (NumberFormatException error) {
        return null;
      }
    }
    return current;
  }

  private static void visit(
      Component component, String path, java.util.function.BiConsumer<Component, String> visitor) {
    visitor.accept(component, path);
    if (component instanceof Container container) {
      for (int i = 0; i < container.getComponentCount(); i++) {
        visit(
            container.getComponent(i),
            path.isEmpty() ? Integer.toString(i) : path + "." + i,
            visitor);
      }
    }
  }
}
