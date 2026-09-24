package com.example.standalone.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import javax.swing.JComponent;
import java.awt.Component;

/**
 * 明暗主题管理（基于 FlatLaf）
 */
public final class ThemeManager {
    private static boolean dark = true;

    private ThemeManager() {
    }

    public static boolean isDark() {
        return dark;
    }

    public static void toggle(Component root) {
        dark = !dark;
        try {
            if (dark) {
                UIManager.setLookAndFeel(new FlatDarkLaf());
            } else {
                UIManager.setLookAndFeel(new FlatLightLaf());
            }
            if (root instanceof JComponent comp) {
                javax.swing.SwingUtilities.updateComponentTreeUI(comp);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void init() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
