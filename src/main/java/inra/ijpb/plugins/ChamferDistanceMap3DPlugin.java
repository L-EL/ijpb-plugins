package inra.ijpb.plugins;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;
import inra.ijpb.binary.ChamferWeights3D;
import inra.ijpb.binary.distmap.ChamferDistance3D;
import inra.ijpb.binary.distmap.ChamferDistance3DFloat;
import inra.ijpb.util.IJUtils;

/**
 * Compute distance map, with possibility to choose chamfer weights, result 
 * type, and to normalize result or not.
 *
 * @author dlegland
 *
 */
public class ChamferDistanceMap3DPlugin implements PlugIn {

	/* (non-Javadoc)
	 * @see ij.plugin.PlugIn#run(java.lang.String)
	 */
	@Override
	public void run(String arg) {
		
//		if ( IJ.getVersion().compareTo("1.48a") < 0 )
//		{
//			IJ.error( "Morphological Filter 3D", "ERROR: detected ImageJ version " + IJ.getVersion()  
//					+ ".\nThis plugin requires version 1.48a or superior, please update ImageJ!" );
//			return;
//		}
		
		ImagePlus imagePlus = WindowManager.getCurrentImage();
		if (imagePlus == null) {
			IJ.error("No image", "Need at least one image to work");
			return;
		}
		
		// Create a new generic dialog with appropriate options
    	GenericDialog gd = new GenericDialog("Distance Map 3D");
    	gd.addChoice("Distances", ChamferWeights3D.getAllLabels(), 
    			ChamferWeights3D.BORGEFORS.toString());			
    	String[] outputTypes = new String[]{"32 bits", "16 bits"};
    	gd.addChoice("Output Type", outputTypes, outputTypes[0]);
    	gd.addCheckbox("Normalize weights", true);	
//    	gd.addPreviewCheckbox(pfr);
//    	gd.addDialogListener(this);
//        previewing = true;
//		gd.addHelp("http://imagejdocu.tudor.lu/doku.php?id=plugin:morphology:fast_morphological_filters:start");
        gd.showDialog();
//        previewing = false;
        
    	// test cancel  
    	if (gd.wasCanceled())
    		return;

    	// set up current parameters
    	String weightLabel = gd.getNextChoice();
    	boolean floatProcessing = gd.getNextChoiceIndex() == 0;
    	boolean normalize = gd.getNextBoolean();

    	// identify which weights should be used
    	ChamferWeights3D weights = ChamferWeights3D.fromLabel(weightLabel);

    	long t0 = System.currentTimeMillis();

    	ChamferDistance3D algo = new ChamferDistance3DFloat(weights.getFloatWeights(), normalize);
    	ImageStack image = imagePlus.getStack();
    	ImageStack res = algo.distanceMap(image);

		if (res == null)
			return;

		String newName = imagePlus.getShortTitle() + "-dist";
		ImagePlus resPlus = new ImagePlus(newName, res);
		
		// Display the result image
		resPlus.show();
		resPlus.setSlice(imagePlus.getSlice());

		// Display elapsed time
		long t1 = System.currentTimeMillis();
		IJUtils.showElapsedTime("distance map", t1 - t0, imagePlus);
//		IJ.showStatus("Elapsed time: " + (t1 - t0) / 1000. + "s");
	}
}
