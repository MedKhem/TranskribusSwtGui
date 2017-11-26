package eu.transkribus.swt_gui.metadata;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CellLabelProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.ViewerCell;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ControlEditor;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.ColorDialog;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.customtags.CustomTag;
import eu.transkribus.core.model.beans.customtags.CustomTagAttribute;
import eu.transkribus.core.model.beans.customtags.CustomTagFactory;
import eu.transkribus.core.model.beans.customtags.CustomTagFactory.TagRegistryChangeEvent;
import eu.transkribus.swt.util.ColorChooseButton;
import eu.transkribus.swt.util.Colors;
import eu.transkribus.swt.util.CustomTagPropertyTable;
import eu.transkribus.swt.util.DialogUtil;
import eu.transkribus.swt.util.Fonts;
import eu.transkribus.swt.util.Images;
import eu.transkribus.swt_gui.mainwidget.TrpMainWidget;
import eu.transkribus.swt_gui.mainwidget.storage.IStorageListener;
import eu.transkribus.swt_gui.mainwidget.storage.Storage;

public class TagConfWidget extends Composite {
	private static final Logger logger = LoggerFactory.getLogger(TagConfWidget.class);
	
	Set<String> availableTagNames = new TreeSet<>();
	
	TableViewer availableTagsTv, tagDefsTv;
	SashForm availableTagsSf;
	CustomTagPropertyTable propsTable;

	SashForm horizontalSf;
	
//	List<ITaggingWidgetListener> listener = new ArrayList<>();
	
	Map<String, ControlEditor> addTagToListEditors = new HashMap<>();
	Map<CustomTagDef, ControlEditor> removeTagDefEditors = new HashMap<>();
	Map<CustomTagDef, ControlEditor> colorEditors = new HashMap<>();
	
