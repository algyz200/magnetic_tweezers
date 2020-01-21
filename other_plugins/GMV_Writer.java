import ij.*;
import ij.plugin.filter.*;
import ij.measure.*;
import ij.process.*;
import ij.gui.*;
import ij.io.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;

// This plugin writes GMV format video data files using Random Access Method:

// Written by Justin E. Molloy, NIMR, January 2011
// With help and advice from Asif Tamuri, NIMR
// Contact: jmolloy@nimr.mrc.ac.uk 
// MRC National Institute for Medical Research, Mill Hill, LONDON

// Each video frame has a 48 (88) byte header.
// The frame size is determind by the "bit_pix" value
// If <=8 then the next frame 1-byte per pixel 
// if >8 then 2-byte (short) per pixel
// The program always creates a 2-byte (short) stack.
// All GMV frame header info is retrieved (where possible) from 
// the slice label - if this has been corrupted (several plugins destroy
// the data!); then the header is reconstructed from the Properties/Info fields.

//Algirdas Toleikis, modified March, 2013. add_info==1 -> stageXnm, stageYnm, focusZnm

public class GMV_Writer extends ImagePlus implements PlugInFilter {

// JAVA File I/O and IJ image & stack handling things
	private		ImagePlus	imp;
	private		ImageStack 	stack;
	private		RandomAccessFile raFile;
	private 	File file;

	private		String fileDir="";
	private		String fileName="";
	private		String path="";
	private		String msg="";
    	private		String GMVtag = "GMV:";	// "GMV" reserved word starts data section
	private		String sliceLabel = "";	// Concatenated string to hold the GMV frame variables
	private		char cr = (char)10;	// Carriage return character to stop the GMV variables 
						// being displayed in the "Info bar" note /n would do also!

// GMV file frame header information
	private		int leftx;		// 0->3			0
	private		int width;		// 4->7			1
	private		int topy;		// 8->11		2
	private		int height;		// 12->15		3
	private		int exp_in_ms;		// 16->19		4
	private		int fr_size;		// 20->23		5
	private		int fr_time;		// 24->27		6

 	private		float x_nm_pixels;	// 28->31		7
	private		float y_nm_pixels;	// 32->35		8

						// note: the byte variables are held in IJ as ints 
						// because they are unsigned!
	private		int igain;		// 36			9
	private		int vgain;		// 37			10
	private		int bit_pix;		// 38			11
	private		int bin;			// 39			12
	private		int byte_info;		// 40			13
	private		int add_info;		// 41			14
	
	private		short laser_power;	// 42->43		15
	private		short temperature;	// 44->45		16
	private		short illum_time;	// 46->48		17

//<added by AT
	private		int stageXnm;		//49-52		18
	private		int stageYnm;		//53-56		19
	private		int focusZnm;		//57-60		20
	
	private		int dummy_int1;	//61-64		21
	private		int dummy_int2;	//65-68		22
	private		int dummy_int3;	//69-72		23
	
