/**
 * 
 */
package inra.ijpb.data.image;

import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

/**
 * A set of static methods for working with color images.
 * 
 * @author David Legland
 *
 */
public class ColorImages
{
	/**
	 * Splits the channels of the color image into three new instances of ByteProcessor.
	 *  
	 * @param image the original image, assumed to be a ColorProcessor
	 * @return a collection containing the red, green and blue channels
	 */
	public static final Collection<ByteProcessor> splitChannels(ImageProcessor image) {
		if (!(image instanceof ColorProcessor)) 
		{
			throw new IllegalArgumentException("Requires an instance of ColorProcessor");
		}
		
		// size of input image
		int width = image.getWidth();
		int height = image.getHeight();
		int size = width * height;

		// Extract red, green and blue components
		byte[] redArray = new byte[size];
		byte[] greenArray = new byte[size];
		byte[] blueArray = new byte[size];
		((ColorProcessor) image).getRGB(redArray, greenArray, blueArray);

		// create image processor for each channel
		ByteProcessor red = new ByteProcessor(width, height, redArray, null);
		ByteProcessor green = new ByteProcessor(width, height, greenArray, null);
		ByteProcessor blue  = new ByteProcessor(width, height, blueArray, null);

		// concatenate channels into a new collection
		ArrayList<ByteProcessor> result = new ArrayList<ByteProcessor>(3);
		result.add(red);
		result.add(green);
		result.add(blue);
		
		return result;
	}

	/**
	 * Splits the channels of the color image and returns the new ByteImages
	 * into a Map, using channel names as key. 
	 *  
	 * Example:
	 * <pre><code>
	 * ColorProcessor colorImage = ...
	 * HashMap&lt;String, ByteProcessor&gt; channels = mapChannels(colorImage);
	 * ByteProcessor blue = channels.get("blue");
	 * </code></pre>
	 * 
	 * @param image the original image, assumed to be a ColorProcessor
	 * @return a hashmap indexing the three channels by their names
	 */
	public static final HashMap<String, ByteProcessor> mapChannels(ImageProcessor image) {
		if (!(image instanceof ColorProcessor)) 
		{
			throw new IllegalArgumentException("Requires an instance of ColorProcessor");
		}
		
		// size of input image
		int width = image.getWidth();
		int height = image.getHeight();
		int size = width * height;

		// Extract red, green and blue components
		byte[] redArray = new byte[size];
		byte[] greenArray = new byte[size];
		byte[] blueArray = new byte[size];
		((ColorProcessor) image).getRGB(redArray, greenArray, blueArray);

		// create image processor for each channel
		ByteProcessor red = new ByteProcessor(width, height, redArray, null);
		ByteProcessor green = new ByteProcessor(width, height, greenArray, null);
		ByteProcessor blue  = new ByteProcessor(width, height, blueArray, null);

		// concatenate channels into a new collection
		HashMap<String, ByteProcessor> map = new HashMap<String, ByteProcessor>(3);
		map.put("red", red);
		map.put("green", green);
		map.put("blue", blue);
		
		return map;
	}

	/**
	 * Creates a new ColorProcessor from a collection of three channels.
	 * 
	 * @param channels
	 *            a collection containing the red, green and blue channels
	 * @return the color image corresponding to the concatenation of the three
	 *         channels
	 * @throws IllegalArgumentException
	 *             if the collection contains less than three channels
	 */
	public static final ColorProcessor mergeChannels(Collection<ImageProcessor> channels) {
		// check validity of input
		if (channels.size() < 3)
			throw new IllegalArgumentException("Requires at least three channels in the collection");
		
		// extract each individual channel
		Iterator<ImageProcessor> iterator = channels.iterator();
		ImageProcessor red = iterator.next();
		ImageProcessor green = iterator.next();
		ImageProcessor blue = iterator.next();

		// call helper function
		return mergeChannels(red, green, blue);
	}

