import ij.*;
import ij.measure.*;
import ij.process.*;
import ij.plugin.*;
import ij.gui.*;
import ij.io.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;

// This plugin reads GMV format video data files using Random Access Method:
// Written by Justin E. Molloy, NIMR, January 2011
// With help and advice from Asif Tamuri, NIMR
// Contact: jmolloy@nimr.mrc.ac.uk 
// MRC National Institute for Medical Research, Mill Hill, LONDON

// Each video frame has a (48) 88 (after AT changes) byte header.
// The frame size is determind by the "bit_pix" value
// If <=8 then 1-byte per pixel if >8 then 2-byte (short) per pixel
// This makes reading a bit slow because each frame must be 
// checked for the format change before loading.
// The program always creates a 2byte (short) stack.
// All GMV frame header info is saved as a string in the slice label.

//
// To allow "HandleExtraFileTypes.class" to make ImageJ aware of this loader.
// You will need to add :
// 		// J. Molloy: Added GMV RandomAccessFile reader Jan-2011
//		// -----------------------------------------------------
//		// check if the file ends in .GMV
//		if (name.endsWith(".gmv")) {
//			return tryPlugIn("GMV_Reader", path);
//		}
//

//Algirdas Toleikis, modified March, 2013. add_info==1 -> stageXnm, stageYnm, focusZnm

public class GMV_Reader implements PlugIn{
// JAVA File I/O and IJ image & stack handling things
	private		ImagePlus	imp;
	private		ImageProcessor ip;
	private		ImageStack	stack = new ImageStack(0,0);
	private		RandomAccessFile raFile;
	private		File file;

	private		String fileDir="";
	private		String fileName="";
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
	private		int fr_size ;		// 20->23		5
	private		int fr_time ;		// 24->27		6

 	private		float x_nm_pixels;	// 28->31		7
	private		float y_nm_pixels;	// 32->35		8

