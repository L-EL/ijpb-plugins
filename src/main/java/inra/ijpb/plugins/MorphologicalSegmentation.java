package inra.ijpb.plugins;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.event.WindowEvent;
import java.awt.image.ColorModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.ImageRoi;
import ij.gui.ImageWindow;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.gui.ShapeRoi;
import ij.gui.StackWindow;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.plugin.frame.Recorder;
import ij.process.ImageConverter;
import ij.process.ImageProcessor;
import ij.process.StackConverter;
import inra.ijpb.binary.BinaryImages;
import inra.ijpb.binary.ConnectedComponents;
import inra.ijpb.data.image.ColorImages;
import inra.ijpb.data.image.Images3D;
import inra.ijpb.morphology.MinimaAndMaxima3D;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel3D;
import inra.ijpb.util.ColorMaps;
import inra.ijpb.util.ColorMaps.CommonLabelMaps;
import inra.ijpb.watershed.Watershed;
import ij.io.LogStream;

//import Sync_Win; // .*;//. //DisplayChangeEvent // pb : http://www.wikihow.com/Add-JARs-to-Project-Build-Paths-in-Eclipse-%28Java%29

/**
 * Plugin to perform automatic segmentation of 2D and 3D images 
 * based on morphological operations, mainly extended minima
 * and watershed transforms.
 * 
 * References:
 * [1] Soille, P., Morphological Image Analysis: Principles and
 *     Applications, Springer-Verlag, 1999, pp. 170-171.
 * 
 * @author Ignacio Arganda-Carreras
 *
 */
public class MorphologicalSegmentation implements PlugIn {

	/** main GUI window */
	private CustomWindow win;

	/** original input image */
	ImagePlus inputImage = null;
	
	/** copy of original input image stack */
	ImageStack inputStackCopy = null;

	/** image to be displayed in the GUI */
	ImagePlus displayImage = null;

	/** gradient image stack */
	ImageStack gradientStack = null;

	/** image containing the final results of the watershed segmentation (basins with or without dams) */
	ImagePlus resultImage = null;	
	
	// add by elise 
	/** image containing the final results of the watershed segmentation (basins with or without dams) */
	ImagePlus markersImage = null;
	ImagePlus reslicedImage = null;
	double coordResliceToRaw[][] = new double[3][3],coordRawToReslice[][] = new double[3][3];
	double origine[] = new double[3];
	int xMouse, yMouse, zMouse, oldXMouse, oldYMouse, oldZMouse;
	int p[]= new int[3];
	int oldp[]= new int[3];
	double currentMag ;
	Rectangle currentSrcRect = new Rectangle(0,0,400,400);
	//end add

	/** parameters panel (segmentation + display options) */
	JPanel paramsPanel = new JPanel();

	/** main panel */
	Panel all = new Panel();
	
	/** flag to indicate 2D input image */
	boolean inputIs2D = false;

	//	 input panel design:
	//	 __ Input Image ____________
	//  |                 _______   |
	//	| o Border Image | icon  |  |
	//	| o Object Image |_______|  |
	//	|---------------------------|
	//	|| Gradient type: [options]||
	//	|| Gradient size: [3]      ||
	//	|| x - show gradient       ||
	//	| --------------------------|
	//	|___________________________| 


	/** input image panel */
	JPanel inputImagePanel = new JPanel();
	/** input image options */
	ButtonGroup inputImageButtons;
	/** radio button to specify input image has borders already highlighted */
	JRadioButton borderButton;
	/** radio button to specify input image has highlighted objects and not borders */
	JRadioButton objectButton;
	/** text for border image type radio button */
	static String borderImageText = "Border Image";
	/** text for object image type radio button */
	static String objectImageText = "Object Image";
	/** panel to store the radio buttons with the image type options */
	JPanel radioPanel = new JPanel( new GridLayout(0, 1) );
	
	ImageIcon borderIcon = new ImageIcon( MorphologicalSegmentation.class.getResource( "/gradient-icon.png" ));
	ImageIcon objectIcon = new ImageIcon( MorphologicalSegmentation.class.getResource( "/blobs-icon.png" ));
	JLabel inputImagePicture;
	JPanel buttonsAndPicPanel = new JPanel();

	/** gradient options panel */
	JPanel gradientOptionsPanel = new JPanel();	
	/** gradient type panel */
	JPanel gradientTypePanel = new JPanel();
	/** gradient type label */
	JLabel gradientTypeLabel;
	/** gradient list of options */
	String[] gradientOptions = new String[]{ "Morphological" };
	/** gradient combo box */
	JComboBox gradientList;		
	/** gradient radius size panel */
	JPanel gradientSizePanel = new JPanel();
	/** gradient size label */
	JLabel gradientRadiusSizeLabel;
	/** gradient size text field */
	JTextField gradientRadiusSizeText;
	/** gradient radius */
	int gradientRadius = 1;
	/** flag to show the gradient result in the displayed canvas */
	boolean showGradient = false;

	/** checkbox to enable/disable the display of the gradient image */
	JCheckBox gradientCheckBox;
	/** gradient checkbox panel */
	JPanel showGradientPanel = new JPanel();
	/** flag to apply gradient to the input image */
	private boolean applyGradient = false;
	
	/** Resegmentation button (ReRun) ---> Elise*/
	JButton ResegmentButton;

	//	 Watershed segmentation panel design:
	//
	//	 __ Watershed Segmentation___
	//	| Tolerance: [10]            |	
	//	| x - Advanced options       |
	//	|  ------------------------- |
	//	| | x - Use dams            ||
	//	| | Connectivity: [6/26]    ||	
	//	|  ------------------------- |
	//  |          -----             |
	//  |         | Run |	         |
	//  |          -----             |	
	//	|____________________________| 

	/** watershed segmentation panel */
	JPanel segmentationPanel = new JPanel();

	/** extended regional minima dynamic panel */
	JPanel dynamicPanel = new JPanel();
	/** extended regional minima dynamic label */
	JLabel dynamicLabel;
	/** extended regional minima dynamic text field */
	JTextField dynamicText;

	/** advanced options panel */
	JPanel advancedOptionsPanel = new JPanel();
	/** checkbox to enable/disable the advanced options */
	JCheckBox advancedOptionsCheckBox;
	/** flag to select/deselect the advanced options */
	private boolean selectAdvancedOptions = false;	

	/** dams panel */
	JPanel damsPanel = new JPanel();
	/** checkbox to enable/disable the calculation of watershed dams */
	JCheckBox damsCheckBox;
	/** flag to select/deselect the calculation of watershed dams */
	private boolean calculateDams = true;

	/** connectivity choice */
	JPanel connectivityPanel = new JPanel();
	/** connectivity label */
	JLabel connectivityLabel;
	/** connectivity list of options (6 and 26) */
	String[] connectivityOptions = new String[]{ "6", "26" };
	/** connectivity combo box */
	JComboBox connectivityList;

	/** segmentation button (Run) */
	JButton segmentButton;

	//	Results panel design:
	//
	//	 __ Results__________________
	//	| Display: [Overlay basins]  |	
	//	| x - Overlay results        |
	//  |      --------------        |
	//  |     | Create Image |       |
	//  |      --------------        |	
	//	|____________________________| 

	/** main Results panel */
	JPanel displayPanel = new JPanel();

	/** label for the display combo box */
	JLabel displayLabel = null;

	/** text of option to display results as overlaid catchment basins */
	static String  overlaidBasinsText = "Overlaid basins";
	/** text of option to display results as overlaid dams (watershed lines) */
	static String overlaidDamsText = "Overlaid dams";
	/** text of option to display results as catchment basins */
	static String catchmentBasinsText = "Catchment basins";
	/** text of option to display results as binary watershed lines */
	static String watershedLinesText = "Watershed lines";
	
	// add by elise
	JPanel correctionPanel = new JPanel();
	/** text of option to display results as overlaid markers on raw image*/
	static String overlaidImageMarkersText = "image with markers";
	/** text of option to display results as overlaid markers on catchement basins image */
	static String overlaidBasinsMarkersText = "basins with markers";
	/** text of option to display results as markers image */
	static String markersText = "markers";
		// checkbox to modify markers
	/** checkbox to enable/disable the modifying of marker image */
	JCheckBox addMarkersCheckBox;
	/** flag to select/deselect the advanced options */
	private boolean addMarkers = false;	
	/** checkbox to enable/disable the modifying of marker image */
	JCheckBox removeMarkersCheckBox;
	/** flag to select/deselect the advanced options */
	private boolean removeMarkers = false;	
	
	/** checkbox to enable/disable the modifying of local marker image */
	JCheckBox localMarkersCheckBox;
	/** flag to select/deselect the advanced options */
	private boolean localMarkers = false;	
	
	/** checkbox to enable/disable the spreading of local markers */
	JCheckBox spreadingCheckBox;
	/** flag to select/deselect the spreading of local markers */
	private boolean spreadingMarkers = false;
	
	/** checkbox to synchronize resliced image */
	JCheckBox synchronizeCheckBox;
	/** flag to select/deselect  resliced image synchronizing*/
	private boolean synchronize = false;
	
	JCheckBox removeSmallLabelsCheckBox;
	private boolean removeMinLabels = false;	
	
	/*threshold panel */
	JPanel thresholdPanel = new JPanel();
	/** thresold label */
	JLabel thresholdLabel;
	/**  threshold text field */
	JTextField thresholdText;
	
	
	//end add

	/** list of result display options (to show in the GUI canvas) */
	String[] resultDisplayOption = new String[]{ overlaidBasinsText, 
			overlaidDamsText, catchmentBasinsText, watershedLinesText, overlaidImageMarkersText, overlaidBasinsMarkersText, markersText};
	/** result display combo box */
	JComboBox resultDisplayList = null;
	/** panel to store the combo box with the display options */
	JPanel resultDisplayPanel = new JPanel();

	/** check box to toggle the result overlay */
	JCheckBox toggleOverlayCheckBox = null;
	/** panel to store the check box to toggle the result overlay */
	JPanel overlayPanel = new JPanel();

	/** button to display the results in a new window ("Create Image") */
	JButton resultButton = null;	

	/** flag to display the result overlay in the canvas */
	private boolean showColorOverlay = false;

	/** executor service to launch threads for the plugin methods and events */
	final ExecutorService exec = Executors.newFixedThreadPool(1);

	/** thread to run the segmentation */
	private Thread segmentationThread = null;

	/** text of the segmentation button when segmentation not running */
	private String segmentText = "Run";
	/** tip text of the segmentation button when segmentation not running */
	private String segmentTip = "Run the morphological segmentation";
	/** text of the segmentation button when segmentation running */
	private String stopText = "STOP";
	/** tip text of the segmentation button when segmentation running */
	private String stopTip = "Click to abort segmentation";
	
	  //add by ----> Elise
    /** text of the segmentation button when segmentation not running */
	private String ResegmentText = "ReRun";
	/** tip text of the segmentation button when segmentation not running */
	private String ResegmentTip = "ReRun the morphological segmentation";
    //end Elise

	/** enumeration of result modes */
	public static enum ResultMode { OVERLAID_BASINS, OVERLAID_DAMS, BASINS, LINES ,OVERLAID_IMG_MARKERS,OVERLAID_BASINS_MARKERS,MARKERS}; //modify by elise

	// Macro recording constants (corresponding to  
	// the static method names to be called)
	/** name of the macro method to segment the 
	 * current image based on the current parameters */
	public static String RUN_SEGMENTATION = "segment";
	/** name of the macro method to toggle the current overlay */
	public static String SHOW_RESULT_OVERLAY = "toggleOverlay";
	/** name of the macro method to show current segmentation result in a new window */
	public static String CREATE_IMAGE = "createResultImage";
	
	/** name of the macro method to set the input image type */
	public static String SET_INPUT_TYPE = "setInputImageType";
	/** name of the macro method to set the flag to show the gradient image */
	public static String SHOW_GRADIENT = "setShowGradient";
	/** name of the macro method to set the output display format */
	public static String SET_DISPLAY = "setDisplayFormat";

	/** name of the macro method to set the gradient radius */
	public static String SET_RADIUS = "setGradientRadius";
	
	/** opacity to display overlays */
	double opacity = 1.0/3.0;

	/**
	 * Custom window to define the plugin GUI
	 */
	
	private class CustomWindow extends StackWindow
	{
		/**
		 * serial version uid
		 */
		private static final long serialVersionUID = -6201855439028581892L;

		/**
		 * Listener for the GUI buttons
		 */
		private ActionListener listener = new ActionListener() {

			@Override
			public void actionPerformed( final ActionEvent e ) 
			{

				final String command = e.getActionCommand();

				// listen to the buttons on separate threads not to block
				// the event dispatch thread
				exec.submit(new Runnable() {

					public void run()
					{
						IJ.log(""+e.getSource()+"");
						
						// "Run" segmentation button		
						if( e.getSource() == segmentButton )
						{
							runSegmentation( command );						
						}	
						// ReRun ---> add by Elise
						else if( e.getSource() == ResegmentButton )
						{
							RerunSegmentation( command );
						}
						else if( e.getSource() == addMarkersCheckBox )
						{
							addMarkers = !addMarkers;
							resultDisplayList.setSelectedItem( overlaidImageMarkersText);
							updateResultOverlay();
							if (addMarkers)
							{
								ic.addMouseListener(mlAdd);
								ic.addMouseMotionListener(mlAddDragged);
							}
							else
							{
								ic.removeMouseListener(mlAdd); // doesn't work 
								ic.removeMouseMotionListener(mlAddDragged);
							}
						}
						else if( e.getSource() == removeMarkersCheckBox )
						{
							removeMarkers = !removeMarkers;
							
							if (removeMarkers)
							{
								ic.addMouseListener(mlRemove);
								resultDisplayList.setSelectedItem( overlaidImageMarkersText );
								updateResultOverlay();
							}
							else
							{
								ic.removeMouseListener(mlRemove); // doesn't work 
							}
						}
						else if( e.getSource() == synchronizeCheckBox )
						{
							
							synchronize = !synchronize;
							 
							
							if (synchronize)
							{
								reslicedImage = AskForAnotherImage();
								ImageWindow iw = reslicedImage.getWindow();
								IJ.log(""+reslicedImage+"");
								if( null ==  reslicedImage )
								{
									IJ.log( "The synchronization was interrupted!" );
									IJ.showStatus( "The synchronization was interrupted!" );
									IJ.showProgress( 1.0 );
									return;
								}
								 changeOfBase();
								 IJ.log("coordResliceToRaw =  "+coordResliceToRaw[0][0]+" "+coordResliceToRaw[0][1]+" "+coordResliceToRaw[0][2]+"\n "+coordResliceToRaw[1][0]+" "+coordResliceToRaw[1][1]+" "+coordResliceToRaw[1][2]+"\n "+coordResliceToRaw[2][0]+" "+coordResliceToRaw[2][1]+" "+coordResliceToRaw[2][2]+" \n");
								 IJ.log(" coordRawToReslice = "+coordRawToReslice[0][0]+" "+coordRawToReslice[0][1]+" "+coordRawToReslice[0][2]+"\n "+coordRawToReslice[1][0]+" "+coordRawToReslice[1][1]+" "+coordRawToReslice[1][2]+"\n "+coordRawToReslice[2][0]+" "+coordRawToReslice[2][1]+" "+coordRawToReslice[2][2]+" \n");
								 System.out.println(ic); 
								//ic.addMouseListener(mlSyn); // no need to click on the segmentation plugin image
								ic.addMouseMotionListener(mmlSyn);
								//ic.addMouseWheelListener(mlSynWheel); // --> to improve : erase normal behavior of the Wheel
								// synchro 
								iw.getCanvas().addMouseMotionListener(mmlSyn);
								iw.getCanvas().addMouseListener(mlSyn);
								//iw.getCanvas().addMouseWheelListener(mlSynWheel); // --> to improve : erase normal behavior of the Wheel

								
							}
							else
							{
								ImageWindow iw = reslicedImage.getWindow();
								ic.removeMouseListener(mlSyn);
								ic.removeMouseMotionListener(mmlSyn);
								// synchro 
								iw.getCanvas().removeMouseMotionListener(mmlSyn);
								iw.getCanvas().removeMouseListener(mlSyn);
								reslicedImage = null;
							}
						}
						else if( e.getSource() == localMarkersCheckBox )
						{
							localMarkers = !localMarkers;
							//resultDisplayList.setSelectedItem( overlaidImageMarkersText );
							//updateResultOverlay();
							if (localMarkers)
							{
								ic.addMouseListener(mlLocalChange);
							}
							else
							{
								ic.removeMouseListener(mlLocalChange); 
							}
						}
						else if( e.getSource() == removeSmallLabelsCheckBox )
						{
							removeMinLabels = !removeMinLabels;
						}
						else if( e.getSource() == spreadingCheckBox )
						{
							spreadingMarkers = !spreadingMarkers;
						}
						//end add
						// "Show result overlay" check box
						else if( e.getSource() == toggleOverlayCheckBox )
						{
							toggleOverlay();
							// Macro recording
							String[] arg = new String[] {};
							record( SHOW_RESULT_OVERLAY, arg );
						}
						// "Create Image" button
						else if( e.getSource() == resultButton )
						{
							createResultImage();						
						}
						// "Advanced options" check box
						else if( e.getSource() == advancedOptionsCheckBox )
						{
							selectAdvancedOptions = !selectAdvancedOptions;
							enableAdvancedOptions( selectAdvancedOptions );
						}
						// "Show gradient" check box
						else if ( e.getSource() == gradientCheckBox )
						{
							setShowGradient( !showGradient );
							// Macro recording
							String[] arg = new String[] { String.valueOf( showGradient ) };
							record( SHOW_GRADIENT, arg );
						}
						// "Display" result combo box
						else if ( e.getSource() == resultDisplayList )
						{
							if( showColorOverlay )
								updateResultOverlay();
							// Macro recording
							String[] arg = new String[] { (String) resultDisplayList.getSelectedItem() };
							record( SET_DISPLAY, arg );
						}
						// Input image radio buttons (object or border) 
						else if( command == objectImageText ||  command == borderImageText)
						{
							setInputImageType( command );
							
							if ( command == objectImageText )
								inputImagePicture.setIcon( objectIcon );
							else
								inputImagePicture.setIcon( borderIcon );
							
							String type = command == objectImageText ? "object" : "border";
							// Macro recording
							String[] arg = new String[] { type };
							record( SET_INPUT_TYPE, arg );
						}
					}

					
					
				});
			}

		};


