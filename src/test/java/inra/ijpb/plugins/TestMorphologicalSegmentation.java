package inra.ijpb.plugins;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;
import inra.ijpb.binary.ConnectedComponents;
import inra.ijpb.morphology.MinimaAndMaxima3D;
import inra.ijpb.morphology.Morphology;
import inra.ijpb.morphology.Strel3D;
import inra.ijpb.watershed.Watershed;

public class TestMorphologicalSegmentation {
	
	
	/**
	 * Test the morphological segmentation pipeline over the same image stored as 8, 16 and 32-bit.
	 */
	@Test
	public void testSegmentationDifferentImageTypes()
	{
		ImagePlus input = IJ.openImage( TestMorphologicalSegmentation.class.getResource( "/files/grains.tif" ).getFile() );
		int dynamic = 10;
		int connectivity = 6;
		int gradientRadius = 1;
		
		final ImagePlus copy = input.duplicate();
		
		boolean[] values = new boolean[]{ true, false };

		for( boolean usePriorityQueue : values )
			for( boolean calculateDams : values )
			{
				input = copy;
				
				ImagePlus result8bit = segmentImage( input, dynamic, connectivity, gradientRadius, usePriorityQueue, calculateDams );
				IJ.run( input, "16-bit", "" );
				ImagePlus result16bit = segmentImage( input, dynamic, connectivity, gradientRadius, usePriorityQueue, calculateDams );		
				assertEquals( "Different results for 8 and 16 bit images (priority queue = " 
								+ usePriorityQueue + ", dams = " + calculateDams + ")", 0, diffImagePlus( result8bit, result16bit ) );
				IJ.run( input.duplicate(), "32-bit", "" );
				ImagePlus result32bit = segmentImage( input, dynamic, connectivity, gradientRadius, usePriorityQueue, calculateDams );
				assertEquals( "Different results for 8 and 32 bit images (priority queue = " 
						+ usePriorityQueue + ", dams = " + calculateDams + ")", 0, diffImagePlus( result8bit, result32bit ) );
			}
	}
	
	ImagePlus segmentImage( 
			ImagePlus input, 
			int dynamic, 
			int connectivity,
			int gradientRadius,
			boolean usePriorityQueue,
			boolean calculateDams )
	{
		Strel3D strel = Strel3D.Shape.CUBE.fromRadius( gradientRadius );
		ImageStack image = Morphology.gradient( input.getImageStack(), strel );
		ImageStack regionalMinima = MinimaAndMaxima3D.extendedMinima( image, dynamic, connectivity );
		ImageStack imposedMinima = MinimaAndMaxima3D.imposeMinima( image, regionalMinima, connectivity );
		ImageStack labeledMinima = ConnectedComponents.computeLabels( regionalMinima, connectivity, 32 );
		ImageStack resultStack = Watershed.computeWatershed( imposedMinima, labeledMinima, 
				connectivity, usePriorityQueue, calculateDams );
		ImagePlus resultImage = new ImagePlus( "watershed", resultStack );
		resultImage.setCalibration( input.getCalibration() );
		return resultImage;
	}
	
	private int diffImagePlus(final ImagePlus a, final ImagePlus b) {
		final int[] dimsA = a.getDimensions(), dimsB = b.getDimensions();
		if (dimsA.length != dimsB.length) return dimsA.length - dimsB.length;
		for (int i = 0; i < dimsA.length; i++) {
			if (dimsA[i] != dimsB[i]) return dimsA[i] - dimsB[i];
		}
		int count = 0;
		final ImageStack stackA = a.getStack(), stackB = b.getStack();
		for (int slice = 1; slice <= stackA.getSize(); slice++) {
			count += diff( stackA.getProcessor( slice ), stackB.getProcessor( slice ) );
		}
		return count;
	}

