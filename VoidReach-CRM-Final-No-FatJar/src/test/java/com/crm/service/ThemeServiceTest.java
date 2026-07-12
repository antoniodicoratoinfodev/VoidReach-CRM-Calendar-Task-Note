package com.crm.service;

import javafx.css.CssParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeServiceTest {
    @Test
    void cyclesThroughDarkLightAndBlueGrayThemes() {
        ThemeService service = new ThemeService(() -> null);

        assertEquals(ThemeService.Theme.DARK, service.activeTheme());
        assertTrue(service.isDarkMode());

        service.toggle();
        assertEquals(ThemeService.Theme.LIGHT, service.activeTheme());
        assertFalse(service.isDarkMode());

        service.toggle();
        assertEquals(ThemeService.Theme.BLUE_GRAY, service.activeTheme());
        assertTrue(service.isDarkMode());
        assertTrue(service.isBlueGrayTheme());

        service.toggle();
        assertEquals(ThemeService.Theme.DARK, service.activeTheme());
    }

    @Test
    void restoresStoredThemeAndFallsBackToDarkForInvalidValues() {
        ThemeService service = new ThemeService(() -> null);

        service.restore("BLUE_GRAY");
        assertEquals(ThemeService.Theme.BLUE_GRAY, service.activeTheme());

        service.restore("not-a-theme");
        assertEquals(ThemeService.Theme.DARK, service.activeTheme());

        service.restore(null);
        assertEquals(ThemeService.Theme.DARK, service.activeTheme());
    }

    @Test
    void themeStylesheetsArePackagedAndValid() throws IOException {
        for (String resource : new String[]{"/css/style.css", "/css/style-dark.css", "/css/style-blue-gray.css"}) {
            URL stylesheet = ThemeService.class.getResource(resource);
            assertNotNull(stylesheet);

            CssParser.errorsProperty().clear();
            new CssParser().parse(stylesheet);
            assertTrue(CssParser.errorsProperty().isEmpty(),
                    resource + ": " + CssParser.errorsProperty());
        }
    }
}