		/**
		 * Construct the plugin window
		 * 
		 * @param imp input image
		 */
		CustomWindow( ImagePlus imp )
		{
			super(imp, new ImageCanvas(imp));

			final ImageCanvas canvas = (ImageCanvas) getCanvas();
			
			
			// Zoom in if image is too small
			while(ic.getWidth() < 512 && ic.getHeight() < 512)
				IJ.run( imp, "In","" );

			setTitle( "Morphological Segmentation" );

			// === Input Image panel ===

			// input image options (border or object types)			
			borderButton = new JRadioButton( borderImageText );
			borderButton.setSelected( !applyGradient );
			borderButton.setActionCommand( borderImageText );
			borderButton.addActionListener( listener );
			borderButton.setToolTipText( "input image has object borders already highlighted" );
			
			inputImagePicture = new JLabel( borderIcon );
			inputImagePicture.setToolTipText( "simplified model of your image" );
			
			objectButton = new JRadioButton( objectImageText );
			objectButton.setActionCommand( objectImageText );
			objectButton.addActionListener( listener );
			objectButton.setToolTipText( "input image has highlighted objects (dark or bright)" );

			inputImageButtons = new ButtonGroup();
			inputImageButtons.add( borderButton );
			inputImageButtons.add( objectButton );
			radioPanel.add( borderButton );
			radioPanel.add( objectButton );

			buttonsAndPicPanel.add( radioPanel);
	        buttonsAndPicPanel.add( inputImagePicture );
			
			// gradient options panel (activated when selecting object image)
			gradientTypeLabel = new JLabel( "Gradient type " );			
			gradientTypeLabel.setToolTipText( "type of gradient filter to apply" );		
			gradientList = new JComboBox( gradientOptions );			
			gradientTypePanel.add( gradientTypeLabel );
			gradientTypePanel.add( gradientList );			
			gradientTypePanel.setToolTipText( "type of gradient filter to apply" );	

			gradientRadiusSizeLabel = new JLabel( "Gradient radius" );
			gradientRadiusSizeLabel.setToolTipText( "radius in pixels of the gradient filter");			
			gradientRadiusSizeText = new JTextField( String.valueOf( gradientRadius ), 5 );
			gradientRadiusSizeText.setToolTipText( "radius in pixels of the gradient filter");
			gradientSizePanel.add( gradientRadiusSizeLabel );
			gradientSizePanel.add( gradientRadiusSizeText );

			gradientCheckBox = new JCheckBox( "Show gradient", false );
			gradientCheckBox.setToolTipText( "display gradient image instead of input image" );
			gradientCheckBox.addActionListener( listener );
			showGradientPanel.add( gradientCheckBox );

			GridBagLayout gradientOptionsLayout = new GridBagLayout();
			GridBagConstraints gradientOptionsConstraints = new GridBagConstraints();
			gradientOptionsConstraints.anchor = GridBagConstraints.WEST;
			gradientOptionsConstraints.gridwidth = 1;
			gradientOptionsConstraints.gridheight = 1;
			gradientOptionsConstraints.gridx = 0;
			gradientOptionsConstraints.gridy = 0;
			gradientOptionsPanel.setLayout( gradientOptionsLayout );

			gradientOptionsPanel.add( gradientTypePanel, gradientOptionsConstraints );
			gradientOptionsConstraints.gridy++;
			gradientOptionsPanel.add( gradientSizePanel, gradientOptionsConstraints );
			gradientOptionsConstraints.gridy++;			
			gradientOptionsPanel.add( showGradientPanel, gradientOptionsConstraints );
			gradientOptionsConstraints.gridy++;

			gradientOptionsPanel.setBorder( BorderFactory.createTitledBorder("") );

			enableGradientOptions( applyGradient );

			// add components to input image panel
			inputImagePanel.setBorder( BorderFactory.createTitledBorder( "Input Image" ) );
			GridBagLayout inputImageLayout = new GridBagLayout();
			GridBagConstraints inputImageConstraints = new GridBagConstraints();
			inputImageConstraints.anchor = GridBagConstraints.CENTER;
			inputImageConstraints.fill = GridBagConstraints.NONE;
			inputImageConstraints.gridwidth = 1;
			inputImageConstraints.gridheight = 1;
			inputImageConstraints.gridx = 0;
			inputImageConstraints.gridy = 0;
			inputImageConstraints.insets = new Insets(5, 5, 6, 6);
			inputImagePanel.setLayout( inputImageLayout );						

			inputImagePanel.add( buttonsAndPicPanel, inputImageConstraints );
			inputImageConstraints.gridy++;
			inputImageConstraints.anchor = GridBagConstraints.NORTHWEST;
			inputImageConstraints.fill = GridBagConstraints.HORIZONTAL;
			inputImagePanel.add( gradientOptionsPanel, inputImageConstraints );			
			inputImageConstraints.gridy++;


			// === Watershed Segmentation panel ===

			// regional minima dynamic value ("Tolerance")
			dynamicLabel = new JLabel( "Tolerance" );
			dynamicLabel.setToolTipText( "Tolerance in the search of local minima" );
			dynamicText = new JTextField( "10", 5 );
			dynamicText.setToolTipText( "Tolerance in the search of local minima" );
			dynamicPanel.add( dynamicLabel );
			dynamicPanel.add( dynamicText );
			dynamicPanel.setToolTipText( "Tolerance in the search of local minima" );							

			// advanced options (connectivity + priority queue choices)
			advancedOptionsCheckBox = new JCheckBox( "Advanced options", selectAdvancedOptions );
			advancedOptionsCheckBox.setToolTipText( "Enable advanced options" );
			advancedOptionsCheckBox.addActionListener( listener );

			// dams option
			damsCheckBox = new JCheckBox( "Calculate dams", calculateDams );
			damsCheckBox.setToolTipText( "Calculate watershed dams" );
			damsPanel.add( damsCheckBox );

			// connectivity
			if( inputIs2D )
				connectivityOptions = new String[]{ "4", "8" };
			connectivityList = new JComboBox( connectivityOptions );
			connectivityList.setToolTipText( "Voxel connectivity to use" );
			connectivityLabel = new JLabel( "Connectivity" );
			connectivityPanel.add( connectivityLabel );
			connectivityPanel.add( connectivityList );
			connectivityPanel.setToolTipText( "Voxel connectivity to use" );

			enableAdvancedOptions( selectAdvancedOptions );

			// add components to advanced options panel			
			GridBagLayout advancedOptionsLayout = new GridBagLayout();
			GridBagConstraints advancedOptoinsConstraints = new GridBagConstraints();
			advancedOptoinsConstraints.anchor = GridBagConstraints.WEST;
			advancedOptoinsConstraints.gridwidth = 1;
			advancedOptoinsConstraints.gridheight = 1;
			advancedOptoinsConstraints.gridx = 0;
			advancedOptoinsConstraints.gridy = 0;
			advancedOptionsPanel.setLayout( advancedOptionsLayout );

			advancedOptionsPanel.add( damsPanel, advancedOptoinsConstraints );
			advancedOptoinsConstraints.gridy++;			
			advancedOptionsPanel.add( connectivityPanel, advancedOptoinsConstraints );

			advancedOptionsPanel.setBorder(BorderFactory.createTitledBorder(""));

			// Segmentation button
			segmentButton = new JButton( segmentText );
			segmentButton.setToolTipText( segmentTip );
			segmentButton.addActionListener( listener );
			
			 // ReSegmentation button ---> Elise
            ResegmentButton = new JButton( ResegmentText );
            ResegmentButton.setToolTipText( ResegmentTip );
            ResegmentButton.addActionListener( listener );
            
            	// add modify markers button
         			addMarkersCheckBox = new JCheckBox( "add markers", addMarkers );
         			addMarkersCheckBox.setToolTipText( "Enable modifying markers" );
         			addMarkersCheckBox.addActionListener( listener );
         			removeMarkersCheckBox = new JCheckBox( "remove markers", removeMarkers );
         			removeMarkersCheckBox.setToolTipText( "Enable modifying markers" );
         			removeMarkersCheckBox.addActionListener( listener );
         			localMarkersCheckBox = new JCheckBox( "change localy markers", localMarkers );
         			localMarkersCheckBox.setToolTipText( "Enable modifying localy markers" );
         			localMarkersCheckBox.addActionListener( listener );
         			
         			spreadingCheckBox = new JCheckBox( "spread", spreadingMarkers);
         			spreadingCheckBox.setToolTipText( "spread local markers" );
         			spreadingCheckBox.addActionListener( listener );
        			
         			removeSmallLabelsCheckBox = new JCheckBox( "remove small markers", removeMinLabels );
         			removeSmallLabelsCheckBox.setToolTipText( "remove small labels" );
         			removeSmallLabelsCheckBox.addActionListener( listener );
         			
         			synchronizeCheckBox = new JCheckBox( "synchronize", synchronize );
         			synchronizeCheckBox.setToolTipText( "synchronize resliced image" );
         			synchronizeCheckBox.addActionListener( listener );
         			
         		// size label threshold  value ("threshold")
         			thresholdLabel = new JLabel( "threshold" );
         			thresholdLabel.setToolTipText( "threshold" );
         			thresholdText = new JTextField( "10", 5 );
         			thresholdText.setToolTipText( "threshold" );
         			thresholdPanel.add( thresholdLabel );
         			thresholdPanel.add( thresholdText );
         			thresholdPanel.setToolTipText( "threshold" );	
        			
            //end add

			// Segmentation panel
			segmentationPanel.setBorder( BorderFactory.createTitledBorder( "Watershed Segmentation" ) );
			GridBagLayout segmentationLayout = new GridBagLayout();
			GridBagConstraints segmentationConstraints = new GridBagConstraints();
			segmentationConstraints.anchor = GridBagConstraints.NORTHWEST;
			segmentationConstraints.fill = GridBagConstraints.HORIZONTAL;
			segmentationConstraints.gridwidth = 1;
			segmentationConstraints.gridheight = 1;
			segmentationConstraints.gridx = 0;
			segmentationConstraints.gridy = 0;
			segmentationConstraints.insets = new Insets(5, 5, 6, 6);
			segmentationPanel.setLayout( segmentationLayout );						

			segmentationPanel.add( dynamicPanel, segmentationConstraints );
			segmentationConstraints.gridy++;
			segmentationPanel.add( advancedOptionsCheckBox, segmentationConstraints );
			segmentationConstraints.gridy++;			
			segmentationPanel.add( advancedOptionsPanel, segmentationConstraints );			
			segmentationConstraints.gridy++;
			segmentationConstraints.anchor = GridBagConstraints.CENTER;
			segmentationConstraints.fill = GridBagConstraints.NONE;
			segmentationPanel.add( segmentButton, segmentationConstraints );
			
			// add by ----> Elise
			segmentationConstraints.gridy++;
			segmentationPanel.add( addMarkersCheckBox, segmentationConstraints );
			segmentationConstraints.gridx++;		
			segmentationPanel.add( removeMarkersCheckBox, segmentationConstraints );
			segmentationConstraints.gridx--;
			segmentationConstraints.gridy++;			
			segmentationPanel.add( localMarkersCheckBox, segmentationConstraints );
			segmentationConstraints.gridx++;		
			segmentationPanel.add( spreadingCheckBox, segmentationConstraints );
			segmentationConstraints.gridx--;
			segmentationConstraints.gridy++;			
			segmentationPanel.add( removeSmallLabelsCheckBox, segmentationConstraints );
			segmentationConstraints.gridx++;		
			segmentationPanel.add( thresholdPanel, segmentationConstraints );
			segmentationConstraints.gridx--;	
			segmentationConstraints.gridy++;					
			segmentationPanel.add( synchronizeCheckBox, segmentationConstraints );
			segmentationConstraints.gridx++;
			segmentationConstraints.anchor = GridBagConstraints.CENTER;
			segmentationConstraints.fill = GridBagConstraints.NONE;
			segmentationPanel.add( ResegmentButton, segmentationConstraints );
			// end add
    		
			// === Results panel ===

			// Display result panel
			displayLabel = new JLabel( "Display" );
			displayLabel.setEnabled( false );
			resultDisplayList = new JComboBox( resultDisplayOption );
			resultDisplayList.setEnabled( false );
			resultDisplayList.setToolTipText( "Select how to display segmentation results" );
			resultDisplayList.addActionListener( listener );

			resultDisplayPanel.add( displayLabel );
			resultDisplayPanel.add( resultDisplayList );

			// Toggle overlay check box
			showColorOverlay = false;
			toggleOverlayCheckBox = new JCheckBox( "Show result overlay" );
			toggleOverlayCheckBox.setEnabled( showColorOverlay );
			toggleOverlayCheckBox.setToolTipText( "Toggle overlay with segmentation result" );
			toggleOverlayCheckBox.addActionListener( listener );
			overlayPanel.add( toggleOverlayCheckBox );

			// Create Image button
			resultButton = new JButton( "Create Image" );
			resultButton.setEnabled( false );
			resultButton.setToolTipText( "Show segmentation result in new window" );
			resultButton.addActionListener( listener );					

			// main Results panel
			displayPanel.setBorder( BorderFactory.createTitledBorder( "Results" ) );
			GridBagLayout displayLayout = new GridBagLayout();
			GridBagConstraints displayConstraints = new GridBagConstraints();
			displayConstraints.anchor = GridBagConstraints.NORTHWEST;
			displayConstraints.fill = GridBagConstraints.HORIZONTAL;
			displayConstraints.gridwidth = 1;
			displayConstraints.gridheight = 1;
			displayConstraints.gridx = 0;
			displayConstraints.gridy = 0;
			displayConstraints.insets = new Insets(5, 5, 6, 6);
			displayPanel.setLayout( displayLayout );					

			displayPanel.add( resultDisplayPanel, displayConstraints );
			displayConstraints.gridy++;
			displayPanel.add( overlayPanel, displayConstraints );
			displayConstraints.gridy++;
			displayConstraints.anchor = GridBagConstraints.CENTER;
			displayConstraints.fill = GridBagConstraints.NONE;
			displayPanel.add( resultButton, displayConstraints );


			// Parameter panel (left side of the GUI, it includes the 
			// three main panels: Input Image, Watershed Segmentation
			// and Results).
			GridBagLayout paramsLayout = new GridBagLayout();
			GridBagConstraints paramsConstraints = new GridBagConstraints();
			paramsConstraints.insets = new Insets( 5, 5, 6, 6 );
			paramsPanel.setLayout( paramsLayout );
			paramsConstraints.anchor = GridBagConstraints.NORTHWEST;
			paramsConstraints.fill = GridBagConstraints.HORIZONTAL;
			paramsConstraints.gridwidth = 1;
			paramsConstraints.gridheight = 1;
			paramsConstraints.gridx = 0;
			paramsConstraints.gridy = 0;
			paramsPanel.add( inputImagePanel, paramsConstraints);
			paramsConstraints.gridy++;
			paramsPanel.add( segmentationPanel, paramsConstraints);
			paramsConstraints.gridy++;
			paramsPanel.add( displayPanel, paramsConstraints);
			paramsConstraints.gridy++;


			// main panel (including parameters panel and canvas)
			GridBagLayout layout = new GridBagLayout();
			GridBagConstraints allConstraints = new GridBagConstraints();
			all.setLayout(layout);

			// put parameter panel in place
			allConstraints.anchor = GridBagConstraints.NORTHWEST;
			allConstraints.fill = GridBagConstraints.BOTH;
			allConstraints.gridwidth = 1;
			allConstraints.gridheight = 1;
			allConstraints.gridx = 0;
			allConstraints.gridy = 0;
			allConstraints.gridheight = 2;
			allConstraints.weightx = 0;
			allConstraints.weighty = 0;

			all.add( paramsPanel, allConstraints );

			// put canvas in place
			allConstraints.gridx++;
			allConstraints.weightx = 1;
			allConstraints.weighty = 1;
			allConstraints.gridheight = 1;
			all.add( canvas, allConstraints );

			allConstraints.gridy++;
			allConstraints.weightx = 0;
			allConstraints.weighty = 0;

			// if the input image is 3d, put the
			// slice selectors in place
			if( null != super.sliceSelector )
			{
				sliceSelector.setValue( inputImage.getCurrentSlice() );
				displayImage.setSlice( inputImage.getCurrentSlice() );
				
				all.add( super.sliceSelector, allConstraints );

				if( null != super.zSelector )
					all.add( super.zSelector, allConstraints );
				if( null != super.tSelector )
					all.add( super.tSelector, allConstraints );
				if( null != super.cSelector )
					all.add( super.cSelector, allConstraints );

			}
			allConstraints.gridy--;


			GridBagLayout wingb = new GridBagLayout();
			GridBagConstraints winc = new GridBagConstraints();
			winc.anchor = GridBagConstraints.NORTHWEST;
			winc.fill = GridBagConstraints.BOTH;
			winc.weightx = 1;
			winc.weighty = 1;
			setLayout( wingb );
			add( all, winc );
			
			// Fix minimum size to the preferred size at this point
			pack();
			setMinimumSize( getPreferredSize() );

			// add especial listener if the input image is a stack
			if(null != sliceSelector)
			{
				// add adjustment listener to the scroll bar
				sliceSelector.addAdjustmentListener(new AdjustmentListener() 
				{

					public void adjustmentValueChanged(final AdjustmentEvent e) {
						exec.submit(new Runnable() {
							public void run() {							
								if(e.getSource() == sliceSelector)
								{
									//IJ.log("moving scroll");
									displayImage.killRoi();
									if( showColorOverlay )
									{
										updateResultOverlay();
										displayImage.updateAndDraw();
									}
								}

							}							
						});
					}
				});

				// mouse wheel listener to update the rois while scrolling
				addMouseWheelListener(new MouseWheelListener() {

					@Override
					public void mouseWheelMoved(final MouseWheelEvent e) {

						exec.submit(new Runnable() {
							public void run() 
							{
								//IJ.log("moving scroll");
								displayImage.killRoi();
								if( showColorOverlay )
								{
									updateResultOverlay();
									displayImage.updateAndDraw();
								}
							}
						});

					}
				});

				// key listener to repaint the display image and the traces
				// when using the keys to scroll the stack
				KeyListener keyListener = new KeyListener() {

					@Override
					public void keyTyped(KeyEvent e) {}

					@Override
					public void keyReleased(final KeyEvent e) {
						exec.submit(new Runnable() {
							public void run() 
							{
								if(e.getKeyCode() == KeyEvent.VK_LEFT ||
										e.getKeyCode() == KeyEvent.VK_RIGHT ||
										e.getKeyCode() == KeyEvent.VK_LESS ||
										e.getKeyCode() == KeyEvent.VK_GREATER ||
										e.getKeyCode() == KeyEvent.VK_COMMA ||
										e.getKeyCode() == KeyEvent.VK_PERIOD)
								{
									//IJ.log("moving scroll");
									displayImage.killRoi();
									if( showColorOverlay )
									{
										updateResultOverlay();
										displayImage.updateAndDraw();
									}
								}
							}
						});

					}

					@Override
					public void keyPressed(KeyEvent e) {}
				};
				// add key listener to the window and the canvas
				addKeyListener(keyListener);
				canvas.addKeyListener(keyListener);

			}		
		}// end CustomWindow constructor

