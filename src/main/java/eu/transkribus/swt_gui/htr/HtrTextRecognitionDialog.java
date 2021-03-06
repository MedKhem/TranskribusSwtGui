package eu.transkribus.swt_gui.htr;

import java.io.IOException;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.util.CoreUtils;
import eu.transkribus.swt.util.DialogUtil;
import eu.transkribus.swt_gui.mainwidget.storage.Storage;
import eu.transkribus.swt_gui.util.DocPagesSelector;
import eu.transkribus.util.TextRecognitionConfig;

public class HtrTextRecognitionDialog extends Dialog {
	private static final Logger logger = LoggerFactory.getLogger(HtrTextRecognitionDialog.class);
	
	private HtrTextRecognitionConfigDialog trcd = null;
	
	private Button thisPageBtn, severalPagesBtn;
	private DocPagesSelector dps;
	
	private Storage store = Storage.getInstance();
	
	private TextRecognitionConfig config;
	private String pages;

	
	public HtrTextRecognitionDialog(Shell parent) {
		super(parent);
	}
    
	public void setVisible() {
		if(super.getShell() != null && !super.getShell().isDisposed()) {
			super.getShell().setVisible(true);
		}
	}
	
	@Override
	protected Control createDialogArea(Composite parent) {
		Composite cont = (Composite) super.createDialogArea(parent);
		cont.setLayout(new GridLayout(3, false));
		
		thisPageBtn = new Button(cont, SWT.RADIO);
		thisPageBtn.setText("On this page");
		thisPageBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		thisPageBtn.setSelection(true);
		
		severalPagesBtn = new Button(cont, SWT.RADIO);
		severalPagesBtn.setText("Pages:");
		severalPagesBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		dps = new DocPagesSelector(cont, SWT.NONE, false, store.getDoc().getPages());
		dps.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		dps.setEnabled(false);
		
		severalPagesBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				dps.setEnabled(severalPagesBtn.getSelection());
			}
		});
		
		Text configTxt = new Text(cont, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER);
		configTxt.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 3, 6));
				
		Button configBtn = new Button(cont, SWT.PUSH);
		configBtn.setText("Configure...");
		configBtn.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 3, 1));
		
		configBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if(trcd == null) {
					trcd = new HtrTextRecognitionConfigDialog(parent.getShell(), config);
					if(trcd.open() == IDialogConstants.OK_ID) {
						logger.info("OK pressed");
						config = trcd.getConfig();
						configTxt.setText(config.toString());
						store.saveTextRecognitionConfig(config);
					}
					trcd = null;
				} else {
					trcd.setVisible();
				}
			}
		});
		
		config = store.loadTextRecognitionConfig();
		logger.debug("" + config);
		if(config != null) {
			configTxt.setText(config.toString());
		}
		
		return cont;
	}

		
	@Override
	protected void okPressed() {
		if(thisPageBtn.getSelection()) {
			pages = ""+store.getPage().getPageNr();
		} else if(severalPagesBtn.getSelection()) {
			pages = dps.getPagesText().getText();
		}
		
		if(pages == null || pages.isEmpty()) {
			DialogUtil.showErrorMessageBox(this.getParentShell(), "Error", "Please specify pages for recognition.");
			return;
		}
		
		try {
			CoreUtils.parseRangeListStr(pages, store.getDoc().getNPages());
		} catch (IOException e) {
			DialogUtil.showErrorMessageBox(this.getParentShell(), "Error", "Page selection is invalid.");
			return;
		}
		
		if(config == null) {
			DialogUtil.showErrorMessageBox(getShell(), "Bad Configuration", "Please define a configuration.");
			return;
		}
		
		super.okPressed();
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("Text Recognition");
		newShell.setMinimumSize(300, 400);
	}

	@Override
	protected Point getInitialSize() {
		return new Point(300, 400);
	}

	@Override
	protected void setShellStyle(int newShellStyle) {
		super.setShellStyle(SWT.CLOSE | SWT.MAX | SWT.RESIZE | SWT.TITLE);
		// setBlockOnOpen(false);
	}
	
	public TextRecognitionConfig getConfig() {
		return this.config;
	}
	
	public String getPages() {
		return this.pages;
	}
}
