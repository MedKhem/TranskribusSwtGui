package eu.transkribus.swt_gui.search.kws_solr;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.InvocationCallback;

import org.apache.commons.lang3.StringUtils;
import org.dea.fimgstoreclient.FimgStoreGetClient;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.transkribus.core.TrpFimgStoreConf;
import eu.transkribus.core.model.beans.kws.TrpKwsHit;
import eu.transkribus.core.model.beans.searchresult.FulltextSearchResult;
import eu.transkribus.core.model.beans.searchresult.KeywordHit;
import eu.transkribus.core.model.beans.searchresult.KeywordSearchResult;
import eu.transkribus.swt.util.Colors;
import eu.transkribus.swt.util.Images;
import eu.transkribus.swt.util.LabeledText;
import eu.transkribus.swt_gui.Msgs;
import eu.transkribus.swt_gui.mainwidget.TrpMainWidget;
import eu.transkribus.swt_gui.mainwidget.storage.Storage;
import eu.transkribus.swt_gui.search.fulltext.FullTextSearchComposite;
import eu.transkribus.swt_gui.search.kws.KwsHitTableWidget;

public class KWSearchComposite extends Composite{
	
	private final static boolean TEST_ONLY = true;
	
	private final static Logger logger = LoggerFactory.getLogger(FullTextSearchComposite.class);
	FimgStoreGetClient imgStoreClient;
	Shell shell;
	Storage storage;
	Group facetsGroup;
	LabeledText inputText;
	
	Slider confSlider;
	private final static double MIN_CONF = 0.01;
	private final static double MAX_CONF = 0.99;
	private final static double DEFAULT_CONF = 0.05;
	private final static int THUMB_SIZE = 5; // size of slider thumb
	private static final DecimalFormat CONF_FORMAT = new DecimalFormat("0.00");
	
	
	boolean prob_desc = true;
	String searchWord;
	KeywordSearchResult kwSearchResult;
	ArrayList<KeywordHit> keywordHits;
	TableViewer tv;
	
	public KWSearchComposite(Composite parent, int style){
		super(parent, style);
		shell = parent.getShell();	
		try {
			imgStoreClient = new FimgStoreGetClient(new URL(TrpFimgStoreConf.getFimgStoreUrl()+"/"));
		} catch (Exception e) {
			logger.error("Could not create connection to FimgStore" + e);
			e.printStackTrace();
		}

		createContents();
		
	}
	
	private void createContents(){
		
		storage = Storage.getInstance();
		this.setLayout(new FillLayout());
		Composite c = new Composite(this, 0);
		c.setLayout(new FillLayout());
				
		SashForm sf = new SashForm(c, SWT.VERTICAL);
		sf.setLayout(new GridLayout(1, false));			
		
		facetsGroup = new Group(sf, SWT.NONE);
		facetsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));	
		facetsGroup.setLayout(new GridLayout(2, false));
		facetsGroup.setText("Search HTR text for single words");
		
		Label scopeLbl = new Label(facetsGroup, SWT.NONE);
		scopeLbl.setText("Search in collection:");
		Combo scopeCombo = new Combo(facetsGroup, SWT.BORDER | SWT.DROP_DOWN | SWT.READ_ONLY);
		String[] SCOPES = new String[] { "1335"};
		scopeCombo.setItems(SCOPES);
		//FIXME Java Heap space error when to many confmats are loaded. Thus for now only scope "document"
		scopeCombo.select(0);
		scopeCombo.setEnabled(false);
		
		TraverseListener findTagsOnEnterListener = new TraverseListener() {
			@Override public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_RETURN) {
					findKW();
				}
			}
		};
		
		inputText = new LabeledText(facetsGroup, "Search for keyword:");
		inputText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 2, 1));
		inputText.text.addTraverseListener(findTagsOnEnterListener);
		
		Composite sliderComp = new Composite(facetsGroup, SWT.NONE);
		sliderComp.setLayout(new GridLayout(3, false));
		sliderComp.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, 5, 1));
		
		Label sliderLabel = new Label(sliderComp, SWT.NONE);
		sliderLabel.setText("Confidence Threshold:");
		Text confValueTxt = new Text(sliderComp, SWT.BORDER);
		confValueTxt.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
