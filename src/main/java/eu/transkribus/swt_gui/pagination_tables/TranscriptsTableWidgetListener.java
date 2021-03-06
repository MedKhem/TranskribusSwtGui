package eu.transkribus.swt_gui.pagination_tables;

import java.util.List;

import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.TableItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.model.beans.TrpTranscriptMetadata;
import eu.transkribus.swt.util.DialogUtil;
import eu.transkribus.swt.util.SWTUtil;
import eu.transkribus.swt_gui.mainwidget.TrpMainWidget;
import eu.transkribus.swt_gui.mainwidget.storage.IStorageListener;
import eu.transkribus.swt_gui.mainwidget.storage.Storage;

public class TranscriptsTableWidgetListener implements SelectionListener, IDoubleClickListener, MouseListener, IStorageListener {
	private final static Logger logger = LoggerFactory.getLogger(TranscriptsTableWidgetListener.class);
	
	TranscriptsTableWidgetPagination tw;
	TableViewer tv;
	
	public TranscriptsTableWidgetListener(TranscriptsTableWidgetPagination tw) {
		this.tw = tw;
		this.tv = tw.getPageableTable().getViewer();
		
		tw.addDisposeListener(new DisposeListener() {
			@Override public void widgetDisposed(DisposeEvent e) {
				detach();
			}
		});
		
		attach();
	}
	
	public void attach() {
		tv.getTable().addMouseListener(this);
		tv.addDoubleClickListener(this);
		tv.getTable().addSelectionListener(this);
		Storage.getInstance().addListener(this);
		
		if (tw.deleteBtn != null)
			tw.deleteBtn.addSelectionListener(this);		
	}
	
	public void detach() {
		tv.getTable().removeMouseListener(this);
		tv.removeDoubleClickListener(this);
		tv.getTable().removeSelectionListener(this);
		Storage.getInstance().removeListener(this);
		
		if (tw.deleteBtn != null)
			tw.deleteBtn.removeSelectionListener(this);
		
	}
	
	@Override public void handleLoginOrLogout(LoginOrLogoutEvent arg) {
		if (SWTUtil.isDisposed(tw))
			return;
			
		tw.refreshPage(true);
	}
	
	@Override public void handleTranscriptListLoadEvent(TranscriptListLoadEvent arg) {
		if (SWTUtil.isDisposed(tw))
			return;		
		
		tw.refreshPage(true);
	}

	@Override public void handleTranscriptLoadEvent(TranscriptLoadEvent arg) {
		if (SWTUtil.isDisposed(tw))
			return;		
		
		tv.refresh();
	}	

	@Override public void widgetSelected(SelectionEvent e) {
		Object s = e.getSource();
		if(s == tw.deleteBtn){
			List<TrpTranscriptMetadata> selectedVersions = tw.getSelected();
			int nrOfVersions2Delete = selectedVersions.size();
			if (DialogUtil.showYesNoDialog(tw.getShell(), "Delete Version(s)", "Do you really want to delete " + nrOfVersions2Delete + " selected versions ")!=SWT.YES) {
				return;
			}
			for (TrpTranscriptMetadata md : selectedVersions){
				nrOfVersions2Delete--;
				//to load only the new version in the canvas after deleting the 
				boolean lastVersion2Delete = (nrOfVersions2Delete == 0 ? true : false);
				if (md!=null) {
					deleteTranscript(md, lastVersion2Delete);
				}
			}
//			TrpTranscriptMetadata md = tw.getFirstSelected();
//			if (md!=null) {
//				deleteTranscript(md);
//			}
		}
	}

	@Override public void widgetDefaultSelected(SelectionEvent e) {
	}

	@Override public void doubleClick(DoubleClickEvent event) {
		TrpTranscriptMetadata md = tw.getFirstSelected();
		logger.debug("double click on transcript: "+md);
		
		if (md!=null) {
			logger.debug("Loading transcript: "+md);
			TrpMainWidget.getInstance().jumpToTranscript(md, true);
			TrpMainWidget.getInstance().updateVersionStatus();
		}		
	}
	
	private void deleteTranscript(TrpTranscriptMetadata tMd, boolean lastVersion2Delete) {
		logger.info("delete transcript: " + tMd.getKey());
		
		int itemCount = (int) tw.getPageableTable().getController().getTotalElements();
		
		if(itemCount == 1 || tMd.getKey() == null){
			MessageBox messageBox = new MessageBox(tw.getShell(), SWT.ICON_INFORMATION
		            | SWT.OK);
	        messageBox.setMessage("Can not delete this version.");
	        messageBox.setText("Unauthorized");
	        messageBox.open();
		} else {
			try {
				Storage store = Storage.getInstance();
				
				TrpTranscriptMetadata currentTranscript = store.getTranscriptMetadata();
				logger.debug("deleting transcript");
				store.deleteTranscript(tMd);
				
				// reload page if current transcript was deleted:
				if (currentTranscript!=null && currentTranscript.equals(tMd)) {
					//TrpMainWidget.getInstance().reloadCurrentPage(false);
					store.setLatestTranscriptAsCurrent();
				} else {
					store.reloadTranscriptsList(store.getCurrentDocumentCollectionId());
				}
				
				if (lastVersion2Delete){
					TrpMainWidget.getInstance().reloadCurrentPage(false);
				}
				
			} catch (Exception e1) {
				MessageBox messageBox = new MessageBox(tw.getShell(), SWT.ICON_ERROR
			            | SWT.OK);
		        messageBox.setMessage("Could not delete transcript: " + e1.getMessage());
		        messageBox.setText("Error");
		        messageBox.open();
			}
		}
	}


	@Override
	public void mouseDown(MouseEvent e) {
		
        TableItem[] selection = tv.getTable().getSelection();
        
        boolean isLatestTranscriptAndLoaded = false;
        if(tw.getFirstSelected() != null) {
        	//current transcript means 'latest'
        	isLatestTranscriptAndLoaded = (tw.getFirstSelected().getTsId() == Storage.getInstance().getPage().getCurrentTranscript().getTsId()) && (tw.getFirstSelected().getTsId() == Storage.getInstance().getTranscriptMetadata().getTsId());
        }
        /*
         * only the newest transcript can be set to a new status with right click (only  for this transcipt the menu gets visible)
         */
        if(selection.length==1 && isLatestTranscriptAndLoaded && (e.button == 3)){
        	logger.debug("show content menu");
        	//vw.setContextMenuVisible(true);
        	tw.enableContextMenu();
        }
        else{
        	tw.disableContextMenu();
        	//vw.setContextMenuVisible(false);
        }

		
	}

	@Override
	public void mouseDoubleClick(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void mouseUp(MouseEvent e) {
		// TODO Auto-generated method stub
		
	}

}