	public TagConfWidget(Composite parent, int style) {
		super(parent, style);
		this.setLayout(new FillLayout());
		horizontalSf = new SashForm(this, SWT.HORIZONTAL);
		
		Composite leftWidget = new Composite(horizontalSf, 0);
		leftWidget.setLayout(new GridLayout(1, false));
				
		availableTagsSf = new SashForm(leftWidget, SWT.VERTICAL);
		availableTagsSf.setLayoutData(new GridData(GridData.FILL_BOTH));
		initTagsTable(availableTagsSf);
		initPropertyTable(availableTagsSf);
		
		Composite colorComp = new Composite(leftWidget, 0);
		colorComp.setLayout(new GridLayout(2, false));
		colorComp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		Label tagColorLbl = new Label(colorComp, 0);
		tagColorLbl.setText("Color");
		Fonts.setBoldFont(tagColorLbl);
		
		ColorChooseButton color = new ColorChooseButton(colorComp, CustomTagDef.DEFAULT_COLOR);
		color.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		Button addTagDefBtn = new Button(leftWidget, 0);
		addTagDefBtn.setText("Add tag definition");
		addTagDefBtn.setImage(Images.ADD);
		addTagDefBtn.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		addTagDefBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String tagName = getSelectedAvailableTagsName();
				if (tagName == null)
					return;
				
				try {
					logger.debug("selected tag: "+getSelectedAvailableTagsName()+", tagName: "+tagName+", curr-atts: "+getCurrentAttributes());
					CustomTag tag = CustomTagFactory.create(tagName, getCurrentAttributes());

					CustomTagDef tagDef = new CustomTagDef(tag);
					tagDef.setRGB(color.getRGB());
					
					logger.info("tagDef: "+tagDef);
					Storage.getInstance().addCustomTagDef(tagDef);
				} catch (Exception ex) {
					DialogUtil.showDetailedErrorMessageBox(getShell(), "Error adding tag definiton", ex.getMessage(), ex);
				}
			}
		});
		
		initTagDefsTable();
		
		horizontalSf.setWeights(new int[] { 50, 50 });
		availableTagsSf.setWeights(new int[] { 70, 30 });
		
		updateAvailableTags();
		updateTagDefsFromStorage();
		
		Storage.getInstance().addListener(new IStorageListener() {
			public void handlTagDefsChangedEvent(TagDefsChangedEvent e) {
				updateTagDefsFromStorage();
			}
		});
		
		CustomTagFactory.registryObserver.addObserver(new Observer() {
			@Override
			public void update(Observable o, Object arg) {
				if (arg instanceof TagRegistryChangeEvent) {
					logger.debug("TagRegistryChangeEvent: "+arg);
					updateAvailableTags();
				}				
			}
		});
	}
	
	private void initTagsTable(Composite parent) {
		Composite tagsTableContainer = new Composite(parent, SWT.NONE);
		tagsTableContainer.setLayout(new GridLayout(1, false));
		
		Label headerLbl = new Label(tagsTableContainer, 0);
		headerLbl.setText("Available Tags");
		Fonts.setBoldFont(headerLbl);
		headerLbl.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		tagsTableContainer.setLayout(new GridLayout(1, false));
		tagsTableContainer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		
		Composite btnsContainer = new Composite(tagsTableContainer, 0);
		btnsContainer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		btnsContainer.setLayout(new GridLayout(4, false));

		Button createTagBtn = new Button(btnsContainer, SWT.PUSH);
		createTagBtn.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		createTagBtn.setText("Create new tag...");
		createTagBtn.setImage(Images.ADD);
		createTagBtn.setToolTipText("Creates a new tag");
		
		createTagBtn.addSelectionListener(new SelectionAdapter() {
			@Override public void widgetSelected(SelectionEvent e) {
				CreateTagNameDialog d = new CreateTagNameDialog(getShell(), "Specify new tag name");				
				if (d.open() == Window.OK) {
					String name = d.getName();
					try {
						CustomTagFactory.addToRegistry(CustomTagFactory.create(name), null);
					} catch (Exception e1) {
						DialogUtil.showDetailedErrorMessageBox(getShell(), "Error creating tag", e1.getMessage(), e1);
					}
				}
			}
		});
		
		Button deleteTagDefBtn = new Button(btnsContainer, SWT.PUSH);
		deleteTagDefBtn.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		deleteTagDefBtn.setText("Delete tag definition");
		deleteTagDefBtn.setImage(Images.DELETE);
		deleteTagDefBtn.setToolTipText("Deletes the selected tag from the list of available tags");
		deleteTagDefBtn.addSelectionListener(new SelectionAdapter() {
			@Override public void widgetSelected(SelectionEvent e) {
				String tn = getSelectedAvailableTagsName();
				if (tn != null) {
					try {
						logger.debug("deleting tag: "+tn);
						CustomTagFactory.removeFromRegistry(tn);
						updateAvailableTags();
					} catch (IOException ex) {
						DialogUtil.showErrorMessageBox(getShell(), "Cannot remove tag", ex.getMessage());
					}
				}
			}
		});
		
//		Button saveBtn = new Button(btnsContainer, SWT.PUSH);
//		saveBtn.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
//		saveBtn.setImage(Images.DISK);
//		saveBtn.setToolTipText("Save the tag definitions to the local config.properties file s.t. they are recovered next time around");
//		saveBtn.addSelectionListener(new SelectionAdapter() {
//			@Override public void widgetSelected(SelectionEvent e) {
//				String tagNamesProp = CustomTagFactory.constructTagDefPropertyForConfigFile();
//				logger.debug("storing tagNamesProp: "+tagNamesProp);
//				TrpConfig.getTrpSettings().setTagNames(tagNamesProp);
//			}
//		});

		int tableViewerStyle = SWT.NO_FOCUS | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION;
		availableTagsTv = new TableViewer(tagsTableContainer, tableViewerStyle);
		availableTagsTv.getTable().setToolTipText("List of tags - italic tags are predefined and cannot be removed");
		
//		tagsTableViewer = new TableViewer(taggingGroup, SWT.FULL_SELECTION|SWT.HIDE_SELECTION|SWT.NO_FOCUS | SWT.H_SCROLL
//		        | SWT.V_SCROLL | SWT.FULL_SELECTION /*| SWT.BORDER*/);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
//		GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1);
		gd.heightHint = 150;
		Table tagsTable = availableTagsTv.getTable();
		tagsTable.setLayoutData(gd);
		
		availableTagsTv.setContentProvider(new ArrayContentProvider());
		tagsTable.setHeaderVisible(false);
		tagsTable.setLinesVisible(true);
		
		availableTagsTv.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override public void selectionChanged(SelectionChangedEvent event) {
				deleteTagDefBtn.setEnabled(getSelectedAvailableTagsName() != null);
				updatePropertiesForSelectedTag();
			}
		});
		
		TableViewerColumn nameCol = new TableViewerColumn(availableTagsTv, SWT.NONE);
		nameCol.getColumn().setText("Name");
		nameCol.getColumn().setResizable(true);
		nameCol.getColumn().setWidth(150);
		ColumnLabelProvider nameColLP = new ColumnLabelProvider() {
			@Override public String getText(Object element) {
				return (String) element;
			}
			@Override public Font getFont(Object element) {
				CustomTag t = CustomTagFactory.getTagObjectFromRegistry((String)element);
				if (t != null && !t.isDeleteable()) {
					return Fonts.createItalicFont(tagsTable.getFont());
				}
				
				return null;
			}
		};
		nameCol.setLabelProvider(nameColLP);

		if (false) { // add btns for table rows
		TableViewerColumn addButtonCol = new TableViewerColumn(availableTagsTv, SWT.NONE);
		addButtonCol.getColumn().setText("");
		addButtonCol.getColumn().setResizable(false);
		addButtonCol.getColumn().setWidth(100);
		
		class AddTagToListSelectionAdapter extends SelectionAdapter {
			String tagName;
			public AddTagToListSelectionAdapter(String tagName) {
				this.tagName = tagName;
			}
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (!StringUtils.isEmpty(tagName)) {
					try {
						logger.info("selected tag: "+getSelectedAvailableTagsName()+", tagName: "+tagName+", curr-atts: "+getCurrentAttributes());
						CustomTag tag = null;
						if (tagName.equals(getSelectedAvailableTagsName())) {
							tag = CustomTagFactory.create(tagName, getCurrentAttributes());
						}
						else {
//							CustomTag t = CustomTagFactory.create(tagName, 0, sel.getUnicodeText().length(), attributes);	
							tag = CustomTagFactory.create(tagName);
						}
						logger.info("tag = "+tag);
						
						CustomTagDef tagDef = new CustomTagDef(tag);
						Storage.getInstance().addCustomTagDef(tagDef);
					} catch (Exception ex) {
						DialogUtil.showDetailedErrorMessageBox(getShell(), "Error adding tag definiton", ex.getMessage(), ex);
					}
				}
			}
		};
		
		CellLabelProvider addButtonColLabelProvider = new CellLabelProvider() {
			@Override public void update(final ViewerCell cell) {
				String tagName = (String) cell.getElement();				
				final TableItem item = (TableItem) cell.getItem();
				TableEditor editor = new TableEditor(item.getParent());
				
//				boolean createDelBtn = false;
//				TagAddRemoveComposite c = new TagAddRemoveComposite((Composite) cell.getViewerRow().getControl(), SWT.NONE, true, createDelBtn);
//				if (c.getAddButton() != null)
//					c.getAddButton().addSelectionListener(addTagToListSelectionListener);
//				c.pack();
				
				Button addButton = new Button((Composite) cell.getViewerRow().getControl(), SWT.PUSH);
//		        addButton.setImage(Images.ADD);
		        addButton.setImage(Images.getOrLoad("/icons/add_12.png"));
		        addButton.setToolTipText("Add tag (including properties below) to list of tags for collection");
		        addButton.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, true));
		        addButton.addSelectionListener(new AddTagToListSelectionAdapter(tagName));
		        Control c = addButton;
		        
                Point size = c.computeSize(SWT.DEFAULT, SWT.DEFAULT);
				editor.minimumWidth = size.x;
				editor.horizontalAlignment = SWT.LEFT;
                editor.setEditor(c , item, cell.getColumnIndex());
                editor.layout();
                
                TaggingWidgetUtils.replaceEditor(addTagToListEditors, tagName, editor);
			}
		};
		addButtonCol.setLabelProvider(addButtonColLabelProvider);
		}
		
		availableTagsTv.refresh(true);
		availableTagsTv.getTable().pack();

		tagsTableContainer.layout(true);
	}
	
	private void initPropertyTable(Composite parent) {
		Composite propsContainer = new Composite(parent, ExpandableComposite.COMPACT);
		propsContainer.setLayoutData(new GridData(GridData.FILL_BOTH));
		propsContainer.setLayout(new GridLayout(2, false));
		
		Label headerLbl = new Label(propsContainer, 0);
		headerLbl.setText("Properties");
		Fonts.setBoldFont(headerLbl);
		headerLbl.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		
		Button addAtrributeBtn = new Button(propsContainer, SWT.PUSH);
		addAtrributeBtn.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		addAtrributeBtn.setText("Add attribute...");
		addAtrributeBtn.setImage(Images.ADD);
		addAtrributeBtn.addSelectionListener(new SelectionAdapter() {
			@Override public void widgetSelected(SelectionEvent e) {
				final String tn = getSelectedAvailableTagsName();
				if (tn == null)
					return;
				
				CreateTagNameDialog d = new CreateTagNameDialog(getShell(), "Specify attribute for '"+tn+"' tag");				
				if (d.open() == Window.OK) {
					try {
						String name = d.getName();
						CustomTagAttribute att = new CustomTagAttribute(name);
						
						CustomTag t = CustomTagFactory.getTagObjectFromRegistry(tn);
						logger.debug("tag object: "+t);
						
						if (t.hasAttribute(att.getName())) {
							DialogUtil.showErrorMessageBox(getShell(), "Cannot add attribute", "Attribute already exists!");
							return;
						}
						
						if (t != null) {
							t.setAttribute(att.getName(), null, true);
						}
						
						updatePropertiesForSelectedTag();
					}
					catch (Exception ex) {
						DialogUtil.showDetailedErrorMessageBox(getShell(), "Error adding attribute", ex.getMessage(), ex);
					}
				}
			}
		});
		
		Button deleteAttributeButton = new Button(propsContainer, SWT.PUSH);
		deleteAttributeButton.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		deleteAttributeButton.setText("Delete selected attribute");
		deleteAttributeButton.setImage(Images.DELETE);
		deleteAttributeButton.addSelectionListener(new SelectionAdapter() {
			@Override public void widgetSelected(SelectionEvent e) {
				String tn = getSelectedAvailableTagsName();
				if (tn == null)
					return;
				
				CustomTagAttribute selectedProperty = getSelectedProperty();
				logger.debug("selected property: "+selectedProperty);
				
				if (!StringUtils.isEmpty(tn) && selectedProperty != null) {
					try {
						CustomTag t = CustomTagFactory.getTagObjectFromRegistry(tn);
						if (t != null) {
							t.deleteCustomAttribute(selectedProperty.getName());
						}
						
						updatePropertiesForSelectedTag();
					} catch (Exception ex) {
						DialogUtil.showDetailedErrorMessageBox(getShell(), "Error adding attribute to tag "+tn, ex.getMessage(), ex);
					}
				}
			}
		});
		
		propsTable = new CustomTagPropertyTable(propsContainer, 0, false);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 2);
