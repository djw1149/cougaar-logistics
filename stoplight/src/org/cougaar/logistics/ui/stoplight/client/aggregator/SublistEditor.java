/*
 * <copyright>
 *  
 *  Copyright 2003-2004 BBNT Solutions, LLC
 *  under sponsorship of the Defense Advanced Research Projects
 *  Agency (DARPA).
 * 
 *  You can redistribute this software and/or modify it under the
 *  terms of the Cougaar Open Source License as published on the
 *  Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 *  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 *  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *  
 * </copyright>
 */
package org.cougaar.logistics.ui.stoplight.client.aggregator;

import java.awt.*;
import java.awt.event.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import javax.swing.*;
import javax.swing.event.*;

import org.cougaar.logistics.ui.stoplight.ui.components.CFrame;

/**
 * A reuseable component used for selecting sublists
 */
public class SublistEditor extends JPanel
{
  private static int spacing = 10;

  private Collection orgUserList;
  private JList userList;
  private JList masterList;
  private JButton addButton;
  private JButton removeButton;

  public SublistEditor(String userListTitle, Collection orgUserList,
                       String masterListTitle, Collection orgMasterList)
  {
    super(new GridBagLayout());
    this.orgUserList = orgUserList;

    createComponents(userListTitle, masterListTitle, orgMasterList);
  }

  public Collection getUserList()
  {
    LinkedList ul = new LinkedList();
    for (int i = 0; i < userList.getModel().getSize(); i++)
    {
      ul.add(userList.getModel().getElementAt(i));
    }

    return ul;
  }

  /*
  public void reset()
  {
    userList.clear();
    userList.addAll(orgUserList);
  }
  */

  private void createComponents(String userListTitle, String masterListTitle,
                                Collection orgMasterList)
  {
    // user list
    JPanel userListPanel = new JPanel(new BorderLayout());
    final DefaultListModel userListModel = new DefaultListModel();
    for (Iterator i = orgUserList.iterator(); i.hasNext();)
      userListModel.addElement(i.next());
    userList = new JList(userListModel);
    JScrollPane scrolledUserList = new JScrollPane(userList);
    scrolledUserList.setPreferredSize(new Dimension(0, 0));
    userListPanel.add(new JLabel(userListTitle, JLabel.CENTER),
                      BorderLayout.NORTH);
    userListPanel.add(scrolledUserList, BorderLayout.CENTER);

    // master list
    JPanel masterListPanel = new JPanel(new BorderLayout());
    final DefaultListModel masterListModel = new DefaultListModel();
    for (Iterator i = orgMasterList.iterator(); i.hasNext();)
    {
      Object element = i.next();
      if (!userListModel.contains(element))
      {
        masterListModel.addElement(element);
      }
    }
    masterList = new JList(masterListModel);
    JScrollPane scrolledMasterList = new JScrollPane(masterList);
    scrolledMasterList.setPreferredSize(new Dimension(0, 0));
    masterListPanel.add(new JLabel(masterListTitle, JLabel.CENTER),
                        BorderLayout.NORTH);
    masterListPanel.add(scrolledMasterList, BorderLayout.CENTER);

    // button column
    JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
    buttonPanel.add(Box.createGlue());
    addButton = new JButton("   Add ->   ");
    buttonPanel.add(addButton);
    buttonPanel.add(Box.createGlue());
    removeButton = new JButton("<- Remove");
    buttonPanel.add(removeButton);
    buttonPanel.add(Box.createGlue());

    // high level layout
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets = new Insets(spacing, spacing, spacing, spacing);
    gbc.weightx = 1;
    gbc.weighty = 1;
    gbc.fill = GridBagConstraints.BOTH;
    add(masterListPanel, gbc);
    gbc.weightx = 0;
    add(buttonPanel, gbc);
    gbc.weightx = 1;
    add(userListPanel, gbc);

    // event handling
    setEnabledState();
    userList.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e)
        {
          if (!userList.isSelectionEmpty())
          {
            masterList.clearSelection();
            setEnabledState();
          }
        }
      });

    masterList.addListSelectionListener(new ListSelectionListener() {
        public void valueChanged(ListSelectionEvent e)
        {
          if (!masterList.isSelectionEmpty())
          {
            userList.clearSelection();
            setEnabledState();
          }
        }
      });

    addButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e)
        {
          Object[] items = masterList.getSelectedValues();
          for (int i = 0; i < items.length; i++)
          {
            userListModel.addElement(items[i]);
            masterListModel.removeElement(items[i]);
          }
        }
      });

    removeButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e)
        {
          Object[] items = userList.getSelectedValues();
          for (int i = 0; i < items.length; i++)
          {
            masterListModel.addElement(items[i]);
            userListModel.removeElement(items[i]);
          }
        }
      });
  }

  private void setEnabledState()
  {
    removeButton.setEnabled(!userList.isSelectionEmpty());
    addButton.setEnabled(!masterList.isSelectionEmpty());
  }

  /*
  public void updateUI()
  {
    super.updateUI();

    if (addButton != null)
    {
      removeButton.updateUI();
      addButton.setPreferredSize(removeButton.getPreferredSize());
    }
  }
  */

  /**
   * for unit testing
   */
  public static void main(String[] args)
  {
    CFrame frame = new CFrame();
    LinkedList mainList = new LinkedList();
    mainList.add("one");
    mainList.add("two");
    mainList.add("three");
    mainList.add("four");
    mainList.add("five");
    mainList.add("six");
    mainList.add("seven");
    LinkedList subList = new LinkedList();
    subList.add("three");
    subList.add("six");
    SublistEditor se =
      new SublistEditor("User List", subList, "Master List", mainList);
    frame.getContentPane().add(se, BorderLayout.CENTER);
    frame.show();
  }
}