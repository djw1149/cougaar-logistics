package org.cougaar.logistics.ui.stoplight.client.aggregator;

import java.awt.*;
import javax.swing.*;
import java.util.Collection;
import java.util.Iterator;

import org.cougaar.lib.uiframework.ui.components.CRadioButtonSelectionControl;

import org.cougaar.lib.aggagent.query.ScriptSpec;
import org.cougaar.lib.aggagent.util.Enum.*;

/**
 * Used by QueryEditor and AlertEditor for directly editing scripts.
 */
public class ScriptEditor extends JPanel
{
  protected final static int spacing = 5;
  protected int gridyCount = 1;
  protected JPanel xmlSelectorBox;
  protected CRadioButtonSelectionControl languageSelector;
  protected JTextArea script;

  public ScriptEditor () {
    super(new BorderLayout());
    createComponents();
  }

  public ScriptEditor (String title) {
    this();
    setBorder(BorderFactory.createTitledBorder(title));
  }

  public void setLanguage(Language l)
  {
    languageSelector.setSelectedItem(l.toString());
  }

  public Language getLanguage()
  {
    return Language.fromString((String)languageSelector.getSelectedItem());
  }

  public void setScript(String scriptString)
  {
    script.setText(scriptString);
  }

  public String getScript()
  {
    return script.getText();
  }

  /**
   *  Wants for implementation
   */
  public ScriptSpec getScriptSpec () {
    return null;
  }

  /**
   *  Wants for implementation
   */
  public void setScriptSpec (ScriptSpec ss) {
  }

  public void addAuxControl(Component c)
  {
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.gridy = gridyCount++;
    gbc.weightx = 1;
    gbc.weighty = 1;
    gbc.insets = new Insets(0, spacing, 0, 0);
    xmlSelectorBox.add(c, gbc);
  }

  public Dimension getControlSize()
  {
    return xmlSelectorBox.getPreferredSize();
  }

  public void setControlSize(Dimension d)
  {
    xmlSelectorBox.setPreferredSize(d);
  }

  protected void createComponents () {
    xmlSelectorBox = new JPanel();
    xmlSelectorBox.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.weightx = 1;
    gbc.weighty = 1;
    languageSelector = createLanguageSelector();
    xmlSelectorBox.add(languageSelector, gbc);

    script = new JTextArea();
    JScrollPane scrolledScript = new JScrollPane(script);

    // don't let preferred size of scrolled pane increase when text is added
    // to text area
    scrolledScript.setPreferredSize(scrolledScript.getPreferredSize());

    setLayout(new BorderLayout());
    add(xmlSelectorBox, BorderLayout.EAST);
    add(scrolledScript, BorderLayout.CENTER);
  }

  protected CRadioButtonSelectionControl createLanguageSelector()
  {
    String[] scriptingLanguages =
      collectionToStringArray(Language.getValidValues());
    CRadioButtonSelectionControl control =
      new CRadioButtonSelectionControl(scriptingLanguages, BoxLayout.Y_AXIS);
    control.setSelectedItem(Language.SILK.toString());
    return control;
  }

  private String[] collectionToStringArray(Collection col)
  {
    String[] sa = new String[col.size()];
    int count = 0;
    for (Iterator i = col.iterator(); i.hasNext();)
    {
      sa[count++] = i.next().toString();
    }
    return sa;
  }
}