//		gd.heightHint = 200;
		propsTable.setLayoutData(gd);
		
//		initPropertyTable();

		layout();
	}
	
	private void initTagDefsTable() {
		Composite container = new Composite(horizontalSf, SWT.NONE);
		container.setLayout(new GridLayout(1, false));
		
		Label headerLbl = new Label(container, 0);
		headerLbl.setText("Tag defintions for current collection");
		Fonts.setBoldFont(headerLbl);
		headerLbl.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		container.setLayout(new GridLayout(1, false));
		container.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		
		Composite btnsContainer = new Composite(container, 0);
		btnsContainer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		btnsContainer.setLayout(new GridLayout(4, false));

		int tableViewerStyle = SWT.NO_FOCUS | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION;
		tagDefsTv = new TableViewer(container, tableViewerStyle);
		tagDefsTv.getTable().setToolTipText("List of tag definitions that are available in the user interface");
		
//		tagsTableViewer = new TableViewer(taggingGroup, SWT.FULL_SELECTION|SWT.HIDE_SELECTION|SWT.NO_FOCUS | SWT.H_SCROLL
//		        | SWT.V_SCROLL | SWT.FULL_SELECTION /*| SWT.BORDER*/);
		GridData gd = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
//		GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1);
		gd.heightHint = 150;
		tagDefsTv.getTable().setLayoutData(gd);
		tagDefsTv.getTable().setHeaderVisible(true);
		tagDefsTv.getTable().setLinesVisible(true);
		tagDefsTv.setContentProvider(new ArrayContentProvider());
		
		TableViewerColumn tagDefCol = new TableViewerColumn(tagDefsTv, SWT.NONE);
		tagDefCol.getColumn().setText("Tag definition");
		tagDefCol.getColumn().setResizable(true);
		tagDefCol.getColumn().setWidth(300);
		ColumnLabelProvider nameColLP = new ColumnLabelProvider() {
			@Override public String getText(Object element) {
				if (!(element instanceof CustomTagDef)) {
					return "i am error";
				}
				
				CustomTagDef tagDef = (CustomTagDef) element;
				return tagDef.getCustomTag().getCssStr();
			}
		};
		tagDefCol.setLabelProvider(nameColLP);
		
		if (true) {
			TableViewerColumn colorCol = new TableViewerColumn(tagDefsTv, SWT.NONE);
			colorCol.getColumn().setText("Color");
			colorCol.getColumn().setResizable(true);
			colorCol.getColumn().setWidth(50);
			colorCol.setLabelProvider(new CellLabelProvider() {
				@Override public void update(ViewerCell cell) {
					if (!(cell.getElement() instanceof CustomTagDef)) {
						return;
					}
					
					TableItem item = (TableItem) cell.getItem();
					CustomTagDef tagDef = (CustomTagDef) cell.getElement();
					
					TableEditor editor = new TableEditor(item.getParent());				
	                editor.grabHorizontal  = true;
	                editor.grabVertical = true;
	                editor.horizontalAlignment = SWT.LEFT;
	                editor.verticalAlignment = SWT.TOP;
	                
	                ColorChooseButton colorCtrl = new ColorChooseButton((Composite) cell.getViewerRow().getControl(), tagDef.getRGB()) {
	                	@Override protected void onColorChanged(RGB rgb) {
	                		tagDef.setRGB(rgb);
	                		logger.info("color of tag def changed, tagDef: "+tagDef);
	                		// TODO: update tagDef list on server
	                	}
	                };

	                editor.setEditor(colorCtrl , item, cell.getColumnIndex());
	                editor.layout();
	                
	                TaggingWidgetUtils.replaceEditor(colorEditors, tagDef, editor);
				}
			});
			
		}

		if (true) { // remove btn for table rows
		TableViewerColumn removeBtnCol = new TableViewerColumn(tagDefsTv, SWT.NONE);
		removeBtnCol.getColumn().setText("");
		removeBtnCol.getColumn().setResizable(false);
		removeBtnCol.getColumn().setWidth(100);
		
		class RemoveTagDefSelectionListener extends SelectionAdapter {
			CustomTagDef tagDef;
			
			public RemoveTagDefSelectionListener(CustomTagDef tagDef) {
				this.tagDef = tagDef;
			}
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				Storage.getInstance().removeCustomTag(tagDef);
 				tagDefsTv.refresh();
			}
		};
		
		CellLabelProvider removeButtonColLabelProvider = new CellLabelProvider() {
			@Override public void update(final ViewerCell cell) {
							
				final TableItem item = (TableItem) cell.getItem();
				TableEditor editor = new TableEditor(item.getParent());
				
				Button removeButton = new Button((Composite) cell.getViewerRow().getControl(), SWT.PUSH);
//		        addButton.setImage(Images.ADD);
		        removeButton.setImage(Images.getOrLoad("/icons/delete_12.png"));
		        removeButton.setToolTipText("Remove tag definition");
		        removeButton.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, true));
		        
		        CustomTagDef tagDef = (CustomTagDef) cell.getElement();	
		        removeButton.addSelectionListener(new RemoveTagDefSelectionListener(tagDef));
		        Control c = removeButton;
		        
                Point size = c.computeSize(SWT.DEFAULT, SWT.DEFAULT);
				editor.minimumWidth = size.x;
				editor.horizontalAlignment = SWT.LEFT;
                editor.setEditor(c , item, cell.getColumnIndex());
                editor.layout();
                
                TaggingWidgetUtils.replaceEditor(removeTagDefEditors, tagDef, editor);
			}
		};
		removeBtnCol.setLabelProvider(removeButtonColLabelProvider);
		}
		
		tagDefsTv.refresh(true);
		tagDefsTv.getTable().pack();

		container.layout(true);
	}

	public String getSelectedAvailableTagsName() {
		if (availableTagsTv.getSelection().isEmpty())
			return null;
		else
			return (String) ((IStructuredSelection) availableTagsTv.getSelection()).getFirstElement();	
	}
	
	public CustomTagAttribute getSelectedProperty() {
		return propsTable.getSelectedProperty();
	}
	
	public boolean isAvailableTagSelected() {
		return getSelectedAvailableTagsName() != null;
	}
	
	private void updateAvailableTags() {
		logger.debug("updating available tags");
		
		availableTagNames.clear();
		for (CustomTag t : CustomTagFactory.getRegisteredTagObjects()) {
			logger.trace("update of av. tags, tn = "+t.getTagName()+" showInTagWidget: "+t.showInTagWidget());
			if (t.showInTagWidget()) {
				availableTagNames.add(t.getTagName());
			}
		}
		
		Display.getDefault().asyncExec(new Runnable() {
		@Override public void run() {
//			updateAvailableTags();
			availableTagsTv.setInput(availableTagNames);
			
			availableTagsTv.refresh(true);
			
			TaggingWidgetUtils.updateEditors(addTagToListEditors, availableTagNames);
			}
		});
	}
	
	private void updateTagDefsFromStorage() {
		Display.getDefault().asyncExec(() -> {
			tagDefsTv.setInput(Storage.getInstance().getCustomTagDefs());
			tagDefsTv.refresh();
			
			TaggingWidgetUtils.updateEditors(colorEditors, Storage.getInstance().getCustomTagDefs());
			TaggingWidgetUtils.updateEditors(removeTagDefEditors, Storage.getInstance().getCustomTagDefs());
		});
	}
	