	private		float dummy_float1;	//73-76		24
	private		float dummy_float2;	//77-80		25
	private		float dummy_float3;	//81-84		26
	private		float dummy_float4; 	//85-88		27
//>added by AT

// some other variables that we might need!
	private		long fileSize, pos, nSlices, numframes, header_size;
	private		int c, i, j, slice, frameSize, startTime;
	private		double progr=0;

//============================= This is the "Setup" call =============================
// This is called first by the ImageJ PluginFilter interface

public int setup(String arg, ImagePlus imp) {
	if (imp == null) {
		IJ.noImage();
		return DONE;
	}
		this.imp = imp;			// "this." makes local "imp" synonymous with passed imp
		return (DOES_8G + DOES_16 + NO_CHANGES);
}

//============================= This is the main program =============================
// The main program runs following successful outcome from Setup.

public void run(ImageProcessor ip) {

	try {
		saveGMV();			// This calls the file saving routine......
	} catch (Exception e) {
		msg  = e + ":"+ e.getMessage();
		IJ.showMessage("GMV Writer", "An error occurred:" + msg);
	} finally {
		try {
			raFile.close();		// Close the random access file
		} catch (Exception e) {
		}
	}
}

//============================ This is the "file Writer" routine =========================

public void saveGMV() throws Exception, IOException {

	SaveDialog sd = new SaveDialog("Save as GMV...", imp.getTitle(), ".gmv");
	String fileName = sd.getFileName();
	if (fileName == null)
		return;
	String fileDir = sd.getDirectory();
	File file = new File(fileDir + fileName);

	stack = imp.getStack();
 	nSlices = stack.getSize();


// create the full filename+path
	IJ.showProgress (progr);					// Show "Progress bar"
	IJ.showStatus("Saving "+nSlices+" frames to: " + fileName);	
	try {
		raFile = new RandomAccessFile(file, "rw");		// Open a randomaccess, r/w, file
	} catch (IOException e) {
		IJ.showMessage("Exception: " + e);
		return;
	}
	
// Note: we need to set some values here.. as they might have been changed by ImageJ operations

	makeGlobalHeader();

	slice=1;
	while (slice<=nSlices){

	try {	
		getHeader();
		saveHeader();
	
	if (imp.getType() == ImagePlus.GRAY16)  {
//	IJ.showMessage("Into 16-bit saver "+"bit_pix ="+bit_pix);	//Test
		saveFrame16();

	} else {
//	IJ.showMessage("Into 8-bit saver "+"bit_pix ="+bit_pix);	//Test
		saveFrame8();	
	}

	} catch (IOException e) {
		IJ.showMessage("Exception: " + e);
		return;
	}

	slice++;
	progr = (slice/nSlices);
	IJ.showProgress (progr);

	}

	IJ.showProgress(1);					// done kill progress bar

}


// ================ Create Global GMV header Info ==================
// These are the "invariant" variables!
//
public void makeGlobalHeader ()  { 

// Grab whatever we can from the FileInfo and Calibration fields
// and create default values where necessary

	Calibration cal = imp.getCalibration();
	leftx =		(int) cal.xOrigin;
	width =		(int) stack.getWidth();
	topy =		(int) cal.yOrigin;
	height =	(int) stack.getHeight();

	double dummy = 0;
	dummy = cal.frameInterval;
	if (dummy == 0) {
		dummy = 0.04;
	}
	exp_in_ms = 	(int) (dummy * 1000);

	fr_size = 	(int) width*height;	

			dummy = cal.pixelWidth;
	x_nm_pixels = 	(float) dummy * 1000;
	 		dummy = cal.pixelHeight;
	y_nm_pixels = 	(float) dummy * 1000;

	igain =		(byte) (0);
	vgain =	(byte) (0);
	bit_pix =	(byte) (10);
	bin = 		(byte) (1);
	byte_info = 	(byte) (128);
	add_info = 	(byte) (0);

	laser_power = 	(short) 0;
	temperature = 	(short) 230;
	illum_time = 	(short) exp_in_ms;

// If we have a slice Label with valid GMV info then load that up.

	sliceLabel = " " + stack.getSliceLabel(1);	// avoid "null" string error
	if (sliceLabel.indexOf("GMV:") >1){		// check for "GMV:" tag.
	
	sliceLabel = sliceLabel.substring(sliceLabel.indexOf("GMV:")+4);	// truncate the label
	
	String[] values = sliceLabel.split(",");	// slice and dice the slice label!

	leftx =		Integer.parseInt (values[0]);	
	topy =		Integer.parseInt (values[2]);
	exp_in_ms =	Integer.parseInt (values[4]);

	x_nm_pixels =	Float.valueOf (values[7]);
	y_nm_pixels =	Float.valueOf (values[8]);

	igain =		(byte) (Integer.parseInt(values[9]));
	vgain =	(byte) (Integer.parseInt (values[10]));
	bit_pix =	(byte) (Integer.parseInt(values[11]));

	bin =		(byte) (Integer.parseInt(values[12]));
	byte_info =	(byte) (Integer.parseInt(values[13]));
	add_info =	(byte) (Integer.parseInt (values[14]));

	laser_power =	Short.parseShort (values[15]);
	temperature =	Short.parseShort (values[16]);
	illum_time =	Short.parseShort (values[17]);
	
	if (add_info==1) 
	{
	stageXnm =	Integer.parseInt (values[18]);
	stageYnm =	Integer.parseInt (values[19]);
	focusZnm = 	Integer.parseInt (values[20]);

	dummy_int1 =	Integer.parseInt (values[21]);
	dummy_int2 =	Integer.parseInt (values[22]);
	dummy_int3 =	Integer.parseInt (values[23]);

	dummy_float1 =	Float.valueOf (values[24]);
	dummy_float1 =	Float.valueOf (values[25]);
	dummy_float1 =	Float.valueOf (values[26]);
	dummy_float1 =	Float.valueOf (values[27]);
	}

	}

// Check for dud values...

	if ((imp.getType() == ImagePlus.GRAY16) & (bit_pix < 9))  {
		bit_pix = (byte) 9;
	}
	if ((imp.getType() == ImagePlus.GRAY8) & (bit_pix >8 ))  {
		bit_pix = (byte) 8;
	}

	if (x_nm_pixels <0.01) {			// Something wrong with the xcalib!
		x_nm_pixels = (float) 100;
	}
	if (y_nm_pixels <0.01) {
		y_nm_pixels = (float) 100;		// Something wrong with the ycalib!
	}

// return

}


// ======================= get Header  ==================

public void getHeader ()  { 
// get - or generate frame specific information for the GMV header

	sliceLabel = " " + stack.getSliceLabel(slice);	// avoid "null" string error by adding a space!

	if (sliceLabel.indexOf("GMV:") <1){

		fr_time = (int) (exp_in_ms * (slice-1)); // best guess at individual frame times

	} else {

	sliceLabel = sliceLabel.substring(sliceLabel.indexOf("GMV:")+4);
	String[] values = sliceLabel.split(",");

	exp_in_ms = 	Integer.parseInt (values[4]);
	fr_time = 	Integer.parseInt (values[6]);

	igain =		(byte) (Integer.parseInt(values[9]));
	vgain =	(byte) (Integer.parseInt (values[10]));
	bit_pix =	(byte) (Integer.parseInt(values[11]));

	bin =		(byte) (Integer.parseInt(values[12]));
	byte_info =	(byte) (Integer.parseInt(values[13]));
	add_info =	(byte) (Integer.parseInt (values[14]));




	laser_power = Short.parseShort (values[15]);
	temperature = Short.parseShort (values[16]);
	illum_time = 	Short.parseShort (values[17]);
	
	if (add_info==1) 
	{
	stageXnm =	Integer.parseInt (values[18]);
	stageYnm =	Integer.parseInt (values[19]);
	focusZnm = 	Integer.parseInt (values[20]);

	dummy_int1 =	Integer.parseInt (values[21]);
	dummy_int2 =	Integer.parseInt (values[22]);
	dummy_int3 =	Integer.parseInt (values[23]);

	dummy_float1 =	Float.valueOf (values[24]);
	dummy_float1 =	Float.valueOf (values[25]);
	dummy_float1 =	Float.valueOf (values[26]);
	dummy_float1 =	Float.valueOf (values[27]);
	}	
	
	}

	if ((imp.getType() == ImagePlus.GRAY16) & (bit_pix < 9))  {
		bit_pix = (byte) 9;
	}
	if ((imp.getType() == ImagePlus.GRAY8) & (bit_pix >8 ))  {
		bit_pix = (byte) 8;
	}
	if (fr_time <1) {
		fr_time = (int) (0.04 * 1000 * (slice-1));
	}

}


// ======================= save Header  ==================

public void saveHeader () throws Exception, IOException { 

	try {
	raFile.writeInt(swap(leftx));
	raFile.writeInt(swap(width));
	raFile.writeInt(swap(topy));
	raFile.writeInt(swap(height));
	raFile.writeInt(swap(exp_in_ms));
	raFile.writeInt(swap(fr_size));
	raFile.writeInt(swap(fr_time));

	raFile.writeFloat(swap(x_nm_pixels));
	raFile.writeFloat(swap(y_nm_pixels));

    	raFile.writeByte(igain);
   	raFile.writeByte(vgain);
   	raFile.writeByte(bit_pix);
   	raFile.writeByte(bin);
   	raFile.writeByte(byte_info);
   	raFile.writeByte(add_info);

	raFile.writeShort(swap(laser_power));
	raFile.writeShort(swap(temperature));
	raFile.writeShort(swap(illum_time));

	if (add_info==1) 
	{
	raFile.writeInt(swap(stageXnm));
	raFile.writeInt(swap(stageYnm));
	raFile.writeInt(swap(focusZnm));

	raFile.writeInt(swap(dummy_int1));
	raFile.writeInt(swap(dummy_int2));
	raFile.writeInt(swap(dummy_int3));
	
	raFile.writeFloat(swap(dummy_float1));
	raFile.writeFloat(swap(dummy_float2));
	raFile.writeFloat(swap(dummy_float3));
	raFile.writeFloat(swap(dummy_float4));
	}

	} catch (IOException e) {
		IJ.showMessage("Exception: " + e);
	}
}

// ======================= saveFrame 16-bits  ==================

public void saveFrame16 () throws Exception, IOException { 

	short[] pix16 = (short []) stack.getPixels(slice);

	byte[] buf2byte = new byte [fr_size*2];

	j=0;
	for (i = 0 ; i< fr_size; i++){
		buf2byte[j++] = (byte) (pix16[ i ] & 0xff);
		buf2byte[j++] = (byte) (pix16[ i ] >> 8);
	}

	try {

		raFile.write(buf2byte);

	} catch (IOException e) {
		IJ.showMessage("Exception: " + e);
	}

}

// ======================= saveFrame 8-bits  ==================

public void saveFrame8 () throws Exception, IOException { 

	byte[] pix8 = (byte[]) stack.getPixels(slice);

	try {
		raFile.write(pix8);

	} catch (IOException e) {
		IJ.showMessage("Exception: " + e);
	}

}


//============================= Utility routines =================================
//			"Endian" swapping utilities:   
//		(C) 2004 Geotechnical Software Services


public static short swap (short value)
  {
    int b1 = value & 0xff;
    int b2 = (value >> 8) & 0xff;
    return (short) (b1 << 8 | b2 << 0);
  }

public static int swap (int value)
  {
    int b1 = (value >>  0) & 0xff;
    int b2 = (value >>  8) & 0xff;
    int b3 = (value >> 16) & 0xff;
    int b4 = (value >> 24) & 0xff;
    return b1 << 24 | b2 << 16 | b3 << 8 | b4 << 0;
  }

public static long swap (long value)
  {
    long b1 = (value >>  0) & 0xff;
    long b2 = (value >>  8) & 0xff;
    long b3 = (value >> 16) & 0xff;
    long b4 = (value >> 24) & 0xff;
    long b5 = (value >> 32) & 0xff;
    long b6 = (value >> 40) & 0xff;
    long b7 = (value >> 48) & 0xff;
    long b8 = (value >> 56) & 0xff;
    return b1 << 56 | b2 << 48 | b3 << 40 | b4 << 32 |
           b5 << 24 | b6 << 16 | b7 <<  8 | b8 <<  0;
  }
  
public static float swap (float value)
  {
    int intValue = Float.floatToIntBits (value);
    intValue = swap (intValue);
    return Float.intBitsToFloat (intValue);
  }

public static double swap (double value)
  {
    long longValue = Double.doubleToLongBits (value);
    longValue = swap (longValue);
    return Double.longBitsToDouble (longValue);
  }

 public static void swap (short[] array)
  {
    for (int i = 0; i < array.length; i++)
      array[i] = swap (array[i]);
  }

// convert "signed byte" - stored locally as an "int" to an "unsigned byte"!
// it dirty but worky.

//public static int unsigned (int value)
// {
//     int b = value;
//     if (b >127) {
//        b = b-255;
//      }
// return (b);
//  }


}