	/**
	 * Creates a new ColorProcessor from the red, green and blue channels.
	 * Each channel must be an instance of ByteProcessor.
	 *  
	 * @return the color image corresponding to the concatenation of the three
	 *         channels
	 * @throws IllegalArgumentException
	 *             if one of the channel is not an instance of ByteProcessor
	 */
	public static final ColorProcessor mergeChannels(ImageProcessor red, 
			ImageProcessor green, ImageProcessor blue) {
		// check validity of input
		if (!(red instanceof ByteProcessor))
			throw new IllegalArgumentException("Input channels must be instances of ByteProcessor");
		if (!(green instanceof ByteProcessor))
			throw new IllegalArgumentException("Input channels must be instances of ByteProcessor");
		if (!(blue instanceof ByteProcessor))
			throw new IllegalArgumentException("Input channels must be instances of ByteProcessor");
		
		// Extract byte array of each channel
		byte[] redArray 	= (byte[]) red.getPixels();
		byte[] greenArray 	= (byte[]) green.getPixels();
		byte[] blueArray 	= (byte[]) blue.getPixels();
		
		// get image size
		int width = red.getWidth();
		int height = red.getHeight();

		// create result color image
		ColorProcessor result = new ColorProcessor(width, height);
		result.setRGB(redArray, greenArray, blueArray);
		
		return result;	
	}
	
	public final static ImagePlus binaryOverlay(ImagePlus imagePlus, 
			ImagePlus maskPlus, Color color) {
		
		String newName = imagePlus.getShortTitle() + "-ovr";
		ImagePlus resultPlus;
		
		if (imagePlus.getStackSize() == 1)
		{
			ImageProcessor image = imagePlus.getProcessor();
			ImageProcessor mask = maskPlus.getProcessor();
			ImageProcessor result = binaryOverlay(image, mask, color);
			resultPlus = new ImagePlus(newName, result);
		} else
		{
			// get reference image stack
			ImageStack image = imagePlus.getStack();
			
			// convert image to gray8 if necessary
			if (imagePlus.getBitDepth() != 24) {
				double grayMin = imagePlus.getDisplayRangeMin();
				double grayMax = imagePlus.getDisplayRangeMax();
				image = adjustDynamic(image, grayMin, grayMax);
			}
			
			// get binary mask
			ImageStack mask = maskPlus.getStack();

			// overlay binary mask on original image
			ImageStack result = binaryOverlay(image, mask, color);
			resultPlus = new ImagePlus(newName, result);
		}
		
		// keep calibration of parent image
		resultPlus.copyScale(imagePlus);
		return resultPlus;
	}
	
	public final static ImageProcessor binaryOverlay(ImageProcessor refImage, 
			ImageProcessor mask, Color color) {
		if (refImage instanceof ColorProcessor) 
		{
			return binaryOverlayRGB(refImage, mask, color);
		}
		else
		{
			if (!(refImage instanceof ByteProcessor)) 
			{
				refImage = refImage.convertToByteProcessor();
			}
			return binaryOverlayGray8(refImage, mask, color);
		}
	}
	