//	private void updateTable() {
//		updateEditors();
//		availableTagsTv.refresh(true);
//		availableTagsTv.getTable().layout(true);
//	}
	
//	private void updateEditors() {
////		TaggingWidgetUtils.updateEditors(colorEditors, availableTagNames);
//		TaggingWidgetUtils.updateEditors(addTagToListEditors, availableTagNames);
////		TaggingWidgetUtils.updateEditors(delSelectedEditors, getSelectedTagNames());
////		TaggingWidgetUtils.updateEditors(delSelectedEditors, selectedTags);
//	}
	
	public void updatePropertiesForSelectedTag() {
		String tn = getSelectedAvailableTagsName();
		if (tn == null) {
			propsTable.setInput(null, null);
			propsTable.update();
			return;
		}
			
		try {
			CustomTag tag = CustomTagFactory.getTagObjectFromRegistry(tn);
			if (tag == null)
				throw new Exception("could not retrieve tag from registry: "+tn+" - should not happen here!");
			
			logger.debug("tag from object registry: "+tag);
			logger.debug("tag atts: "+tag.getAttributeNames());
			
			CustomTag protoTag = tag.copy();
			logger.debug("protoTag copy: "+protoTag);
			logger.debug("protoTag atts: "+protoTag.getAttributeNames());
			
			propsTable.setInput(protoTag, null);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return;
		}
	}
	
	public Map<String, Object> getCurrentAttributes() {
		Map<String, Object> props = new HashMap<>();
		CustomTag pt = propsTable.getPrototypeTag();
		if (pt != null) {
			return pt.getAttributeNamesValuesMap();
		}
	
		return props;
	}

}
