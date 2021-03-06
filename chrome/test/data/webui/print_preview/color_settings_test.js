// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

cr.define('color_settings_test', function() {
  suite('ColorSettingsTest', function() {
    /** @type {?PrintPreviewColorSettingsElement} */
    let colorSection = null;

    /** @type {?PrintPreviewModelElement} */
    let model = null;

    /** @override */
    setup(function() {
      PolymerTest.clearBody();
      model = document.createElement('print-preview-model');
      document.body.appendChild(model);

      colorSection = document.createElement('print-preview-color-settings');
      colorSection.settings = model.settings;
      colorSection.disabled = false;
      test_util.fakeDataBind(model, colorSection, 'settings');
      model.set('settings.color.available', true);
      document.body.appendChild(colorSection);
    });

    // Tests that setting the setting updates the UI.
    test('set setting', async () => {
      const select = colorSection.$$('select');
      assertEquals('color', select.value);

      colorSection.setSetting('color', false);
      await test_util.eventToPromise('process-select-change', colorSection);
      assertEquals('bw', select.value);
    });

    // Tests that selecting a new option in the dropdown updates the setting.
    test('select option', async () => {
      // Verify that the selected option and names are as expected.
      const select = colorSection.$$('select');
      assertEquals('color', select.value);
      assertTrue(colorSection.getSettingValue('color'));
      assertFalse(colorSection.getSetting('color').setFromUi);
      assertEquals(2, select.options.length);

      // Verify that selecting an new option in the dropdown sets the setting.
      await print_preview_test_utils.selectOption(colorSection, 'bw');
      assertFalse(colorSection.getSettingValue('color'));
      assertTrue(colorSection.getSetting('color').setFromUi);
    });

    if (cr.isChromeOS) {
      // Tests that if the setting is enforced by enterprise policy it is
      // disabled.
      test('disabled by policy', function() {
        // Verify that the selected option and names are as expected.
        const select = colorSection.$$('select');
        assertFalse(select.disabled);

        model.set('settings.color.setByPolicy', true);
        assertTrue(select.disabled);
      });
    }
  });
});
