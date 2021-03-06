package eu.transkribus.swt_gui.collection_manager;

import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.client.connection.TrpServerConn;
import eu.transkribus.core.model.beans.TrpCollection;
import eu.transkribus.core.model.beans.auth.TrpRole;
import eu.transkribus.core.model.beans.auth.TrpUser;
import eu.transkribus.swt.util.ComboInputDialog;
import eu.transkribus.swt.util.DialogUtil;
import eu.transkribus.swt_gui.mainwidget.TrpMainWidget;
import eu.transkribus.swt_gui.mainwidget.storage.IStorageListener;
import eu.transkribus.swt_gui.mainwidget.storage.Storage;

public class CollectionUsersWidgetListener implements IStorageListener, SelectionListener, DragSourceListener  {
	private static final Logger logger = LoggerFactory.getLogger(CollectionUsersWidgetListener.class);
	
	CollectionUsersWidget cuw;
	Shell shell;
	static TrpMainWidget mw = TrpMainWidget.getInstance();
	static Storage store = Storage.getInstance();

	public CollectionUsersWidgetListener(CollectionUsersWidget cuw) {
		this.cuw = cuw;
		shell = cuw.getShell();
				
		cuw.getShell().addDisposeListener(new DisposeListener() {
			@Override public void widgetDisposed(DisposeEvent e) {
				detach();
			}
		});
		
		attach();
	}
	
	public void attach() {
		cuw.addUserToColBtn.addSelectionListener(this);
		cuw.removeUserFromColBtn.addSelectionListener(this);
		cuw.role.addSelectionListener(this);
	}
	
	public void detach() {
		cuw.addUserToColBtn.removeSelectionListener(this);
		cuw.removeUserFromColBtn.removeSelectionListener(this);
		cuw.role.removeSelectionListener(this);
	}

	@Override
	public void dragEnter(DragSourceDragEvent dsde) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dragOver(DragSourceDragEvent dsde) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dropActionChanged(DragSourceDragEvent dsde) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dragExit(DragSourceEvent dse) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dragDropEnd(DragSourceDropEvent dsde) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void widgetSelected(SelectionEvent e) {
		try {
			Object s = e.getSource();
			if (s == cuw.addUserToColBtn) {
				addSelectedUsersToCollection();
			} else if (s == cuw.removeUserFromColBtn) {
				removeSelectedUsersFromCollection();
			} 
			else if (s == cuw.role) {
				editSelectedUsersFromCollection();
			} 

		} catch (Throwable th) {
			mw.onError("Unexpected error", "An unexpected error occured: "+th.getMessage(), th);
		}		
	}

	@Override
	public void widgetDefaultSelected(SelectionEvent e) {
		// TODO Auto-generated method stub
		
	}
	
	void editSelectedUsersFromCollection() {
		//add dialog to check with the user
		int a = DialogUtil.showYesNoDialog(cuw.getShell(), "Change user role", "Really change the role of the selected user?");
		if (a == SWT.YES){
	
			TrpCollection collection = cuw.getCollection();
			if (store.isLoggedIn() && collection!=null) {
				TrpServerConn conn = store.getConnection();
				TrpRole r = cuw.getSelectedRole();
				List<TrpUser> selected = cuw.getSelectedUsersInCollection();
				
				List<String> error = new ArrayList<>();
				for (TrpUser u : selected) {
					logger.debug("edit user: "+u+ " new role: "+r.toString());				
					try {
						conn.addOrModifyUserInCollection(collection.getColId(), u.getUserId(), r);
						logger.info("edited user: "+u+ " new role: "+r.toString());		
					} catch (Throwable e) {
						logger.warn("Could not edit user: "+u+ " new role: "+r.toString());		
						error.add(u.getUserName()+" - reason: "+e.getMessage());
					}
				}
				
				if (!error.isEmpty()) {
					String msg = "Could not edit the following user:\n";
					for (String u : error) {
						msg += u + "\n";
					}
								
					mw.onError("Error editing user", msg, null);
				} else {
					DialogUtil.showInfoMessageBox(shell, "Success", "Successfully edited user ("+selected.size()+")");
				}
				
				cuw.updateUsersForSelectedCollection();
			}
		}
	}
	
	void removeSelectedUsersFromCollection() {
		TrpCollection collection = cuw.getCollection();
		if (store.isLoggedIn() && collection!=null) {
//			if (cuw.isMyDocsTabOpen()) {
//				DialogUtil.showErrorMessageBox(shell, "Error", "Cannot determine the collection for an uploaded document");
//				
//				return;
//			}
			
			TrpServerConn conn = store.getConnection();
			List<TrpUser> selected = cuw.getSelectedUsersInCollection();
			
			List<String> error = new ArrayList<>();
			boolean currentUserAffected=false;
			for (TrpUser u : selected) {
				if (u.getUserId() == store.getUser().getUserId()) {
					currentUserAffected=true;
				}
				
				logger.debug("removing user: "+u);				
				try {
					conn.removeUserFromCollection(collection.getColId(), u.getUserId());
					logger.info("removed user: "+u);
				} catch (Throwable e) {
					logger.warn("Could not remove user: "+u);
					error.add(u.getUserName()+" - reason: "+e.getMessage());
				}
			}
			
			if (!error.isEmpty()) {
				String msg = "Could not remove the following user:\n";
				for (String u : error) {
					msg += u + "\n";
				}
				
				mw.onError("Error removing user", msg, null);
			} else {
				DialogUtil.showInfoMessageBox(shell, "Success", "Successfully removed user ("+selected.size()+")");
			}
			
			if (currentUserAffected) {
				try {
					store.reloadCollections();
				} catch (Throwable e) {
					logger.error(e.getMessage(), e);
				}
			}
			
			cuw.updateUsersForSelectedCollection();
		}
	}
	
	TrpRole getRoleFromUser(String title) {
		List<String> roleStrs = new ArrayList<>();
		for (TrpRole r : TrpRole.values()) {
			if (!r.isVirtual()) {
				roleStrs.add(r.toString());
			}
		}
		
		ComboInputDialog d = new ComboInputDialog(shell, title==null?"Choose a role: ":title, roleStrs.toArray(new String[0]));
		
		if (d.open() != Window.OK)
			return null;
		
		return TrpRole.fromString(d.getSelectedText());
	}
	
	void addSelectedUsersToCollection() {
		TrpCollection collection = cuw.getCollection();
		if (store.isLoggedIn() && collection!=null) {
			TrpServerConn conn = store.getConnection();
			
			TrpRole r = getRoleFromUser(null);
			if (r==null)
				return;
			
			List<TrpUser> selected = cuw.findUsersWidget.getSelectedUsers();
			if (selected.isEmpty())
				return;

			List<String> error = new ArrayList<>();
			for (TrpUser u : selected) {
				logger.debug("adding user: "+u);
				
				try {
					conn.addOrModifyUserInCollection(collection.getColId(), u.getUserId(), r);
					logger.info("added user: "+u);
				} catch (Throwable e) {
					logger.warn("Could not add user: "+u);
					error.add(u.getUserName()+" - reason: "+e.getMessage());
				}
			}
			
			if (!error.isEmpty()) {
				String msg = "Could not add the following users:\n";
				for (String u : error) {
					msg += u + "\n";
				}
				
				mw.onError("Error adding user", msg, null);
			} else {
				DialogUtil.showInfoMessageBox(shell, "Success", "Successfully adding user ("+selected.size()+")");
			}
			
			cuw.updateUsersForSelectedCollection();
		}
	}

}