		/**
		 * Overwrite windowClosing to display the input image after closing 
		 * the GUI and shut down the executor service
		 */
		@Override
		public void windowClosing( WindowEvent e ) 
		{							
			super.windowClosing( e );

			if( null != inputImage )
			{
				if( null != displayImage )
					inputImage.setSlice( displayImage.getCurrentSlice() );
				
				// display input image
				inputImage.getWindow().setVisible( true );
			}

			// remove listeners
			borderButton.removeActionListener( listener );
			objectButton.removeActionListener( listener );
			gradientCheckBox.removeActionListener( listener );
			advancedOptionsCheckBox.removeActionListener( listener );
			segmentButton.removeActionListener( listener );
			ResegmentButton.removeActionListener( listener );// add by elise 
			resultDisplayList.removeActionListener( listener );
			toggleOverlayCheckBox.removeActionListener( listener );
			resultButton.removeActionListener( listener );
			
			if( null != displayImage )
			{
				//displayImage.close();
				displayImage = null;
			}
			// shut down executor service
			exec.shutdownNow();
		}

		/**
		 * Set dynamic value in the GUI
		 * modify by Elise : int to double
		 * @param dynamic dynamic value
		 */
		void setDynamic( double dynamic )
		{
			dynamicText.setText( Double.toString(dynamic) );
		}

		/**
		 * Set flag and GUI checkbox to calculate watershed dams
		 * 
		 * @param b boolean flag
		 */
		void setCalculateDams( boolean b )
		{
			calculateDams = b;
			damsCheckBox.setSelected( b );
		}

		/**
		 * Set connectivity value in the GUI
		 * 
		 * @param connectivity 4-8 or 6-26 neighbor connectivity
		 */
		void setConnectivity( int connectivity )
		{			
			if( ( inputImage.getImageStackSize() > 1 && (connectivity == 6  || connectivity == 26 ) )
				|| ( inputImage.getImageStackSize() == 1 && (connectivity == 4  || connectivity == 8 ) ) )
				connectivityList.setSelectedItem( Integer.toString(connectivity) );									
		}


		/**
		 * Get segmentation command (text on the segment button)
		 * @return text on the segment button when segmentation is not running
		 */		
		String getSegmentText(){
			return segmentText;
		}

		/**
		 * Run morphological segmentation pipeline
		 */
		private void runSegmentation( String command ) 
		{
			// If the command is the text on the run segmentation button
			if ( command.equals( segmentText ) ) 
			{			
				// read connectivity
				int readConn = Integer.parseInt( (String) connectivityList.getSelectedItem() );

				// convert connectivity to 3D if needed (2D images are processed as 3D)
				if( inputIs2D )
					readConn = readConn == 4 ? 6 : 26;
				
				final int connectivity = readConn;
				
				// read dynamic
				final double dynamic;
				try{
					dynamic = Double.parseDouble( dynamicText.getText() );
				}
				catch( NullPointerException ex )
				{
					IJ.error( "Morphological Sementation", "ERROR: missing dynamic value" );
					return;
				}
				catch( NumberFormatException ex )
				{
					IJ.error( "Morphological Sementation", "ERROR: dynamic value must be a number" );
					return;
				}

				double max = 255;
				int bitDepth = inputImage.getBitDepth();
				if( bitDepth == 16 )
					max = 65535;
				else if( bitDepth == 32 )
					max = Float.MAX_VALUE;

				if( dynamic < 0 || dynamic > max )
				{
					IJ.error( "Morphological Sementation", "ERROR: the dynamic value must be a number between 0 and " + max );
					return;
				}

				// Set button text to "STOP"
				segmentButton.setText( stopText );
				segmentButton.setToolTipText( stopTip );
				segmentButton.setSize( segmentButton.getMinimumSize() );
				segmentButton.repaint();

				final Thread oldThread = segmentationThread;

				// Thread to run the segmentation
				Thread newThread = new Thread() {								 

					public void run()
					{
						// Wait for the old task to finish
						if (null != oldThread) 
						{
							try { 
								IJ.log("Waiting for old task to finish...");
								oldThread.join(); 
							} 
							catch (InterruptedException ie)	{ /*IJ.log("interrupted");*/ }
						}

						// read dams flag
						calculateDams = damsCheckBox.isSelected();

						// disable parameter panel
						setParamsEnabled( false );

						// get original image info
						ImageStack image = inputStackCopy;

						final long start = System.currentTimeMillis();

						if( applyGradient )
						{
							// read radius to use
							try{
								gradientRadius = Integer.parseInt( gradientRadiusSizeText.getText() );
							}
							catch( NullPointerException ex )
							{
								IJ.error( "Morphological Sementation", "ERROR: missing gradient radius value" );
								return;
							}
							catch( NumberFormatException ex )
							{
								IJ.error( "Morphological Sementation", "ERROR: radius value must be an integer number" );
								return;
							}

							final long t1 = System.currentTimeMillis();
							IJ.log( "Applying morphological gradient to input image..." );

							Strel3D strel = Strel3D.Shape.CUBE.fromRadius( gradientRadius );
							image = Morphology.gradient( image, strel );
							//(new ImagePlus("gradient", image) ).show();

							// store gradient image
							gradientStack = image;

							final long t2 = System.currentTimeMillis();
							IJ.log( "Morphological gradient took " + (t2-t1) + " ms.");
							
							// macro recording
							String[] arg = new String[] { gradientRadiusSizeText.getText() };
							record( SET_RADIUS, arg );
						}

						IJ.log( "Running extended minima with dynamic value " + dynamic + "..." ); //modify by Elise (remove (int) in front of dynamic value)
						final long step0 = System.currentTimeMillis();				

						// Run extended minima
						ImageStack regionalMinima = MinimaAndMaxima3D.extendedMinimaDouble( image, dynamic, connectivity );//modify by Elise (remove (int) in front of dynamic value)
						// add by elise
						markersImage = new ImagePlus( "markers", regionalMinima );
						markersImage.setCalibration( inputImage.getCalibration() );
						
						//conversion 32 bit : la segmentation ne fonctionne plus
						//if (imp.getStackSize() > 1)     new StackConverter(markersImage).convertToGray32();
						 //else     new ImageConverter(markersImage).convertToGray32(); 
						
						
						
						//end add 
						
						if( null == regionalMinima )
						{
							IJ.log( "The segmentation was interrupted!" );
							IJ.showStatus( "The segmentation was interrupted!" );
							IJ.showProgress( 1.0 );
							return;
						}

						final long step1 = System.currentTimeMillis();		
						IJ.log( "Regional minima took " + (step1-step0) + " ms.");

						IJ.log( "Imposing regional minima on original image (connectivity = " + connectivity + ")..." );

						// Impose regional minima over the original image
						ImageStack imposedMinima = MinimaAndMaxima3D.imposeMinima( image, regionalMinima, connectivity );

						if( null == imposedMinima )
						{
							IJ.log( "The segmentation was interrupted!" );
							IJ.showStatus( "The segmentation was interrupted!" );
							IJ.showProgress( 1.0 );
							return;
						}

						final long step2 = System.currentTimeMillis();
						IJ.log( "Imposition took " + (step2-step1) + " ms." );

						IJ.log( "Labeling regional minima..." );
						//new ImagePlus("imposedMinima",imposedMinima).show();
						// Label regional minima
						ImageStack labeledMinima = ConnectedComponents.computeLabels( regionalMinima, connectivity, 32 );
						if( null == labeledMinima )
						{
							IJ.log( "The segmentation was interrupted!" );
							IJ.showStatus( "The segmentation was interrupted!" );
							IJ.showProgress( 1.0 );
							return;
						}

						final long step3 = System.currentTimeMillis();
						IJ.log( "Connected components took " + (step3-step2) + " ms." );

						// Apply watershed		
						IJ.log("Running watershed...");

						ImageStack resultStack = null;
						
						try{
							resultStack = Watershed.computeWatershed( imposedMinima, labeledMinima, 
								connectivity, calculateDams );
						}
						catch( Exception ex )
						{							
							ex.printStackTrace();
							IJ.log( "Error while runing watershed: " + ex.getMessage() );							
						}
						catch( OutOfMemoryError err )
						{
							err.printStackTrace();
							IJ.log( "Error: the plugin run out of memory. Please use a smaller input image." );
						}
						if( null == resultStack )
						{
							new ImagePlus("a",imposedMinima).show();
							new ImagePlus("b",labeledMinima).show();
							
							IJ.log( "The segmentation was interrupted!" );
							IJ.showStatus( "The segmentation was interrupted!" );
							IJ.showProgress( 1.0 );
							// set button back to initial text
							segmentButton.setText( segmentText );
							segmentButton.setToolTipText( segmentTip );
							return;
						}

						resultImage = new ImagePlus( "watershed", resultStack );
						resultImage.setCalibration( inputImage.getCalibration() );

						final long end = System.currentTimeMillis();
						IJ.log( "Watershed 3d took " + (end-step3) + " ms.");
						IJ.log( "Whole plugin took " + (end-start) + " ms.");

						// Adjust min and max values to display
						Images3D.optimizeDisplayRange( resultImage );

						byte[][] colorMap = CommonLabelMaps.fromLabel( CommonLabelMaps.SPECTRUM.getLabel() ).computeLut(255, true);;
						ColorModel cm = ColorMaps.createColorModel(colorMap, Color.BLACK);
						resultImage.getProcessor().setColorModel(cm);
						resultImage.getImageStack().setColorModel(cm);
						resultImage.updateAndDraw();

						// display result overlaying the input image						
						updateDisplayImage();
						updateResultOverlay();

						showColorOverlay = true;
						toggleOverlayCheckBox.setSelected( true );

						// enable parameter panel
						setParamsEnabled( true );
						// set button back to initial text
						segmentButton.setText( segmentText );
						segmentButton.setToolTipText( segmentTip );
						// set thread to null					
						segmentationThread = null;

						// Record
						String[] arg = new String[] {
								"tolerance=" + Double.toString(  dynamic ), //modify by Elise (remove (int) in front of dynamic value)
								"calculateDams=" + calculateDams,
								"connectivity=" + Integer.toString( connectivity ) };
						record( RUN_SEGMENTATION, arg );
					}
				};

				segmentationThread = newThread;
				newThread.start();

			}
			else if( command.equals( stopText ) ) 							  
			{
				if( null != segmentationThread )
					segmentationThread.interrupt();
				else
					IJ.log("Error: interrupting segmentation failed becaused the thread is null!");

				// set button back to initial text
				segmentButton.setText( segmentText );
				segmentButton.setToolTipText( segmentTip );
				// enable parameter panel
				setParamsEnabled( true );			
			}
		}
		