						// note: the byte variables are held in IJ as ints 
						// because they are unsigned!
	private		int igain;		// 36			9
	private		int vgain ;		// 37			10
	private		int bit_pix;		// 38			11
	private		int bin;		// 39			12
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
	private		long fileSize, pos, nSlices, numframes;
	private		int c, i, j, slice, frameSize, startTime,blank, header_size;
	private		double progr=0;


//============================= This is the main program =============================

public void run(String arg) {

// All the try... catch thing have to do with IO error handling - covers most eventualities!
//
// This calls the file reading routine......
	try {
		loadGMV(arg);						// "arg" should be the default pathname
	} catch (Exception e) {
		msg  = e + ":"+ e.getMessage();
		IJ.showMessage("GMV Reader", "An error occurred:" + msg);
	} catch (OutOfMemoryError e) {
		IJ.showMessage("GMV Reader", "Out of memory " + stack.getSize() + " frames opened.");
	} finally {
		try {
			raFile.close();					// Close the random access file
		} catch (Exception e) {
		}
	}

}


//============================ This is the "file reader" routine =========================

public void loadGMV(String path) throws Exception, IOException {

	OpenDialog  sd = new OpenDialog("Select GMV File", path);	// Select a file to load from dialog

	fileName    = sd.getFileName();
	if (fileName == null)
		return;
	fileDir = sd.getDirectory();
	file = new File(fileDir + fileName);				// create the full filename+path
	IJ.showProgress (progr);					// Show "Progress bar"
	try {
		raFile = new RandomAccessFile(file, "r");		// Open as a randomaccess, read, file
		pos = raFile.getFilePointer();				// this should point at the start of the file.
		fileSize = raFile.length();				// total file size
		loadHeader();						// load up the first Header_size (48 by default) bytes of header
		raFile.seek(pos);					// reset the pointer to the start
		frameSize= width*height;
		startTime = fr_time;					// First frame time;
	} catch (IOException e) {
		IJ.showMessage("Exception: " + e);
	}

	if (bit_pix<=8){
		numframes = fileSize/(frameSize+header_size);			// approx. num of frames
	}else {
		numframes = fileSize/(frameSize*2+header_size);			// approx. num of frames
	}

	IJ.showStatus("Loading ~ "+numframes+" frames from: "+fileName);

	slice=0;							// current slice number
	progr=0;							// progress counter		
	ImageStack stack = new ImageStack(width, height);		// create a new ImageStack of size "w*h"
	byte[] buf = new byte[frameSize];				// byte buffer for random access read data
	byte[] buf2byte = new byte[frameSize*2];

// File loading loop
	while ((pos+header_size) < fileSize) {					// loop till EOF
	short[] pixels16 = new short[frameSize];			// local framestore array

	try{
		loadHeader();						// Load frame header information
	if (bit_pix<=8){	
		raFile.readFully(buf);					// read in the whole 8 bit frame
        		for (int i=0; i<frameSize; i++) {		// convert 8 bit to 16 bit "short" format 
			pixels16 [ i ] = (short) (buf[ i ] & 0xff);
			}
	} else {
		raFile.readFully(buf2byte);				// read in the whole 16 bit frame
		j=0;
		int b0, b1;
       		for (int i=0; i<frameSize; i++) {				
			b0 = ((buf2byte[ j++ ] & 0xff) << 0);		// 1st byte = low
			b1 = ((buf2byte[ j++ ] & 0xff) << 8);		// 2nd byte = high
			pixels16 [ i ] = (short) (b0+b1);
			}
	}
		ip = new ShortProcessor(width, height);
		ip.setPixels(pixels16);
		stack.addSlice(sliceLabel,ip);
		slice++;
		pos = raFile.getFilePointer();
	} catch (IOException e) {
		IJ.showMessage("Exception: " + e);
	}
	progr = (double) slice/numframes;
	IJ.showProgress(progr);
	}

	IJ.showProgress(1);						// done - kill progress bar
	ImagePlus imp = new ImagePlus(fileName, stack);			// Create new ImagePlus
	setFileProperties(imp);						// Set stack "Properties"
	imp.show();							// Show the stack
}

//================================================================================
// This is the "header reader"

public void loadHeader () throws Exception, IOException {

	leftx =		swap(raFile.readInt());			// 0->3			0
	leftx = 0;	// dumo this value! it screws up ImageJ things - e.g. ROI Manager
	width = 	swap(raFile.readInt());			// 4->7			1
	topy = 		swap(raFile.readInt());			// 8->11		2
	topy = 0;	// dumo this value! it screws up ImageJ things - e.g. ROI Manager
	height = 	swap(raFile.readInt());			// 12->15		3
	exp_in_ms = 	swap(raFile.readInt());			// 16->19		4
	fr_size = 	swap(raFile.readInt());			// 20->23		5
	fr_time = 	swap(raFile.readInt());			// 24->27		6

	x_nm_pixels = 	swap(raFile.readFloat());		// 28->31		7
	y_nm_pixels = 	swap(raFile.readFloat());		// 32->35		8

	igain =		raFile.readUnsignedByte();		// 36			9
	vgain = 	raFile.readUnsignedByte();		// 37			10
	bit_pix = 	raFile.readUnsignedByte();		// 38			11
	bin = 		raFile.readUnsignedByte();		// 39			12
	byte_info= 	raFile.readUnsignedByte();		// 40			13
	add_info= 	raFile.readUnsignedByte();		// 41			14
	
	laser_power = 	swap(raFile.readShort());		// 42->43		15
	temperature = 	swap(raFile.readShort());		// 44->45		16
	illum_time = 	swap(raFile.readShort());		// 46->48		17
    
	header_size = 48; // (default)	

        if ( add_info == 1 ) 
          { 
	stageXnm = swap(raFile.readInt());		//49-52		18
	stageYnm = swap(raFile.readInt());			//53-56		19
	focusZnm =  swap(raFile.readInt());		//57-60		20

            dummy_int1 = swap(raFile.readInt());		//61-64		21
            dummy_int2 = swap(raFile.readInt());		//65-68		22	
            dummy_int3 = swap(raFile.readInt());		//69-72		23
	
	dummy_float1 = swap(raFile.readFloat());		// 73->76		24
	dummy_float2 = swap(raFile.readFloat());		// 77->80		25
	dummy_float3 = swap(raFile.readFloat());		// 81->84		26
	dummy_float4 = swap(raFile.readFloat());		// 85->88		27

	header_size = 88;
          } 

// Create the text "sliceLabel" containing all parameters associated with this slice
	float absTime = fr_time-startTime;
	if ((absTime)>1000){
		absTime = absTime/1000;
		sliceLabel = absTime+" s";
	} else{
		sliceLabel = absTime+" ms";
	}
  	sliceLabel= sliceLabel+cr+GMVtag + leftx + ","+ width+","+ topy+","+ height +","+ exp_in_ms;
    	sliceLabel= sliceLabel+","+fr_size+","+ fr_time+ ","+ x_nm_pixels+","+ y_nm_pixels+","+igain;
    	sliceLabel= sliceLabel+ ","+ vgain+","+bit_pix+","+bin+","+byte_info+","+add_info;
	sliceLabel= sliceLabel+","+laser_power+","+temperature+","+illum_time;
	sliceLabel= sliceLabel+","+stageXnm+","+stageYnm+","+focusZnm;
	sliceLabel= sliceLabel+","+dummy_int1+","+dummy_int2+","+dummy_int3;
	sliceLabel= sliceLabel+","+dummy_float1+","+dummy_float2+","+dummy_float3+","+dummy_float4;
}

//================================================================================
public void setFileProperties (ImagePlus imp) { 

		Calibration cal = imp.getCalibration();

		cal.xOrigin = leftx;
		cal.yOrigin = topy;

		double dummy=0;
		dummy = x_nm_pixels/1000;
		cal.pixelWidth = dummy;
		dummy = y_nm_pixels/1000;
		cal.pixelHeight = dummy;

		dummy = exp_in_ms;
		dummy = dummy/1000;
		cal.pixelDepth = dummy;

		cal.frameInterval = dummy;
		cal.setUnit("um");

		imp.setCalibration(cal);
}


//============================= Utility routines =================================
//                    "Endian" swapping utilities:   (C) 2004 Geotechnical Software Services

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

}
