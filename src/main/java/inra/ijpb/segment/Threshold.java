/**
 * 
 */
package inra.ijpb.segment;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

/**
 * Static methods for thresholding images.
 * 
 * @author David Legland
 *
 */
public class Threshold
{
	public static final ImagePlus threshold(ImagePlus image, double lower, double upper)
	{
		String newName = image.getShortTitle() + "-bin";
		if (image.getStackSize() == 1) 
		{
			ImageProcessor result = threshold(image.getProcessor(), lower, upper);
			return new ImagePlus(newName, result);
		} 
		else
		{
			ImageStack result = threshold(image.getStack(), lower, upper);
			return new ImagePlus(newName, result);
		}
	}
	
	/**
	 * Creates a new binary image with value 255 when input image has value 
	 * between <code>lower</code> and <code>upper</code> values (included). 
	 * @param image the input grayscale image
	 * @param lower the lower threshold bound 
	 * @param upper the upper threshold bound
	 * @return a binary image
	 */
	public static final ImageProcessor threshold(ImageProcessor image, double lower, double upper)
	{
		if (image instanceof ColorProcessor) 
		{
			throw new IllegalArgumentException("Requires a gray scale image");
		}
		
		int width = image.getWidth();
		int height = image.getHeight();

		ImageProcessor result = new ByteProcessor(width, height);
		for (int y = 0; y < height; y++)
		{
			for (int x = 0; x < width; x++)
			{
				double value = image.getf(x, y);
				if (value >= lower && value <= upper)
					result.set(x, y, 255);
			}

		}

		return result;
	}

	public static final ImageStack threshold(ImageStack image, double lower, double upper)
	{
		int sizeX = image.getWidth();
		int sizeY = image.getHeight();
		int sizeZ = image.getSize();

		ImageStack result = ImageStack.create(sizeX, sizeY, sizeZ, 8);
		for (int z = 0; z < sizeZ; z++)
		{
			for (int y = 0; y < sizeY; y++)
			{
				for (int x = 0; x < sizeX; x++)
				{
					double value = image.getVoxel(x, y, z);
					if (value >= lower && value <= upper)
						result.setVoxel(x, y, z, 255);
				}
			}
		}

		return result;
	}

}