		// add by Elise
		/** Modified by elise in RerunSegmentation (original from morphological segmentation)
		 * Run morphological segmentation pipeline
		 */
			private void RerunSegmentation( String command ) 
			{
			// If the command is "Resegment"
				//ij.io.LogStream.redirectSystem(true); 
			if ( command.equals( ResegmentText ) ) 
			{			
				
				// read connectivity
				int readConn = Integer.parseInt( (String) connectivityList.getSelectedItem() );

				// convert connectivity to 3D if needed (2D images are processed as 3D)
				if( inputIs2D )
					readConn = readConn == 4 ? 6 : 26;
				
				final int connectivity = readConn;

				// before here : read dynamic (no need )
				

				double max = 255;
				int bitDepth = inputImage.getBitDepth();
				if( bitDepth == 16 )
					max = 65535;
				else if( bitDepth == 32 )
					max = Float.MAX_VALUE;			

				// Set button text to "STOP"
				ResegmentButton.setText( stopText );
				ResegmentButton.setToolTipText( stopTip );
				
				final Thread oldThread = segmentationThread;
				
				// Thread to run the segmentation
				Thread newThread = new Thread() {								 

					public void run()
					{
						
						// Wait for the old task to finish
						if (null != oldThread) 
						{
							try { 
								IJ.log("Waiting for old task to finish...");
								oldThread.join(); 
							} 
							catch (InterruptedException ie)	{ /*IJ.log("interrupted");*/ }
						}

						// read dams flag
						calculateDams = damsCheckBox.isSelected();
						
						
						// disable parameter panel
						setParamsEnabled( false );

						ImageStack image = inputImage.getImageStack();

						final long start = System.currentTimeMillis();

						

						IJ.log( "Running find markers by Elise ^^ ..." );
						final long step0 = System.currentTimeMillis();				
						ImageStack regionalMinima = null;
						if( removeMinLabels)
						{
							// read threshold
							int seuil;
							
							
							try{
								seuil = Integer.parseInt( thresholdText.getText() );
							}
							catch( NullPointerException ex )
							{
								IJ.error( "Morphological Segmentation", "ERROR: missing threshold value" );
								return;
							}
							catch( NumberFormatException ex )
							{
								IJ.error( "Morphological Segmentation", "ERROR: threshold value must be a number" );
								return;
							}
							
							filterLabelSize( seuil);
							regionalMinima = markersImage.getImageStack().duplicate();
						}
						else if(addMarkers || removeMarkers || localMarkers)
						{
							regionalMinima = markersImage.getImageStack().duplicate();
						}
						else
						{			
							IJ.log( "start" );
							ImagePlus LabeledImage = AskForAnotherImage();
							if( null ==  LabeledImage )
							{
								IJ.log( "The segmentation was interrupted!" );
								IJ.showStatus( "The segmentation was interrupted!" );
								IJ.showProgress( 1.0 );
								return;
							}
							regionalMinima  = LabeledImage.getStack(); // modif by Elise

							
						}
						//add by elise
						markersImage = new ImagePlus( "markers", regionalMinima );
						markersImage.setCalibration( inputImage.getCalibration() );
						// end add
						if( null == regionalMinima )
						{
							IJ.log( "The segmentation was interrupted!" );
							IJ.showStatus( "The segmentation was interrupted!" );
							IJ.showProgress( 1.0 );
							return;
						}
						
						final long step1 = System.currentTimeMillis();		
						IJ.log( "Regional minima took " + (step1-step0) + " ms.");

						IJ.log( "Imposing regional minima on original image (connectivity = " + connectivity + ")..." );

						// Impose regional minima over the original image
						ImageStack imposedMinima = MinimaAndMaxima3D.imposeMinima( image, regionalMinima, connectivity );
						
						if( null == imposedMinima )
						{
							IJ.log( "The segmentation was interrupted!" );
							IJ.showStatus( "The segmentation was interrupted!" );
							IJ.showProgress( 1.0 );
							return;
						}
						
						final long step2 = System.currentTimeMillis();
						IJ.log( "Imposition took " + (step2-step1) + " ms." );

						IJ.log( "Labeling regional minima..." );

						// Label regional minima
						ImageStack labeledMinima = ConnectedComponents.computeLabels( regionalMinima, connectivity, 32 );
						if( null == labeledMinima )
						{
							IJ.log( "The segmentation was interrupted!" );
							IJ.showStatus( "The segmentation was interrupted!" );
							IJ.showProgress( 1.0 );
							return;
						}

						final long step3 = System.currentTimeMillis();
						IJ.log( "Connected components took " + (step3-step2) + " ms." );

						// Apply watershed		
						IJ.log("Running watershed...");

						ImageStack resultStack = Watershed.computeWatershed( imposedMinima, labeledMinima, 
								connectivity, calculateDams );
						if( null == resultStack )
						{
							IJ.log( "The segmentation was interrupted!" );
							IJ.showStatus( "The segmentation was interrupted!" );
							IJ.showProgress( 1.0 );
							return;
						}
						
						resultImage = new ImagePlus( "watershed", resultStack );
						resultImage.setCalibration( inputImage.getCalibration() );

						final long end = System.currentTimeMillis();
						IJ.log( "Watershed 3d took " + (end-step3) + " ms.");
						IJ.log( "Whole plugin took " + (end-start) + " ms.");

						// Adjust min and max values to display
						resultDisplayList.setSelectedItem( overlaidBasinsText );
						
						Images3D.optimizeDisplayRange( resultImage );
						
						byte[][] colorMap = CommonLabelMaps.fromLabel( CommonLabelMaps.SPECTRUM.getLabel() ).computeLut(255, true);;
						ColorModel cm = ColorMaps.createColorModel(colorMap, Color.BLACK);
						resultImage.getProcessor().setColorModel(cm);
						resultImage.getImageStack().setColorModel(cm);
						resultImage.updateAndDraw();

						// display result overlaying the input image
						updateResultOverlay();
						showColorOverlay = true;

						// enable parameter panel
						setParamsEnabled( true );
						// set button back to initial text
						ResegmentButton.setText( ResegmentText );
						ResegmentButton.setToolTipText( ResegmentTip );
						// set thread to null					
						segmentationThread = null;

					}
				};
				
				segmentationThread = newThread;
				newThread.start();
				
			}
			else if( command.equals( stopText ) ) 							  
			{
				if( null != segmentationThread )
					segmentationThread.interrupt();
				else
					IJ.log("Error: interrupting segmentation failed becaused the thread is null!");
				
				// set button back to "Segment"
				ResegmentButton.setText( ResegmentText );
				ResegmentButton.setToolTipText( ResegmentTip );
				// enable parameter panel
				setParamsEnabled( true );			
			}
		}
			
			void synchronizeReslicing()
			{
				
				IJ.runPlugIn(imp, "Reslice [/]...", "");
				
			Roi roi = imp.getRoi();	
			
			if (roi == null) return ;
			int roiType = roi.getType();
			ImageStack stack = imp.getStack();
			int stackSize = stack.getSize();
			ImageProcessor ip_out = null, ip;
			boolean ortho = false;
			float[] line = null;
			double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
			
			if (roiType == Roi.LINE) {
				Line lineRoi = (Line) roi;
				x1 = lineRoi.x1d;
				y1 = lineRoi.y1d;
				x2 = lineRoi.x2d;
				y2 = lineRoi.y2d;
				ortho = (x1 == x2 || y1 == y2);
			}
			
			}
		//end by elise	
			

		/**
		 * Update the display image with the gradient or the input image voxels.
		 */
		void updateDisplayImage()
		{
			int slice = displayImage.getCurrentSlice();
			if( applyGradient && showGradient && null != gradientStack )			
				displayImage.setStack( gradientStack );
			else
				displayImage.setStack( inputStackCopy );
			displayImage.setSlice( slice );
			displayImage.updateAndDraw();
		}

		/**
		 * Toggle overlay with segmentation results (if any)
		 */
		void toggleOverlay()
		{
			showColorOverlay = !showColorOverlay;
			
			toggleOverlayCheckBox.setSelected( showColorOverlay );

			if ( showColorOverlay )		
				updateResultOverlay();
			else
				displayImage.setOverlay( null );
			displayImage.updateAndDraw();
		}

		/**
		 * Show segmentation result (if exists) in a new window
		 */
		void createResultImage()
		{
			if( null != resultImage )
			{

				final String displayOption = (String) resultDisplayList.getSelectedItem();
				
				ImagePlus watershedResult = null;

				// options: "Catchment basins", "Overlaid dams", "Watershed lines", "Overlaid basins"
				if( displayOption.equals( catchmentBasinsText ) )			
					watershedResult = getResult( ResultMode.BASINS );									
				else if( displayOption.equals( overlaidDamsText ) )
					watershedResult = getResult( ResultMode.OVERLAID_DAMS );
				else if( displayOption.equals( watershedLinesText ) )
					watershedResult = getResult( ResultMode.LINES );
				else if ( displayOption.equals( overlaidBasinsText ) )
					watershedResult = getResult( ResultMode.OVERLAID_BASINS );
				
				// add by elise
				else if ( displayOption.equals( overlaidBasinsMarkersText ) )
					watershedResult = getResult( ResultMode.OVERLAID_BASINS_MARKERS );
				else if ( displayOption.equals( markersText ) )
					watershedResult = getResult( ResultMode.MARKERS );
				else if ( displayOption.equals( overlaidImageMarkersText ) )
					watershedResult = getResult( ResultMode.OVERLAID_IMG_MARKERS );
				// end add
				
				if( null != watershedResult )
				{
					watershedResult.show();
					watershedResult.setSlice( displayImage.getSlice() );
				}

				// Macro recording	
				record( CREATE_IMAGE );
			}
		}

		/**
		 * Set input image type depending on the button that is selected
		 *  
		 * @param command text of the radio button of input image 
		 */
		void setInputImageType(final String command) 
		{
			// apply gradient only when using and object image
			applyGradient = command == objectImageText;
			enableGradientOptions( applyGradient );
			// update display image (so gradient image is shown if needed)
			updateDisplayImage();
			if( showColorOverlay )
				updateResultOverlay();
			// update radio buttons (needed to call this method from macro)
			if( applyGradient )
				objectButton.setSelected( true );
			else
				borderButton.setSelected( true );
		}
		
		/**
		 * Get current segmentation results based on selected mode
		 * @param mode selected result mode ("Overlaid basins", "Overlaid dams", "Catchment basins", "Watershed lines") 
		 * @return result image
		 */
		ImagePlus getResult( ResultMode mode )
		{
			String title = inputImage.getTitle();
			String ext = "";
			int index = title.lastIndexOf( "." );
			if( index != -1 )
			{
				ext = title.substring( index );
				title = title.substring( 0, index );				
			}
			
			ImagePlus result = null;
			
			// if the overlay is not shown
			if( showColorOverlay == false )
			{
				result = displayImage.duplicate();
				
				if ( applyGradient && showGradient )
					title += "-gradient";
				result.setTitle( title + ext );
				return result;
			}
			

			switch( mode ){
			case OVERLAID_BASINS:
					result = displayImage.duplicate();
					result.setOverlay( null ); // remove existing overlay
					ImageStack is = new ImageStack( displayImage.getWidth(), displayImage.getHeight() );
	
					for( slice=1; slice<=result.getImageStackSize(); slice++ )
					{
						ImagePlus aux = new ImagePlus( "", result.getImageStack().getProcessor( slice ) );
						ImageRoi roi = new ImageRoi(0, 0, resultImage.getImageStack().getProcessor( slice ) );
						roi.setOpacity( opacity );
						aux.setOverlay( new Overlay( roi ) );
						aux = aux.flatten();
						is.addSlice( aux.getProcessor() );
					}
					result.setStack( is );
					if( applyGradient && showGradient )
						title += "-gradient";
					result.setTitle( title + "-overlaid-basins" + ext );
					break;
				case BASINS:
					result = resultImage.duplicate();
					result.setTitle( title + "-catchment-basins" + ext );				
					break;
				case OVERLAID_DAMS:
					result = getWatershedLines( resultImage );
					result = ColorImages.binaryOverlay( displayImage, result, Color.red ) ;
					if( applyGradient && showGradient )
						title += "-gradient";
					result.setTitle( title + "-overlaid-dams" + ext );				
					break;
				case LINES:
					result = getWatershedLines( resultImage );
					IJ.run( result, "Invert", "stack" );
					result.setTitle( title + "-watershed-lines" + ext );								
					break;
			//add by elise
				case MARKERS:
					result = markersImage.duplicate();
					result.setTitle( title + "-markers" + ext );	
					break;
				case OVERLAID_IMG_MARKERS:
					result= markersImage.duplicate();
					result = ColorImages.binaryOverlay( displayImage, result, Color.red ) ;
					result.setTitle( title + "-img-markers" + ext );	
					break;
				case OVERLAID_BASINS_MARKERS:
					result = markersImage.duplicate();
					result = ColorImages.binaryOverlay( resultImage, result, Color.white ) ;
					result.setTitle( title + "-catchment-basins-markers" + ext );				
					break;
					
			//end add
					
			}

			return result;
		}
		
		/**
		 * Set "show gradient" flag and update GUI accordingly
		 * @param bool flag to display the gradient image in the GUI
		 */
		void setShowGradient( boolean bool ) 
		{
			showGradient = bool;
			gradientCheckBox.setSelected( bool );
			updateDisplayImage();
			if( showColorOverlay )
				updateResultOverlay();
		}

		/**
		 * Set the result display option in the GUI
		 * @param option output format
		 */
		void setResultDisplayOption( String option )
		{
			if( Arrays.asList( resultDisplayOption ).contains( option ) )
				resultDisplayList.setSelectedItem( option );
		}

		/**
		 * Get the selected display option
		 * @return currently selected display option
		 */
		String getResultDisplayOption()
		{
			return (String) resultDisplayList.getSelectedItem();
		}
		
		/**
		 * Update the overlay in the display image based on 
		 * the current result and slice
		 */
		void updateResultOverlay() 
		{
			if( null != resultImage )
			{
				displayImage.deleteRoi();
				// displayImage = new ImagePlus( inputImage.getTitle(), inputStackCopy );
				int slice = displayImage.getCurrentSlice();
				Overlay overlay = new Overlay();
				final String displayOption = (String) resultDisplayList.getSelectedItem();							

				ImageRoi roi = null;
				
				if( displayOption.equals( catchmentBasinsText ) )
				{
					roi = new ImageRoi(0, 0, resultImage.getImageStack().getProcessor( slice ) );
					roi.setOpacity( 1.0 );
				}
				else if( displayOption.equals( overlaidDamsText ) )				
				{
					ImageProcessor lines = BinaryImages.binarize( resultImage.getImageStack().getProcessor( slice ) );
					lines.invert();
					ImageProcessor gray = displayImage.getImageStack().getProcessor( slice );
					roi = new ImageRoi(0, 0, ColorImages.binaryOverlay( gray, lines, Color.red ) ) ;
					roi.setOpacity( 1.0 );
				}
				else if( displayOption.equals( watershedLinesText ) )
				{
					roi = new ImageRoi(0, 0, BinaryImages.binarize( resultImage.getImageStack().getProcessor( slice ) ) );
					roi.setOpacity( 1.0 );
				}
				else if( displayOption.equals( overlaidBasinsText ) )	
				{
					roi = new ImageRoi(0, 0, resultImage.getImageStack().getProcessor( slice ) );
					roi.setOpacity( opacity );
				}
						
				// add by elise
				else if( displayOption.equals( markersText ) )	
				{
					roi = new ImageRoi(0, 0, markersImage.getImageStack().getProcessor( slice ) );
					roi.setOpacity( 1.0 );
				}
				else if( displayOption.equals( overlaidBasinsMarkersText ) )	
				{	
					roi = new ImageRoi(0, 0, resultImage.getImageStack().getProcessor( slice ) );
					roi.setOpacity( 1.0 );
					overlay.add(roi);
					//displayImage.setOverlay( new Overlay( roi ) );
					roi = new ImageRoi(0, 0, markersImage.getImageStack().getProcessor( slice ) );
					roi.setOpacity( opacity );
					
					/*
					//displayImage = resultImage;
					displayImage.setImage( resultImage);
					resultImage.draw();
					roi = new ImageRoi(0, 0, markersImage.getImageStack().getProcessor( slice ) );
					roi.setOpacity( opacity);
					/*
					ImageRoi roiLabel = null;
					roiLabel = new ImageRoi(0, 0, resultImage.getImageStack().getProcessor( slice ) );
					//roiLabel.setOpacity( 1.0 );
					ShapeRoi roi1 = new ShapeRoi(roiLabel);
					roi = new ImageRoi(0, 0, markersImage.getImageStack().getProcessor( slice ) );
					roi.setOpacity( opacity );
					ShapeRoi roi2 = new ShapeRoi(roi);
					ShapeRoi  combined = roi2.or(roi1);
					roi = (ImageRoi)combined.shapeToRoi(); //getRois()[0]
					roi.drawPixels(displayImage.getProcessor());
					roi.setOpacity( 1.0 );
					/*
					ImagePlus aux = new ImagePlus( "", resultImage.getImageStack().getProcessor( slice ) );
					roi = new ImageRoi(0, 0, markersImage.getImageStack().getProcessor( slice ) );
					roi.setOpacity( opacity );
					aux.setOverlay( new Overlay( roi ) );
					//aux = aux.flatten();
					roi = new ImageRoi(0, 0, aux.getImageStack().getProcessor( slice ) );
					roi.setOpacity( 1.0 );*/
				}
				else if( displayOption.equals( overlaidImageMarkersText ) )	
				{
					ImageProcessor markers = BinaryImages.binarize( markersImage.getImageStack().getProcessor( slice ) );
					ImageProcessor gray = displayImage.getImageStack().getProcessor( slice );
					roi = new ImageRoi(0, 0, ColorImages.binaryOverlay( gray, markers, Color.red ) ) ;
					roi.setOpacity( 1.0 );

				}				
				// end add
				overlay.add(roi);
				displayImage.setOverlay( overlay );
				//displayImage.setOverlay( new Overlay( roi ) );
				
			}
		}
		