	private int diff(final ImageProcessor a, final ImageProcessor b) {
		int count = 0;
		final int width = a.getWidth(), height = a.getHeight();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (a.getf(x, y) != b.getf(x, y)) count++;
			}
		}
		return count;
	}
	

    /**http://www.cs.nyu.edu/~jeremy/atmm/BlockPolly/render/Matrix.java
       Copies the contents of the source matrix to the destination matrix.
       @param src original source matrix
       @param dst target destination matrix
    */
   public static void copy(double src[][], double dst[][]) {
      for (int i = 0 ; i < src.length ; i++)
      for (int j = 0 ; j < src[i].length ; j++)
         dst[i][j] = src[i][j];
   }
   
	   public static double[][] invert(double matSource[][]) {
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
		      System.out.println("tmp = "+tmp[0][0] + " "+ tmp[0][1] + " "+ tmp[0][2] + " \n"+ tmp[1][0] + " "+ tmp[1][1] + " "+ tmp[1][2] + "\n "+ tmp[2][0] + " "+ tmp[2][1] + " "+ tmp[2][2] + " ");
				
		      return tmp;
		   }
	   public static void identity(double dst[][]) {
		      for (int i = 0 ; i < dst.length ; i++)
		      for (int j = 0 ; j < dst.length ; j++)
		         dst[i][j] = (i == j ? 1 : 0);
		   }
	    public static double[][] invertG(double a[][]) 

	    {
	        int n = a.length;
	        double X[][] = new double[n][n];
	        double b[][] = new double[n][n];
	        int index[] = new int[n];
	        for (int i=0; i<n; ++i) 
	            b[i][i] = 1;
	 
	 // Transform the matrix into an upper triangle
	        gaussian(a, index);			 
	        IJ.log(" a = "+a[0][0]+ " "+a[0][1]+ " "+a[0][2]+ " \n"+a[1][0]+ " "+a[1][1]+ " "+a[1][2]+ " \n "+a[2][0]+ " "+a[2][1]+ " "+a[2][2]+ " ");
	        
	 // Update the matrix b[i][j] with the ratios stored
	        for (int i=0; i<n-1; ++i){
	            for (int j=i+1; j<n; ++j){
	                for (int k=0; k<n; ++k){
	                    b[index[j]][k]-= a[index[j]][i]*b[index[i]][k];
	        			}
	            }
	        }
	        IJ.log("b = "+b[0][0]+ " "+b[0][1]+ " "+b[0][2]+ " \n"+b[1][0]+ " "+b[1][1]+ " "+b[1][2]+ " \n "+b[2][0]+ " "+b[2][1]+ " "+b[2][2]+ " ");
	        
	 // Perform backward substitutions
	        for (int i=0; i<n; ++i) 
	        {
	            X[n-1][i] = b[index[n-1]][i]/a[index[n-1]][n-1];
	            IJ.log("X = "+X[0][0]+ " "+X[0][1]+ " "+X[0][2]+ " \n"+X[1][0]+ " "+X[1][1]+ " "+X[1][2]+ " \n "+X[2][0]+ " "+X[2][1]+ " "+X[2][2]+ " ");
		        
	            for (int j=n-2; j>=0; --j) 
	            {
	                X[j][i] = b[index[j]][i];
	                for (int k=j+1; k<n; ++k) 
	                {
	                	IJ.log(""+ i +" "+ j +" "+ k +"");
	                    X[j][i] -= a[index[j]][k]*X[k][i];
	                }
	                IJ.log(" a division = "+ a[index[j]][j] +"");
	                X[j][i] /= a[index[j]][j];
	                
	            }
	        }
	        return X;
	    }
	 
	// Method to carry out the partial-pivoting Gaussian
	// elimination.  Here index[] stores pivoting order.
	    public static void gaussian(double a[][], int index[]) 
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
	                IJ.log("  "+ c[index[i]] +"");
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
	            	IJ.log(" div a gaussian "+ a[index[j]][j] +"");
	                double pj = a[index[i]][j]/a[index[j]][j];	 
	 // Record pivoting ratios below the diagonal
	                a[index[i]][j] = pj;			 
	 // Modify other elements accordingly
	                for (int l=j+1; l<n; ++l)
	                    a[index[i]][l] -= pj*a[index[j]][l];
	            }
	        }
	    }
	/**
	 * Main method to test and debug the Morphological
	 * Segmentation GUI
	 *  
	 * @param args
	 */
	public static void main( final String[] args )
	{
		/*double points[][] = new double[][] {{193,0,12.41},{-12, 0,199.61 },{0, 115, 0}}; // {{x1,x2,x3},{y1,y2,y3},{z1,z2,z3}}
		
		double A[][] = invertG(points);
		System.out.println("A = "+A[0][0] + " "+ A[0][1] + " "+ A[0][2] + " \n"+ A[1][0] + " "+ A[1][1] + " "+ A[1][2] + "\n "+ A[2][0] + " "+ A[2][1] + " "+ A[2][2] + " ");
		*/
		ImageJ.main( args );
		
		IJ.open( TestMorphologicalSegmentation.class.getResource( "/files/trans_Col0_676-s63.tif" ).getFile() );		
		new MorphologicalSegmentation().run( null );
		ImagePlus imgRef = new ImagePlus("/files/trans_Col0_676_133c_fermee-s63.tif");
		imgRef.show();
	}

}