//		confValueTxt.setEnabled(false);
		confSlider = new Slider(sliderComp, SWT.HORIZONTAL);
		confSlider.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		confSlider.setMaximum(convertConfidenceToSliderValue(MAX_CONF) + THUMB_SIZE);
		confSlider.setThumb(THUMB_SIZE);
		confSlider.setMinimum(convertConfidenceToSliderValue(MIN_CONF));
		confSlider.setSelection(convertConfidenceToSliderValue(DEFAULT_CONF));
		
		confValueTxt.setText(CONF_FORMAT.format(getConfidenceSliderValue()));
		confValueTxt.setTextLimit(4);
		
		confValueTxt.addKeyListener(new KeyListener() {

			@Override
			public void keyPressed(KeyEvent e) {
				//DO nothing
			}

			@Override
			public void keyReleased(KeyEvent e) {
				final String text = confValueTxt.getText();
				Double value = getConfidenceSliderValue();
				if(!StringUtils.isEmpty(text)) {
					try {
						value = Double.parseDouble(text);
						confValueTxt.setForeground(Colors.getSystemColor(SWT.COLOR_BLACK));
						if(value < MIN_CONF) {
							value = MIN_CONF;
						}
						if(value > MAX_CONF) {
							value = MAX_CONF;
						}
						setConfidenceSliderValue(value);
					} catch(NumberFormatException nfe) {
						confValueTxt.setForeground(Colors.getSystemColor(SWT.COLOR_RED));
					}
				}
			}
		});
		
		confSlider.addListener(SWT.Selection, new Listener(){
			@Override
			public void handleEvent(Event event){
				if(event.detail == SWT.DRAG){
					confValueTxt.setText(CONF_FORMAT.format(getConfidenceSliderValue()));								
				}
			}
		});
		
		confSlider.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if(e.detail == SWT.NONE){
					confValueTxt.setText(CONF_FORMAT.format(getConfidenceSliderValue()));// + "%");
				}
			}
		});
		
		initResultsTable(sf);	
		initPreviewArea(sf);
		sf.setWeights(new int[] { 20, 60, 20 } );

		
	}
	
	Image currentImgOrig;
	Image currentImgScaled;
	Canvas canvas;
	
	private void initPreviewArea(Composite cont){
		Group previewGrp = new Group(cont, SWT.NONE);
		previewGrp.setText(Msgs.get("search.kws.preview"));
		previewGrp.setLayout(new FillLayout());
		canvas = new Canvas(previewGrp, SWT.NONE);
		canvas.setBackground(Colors.getSystemColor(SWT.COLOR_GRAY));
		
		canvas.addPaintListener(new PaintListener() {
			public void paintControl(PaintEvent e) {
				if(currentImgOrig != null) {
					Rectangle client = canvas.getClientArea();
					if(currentImgScaled != null) {
						currentImgScaled.dispose();
					}
					currentImgScaled = Images.resize(currentImgOrig, client.width, client.height,
							Colors.getSystemColor(SWT.COLOR_GRAY));
					Rectangle imgBounds = currentImgScaled.getBounds();
					final int xOffset = (client.width - imgBounds.width) / 2; 
					e.gc.drawImage(currentImgScaled, xOffset, 0);
				}
			}
		});
		
		tv.getTable().addListener(SWT.MouseMove, new MouseMoveListener(tv.getTable()));
		
		
	}
	
	private void initResultsTable(SashForm sf){
		Group resultsGroup = new Group(sf, SWT.NONE);
		resultsGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		resultsGroup.setText("Search results");
		resultsGroup.setLayout(new GridLayout(1, false));	
		
		tv = new TableViewer(resultsGroup);
		Table table = tv.getTable();
		table.setLayoutData(new GridData(GridData.FILL_BOTH));
		
        tv.getTable().setHeaderVisible(true);
        tv.getTable().setLinesVisible(true);
        tv.setContentProvider(new ArrayContentProvider());
		
		TableColumn tc = new TableColumn(table, SWT.LEFT);
		tc.setText("Probability");
		tc.setWidth(90);
		
		TableViewerColumn probCol = new TableViewerColumn(tv, tc);
		
		Listener sortListener = new Listener(){
			public void handleEvent(Event e){
				TableColumn col = (TableColumn) e.widget;
				prob_desc = !prob_desc;
				findKW();
			}
		};	
        probCol.setLabelProvider(new ColumnLabelProvider(){
            @Override
            public String getText(Object element) {
                KeywordHit hit = (KeywordHit)element;  
                return ""+hit.getProbability();
            }
        });		
        tc.addListener(SWT.Selection, sortListener);
        
        tc = new TableColumn(table, SWT.LEFT);
		tc.setText("Word");
		tc.setWidth(100);		
		TableViewerColumn wordCol = new TableViewerColumn(tv, tc);		
        wordCol.setLabelProvider(new ColumnLabelProvider(){
            @Override
            public String getText(Object element) {
                KeywordHit hit = (KeywordHit)element;  
                return ""+hit.getWord();
            }
        });	
        
        tc = new TableColumn(table, SWT.LEFT);
		tc.setText("Document");
		tc.setWidth(300);		
		TableViewerColumn docCol = new TableViewerColumn(tv, tc);		
        docCol.setLabelProvider(new ColumnLabelProvider(){
            @Override
            public String getText(Object element) {
                KeywordHit hit = (KeywordHit)element;  
                return ""+hit.getDocTitle();
            }
        });
        
        tc = new TableColumn(table, SWT.LEFT);
		tc.setText("Page");
		tc.setWidth(80);		
		TableViewerColumn pageCol = new TableViewerColumn(tv, tc);		
        pageCol.setLabelProvider(new ColumnLabelProvider(){
            @Override
            public String getText(Object element) {
                KeywordHit hit = (KeywordHit)element;  
                return ""+hit.getPageNr();
            }
        });
        
        tc = new TableColumn(table, SWT.LEFT);
		tc.setText("Line");
		tc.setWidth(150);		
		TableViewerColumn lineCol = new TableViewerColumn(tv, tc);		
        lineCol.setLabelProvider(new ColumnLabelProvider(){
            @Override
            public String getText(Object element) {
                KeywordHit hit = (KeywordHit)element;  
                return ""+hit.getLineId();
            }
        });
		
	}
	
	private double getConfidenceSliderValue() {
		return confSlider.getSelection() / 100.0;
	}
	
	private void setConfidenceSliderValue(Double value) {
		confSlider.setSelection(convertConfidenceToSliderValue(value));
	}
	
	private int convertConfidenceToSliderValue(Double value) {
		if(value == null) {
			throw new IllegalArgumentException("Value must not be null");
		}
		final Double sliderVal = value * 100;
		return sliderVal.intValue();
	}
	
	private void findKW(){
		storage = Storage.getInstance();
			
		if(TEST_ONLY){
			final String testServer = "https://transkribus.eu/TrpServerTesting";
			
			if(!storage.getCurrentServer().equals(testServer)){
				TrpMainWidget.getInstance().onError(
						"Error searching keyword", "Keyword search (solr) is currently only supportet on Trp Testserver",
						null, false, false);
				return;
			}
		}
		
		searchWord = inputText.getText().trim();
		if(searchWord.isEmpty()) {
			return;
		}		
		
		//Async search
		InvocationCallback<KeywordSearchResult> callback = new InvocationCallback<KeywordSearchResult>() {

			@Override
			public void completed(KeywordSearchResult response) {
				kwSearchResult = response;
				if(kwSearchResult != null){	
					Display.getDefault().asyncExec(()->{
						logger.debug("searched"+searchWord);
						logger.debug("num hits: "+kwSearchResult.getNumResults());
						updateResultsTable();
					}); 					
				}else{				
//					tv.getTable().clearAll();
//					tv.refresh();
//					shell.redraw();
				}
			}

			@Override
			public void failed(Throwable throwable) {
				logger.error("Fulltext search failed."+ throwable);
				Display.getDefault().asyncExec(() -> {
					TrpMainWidget.getInstance().onError("Error searching keyword", throwable.getMessage(), throwable);
				});
			}
			
		};
		ArrayList<String> filters = new ArrayList<String>();
		
		filters.add("title:passau89pageskwstranskr");
		
		float probLow = (float) getConfidenceSliderValue();

		String sorting = "childfield(probability) desc";
		if(!prob_desc){
			sorting = "childfield(probability) asc";
		}
		
		try{
			storage.getConnection().searchKWAsync(searchWord, 0, 100, probLow, 1.0f, filters, sorting, 0, callback);
		}catch(Exception e){
			e.printStackTrace();
		}		
	}
	
	volatile Map<String,Image> imageMap;
	Thread imgLoaderThread;
	
	private void updateResultsTable(){
		
		if(kwSearchResult == null) return;
		
		keywordHits = (ArrayList<KeywordHit>) kwSearchResult.getKeywordHits();	
		
		Runnable loadPreviewImages = new Runnable(){
			
		public void run(){
				if(imageMap!= null){
					imageMap.clear();
				}
				imageMap = new HashMap<String,Image>();
				for(KeywordHit kwHit : keywordHits){
					putInImageMap(kwHit);		
				}				
			}
		};

		if(imgLoaderThread != null){
			imgLoaderThread.interrupt();
			logger.debug("Image loading thread interrupted");
        }
		imgLoaderThread = new Thread(loadPreviewImages,"WordThmbLoaderThread");
		imgLoaderThread.start();
		imgLoaderThread.setPriority(Thread.MIN_PRIORITY);
		logger.debug("Image loading thread started. Nr of imgages: "+keywordHits.size());  

		
		tv.setInput(kwSearchResult.getKeywordHits());		
		tv.refresh();		
		shell.redraw();		
	}
	
	public void putInImageMap(KeywordHit kwHit){
		String imgKey = kwHit.getPageUrl().replace("https://dbis-thure.uibk.ac.at/f/Get?id=", "");
		imgKey = imgKey.replace("&fileType=view", "");

		String coords = kwHit.getTextCoords();
		String imgId = kwHit.getId();
		
		if(imageMap.containsKey(imgId)) return;
		
		int[] cropValues = FullTextSearchComposite.getCropValues(coords);
		URL url;
		Image img = null;			
		try {
			url = imgStoreClient.getUriBuilder().getImgCroppedUri(imgKey, cropValues[0], cropValues[1], cropValues[2], cropValues[3]).toURL();
			img = ImageDescriptor.createFromURL(url).createImage();

		} catch (MalformedURLException | IllegalArgumentException e1) {
			e1.printStackTrace();
		}
		imageMap.put(imgId, img);
	}
	
	KeywordHit lastHoverHit = null;
	private class MouseMoveListener implements Listener {
		
		final Table table;
		KeywordHit currentHit;
		Thread singleImageLoaderThread;
		
		public MouseMoveListener(Table resultTable) {
			this.table = resultTable;
		}
		
		Runnable loadPreviewImage = new Runnable(){

			@Override
			public void run() {
				if(currentHit != null){
					putInImageMap(currentHit);
					currentImgOrig = imageMap.get(currentHit.getId());
					Display.getDefault().asyncExec(()->{
						canvas.redraw();
					});
				}
			}
			
		};
		
		public void handleEvent(Event e) {
			Point p = new Point(e.x, e.y);

			TableItem hoverItem = table.getItem(p);
			

			if (hoverItem != null 
					&& (currentHit = ((KeywordHit) hoverItem.getData())) != null
					&& !currentHit.equals(lastHoverHit)) {				

				currentHit = (KeywordHit) hoverItem.getData();
//				logger.debug(currentHit.getId());
				
				if(imageMap.get(currentHit.getId()) != null){
					currentImgOrig = imageMap.get(currentHit.getId());

				}else{
					currentImgOrig = Images.LOADING_IMG;
					singleImageLoaderThread = new Thread(loadPreviewImage,"WordThmbLoaderThread");
					singleImageLoaderThread.start();
				}
				canvas.redraw();
				lastHoverHit = currentHit;
			}
		}
	}
}