		/**
		 * Accessor to know if the result overlay needs to be displayed 
		 * @return
		 */
		boolean isShowResultOverlaySelected()
		{
			return showColorOverlay;
		}
		
		/**
		 * Set the gradient radius
		 * @param radius size of the radius
		 */
		void setGradientRadius( int radius )
		{
			if( radius > 0 )
			{
				gradientRadius = radius;
				gradientRadiusSizeText.setText( String.valueOf( radius ) );
			}
		}
		
		
		// add by elise for ... doesn't work (Is it because it's a 2D image ?)
		MouseListener mlAdd = new MouseAdapter() {
		    @Override
		    public void mouseClicked(MouseEvent eImg) {
		    	IJ.log(""+eImg.getSource()+"");
		    	int x = eImg.getX();
		    	int y = eImg.getY();
		    	int ox = imp.getWindow().getCanvas().offScreenX(x);
				int oy = imp.getWindow().getCanvas().offScreenY(y);
		    	int z = displayImage.getCurrentSlice();
		    	//imp.mouseMoved(x, y); //--> affiche les coordonnées au niveau de la fenetre imagej
		    	ImageStack imgStack = markersImage.getStack();
		    	//markersImage.setDefault16bitRange(16);
		    	
		    	//IJ.log(""+markersImage.getPixel(x,y)+"");  // affiche une adresse car 3D pas 2D
		    	//IJ.log(""+imgStack.getVoxel(x,y,z)+"");
		    	IJ.log(""+imgStack.getBitDepth()+"");
		    	//IJ.log(""+imgStack.getHeight()+"");
		    	//IJ.log(""+imgStack.getWidth()+"");
		    	//IJ.log(""+imgStack.getSize()+"");
		    	//IJ.log(""+markersImage.getType()+"");
		    	imgStack.setVoxel(ox, oy, z-1, 255);
		    	if(spreadingMarkers)
				{
						
						//if(regionalMinima.getVoxel(x, y, z) !=0 )
		    		imgStack.setVoxel(ox, oy, z,255);
		    		imgStack.setVoxel(ox+1, oy, z,255);
		    		imgStack.setVoxel(ox, oy+1, z,255);
		    		imgStack.setVoxel(ox, oy, z+1,255);
		    		imgStack.setVoxel(ox-1, oy, z,255);
		    		imgStack.setVoxel(ox, oy-1, z,255);
		    		imgStack.setVoxel(ox, oy, z-1,255);
						
				
				}
		    	IJ.log(""+imgStack.getVoxel(x,y,z)+"");
		    	markersImage.updateAndDraw();
		    	updateResultOverlay();
		    	//IJ.log(""+x+"  "+y+"  "+z+"");
		    	//IJ.log(""+ox+"  "+oy+" ");
		    	//IJ.log(""+markersImage.getPixel(x,y)+""); // affiche une adresse car 3D pas 2D
		    	
		    }
		    
		};
		MouseMotionListener mlAddDragged = new MouseAdapter() {
		    @Override
		    public void mouseDragged(MouseEvent eImg) {
		    	IJ.log(""+eImg.getSource()+"");
		    	int x = eImg.getX();
		    	int y = eImg.getY();
		    	int ox = imp.getWindow().getCanvas().offScreenX(x);
				int oy = imp.getWindow().getCanvas().offScreenY(y);
		    	int z = displayImage.getCurrentSlice();
		    	//imp.mouseMoved(x, y); //--> affiche les coordonnées au niveau de la fenetre imagej
		    	ImageStack imgStack = markersImage.getStack();
		    	//markersImage.setDefault16bitRange(16);
		    	
		    	//IJ.log(""+markersImage.getPixel(x,y)+"");  // affiche une adresse car 3D pas 2D
		    	//IJ.log(""+imgStack.getVoxel(x,y,z)+"");
		    	IJ.log(""+imgStack.getBitDepth()+"");
		    	//IJ.log(""+imgStack.getHeight()+"");
		    	//IJ.log(""+imgStack.getWidth()+"");
		    	//IJ.log(""+imgStack.getSize()+"");
		    	//IJ.log(""+markersImage.getType()+"");
		    	imgStack.setVoxel(ox, oy, z-1, 255);
		    	if(spreadingMarkers)
				{
						
						//if(regionalMinima.getVoxel(x, y, z) !=0 )
		    		imgStack.setVoxel(ox, oy, z,255);
		    		imgStack.setVoxel(ox+1, oy, z,255);
		    		imgStack.setVoxel(ox, oy+1, z,255);
		    		imgStack.setVoxel(ox, oy, z+1,255);
		    		imgStack.setVoxel(ox-1, oy, z,255);
		    		imgStack.setVoxel(ox, oy-1, z,255);
		    		imgStack.setVoxel(ox, oy, z-1,255);
						
				
				}
		    	IJ.log(""+imgStack.getVoxel(x,y,z)+"");
		    	markersImage.updateAndDraw();
		    	updateResultOverlay();
		    	//IJ.log(""+x+"  "+y+"  "+z+"");
		    	//IJ.log(""+ox+"  "+oy+" ");
		    	//IJ.log(""+markersImage.getPixel(x,y)+""); // affiche une adresse car 3D pas 2D
		    	
		    }
		    
		};
		
		//end add
		
		
		// add by elise for 
				MouseListener mlRemove = new MouseAdapter() {
				    @Override
				    public void mouseClicked(MouseEvent eImg) {
				    	IJ.log(""+eImg.getSource()+"");
				    	int xi = eImg.getX();
				    	int yi = eImg.getY();
				    	int ox = imp.getWindow().getCanvas().offScreenX(xi);
						int oy = imp.getWindow().getCanvas().offScreenY(yi);
				    	int zi = displayImage.getCurrentSlice();
				    	
				    	
				    	ImageStack imgStack = resultImage.getStack();
				    	ImageStack markersStack = markersImage.getStack();
				    	//markersImage.setDefault16bitRange(16);
				    	double label = imgStack.getVoxel(ox,oy,zi-1);
				    	
				    	int width = imgStack.getWidth();
						int height = imgStack.getHeight();
						int nSlices = imgStack.getSize();
						int bitDepth = imgStack.getBitDepth() ;
						for(int z = 0; z < nSlices; z++){  	 	 	
					  	 	 	for (int y = 0; y <height; y++){
					  	 	 		for (int x = 0; x < width; x++){
					  	 	 			if(imgStack.getVoxel(x, y, z) == label)
					  	 	 			{
					  	 	 			markersStack.setVoxel(x, y, z, 0);
					  	 	 			}
					  	 	 		}
					  	 	 	}
						}
						
				    	
				    	IJ.log(""+imgStack.getVoxel(xi,yi,zi)+"");
				    	markersImage.updateAndDraw();
				    	updateResultOverlay();
				    	IJ.log(""+xi+"  "+yi+"  "+zi+"");
				    	IJ.log(""+ox+"  "+oy+" ");
				    	IJ.log(""+markersImage.getPixel(xi,yi)+"");
				    	
				    }
				};
				
				MouseMotionListener mlRemoveDragged = new MouseAdapter() {

					java.util.List<Integer> list_value = new ArrayList<Integer>();
				    @Override
				    public void mouseDragged(MouseEvent eImg) {
				    	IJ.log(""+eImg.getSource()+"");
				    	int xi = eImg.getX();
				    	int yi = eImg.getY();
				    	int ox = imp.getWindow().getCanvas().offScreenX(xi);
						int oy = imp.getWindow().getCanvas().offScreenY(yi);
				    	int zi = displayImage.getCurrentSlice();
				    	
				    	list_value.add(ox,oy);
				    	
				    	ImageStack imgStack = resultImage.getStack();
				    	ImageStack markersStack = markersImage.getStack();
				    	//markersImage.setDefault16bitRange(16);
				    	double label = imgStack.getVoxel(ox,oy,zi-1);
				    	
				    	int width = imgStack.getWidth();
						int height = imgStack.getHeight();
						int nSlices = imgStack.getSize();
						int bitDepth = imgStack.getBitDepth() ;
						for(int z = 0; z < nSlices; z++){  	 	 	
					  	 	 	for (int y = 0; y <height; y++){
					  	 	 		for (int x = 0; x < width; x++){
					  	 	 			if(imgStack.getVoxel(x, y, z) == label)
					  	 	 			{
					  	 	 			markersStack.setVoxel(x, y, z, 0);
					  	 	 			}
					  	 	 		}
					  	 	 	}
						}
						
				    	
				    	IJ.log(""+imgStack.getVoxel(xi,yi,zi)+"");
				    	markersImage.updateAndDraw();
				    	updateResultOverlay();
				    	IJ.log(""+xi+"  "+yi+"  "+zi+"");
				    	IJ.log(""+ox+"  "+oy+" ");
				    	IJ.log(""+markersImage.getPixel(xi,yi)+"");
				    	
				    }
				
				};
				
				
				MouseListener mlLocalChange = new MouseAdapter() {
				    @Override
				    public void mouseClicked(MouseEvent eImg) {
				    	IJ.log(""+eImg.getSource()+"");
				    	int xi = eImg.getX();
				    	int yi = eImg.getY();
				    	int ox = imp.getWindow().getCanvas().offScreenX(xi);
						int oy = imp.getWindow().getCanvas().offScreenY(yi);
				    	int zi = displayImage.getCurrentSlice();
				    	
				    	ImageStack imgStack = resultImage.getStack();
				    	ImageStack rawStack = inputStackCopy;
				    	ImageStack markersStack = markersImage.getStack();
				    	//markersImage.setDefault16bitRange(16);
				    	double label = imgStack.getVoxel(ox,oy,zi-1);
				    	int width = imgStack.getWidth();
						int height = imgStack.getHeight();
						int nSlices = imgStack.getSize();
						int bitDepth = imgStack.getBitDepth() ;
						
				    	ImagePlus imageLocal = IJ.createImage("local", width, height, nSlices, bitDepth) ;
				    	imageLocal .setCalibration(imp.getCalibration());
						ImageStack localStack = imageLocal .getStack();					
						
				    	
				    	ArrayList<ArrayList<Integer>> coord = new ArrayList<ArrayList<Integer>>() ;
				    	
				    	IJ.log(""+coord.size()+"");
						for(int z = 0; z < nSlices; z++){  	 	 	
					  	 	 	for (int y = 0; y <height; y++){
					  	 	 		for (int x = 0; x < width; x++){
					  	 	 			if(imgStack.getVoxel(x, y, z) == label)
					  	 	 			{
					  	 	 				coord.add(new ArrayList<Integer>() );
					  	 	 				coord.get(coord.size() -1).add(x);
					  	 	 				coord.get(coord.size() -1).add(y);
					  	 	 				coord.get(coord.size() -1).add(z);
					  	 	 				localStack.setVoxel(x, y, z,rawStack.getVoxel(x, y, z));
					  	 	 			}
					  	 	 		}
					  	 	 	}
						}
						
						
						// read connectivity
						int readConn = Integer.parseInt( (String) connectivityList.getSelectedItem() );

						// convert connectivity to 3D if needed (2D images are processed as 3D)
						if( inputIs2D )
							readConn = readConn == 4 ? 6 : 26;
						
						final int connectivity = readConn;
						
						// read dynamic
						final double dynamic;
						try{
							dynamic = Double.parseDouble( dynamicText.getText() );
						}
						catch( NullPointerException ex )
						{
							IJ.error( "Morphological Sementation", "ERROR: missing dynamic value" );
							return;
						}
						catch( NumberFormatException ex )
						{
							IJ.error( "Morphological Sementation", "ERROR: dynamic value must be a number" );
							return;
						}

						// Run extended minima
						ImageStack regionalMinima = MinimaAndMaxima3D.extendedMinimaDouble( localStack, dynamic, connectivity );
/*
						for(int z = 0; z < nSlices; z++){  	 	 	
							for (int y = 0; y <height; y++){
								for (int x = 0; x < width; x++){
									if(localStack.getVoxel(x, y, z) !=0 )
									{
										markersStack.setVoxel(x, y, z,localStack.getVoxel(x, y, z));
									}
								}
							}
						}*/
						
						if(spreadingMarkers)
						{
							for(int i = 0; i< coord.size();i++)
							{
								int x = coord.get(i).get(0);
								int y = coord.get(i).get(1);
								int z = coord.get(i).get(2);
								if(regionalMinima.getVoxel(x, y, z) !=0 )
								{
									markersStack.setVoxel(x, y, z,regionalMinima.getVoxel(x, y, z));
									markersStack.setVoxel(x+1, y, z,regionalMinima.getVoxel(x, y, z));
									markersStack.setVoxel(x, y+1, z,regionalMinima.getVoxel(x, y, z));
									markersStack.setVoxel(x, y, z+1,regionalMinima.getVoxel(x, y, z));
									markersStack.setVoxel(x-1, y, z,regionalMinima.getVoxel(x, y, z));
									markersStack.setVoxel(x, y-1, z,regionalMinima.getVoxel(x, y, z));
									markersStack.setVoxel(x, y, z-1,regionalMinima.getVoxel(x, y, z));
								}
							}
						}
						else
						{

							for(int i = 0; i< coord.size();i++)
							{
								int x = coord.get(i).get(0);
								int y = coord.get(i).get(1);
								int z = coord.get(i).get(2);
								if(regionalMinima.getVoxel(x, y, z) !=0 )
								{
									markersStack.setVoxel(x, y, z,regionalMinima.getVoxel(x, y, z));
								}
							}
						}

						IJ.log(""+imgStack.getVoxel(xi,yi,zi)+"");
				    	markersImage.updateAndDraw();
				    	updateResultOverlay();
				    	IJ.log(""+xi+"  "+yi+"  "+zi+"");
				    	IJ.log(""+ox+"  "+oy+" ");
				    	IJ.log(""+markersImage.getPixel(xi,yi)+"");
				    	
				    }
				};
				
				MouseListener mlSyn = new MouseAdapter() {
					@Override
					public void mouseClicked(MouseEvent eImg) {
											
						int xc = eImg.getX();
						int yc = eImg.getY();
						
						// get ImageCanvas that received event
						ImageCanvas icc = (ImageCanvas) eImg.getSource();

						int zc = icc.getImage().getCurrentSlice();
						int ox = icc.offScreenX(xc);
						int oy = icc.offScreenY(yc);
						int[] newCoord = new int[3];

						// to keep ImageJ from freezing when a mouse event is processed on exit
						if (IJ.getInstance().quitting()) {
							return;
						}

						if (reslicedImage != null) {
							if(reslicedImage.getCanvas() == icc) {
								newCoord = getMatchingCoords( icc, ox, oy , zc);
								if (addMarkers)
								{
									markersImage.getStack().setVoxel(newCoord[0],newCoord[1],newCoord[2], 255);	
								}
								else if (removeMarkers)
								{
									markersImage.getStack().setVoxel(newCoord[0],newCoord[1],newCoord[2], 0);
								}
							}
							//	if (cCoords.getState()) {
							
								//ic.mouseClicked(adaptEvent(e, ic, p));
							
						}
						storeCanvasState(icc);
					}	
					
				};
				