	/**
	 * Assumes reference image contains a ByteProcessor.
	 */
	private final static ImageProcessor binaryOverlayGray8(ImageProcessor refImage, 
			ImageProcessor mask, Color color) {
		
		int width = refImage.getWidth(); 
		int height = refImage.getHeight(); 
		ColorProcessor result = new ColorProcessor(width, height);
		
		int value;
		int rgbValue = color.getRGB();
		
		// Iterate on image pixels, and choose result value depending on mask
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if(mask.get(x, y) == 0) {
					// choose value from reference image
					value = refImage.get(x, y);
					// convert grayscale to equivalent color
					value = (value & 0x00FF) << 16 | (value & 0x00FF) << 8 | (value & 0x00FF);
					result.set(x, y, value);

				} else {
					// set value to chosen color
					result.set(x, y, rgbValue);
				}
			}
		}
		
		return result;
	}
	
	
	/**
	 * Assumes reference image contains a ColorProcessor.
	 */
	private final static ImageProcessor binaryOverlayRGB(ImageProcessor refImage, 
			ImageProcessor mask, Color color) {
		
		int width = refImage.getWidth(); 
		int height = refImage.getHeight(); 
		ColorProcessor result = new ColorProcessor(width, height);
		
		int value;
		int rgbValue = color.getRGB();
		
		// Iterate on image pixels, and choose result value depending on mask
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if(mask.get(x, y) == 0) {
					// choose RGB value directly from reference image
					value = refImage.get(x, y);
					result.set(x, y, value);

				} else {
					// set value to chosen color
					result.set(x, y, rgbValue);
				}
			}
		}
		
		return result;
	}

	public final static ImageStack binaryOverlay(ImageStack refImage, 
			ImageStack mask, Color color) {
		int sizeX = refImage.getWidth(); 
		int sizeY = refImage.getHeight(); 
		int sizeZ = refImage.getSize();
		
		int bitDepth = refImage.getBitDepth();
		
		ImageStack result = ImageStack.create(sizeX, sizeY, sizeZ, 24);
	
		int intVal;
		int rgbValue = color.getRGB();
				
		// for 16 and 32 bit images, compute gray level extent
		double vmin = Double.MAX_VALUE, vmax = Double.MIN_VALUE;
		if (bitDepth == 16 || bitDepth == 32) 
		{
			for (int z = 0; z < sizeZ; z++) {
				for (int y = 0; y < sizeY; y++) {
					for (int x = 0; x < sizeX; x++) {
						double value = refImage.getVoxel(x, y, z);
						vmin = Math.min(vmin, value);
						vmax = Math.max(vmax, value);
					}
				}
			}
			System.out.println("vmin= " + vmin + "  vmax= " + vmax);
		}
		
		// Iterate on image voxels, and choose result value depending on mask
		for (int z = 0; z < sizeZ; z++) {
			for (int y = 0; y < sizeY; y++) {
				for (int x = 0; x < sizeX; x++) {
					// For voxels in mask, apply the color of the background 
					if (mask.getVoxel(x, y, z) > 0) {
						result.setVoxel(x, y, z, rgbValue);
						continue;
					}
					
					switch (bitDepth) {
					case 8:
						// convert grayscale to equivalent color
						intVal = (int) refImage.getVoxel(x, y, z);
						intVal = (intVal & 0x00FF) << 16 | (intVal & 0x00FF) << 8
								| (intVal & 0x00FF);
						result.setVoxel(x, y, z, intVal);
						break;
						
					case 16:
					case 32:
						// convert grayscale to equivalent color
						double value = refImage.getVoxel(x, y, z);
						intVal = (int) (255 * (value - vmin) / (vmax - vmin));
						intVal = (intVal & 0x00FF) << 16 | (intVal & 0x00FF) << 8
								| (intVal & 0x00FF);
						result.setVoxel(x, y, z, intVal);
						break;

					case 24:
						// directly copy color code (after double conversion
						// through double...)
						result.setVoxel(x, y, z, refImage.getVoxel(x, y, z));
						break;

					default:
					}
				}
			}
		}
		
		return result;
	}

	/**
	 * Returns a new instance of ImageStack containing ByteProcessors such that
	 * display range is specified by vmin and vmax.
	 * 
	 * @param image input image, that can be 8, 16 or 32 bits
	 * @param vmin value that will correspond to 0 in new image
	 * @param vmax value that will correspond to 255 in new image
	 */
	private static final ImageStack adjustDynamic(ImageStack image, double vmin, double vmax)
	{
		// get image size
		int sizeX = image.getWidth(); 
		int sizeY = image.getHeight(); 
		int sizeZ = image.getSize();

		// create result image
		ImageStack result = ImageStack.create(sizeX, sizeY, sizeZ, 8);

		// Iterate on image voxels, and choose result value depending on mask
		for (int z = 0; z < sizeZ; z++)
		{
			for (int y = 0; y < sizeY; y++)
			{
				for (int x = 0; x < sizeX; x++)
				{
					// linearly interpolates new value
					double value = image.getVoxel(x, y, z);
					value = 255 * (value - vmin) / (vmax - vmin);
					value = Math.max(Math.min(value, 255), 0);
					result.setVoxel(x, y, z, value);
				}
			}
		}
		
		return result;
	}
	
}