				MouseWheelListener mlSynWheel = new MouseAdapter() {
					@Override
					public void mouseWheelMoved(final MouseWheelEvent eWheel) {	


						if (reslicedImage.getWindow() == null) return;
						ImagePlus imp;

						ImageCanvas ic;

						//Point p;
						//Point oldp;
						int p[]= new int[3];
						int oldp[]= new int[3];
						Rectangle rect;
						oldZMouse = zMouse;
						rect = boundingRect(xMouse,yMouse,oldXMouse,oldYMouse);


						ImageCanvas icc = (ImageCanvas) eWheel.getSource();
						zMouse = icc.getImage().getSlice();
						imp = reslicedImage;
						if (imp != null) {
							ic = imp.getCanvas();
							if ( ic != icc) //case : mouse in plugin window 
							{
								p = getMatchingCoords( icc, xMouse, yMouse, zMouse);		
								IJ.log("p newCoordF = " +p[0]+"   "+p[1]+"   "+p[2]+"");
								oldp = getMatchingCoords( icc, oldXMouse, oldYMouse, oldZMouse);
								IJ.log("oldp newCoordF = " +oldp[0]+"   "+oldp[1]+"   "+oldp[2]+"");
								if (0>p[0] || p[0]>reslicedImage.getDimensions()[0] || 
										0>p[1] || p[1]>reslicedImage.getDimensions()[1] ||
										0>p[2] || p[2]>reslicedImage.getDimensions()[3] )
								{
									ic.getImage().setZ(p[2]); /// A MODIFIER !!!! setPosition
									imp.updateAndDraw();
									rect = boundingRect(p[0], p[1], oldp[0], oldp[1]);
								}

							} 
							else //case : mouse in resliced image window 
							{
								p[0] = xMouse;
								p[1] = yMouse;
								p[2] = zMouse;
								//displayImage.setZ(p[2]); /// A MODIFIER !!!! Position
								rect = boundingRect(xMouse,yMouse,oldXMouse,oldYMouse);

							}

							// For PolygonRoi the cursor would overwrite the indicator lines.
							Roi roi = imp.getRoi();
							if (! (roi != null  && roi.getState() == Roi.CONSTRUCTING) ) { //&& roi instanceof PolygonRoi
								drawSyncCursor(ic,rect, p[0], p[1]);
							}							
						}

						// second image 
						if (IJ.getInstance().quitting()) {return;}
						imp = displayImage;
						if (imp != null) {

							ic = imp.getCanvas();
							if ( ic != icc)  //case : mouse in resliced image window
							{ 
								p = getMatchingCoords( icc, xMouse, yMouse, zMouse);
								IJ.log("p newCoordF = " +p[0]+"   "+p[1]+"   "+p[2]+"");
								oldp = getMatchingCoords( icc, oldXMouse, oldYMouse, oldZMouse);
								
								imp.setZ(p[2]);
								imp.updateAndDraw();
								updateResultOverlay();
								rect = boundingRect(p[0], p[1], oldp[0], oldp[1]);
							} else //case : mouse in plugin window
							{
								p[0] = xMouse;
								p[1] = yMouse;
								p[2] = zMouse;

								rect = boundingRect(xMouse,yMouse,oldXMouse,oldYMouse);

							}
							// For PolygonRoi the cursor would overwrite the indicator lines.
							Roi roi = imp.getRoi();
							if (! (roi != null && roi.getState() == Roi.CONSTRUCTING) ) { // && roi instanceof PolygonRoi
								drawSyncCursor(ic,rect,  p[0], p[1]);
							}
							
						}

						storeCanvasState(icc);
					}

				};
				
				// --------------------------------------------------
				/** Propagate mouse entered events to all synchronized windows. */
				/*MouseListener mlSynEnter = new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						if (reslicedImage.getWindow() == null) return;
					ImagePlus imp;
					ImageWindow iw;
					ImageCanvas ic;
					int p[]= new int[3];
					xMouse = eImg.getX();
					yMouse = eImg.getY();
					ImageCanvas icc = (ImageCanvas) eImg.getSource();
					zMouse = icc.getImage().getSlice();
					p[0] = xMouse;
					p[1] = yMouse;
					p[2] = zMouse;
					// get ImageCanvas that received event
					ImageCanvas icc = (ImageCanvas) e.getSource();
					ImageWindow iwc = (ImageWindow) icc.getParent();
					for(int n=0; n<vwins.size();++n) {
						// to keep ImageJ from freezing when a mouse event is processed on exit
						if (ijInstance.quitting()) {
							return;
						}
						imp = getImageFromVector(n);
						if (imp != null) {
							iw = imp.getWindow();
							if(iw != iwc) {
								ic = iw.getCanvas();
								if (cCoords.getState()) {
									p = getMatchingCoords(ic, icc, x, y);
								}
								ic.mouseEntered(adaptEvent(e, ic, p));
							}
						}
					}
					// Store srcRect, Magnification and others of current ImageCanvas
					storeCanvasState(icc);
				}
				};*/
				
				void changeOfBase()
				{
					
					Roi roi = displayImage.getRoi();
					IJ.log(""+roi+"");
					if (roi == null) {return;}
					int roiType = roi.getType();
					int reslicedZ = reslicedImage.getStack().getSize();
					
					
					double x1 = 0, x2 = 0, y1 = 0, y2 = 0,xInc = 0.0, yInc = 0.0;
					double outputZSpacing = 1.0;
					
					
					if (roiType == Roi.LINE) {
					Line lineRoi = (Line) roi;
					origine[0] = lineRoi.x1d;
					origine[1] = lineRoi.y1d;
					origine[2] = 1;
					x2 = lineRoi.x2d;
					y2 = lineRoi.y2d;
					double dx = x2 - origine[0];
					double dy = y2 - origine[1];
					double nrm = Math.sqrt(dx*dx + dy*dy)/outputZSpacing;
					xInc = -(dy/nrm);
					yInc = (dx/nrm);
					IJ.log(""+ reslicedImage.getDimensions().length+" "+ reslicedImage.getDimensions()[0]+" "+ reslicedImage.getDimensions()[1]+" "+ reslicedImage.getDimensions()[3]+" display dim 3 = " +displayImage.getDimensions()[3]+"");//(width, height, nChannels, nSlices, nFrames) 
					double pointsResliced[][] = new double[][] {{reslicedImage.getDimensions()[0],0,0},{0, reslicedImage.getDimensions()[1],0 },{0, 0, reslicedImage.getDimensions()[3]}}; // {{x1,x2,x3},{y1,y2,y3},{z1,z2,z3}}
					double pointsInit[][] = new double[][] {{dx,0,reslicedZ*xInc},{dy,0,reslicedZ*yInc},{0,displayImage.getDimensions()[3],0}}; 
					
					IJ.log(" "+ pointsResliced[0][0] + " "+ pointsResliced[0][1] + " "+ pointsResliced[0][2] + " \n"+ pointsResliced[1][0] + " "+ pointsResliced[1][1] + " "+ pointsResliced[1][2] + "\n "+ pointsResliced[2][0] + " "+ pointsResliced[2][1] + " "+ pointsResliced[2][2] + " ");
					IJ.log(" "+pointsInit[0][0] +" "+pointsInit[0][1] +" "+pointsInit[0][2] +"\n "+pointsInit[1][0] +" "+pointsInit[1][1] +" "+pointsInit[1][2] +" \n"+pointsInit[2][0] +" "+pointsInit[2][1] +" "+pointsInit[2][2] +" ");
					double A[][] = multiply(pointsResliced,invertG(pointsInit));
					
					double Ainv[][] = invertG(A);
					IJ.log("Ainv =  "+ Ainv[0][0] + " "+ Ainv[0][1] + " "+ Ainv[0][2] + " \n"+ Ainv[1][0] + " "+ Ainv[1][1] + " "+ Ainv[1][2] + "\n "+ Ainv[2][0] + " "+ Ainv[2][1] + " "+ Ainv[2][2] + " ");
					IJ.log("A = "+A[0][0] + " "+ A[0][1] + " "+ A[0][2] + " \n"+ A[1][0] + " "+ A[1][1] + " "+ A[1][2] + "\n "+ A[2][0] + " "+ A[2][1] + " "+ A[2][2] + " ");
					
					
					coordResliceToRaw = Ainv ; // matrice to obtain Row coordinates from Reslice coordinates Ainv*Csync =  Csegm
					coordRawToReslice = A; // matrice to obtain Reslice coordinates from Row coordinates  A*Csegm = Csync
					}
				}
				
				double[][] multiply(double first[][], double second[][] )
				{
					
					int m = first.length,n = first[0].length;
					int p = second.length,q = second[0].length;
					
					double sum = 0; 
					double multiply[][] = new double[m][q];
					for (int c = 0 ; c < m ; c++ )
			         {
						for (int d = 0 ; d < q ; d++ )
						{   
							for (int k = 0 ; k < p ; k++ )
							{
								sum = sum + first[c][k]*second[k][d];
							}

							multiply[c][d] = sum;
							sum = 0;
						}
			         }
					return multiply;
				}
				
			    /**http://www.cs.nyu.edu/~jeremy/atmm/BlockPolly/render/Matrix.java
			       Copies the contents of the source matrix to the destination matrix.
			       @param src original source matrix
			       @param dst target destination matrix
			    */
			   public void copy(double src[][], double dst[][]) {
			      for (int i = 0 ; i < src.length ; i++)
			      for (int j = 0 ; j < src[i].length ; j++)
			         dst[i][j] = src[i][j];
			   }
			   
				   public double[][] invert(double matSource[][]) {
					      int N = matSource.length;
					      double t;
					      double[][] tmp = new double[N][N];
					      double[][] mat = new double[N][N];
					      copy(matSource, mat);
					      identity(tmp);
					      for (int i = 0; i < N ; i++) {
					         if ((t = mat[i][i]) == 0)
					            break;
					         for (int j = 0; j < N ; j++) {
					            mat[i][j] = mat[i][j] / t;
					            tmp[i][j] = tmp[i][j] / t;
					         }
					         for (int k = 0; k < N ; k++)
						    if (k != i) {
						       t = mat[k][i];
						       for (int j = 0; j < N ; j++) {
						          mat[k][j] = mat[k][j] - t*mat[i][j];
						          tmp[k][j] = tmp[k][j] - t*tmp[i][j];
					               }
						    }
					      }
					      IJ.log("tmp = "+tmp[0][0] + " "+ tmp[0][1] + " "+ tmp[0][2] + " \n"+ tmp[1][0] + " "+ tmp[1][1] + " "+ tmp[1][2] + "\n "+ tmp[2][0] + " "+ tmp[2][1] + " "+ tmp[2][2] + " ");
							
					      return tmp;
					   }
				   public void identity(double dst[][]) {
					      for (int i = 0 ; i < dst.length ; i++)
					      for (int j = 0 ; j < dst.length ; j++)
					         dst[i][j] = (i == j ? 1 : 0);
					   }
				   
				//  function for invert a matrice  from http://www.sanfoundry.com/java-program-find-inverse-matrix/
			    public double[][] invertG(double a[][]) 

			    {
			        int n = a.length;
			        double X[][] = new double[n][n];
			        double b[][] = new double[n][n];
			        int index[] = new int[n];
			        for (int i=0; i<n; ++i) 
			            b[i][i] = 1;
			 
			 // Transform the matrix into an upper triangle
			        gaussian(a, index);			 
			        //IJ.log(" a = "+a[0][0]+ " "+a[0][1]+ " "+a[0][2]+ " \n"+a[1][0]+ " "+a[1][1]+ " "+a[1][2]+ " \n "+a[2][0]+ " "+a[2][1]+ " "+a[2][2]+ " ");
			        
			 // Update the matrix b[i][j] with the ratios stored
			        for (int i=0; i<n-1; ++i){
			            for (int j=i+1; j<n; ++j){
			                for (int k=0; k<n; ++k){
			                    b[index[j]][k]-= a[index[j]][i]*b[index[i]][k];
			        			}
			            }
			        }
			        //IJ.log("b = "+b[0][0]+ " "+b[0][1]+ " "+b[0][2]+ " \n"+b[1][0]+ " "+b[1][1]+ " "+b[1][2]+ " \n "+b[2][0]+ " "+b[2][1]+ " "+b[2][2]+ " ");
			        
			 // Perform backward substitutions
			        for (int i=0; i<n; ++i) 
			        {
			            X[n-1][i] = b[index[n-1]][i]/a[index[n-1]][n-1];
			            //IJ.log("X = "+X[0][0]+ " "+X[0][1]+ " "+X[0][2]+ " \n"+X[1][0]+ " "+X[1][1]+ " "+X[1][2]+ " \n "+X[2][0]+ " "+X[2][1]+ " "+X[2][2]+ " ");
				        
			            for (int j=n-2; j>=0; --j) 
			            {
			                X[j][i] = b[index[j]][i];
			                for (int k=j+1; k<n; ++k) 
			                {
			                	//IJ.log(""+ i +" "+ j +" "+ k +"");
			                    X[j][i] -= a[index[j]][k]*X[k][i];
			                }
			                //IJ.log(" a division = "+ a[index[j]][j] +"");
			                X[j][i] /= a[index[j]][j];
			                
			            }
			        }
			        return X;
			    }
			 
			// Method to carry out the partial-pivoting Gaussian
			// elimination.  Here index[] stores pivoting order.
			    public void gaussian(double a[][], int index[]) 
			    {
			        int n = index.length;
			        double c[] = new double[n];			 
			 // Initialize the index
			        for (int i=0; i<n; ++i) 
			            index[i] = i;			
			 // Find the rescaling factors, one from each row
			        for (int i=0; i<n; ++i) 
			        {
			            double c1 = 0;
			            for (int j=0; j<n; ++j) 
			            {
			                double c0 = Math.abs(a[i][j]);
			                if (c0 > c1) c1 = c0;
			            }
			            c[i] = c1;
			        }
			 // Search the pivoting element from each column
			        int k = 0;
			        for (int j=0; j<n-1; ++j) 
			        {
			            double pi1 = 0;
			            for (int i=j; i<n; ++i) 
			            {
			                double pi0 = Math.abs(a[index[i]][j]);
			                //IJ.log("  "+ c[index[i]] +"");
			                pi0 /= c[index[i]];
			                if (pi0 > pi1) 
			                {
			                    pi1 = pi0;
			                    k = i;
			                }
			            }
			   // Interchange rows according to the pivoting order
			            int itmp = index[j];
			            index[j] = index[k];
			            index[k] = itmp;
			            for (int i=j+1; i<n; ++i) 	
			            {
			            	//IJ.log(" div a gaussian "+ a[index[j]][j] +"");
			                double pj = a[index[i]][j]/a[index[j]][j];	 
			 // Record pivoting ratios below the diagonal
			                a[index[i]][j] = pj;			 
			 // Modify other elements accordingly
			                for (int l=j+1; l<n; ++l)
			                    a[index[i]][l] -= pj*a[index[j]][l];
			            }
			        }
			    }
				
	 //--> end from http://www.sanfoundry.com/java-program-find-inverse-matrix/

				MouseMotionListener mmlSyn = new MouseAdapter() {
					@Override
					public void mouseMoved(MouseEvent eImg) {						
						IJ.log(""+eImg.getModifiers()+"");
						
						if(eImg.getModifiers() == 2 || eImg.getModifiers() == 20)
						{
							return;
						}
						if (reslicedImage.getWindow() == null) return;
							ImagePlus imp;
							ImageCanvas ic;
							ImageCanvas icc = (ImageCanvas) eImg.getSource();
							//Point p;
							//Point oldp;
							
							Rectangle rect;
							oldXMouse = xMouse; oldYMouse = yMouse; oldZMouse = zMouse;
							xMouse = eImg.getX();
							yMouse = eImg.getY();
							/*
							 * xMouse = icc.offScreenX(eImg.getX());
							yMouse = icc.offScreenY(eImg.getY());

							 */

							//p = new Point(x, y);
							rect = boundingRect(xMouse,yMouse,oldXMouse,oldYMouse);
							// get ImageCanvas that received event

							/*
						if( icc == displayImage.getCanvas())
						{
							 iwc = (ImageWindow) icc.getParent().getParent();

						}
						else
						{
							iwc = (ImageWindow) icc.getParent();
						}*/
							zMouse = icc.getImage().getSlice();
							// Draw new cursor box in each synchronized window.
							// and pass on mouse moved event


							// first image
							// to keep ImageJ from freezing when a mouse event is processed on exit
							if (IJ.getInstance().quitting()) { return; }
							imp = reslicedImage;
							if (imp != null) {
								ic = imp.getCanvas();
								if ( ic != icc) //case : mouse in plugin window 
								{
									p = getMatchingCoords( icc, xMouse, yMouse, zMouse);		
									IJ.log("p newCoordF = " +p[0]+"   "+p[1]+"   "+p[2]+"");
									oldp = getMatchingCoords( icc, oldXMouse, oldYMouse, oldZMouse);
									IJ.log("oldp newCoordF = " +oldp[0]+"   "+oldp[1]+"   "+oldp[2]+"");
									if (0>p[0] || p[0]>reslicedImage.getDimensions()[0] || 
											0>p[1] || p[1]>reslicedImage.getDimensions()[1] ||
											0>p[2] || p[2]>reslicedImage.getDimensions()[3] )
									{
										ic.getImage().setZ(p[2]); /// A MODIFIER !!!! setPosition
										imp.updateAndDraw();
										rect = boundingRect(p[0], p[1], oldp[0], oldp[1]);
									}

								} 
								else //case : mouse in resliced image window 
								{
									p[0] = xMouse;
									p[1] = yMouse;
									p[2] = zMouse;
									//displayImage.setZ(p[2]); /// A MODIFIER !!!! Position
									rect = boundingRect(xMouse,yMouse,oldXMouse,oldYMouse);

								}

								// For PolygonRoi the cursor would overwrite the indicator lines.
								//Roi roi = imp.getRoi();
								//if (! (roi != null  && roi.getState() == Roi.CONSTRUCTING) ) { //&& roi instanceof PolygonRoi
									drawSyncCursor(ic,rect, p[0], p[1]);
								//}
								/*if(ic != icc)
									ic.mouseMoved(adaptEvent(eImg, ic, p));*/
							}

							// second image 
							if (IJ.getInstance().quitting()) {return;}
							imp = displayImage;
							if (imp != null) {

								ic = imp.getCanvas();
								if ( ic != icc)  //case : mouse in resliced image window
								{ 
									p = getMatchingCoords( icc, icc.offScreenX(xMouse), icc.offScreenY(yMouse), zMouse);
									IJ.log("p newCoordF = " +p[0]+"   "+p[1]+"   "+p[2]+"");
									oldp = getMatchingCoords( icc, icc.offScreenX(oldXMouse), icc.offScreenY(oldYMouse), oldZMouse);
									imp.setZ(p[2]);
									imp.updateAndDraw();
									updateResultOverlay();
									rect = boundingRect(p[0], p[1], oldp[0], oldp[1]);
								} else //case : mouse in plugin window
								{
									p[0] = xMouse;
									p[1] = yMouse;
									p[2] = zMouse;

									rect = boundingRect(xMouse,yMouse,oldXMouse,oldYMouse);

								}
								// For PolygonRoi the cursor would overwrite the indicator lines.
								//Roi roi = imp.getRoi();
								//if (! (roi != null && roi.getState() == Roi.CONSTRUCTING) ) { // && roi instanceof PolygonRoi
									drawSyncCursor(ic,rect,  p[0], p[1]);
								//}
								/*if(ic != icc)
									ic.mouseMoved(adaptEvent(eImg, ic, p));*/
							}

							// Display correct values in ImageJ statusbar
							//icc.getImage().mouseMoved(icc.offScreenX(xMouse), icc.offScreenY(yMouse));
							

							storeCanvasState(icc);
						}
					

				};
				
				
				// --------------------------------------------------
				/**
				 * Method to pass on changes of the z-slice of a stack.
				 * pb : http://stackoverflow.com/questions/11924736/how-to-use-classes-in-referenced-libraries-in-eclipse
				 */
				/* public void displayChanged(DisplayChangeEvent e) {
					if (vwins == null) return;
					Object source = e.getSource();
					int type = e.getType();
					int value = e.getValue();
					ImagePlus imp;
					ImageWindow iw;
					// Current imagewindow
					ImageWindow iwc = WindowManager.getCurrentImage().getWindow();
					// pass on only if event comes from current window
					if (!iwc.equals(source)) return;
					// Change slices in other synchronized windows.
					if(cSlice.getState() && type==DisplayChangeEvent.Z) {
						for(int n=0; n<vwins.size();++n) {
							imp = getImageFromVector(n);
							if (imp != null) {
								iw = imp.getWindow();
								int stacksize = imp.getStackSize();
								if( !iw.equals(source) && (iw instanceof StackWindow) ) {
									((StackWindow)iw).setPosition(imp.getChannel(), value, imp.getFrame());
								}
							}
						}
					}
			
					// Store srcRect, Magnification and others of current ImageCanvas
					ImageCanvas icc = iwc.getCanvas();
					storeCanvasState(icc);
				}*/

				
				// --------------------------------------------------

				/** Store srcRect and Magnification of the currently active ImageCanvas ic */
				private void storeCanvasState(ImageCanvas ic) {
					currentMag = ic.getMagnification();
					currentSrcRect = new Rectangle(ic.getSrcRect());
				}

				/** Get Screen Coordinates for ImageCanvas ic matching
				 * the OffScreen Coordinates of the current ImageCanvas.
				 * (srcRect and magnification stored after each received event.)
				 * Input: The target ImageCanvas, the current ImageCanvas,
				 * x-ScreenCoordinate for current Canvas, y-ScreenCoordinate for current Canvas
				 * If the "ImageScaling" checkbox is selected, Scaling and Offset
				 * of the images are taken into account. */
				int[] getMatchingCoords(ImageCanvas icc, int xc, int yc, int zc) {

					double newCoord[][] ;
					double coord[][] = new double[][] {{xc},{yc},{zc}};
					int xnew,ynew;
					if (icc == reslicedImage.getCanvas()) //"si event sur le resliced"
					{
						newCoord = multiply(coordResliceToRaw,coord);
						IJ.log(" newCoord pour segm = " +newCoord[0][0]+"   "+newCoord[1][0]+"   "+newCoord[2][0]+"");
						newCoord[0][0] = newCoord[0][0]+ origine[0];
						newCoord[1][0] = newCoord[1][0]+ origine[1];
						newCoord[2][0] = newCoord[2][0]+ origine[2];
						double xOffScreen = currentSrcRect.x + (newCoord[0][0]/currentMag);
						double yOffScreen = currentSrcRect.y + (newCoord[1][0]/currentMag);
						xnew = displayImage.getCanvas().screenXD(xOffScreen);
						ynew = displayImage.getCanvas().screenYD(yOffScreen);
					}
					else
					{
						coord[0][0] = coord[0][0]- origine[0];
						coord[1][0] = coord[1][0]- origine[1];
						coord[2][0] = coord[2][0]- origine[2];
						newCoord = multiply(coordRawToReslice,coord);
						IJ.log(" newCoord pour resliced = " +newCoord[0][0]+"   "+newCoord[1][0]+"   "+newCoord[2][0]+"");
						
						double xOffScreen = currentSrcRect.x + (newCoord[0][0]/currentMag);
						double yOffScreen = currentSrcRect.y + (newCoord[1][0]/currentMag);
						xnew = reslicedImage.getCanvas().screenXD(xOffScreen);
						ynew = reslicedImage.getCanvas().screenYD(yOffScreen);
					}
					
					
					/*if (cScaling.getState()) {
						Calibration cal = ((ImageWindow)ic.getParent()).getImagePlus().getCalibration();
						Calibration curCal = ((ImageWindow)icc.getParent()).getImagePlus().getCalibration();
						xOffScreen = ((xOffScreen-curCal.xOrigin)*curCal.pixelWidth)/cal.pixelWidth+cal.xOrigin;
						yOffScreen = ((yOffScreen-curCal.yOrigin)*curCal.pixelHeight)/cal.pixelHeight+cal.yOrigin;
					}*/
					
					
					return new int[] {xnew,ynew,(int)newCoord[2][0]};
				}
				
				void drawSyncCursor(ImageCanvas ic, Rectangle rect,
						int xc, int yc) {					
					
					int SZ = 16/2;
					int xpSZ = xc+SZ;
					int xmSZ = xc-SZ;
					int ypSZ = yc+SZ;
					int ymSZ = yc-SZ;
					int xp2 = xc+2;
					int xm2 = xc-2;
					int yp2 = yc+2;
					int ym2 = yc-2;
					Graphics g = ic.getGraphics();
					try {
						g.setClip(rect.x,rect.y,rect.width,rect.height);
						ic.paint(g);
						g.setColor(Color.red);
						// g.drawRect(x-SZ,y-SZ,RSZ,RSZ);
						g.drawLine(xmSZ, ymSZ, xm2, ym2);
						g.drawLine(xpSZ, ypSZ, xp2, yp2);
						g.drawLine(xpSZ, ymSZ, xp2, ym2);
						g.drawLine(xmSZ, ypSZ, xm2, yp2);
					}
					finally {
						// free up graphics resources
						g.dispose();
					}
				}
				
				 // --------------------------------------------------
				/** Makes a new mouse event from MouseEvent e with the Canvas c
				* as source and the coordinates of Point p as X and Y.*/
				private MouseEvent adaptEvent(MouseEvent e, Canvas c, int[] p) {
					return new MouseEvent(c, e.getID(), e.getWhen(), e.getModifiers(),
							p[0], p[1], e.getClickCount(), e.isPopupTrigger());
				}
				// --------------------------------------------------
				/** Compute bounding rectangle given current and old cursor
				locations. This is used to determine what part of image to
				redraw. */
				protected Rectangle boundingRect(int xMouse, int yMouse,
						int oldXMouse, int oldYMouse) {
					int SZ = 16/2;
					int SCALE = 3;
					int dx = Math.abs(oldXMouse - xMouse)/2;
					int dy = Math.abs(oldYMouse - yMouse)/2;
					int xOffset = dx + SCALE * SZ;
					int yOffset = dy + SCALE * SZ;
					int xCenter = (xMouse + oldXMouse)/2;
					int yCenter = (yMouse + oldYMouse)/2;
					int xOrg = Math.max(xCenter - xOffset,0);
					int yOrg = Math.max(yCenter - yOffset,0);
					int w = 2 * xOffset;
					int h = 2 * yOffset;
					return new Rectangle(xOrg, yOrg, w, h);
				}
				
				//end add
	}// end class CustomWindow


	/**
	 * Get the watershed lines out of the result catchment basins image
	 * @param labels labeled catchment basins image
	 * @return binary image with the watershed lines in white
	 */
	ImagePlus getWatershedLines( ImagePlus labels )
	{
		final ImagePlus lines = BinaryImages.binarize( labels );
		IJ.run( lines, "Invert", "stack" );
		return lines;
	}


	/**
	 * Enable/disable all components in the parameter panel
	 * 
	 * @param enabled boolean flag to enable/disable components
	 */
	void setParamsEnabled( boolean enabled )
	{
		this.dynamicText.setEnabled( enabled );
		this.dynamicLabel.setEnabled( enabled );		
		this.advancedOptionsCheckBox.setEnabled( enabled );
		this.toggleOverlayCheckBox.setEnabled( enabled );
		this.resultButton.setEnabled( enabled );
		this.resultDisplayList.setEnabled( enabled );
		displayLabel.setEnabled( enabled );
		if( selectAdvancedOptions )
			enableAdvancedOptions( enabled );
		if( applyGradient )
			enableGradientOptions( enabled );
	}

	/**
	 * Enable/disable advanced options components
	 * 
	 * @param enabled flag to enable/disable components
	 */
	void enableAdvancedOptions( boolean enabled )
	{
		damsCheckBox.setEnabled( enabled );
		connectivityLabel.setEnabled( enabled );
		connectivityList.setEnabled( enabled );
	}

	/**
	 * Enable/disable gradient options components
	 * 
	 * @param enabled flag to enable/disable components
	 */
	void enableGradientOptions( boolean enabled )
	{
		gradientList.setEnabled( enabled );
		gradientList.setEnabled( enabled );
		gradientRadiusSizeLabel.setEnabled( enabled );
		gradientRadiusSizeText.setEnabled( enabled );
		gradientCheckBox.setEnabled( enabled );
		gradientTypeLabel.setEnabled( enabled );
	}

	@Override
	public void run(String arg0) 
	{
		ij.io.LogStream.redirectSystem(true); 
		if ( IJ.getVersion().compareTo("1.48a") < 0 )
		{
			IJ.error( "Morphological Segmentation", "ERROR: detected ImageJ version " + IJ.getVersion()  
					+ ".\nMorphological Segmentation requires version 1.48a or superior, please update ImageJ!" );
			return;
		}

		// get current image
		if (null == WindowManager.getCurrentImage())
		{
			inputImage = IJ.openImage();
			if (null == inputImage) return; // user canceled open dialog
		}
		else
			inputImage = WindowManager.getCurrentImage();

		if( inputImage.getType() == ImagePlus.COLOR_256 || 
				inputImage.getType() == ImagePlus.COLOR_RGB )
		{
			IJ.error( "Morphological Segmentation", "This plugin only works on grayscale images.\nPlease convert it to 8, 16 or 32-bit." );
			return;
		}

		inputStackCopy = inputImage.getImageStack().duplicate();
		displayImage = new ImagePlus( inputImage.getTitle(), 
				inputStackCopy );
		displayImage.setTitle("Morphological Segmentation");
		displayImage.setSlice( inputImage.getSlice() );

		// hide input image (to avoid accidental closing)
		inputImage.getWindow().setVisible( false );

		// set the 2D flag
		inputIs2D = inputImage.getImageStackSize() == 1;

		// correct Fiji error when the slices are read as frames
		if ( inputIs2D == false && 
				displayImage.isHyperStack() == false && 
				displayImage.getNSlices() == 1 )
		{
			// correct stack by setting number of frames as slices
			displayImage.setDimensions( 1, displayImage.getNFrames(), 1 );
		}
		
		// Build GUI
		SwingUtilities.invokeLater(
				new Runnable() {
					public void run() {
						win = new CustomWindow( displayImage );
						win.pack();
					}
				});

	}

	//add by elise


		public ImagePlus AskForAnotherImage(){
			
			ImagePlus oldImage = null;	
			// inspiration from :http://fiji.sc/Javascript_Scripting
			int[] wList = WindowManager.getIDList();
	  
			//get all the image titles so they can be shown in the dialog
			int nImages = WindowManager.getImageCount();
			String titles[] = new String[nImages+2]; // int newLabels[] =new int[nLabels+1];
			titles[0]= "load marker Image";
			
			for (int i=0, k=1; i<wList.length; i++) {
			    ImagePlus limp = WindowManager.getImage(wList[i]);
			    if (null != limp)
			      titles[k++] = limp.getTitle(); 
				}
			
			titles[nImages+1]= "load labeled Image";
			
			GenericDialog gd = new GenericDialog("New Image");
			gd.addMessage("Select the raw image");			
			gd.addChoice("Image :", titles, "load image");
			gd.showDialog();
			
			if (gd.wasCanceled())
			    return null;
			
			int choiceIndex = gd.getNextChoiceIndex();	
			System.out.print("parmis les "+titles.length+" possibilitées");
			System.out.print( "" + choiceIndex + " choisi" );
			
			if (choiceIndex == 0)
				{
					oldImage = IJ.openImage();
					if (null == oldImage) return null; // user canceled open dialog
				}
			else if (choiceIndex == titles.length-1)
			{
				oldImage = IJ.openImage();
				
				if (null == oldImage) return null; // user canceled open dialog
				
				oldImage = labeledImage_to_Markers(oldImage);
			}
			else
				oldImage =  WindowManager.getImage(wList[choiceIndex-1]); // -1 parce que l'indexe zeros n'est pas une image
		return oldImage;
		}

		public final static ImagePlus labeledImage_to_Markers(ImagePlus imp){
			ImageStack img = imp.getStack();
			int width = img.getWidth();
			int height = img.getHeight();
			int nSlices = img.getSize();
			int bitDepth = img.getBitDepth() ;
			
			int nb_save = 0 ;
			int nb_foundLabel = 0;

			java.util.List<Integer> list_labels = new ArrayList<Integer>();
			java.util.List<ArrayList<ArrayList<Integer>>> list_coordinates = new ArrayList<ArrayList<ArrayList<Integer>>>();
			java.util.List<ArrayList<Integer>> list_min = new ArrayList<ArrayList<Integer>>();
			java.util.List<ArrayList<Integer>> list_max = new ArrayList<ArrayList<Integer>>();
			
			
			ImagePlus imp_markers = IJ.createImage("markers", width, height, nSlices, bitDepth) ;
			imp_markers.setCalibration(imp.getCalibration());
			ImageStack img_markers = imp_markers.getStack();
						
			for(int z = 0; z < nSlices; z++){  	 	 	
		  	 	 	for (int y = 0; y <height; y++){
		  	 	 		for (int x = 0; x < width; x++){
		  	 	 			IJ.log(  "  " + x + "  ");
		  	 	 			img_markers.setVoxel(x,  y,  z,  0);
		  	 	 			nb_foundLabel = list_labels.size();
		  	 	 			ArrayList<Integer>  newCoordinates = new ArrayList<Integer>();
		  	 	 			newCoordinates.add(x);
		  	 	 			newCoordinates.add(y);
		  	 	 			newCoordinates.add(z);
		  	 	 			int Flag_In = 0 ;
		  	 	 			if(img.getVoxel(x, y, z) == 0) continue;
		  	 	 			else if ( list_labels.size() != 0) {
		  	 	 				for (int i = 0 ; i< nb_foundLabel;i++){
		  	 	 					if (img.getVoxel(x, y, z) == list_labels.get(i)){
		  	 	 						Flag_In = 1 ; 
		  	 	 						 //System.out.println(list_coordinates.size());
		  	 	 						 //System.out.println(i);
		  	 	 						list_coordinates.get(i).add(newCoordinates);
		  	 	 						if (x<list_min.get(i).get(0)) { list_min.get(i).set(0,x);}
		  	 	 						else if (x>list_max.get(i).get(0)) {list_max.get(i).set(0,x);}

		  	 	 						if (y<list_min.get(i).get(1)) { list_min.get(i).set(1,y);}
		  	 	 						else if (y>list_max.get(i).get(1)) {list_max.get(i).set(1,y);}

		  	 	 						if (z<list_min.get(i).get(2)) { list_min.get(i).set(2,z);}
		  	 	 						else if (z>list_max.get(i).get(2)) {list_max.get(i).set(2,z);}
		  	 	 					}
		  	 	 				}
		  	 	 			}
		  	 	 			if ( Flag_In == 0){
		  	 	 				
		  	 	 			
		  	 	 				list_labels.add((int)img.getVoxel(x, y, z));

		  	 	 				// coordonnees and min max initialization
		  	 	 				ArrayList<ArrayList<Integer>> coordinate_list = new ArrayList<ArrayList<Integer>>();
		  	 	 				coordinate_list.add(newCoordinates);
		  	 	 				list_coordinates.add(coordinate_list);
								list_min.add(newCoordinates);
		  	 	 				list_max.add(newCoordinates);
		  	 	 			}
		  	 	 		}
		  	 	 	}
		  	 	 }
		  	 	 nb_foundLabel = list_labels.size();
		  	 	 Random rand = new Random();
		  	 	 int  n,Flag_In,label,Size, x,y,z;
		  	 	 int dist = 5; // devrait varier en fonction de x,y, et z car les "distances" ne sont pas identiques puisque les pixels ne sont pas cubiques
		  	 	 System.out.println(nb_foundLabel);
		  	 	 for (int i =0 ; i< nb_foundLabel;i++){
		  	 		IJ.log( "i =  " + i + " ");
		  	 	 	Flag_In = 0;
		  	 	 	label =list_labels.get(i);
		  	 	 	Size =list_coordinates.get(i).size();
		  	 	 	System.out.println(label);
		  	 	 	System.out.println(Size);
		  	 	 	do {
		  	 	 		n = rand.nextInt(Size);
		  	 	 		
		  	 	 		x = (int)list_coordinates.get(i).get(n).get(0);
		  	 	 		y = (int)list_coordinates.get(i).get(n).get(1);
		  	 	 		z = (int)list_coordinates.get(i).get(n).get(2);	  
		  	 	 			 	 		
		  	 	 		if (img.getVoxel(x+dist, y, z) == label && img.getVoxel(x, y+dist, z) == label && img.getVoxel(x, y, z+dist) == label && img.getVoxel(x-dist, y, z) == label && img.getVoxel(x, y-dist, z)== label && img.getVoxel(x, y, z-dist) == label &&  img.getVoxel(x+dist, y+dist, z+dist)== label && img.getVoxel(x-dist, y-dist, z-dist)== label){
		  	 	 			Flag_In = 1 ;
		  	 	 		}
		  	 	 		
		  	 	 		
		  	 	 	} while(Flag_In == 1);
		  	 	 	System.out.println(i);
		  	 	 	img_markers.setVoxel(x,y,z,1);
		  	 	 	//img_markers.setVoxel(list_min.get(i).get(0)+((list_max.get(i).get(0)-list_min.get(i).get(0))/2),list_min.get(i).get(1)+((list_max.get(i).get(1)-list_min.get(i).get(1))/2),list_min.get(i).get(2)+((list_max.get(i).get(2)-list_min.get(i).get(2))/2), 1);
		  	 	 }
				// marker pour le background
					Flag_In = 0;	  	 	 	
		  	 	 	do {
		  	 	 		x = rand.nextInt(width);
		  	 	 		y = rand.nextInt(height);
		  	 	 		z = rand.nextInt(nSlices);
		  	 	 			  	 	 			 	 		
		  	 	 		if (img.getVoxel(x, y, z)== 0){
		  	 	 			Flag_In = 1 ;
		  	 	 		IJ.log( " Find ");
		  	 	 		} 	 	
		  	 	 	} while(Flag_In == 1);
		  	 	 	img_markers.setVoxel(x,y,z,1);
		  	 	 			
		  	 	imp_markers.show();				
		  	 	return 	imp_markers;	
		}
		
		void filterLabelSize( int seuil )
		{
			ImageStack labeledImage = resultImage.getStack();
			ArrayList<ArrayList<Integer>> label = new ArrayList<ArrayList<Integer>>();
						
			// initialization
			label.add(new ArrayList<Integer>()); // index O = label number ; index 1 = number of pixel
			label.get(0).add((int)labeledImage.getVoxel(0, 0, 0));
			label.get(0).add(0);
			
			int sizeX = labeledImage.getWidth();
	        int sizeY = labeledImage.getHeight();
	        int sizeZ = labeledImage.getSize();
	        
	        //TreeSet<Integer> labels = new TreeSet<Integer> ();
	        int flag ;
	        // iterate on image pixels
	        for (int z = 0; z < sizeZ; z++) {
	        	IJ.showProgress(z, sizeZ);
	        	for (int y = 0; y < sizeY; y++) 
	        	{
	        		for (int x = 0; x < sizeX; x++) 
	        		{
	        			flag = 0 ;
	        			for( int i = 0 ; i< label.size() ; i++)
	        			{
	        				if (label.get(i).get(0) == (int) labeledImage.getVoxel(x, y, z))
	        				{
	        					flag = 1;
	        					label.get(i).set(1,label.get(i).get(1) + 1);
	        					break;
	        				}
	        			}
	        			if (flag == 0)
	        			{

	        				label.add(new ArrayList<Integer>());
	        				label.get(label.size()-1).add( (int) labeledImage.getVoxel(x, y, z));
	        				label.get(label.size()-1).add( 1);
	        			}
	        			
	        		}

	        	}
	        }
	        	IJ.showProgress(1);
	        
	        	if(markersImage != null)
	        	{
	        		ImageStack markersStack = markersImage.getStack();

	        		for (int z = 0; z < sizeZ; z++) {
	        			IJ.showProgress(z, sizeZ);
	        			for (int y = 0; y < sizeY; y++) 
	        			{
	        				for (int x = 0; x < sizeX; x++) 
	        				{
	        					for( int i = 0 ; i< label.size() ; i++)
	        					{
	        						if(labeledImage.getVoxel(x, y, z) == label.get(i).get(0)
	        								&& label.get(i).get(1) <= seuil)
	        						{
	        							markersStack.setVoxel(x, y, z, 0);
	        							break;
	        						}
	        					}


	        				}
	        			}
	        		}
	        		IJ.showProgress(1);
	        	}
	        	else
	        	{
	        		markersImage = IJ.createImage("markers", sizeX, sizeY, sizeZ, labeledImage.getBitDepth() );
	        		markersImage.setCalibration( inputImage.getCalibration() );
	        		
	    			ImageStack markersStack = markersImage.getStack();
	    			int dist = 20;
	        		for (int z = 0; z < sizeZ; z++) {
	        			IJ.showProgress(z, sizeZ);
	        			for (int y = 0; y < sizeY; y++) 
	        			{
	        				for (int x = 0; x < sizeX; x++) 
	        				{
	        					
	        					for( int i = 0 ; i< label.size() ; i++)
	        					{
	        						if(labeledImage.getVoxel(x, y, z) == label.get(i).get(0) && label.get(i).get(1) > seuil 
	        								&& labeledImage.getVoxel(x+dist, y, z) == labeledImage.getVoxel(x, y, z)
	        								&& labeledImage.getVoxel(x, y+dist, z) == labeledImage.getVoxel(x, y, z)
	        								&& labeledImage.getVoxel(x, y, z+dist) == labeledImage.getVoxel(x, y, z)
	        								&& labeledImage.getVoxel(x-dist, y, z) == labeledImage.getVoxel(x, y, z)
	        								&& labeledImage.getVoxel(x, y-dist, z)== labeledImage.getVoxel(x, y, z)
	        								&& labeledImage.getVoxel(x, y, z-dist) == labeledImage.getVoxel(x, y, z)
	        								&& labeledImage.getVoxel(x+dist, y+dist, z+dist)== labeledImage.getVoxel(x, y, z)
	        								&& labeledImage.getVoxel(x-dist, y-dist, z-dist)== labeledImage.getVoxel(x, y, z))
	        						{
	        							markersStack.setVoxel(x, y, z, 1);
	        							break;
	        						}
	        					}
	        					      					
	        					
	        				}
	        			}
	        		}
	        	
	        				IJ.showProgress(1);
	        	  
	        	}
	        			/*
	        	ArrayList<Integer> smallLabels = new ArrayList<Integer>();
	        	for( int i = 0 ; i< label.size() ; i++)
    			{
	        		if( label.get(i).get(1) <= seuil)
	        		{
	        			smallLabels.add(label.get(i).get(0));
	        		}
    			}*/
	             
	        //return smallLabels;
		}
		
	// End of Elise Functions
		
	/* **********************************************************
	 * Macro recording related methods
	 * *********************************************************/

	/**
	 * Macro-record a specific command. The command names match the static 
	 * methods that reproduce that part of the code.
	 * 
	 * @param command name of the command including package info
	 * @param args set of arguments for the command
	 */
	public static void record(String command, String... args) 
	{
		command = "call(\"inra.ijpb.plugins.MorphologicalSegmentation." + command;
		for(int i = 0; i < args.length; i++)
			command += "\", \"" + args[i];
		command += "\");\n";
		if(Recorder.record)
			Recorder.recordString(command);
	}

	/**
	 * Segment current image (GUI needs to be running)
	 * 
	 * @param dynamic string containing dynamic value (format: "dynamic=[integer value]")
	 * @param calculateDams string containing boolean flag to create dams (format: "calculateDams=[boolean])
	 * @param connectivity string containing connectivity value (format: "connectivity=[4 or 8 / 6 or 26])
	 * @param usePriorityQueue string containing boolean flag to use priority queue (format: "usePriorityQueue=[boolean])
	 * 
	 * @deprecated the priority queue method is now the only one
	 */
	public static void segment(
			String dynamic,
			String calculateDams,
			String connectivity,
			String usePriorityQueue )
	{		
		final ImageWindow iw = WindowManager.getCurrentImage().getWindow();
		if( iw instanceof CustomWindow )
		{
			//IJ.log( "GUI detected" );			
			final CustomWindow win = (CustomWindow) iw;
			win.setDynamic( Integer.parseInt( dynamic.replace( "tolerance=", "" ) ) );
			win.setCalculateDams( calculateDams.contains( "true" ) );
			win.setConnectivity( Integer.parseInt( connectivity.replace( "connectivity=", "" ) ) );
			win.runSegmentation( win.getSegmentText() );			
		}
		else
			IJ.log( "Error: Morphological Segmentation GUI not detected." );
	}
	/**
	 * Segment current image (GUI needs to be running)
	 * 
	 * @param dynamic string containing dynamic value (format: "dynamic=[integer value]")
	 * @param calculateDams string containing boolean flag to create dams (format: "calculateDams=[boolean])
	 * @param connectivity string containing connectivity value (format: "connectivity=[4 or 8 / 6 or 26])
	 */
	public static void segment(
			String dynamic,
			String calculateDams,
			String connectivity )
	{		
		final ImageWindow iw = WindowManager.getCurrentImage().getWindow();
		if( iw instanceof CustomWindow )
		{
			//IJ.log( "GUI detected" );			
			final CustomWindow win = (CustomWindow) iw;
			win.setDynamic( Integer.parseInt( dynamic.replace( "tolerance=", "" ) ) );
			win.setCalculateDams( calculateDams.contains( "true" ) );
			win.setConnectivity( Integer.parseInt( connectivity.replace( "connectivity=", "" ) ) );
			win.runSegmentation( win.getSegmentText() );			
		}
		else
			IJ.log( "Error: Morphological Segmentation GUI not detected." );
	}
	/**
	 * Toggle current result overlay image
	 */
	public static void toggleOverlay()
	{
		final ImageWindow iw = WindowManager.getCurrentImage().getWindow();
		if( iw instanceof CustomWindow )
		{
			final CustomWindow win = (CustomWindow) iw;
			win.toggleOverlay();
		}
	}

	/**
	 * Show current result in a new image
	 */
	public static void createResultImage()
	{
		final ImageWindow iw = WindowManager.getCurrentImage().getWindow();
		if( iw instanceof CustomWindow )
		{
			final CustomWindow win = (CustomWindow) iw;
			String mode = win.getResultDisplayOption();
			
			ImagePlus result = null;

			if( mode.equals( MorphologicalSegmentation.catchmentBasinsText) )
				result = win.getResult( ResultMode.BASINS );
			else if( mode.equals( MorphologicalSegmentation.overlaidBasinsText ) )
				result = win.getResult( ResultMode.OVERLAID_BASINS );
			else if( mode.equals( MorphologicalSegmentation.watershedLinesText ) )
				result = win.getResult( ResultMode.LINES );
			else if( mode.equals( MorphologicalSegmentation.overlaidDamsText ))
				result = win.getResult( ResultMode.OVERLAID_DAMS );

			if( null != result )
			{
				result.show();
				result.setSlice( win.getImagePlus().getSlice() );
			}
		}
	}
	
	/**
	 * Set input image type 
	 * @param type input image type (border or object)
	 */
	public static void setInputImageType( String type )
	{
		final ImageWindow iw = WindowManager.getCurrentImage().getWindow();
		if( iw instanceof CustomWindow )
		{
			final CustomWindow win = (CustomWindow) iw;
			
			if( type.equals( "object" ) )
				win.setInputImageType( objectImageText );
			else if( type.equals( "border" ) )
				win.setInputImageType( borderImageText );
		}			
	}

	/**
	 * Set GUI to show gradient image in the main canvas
	 * @param bool true to display gradient image and false to display input image
	 */
	public static void setShowGradient( String bool )
	{
		final ImageWindow iw = WindowManager.getCurrentImage().getWindow();
		if( iw instanceof CustomWindow )
		{
			final CustomWindow win = (CustomWindow) iw;
			if( bool.equals( "true" ) )
				win.setShowGradient( true );
			else if( bool.equals( "false" ) )
				win.setShowGradient( false );	
		}
	}

	/**
	 * Set the display format in the GUI
	 * @param format output mode ("Overlaid basins", "Overlaid dams", "Catchment basins", "Watershed lines")
	 */
	public static void setDisplayFormat( String format )
	{
		final ImageWindow iw = WindowManager.getCurrentImage().getWindow();
		if( iw instanceof CustomWindow )
		{
			final CustomWindow win = (CustomWindow) iw;
			win.setResultDisplayOption( format );
			if( win.isShowResultOverlaySelected() )
				win.updateResultOverlay();
		}
	}

	/**
	 * Set the gradient radius
	 * @param radius gradient radius size (in pixels)
	 */
	public static void setGradientRadius( String radius )
	{
		final ImageWindow iw = WindowManager.getCurrentImage().getWindow();
		if( iw instanceof CustomWindow )
		{
			final CustomWindow win = (CustomWindow) iw;
			win.setGradientRadius( Integer.parseInt( radius ) );			
		}
	}
	
}// end MorphologicalSegmentation class
