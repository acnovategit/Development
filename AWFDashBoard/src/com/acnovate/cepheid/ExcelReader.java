/**
 * 
 */

package com.acnovate.cepheid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;



/**
 * @author Anup
 *
 */
/**
 * @author hlp_anupkha1
 *
 * 
 */
public class ExcelReader {

    FileInputStream file;
    XSSFWorkbook workbook;
    XSSFSheet sheet;
    FileOutputStream excelout;
    CellStyle style;
    Font font;
    PropertiesHandler prop;
    private static LinkedHashMap<Integer, String[]> data = new LinkedHashMap<>();

    public ExcelReader() {
    prop = new PropertiesHandler();
    }

    /**
     * Paramterized Constructor to Initialize Execl File
     * 
     * @param sFileName
     */
    public ExcelReader(String sFileName) {
	try {
	    file = new FileInputStream(new File(sFileName));
	    // Create Workbook instance holding reference to .xlsx file
	    workbook = new XSSFWorkbook(file);
	    // Get first/desired sheet from the workbook
	    sheet = workbook.getSheetAt(0);
	}
	catch (FileNotFoundException e) {
	    e.printStackTrace();
	}
	catch (IOException e) {
	    e.printStackTrace();
	}
    }

    /**
     * Function : readMaterialWayList - Reads the Material Ways and stores in an
     * Arraylist
     * 
     * @param sFileName
     * @param MatWayList
     * @return List of Material Ways
     */
    public List<String> readMaterialWayList(String sFileName) {
	List<String> MatWayList = new ArrayList<String>();
	String sCellValue;
	DataFormatter formatter = new DataFormatter(Locale.US);
	try {

	    file = new FileInputStream(new File(sFileName));
	    // Create Workbook instance holding reference to .xlsx file
	    workbook = new XSSFWorkbook(file);
	    // Get first/desired sheet from the workbook
	    sheet = workbook.getSheetAt(0);

	    // Iterate through each rows one by one
	    Iterator<Row> rowIterator = sheet.iterator();
	    Row row = rowIterator.next();

	    while (rowIterator.hasNext()) {
		row = rowIterator.next();
		// For each row, iterate through all the columns
		Iterator<Cell> cellIterator = row.cellIterator();
		while (cellIterator.hasNext()) {
		    Cell cell = cellIterator.next();
		    sCellValue = formatter.formatCellValue(cell);

		    if (!((sCellValue == null) || (sCellValue == "")))
			MatWayList.add(sCellValue);
		}
	    }
	    file.close();

	}
	catch (Exception e) {
	    e.printStackTrace();
	}
	return MatWayList;

    }

   

    public static String sConvertToCommaDelimited(String[] list) {
	StringBuffer ret = new StringBuffer("");
	for (int i = 0; list != null && i < list.length; i++) {
	    ret.append(list[i]);
	    if (i < list.length - 1) {
		ret.append(',');
	    }
	}
	return ret.toString();
    }

    /**
     * Function : readMatMappin - Reads the Material Ways and stores in an
     * Arraylist
     * 
     * @param sFileName
     * @param FilName
     *            to read from
     * @return List of Material Ways
     */
    public List<Map<String, String>> readMaterialMappingText(String sFileName) {
	String sCellValue;
	DataFormatter formatter = new DataFormatter(Locale.US);
	List<Map<String, String>> inputFile = new ArrayList<Map<String, String>>();
	try {

	    file = new FileInputStream(new File(sFileName));
	    // Create Workbook instance holding reference to .xlsx file
	    workbook = new XSSFWorkbook(file);
	    // Get first/desired sheet from the workbook
	    sheet = workbook.getSheetAt(0);

	    // Iterate through each rows one by one
	    Iterator<Row> rowIterator = sheet.iterator();
	    while (rowIterator.hasNext()) {
		Row row = rowIterator.next();

		Map<String, String> inputMap = new LinkedHashMap<String, String>();

		// For each row, iterate through all the columns
		Iterator<Cell> cellIterator = row.cellIterator();
		while (cellIterator.hasNext()) {
		    Cell cell = cellIterator.next();
		    sCellValue = formatter.formatCellValue(cell); // cell.getStringCellValue();

		    if (cell.getColumnIndex() == 0) {
			inputMap.put("Input Material ID", sCellValue);

		    }
		    else if (cell.getColumnIndex() == 1) {
			inputMap.put("Input Structure Name", sCellValue);

		    }
		    else if (cell.getColumnIndex() == 2) {
			inputMap.put("New Material ID", sCellValue);

		    }
		}

		if (!(inputFile.contains(inputMap))) {
		    inputFile.add(inputMap);
		}

	    }

	    file.close();

	}
	catch (Exception e) {
	    e.printStackTrace();
	}
	return inputFile;

    }

   

    /**
     * Generate xls as log file
     * 
     */
    public void createXLSFile() {
	workbook = new XSSFWorkbook();
	sheet = workbook.createSheet("Workflow_Out");

	

	try {

	    Date date = new Date();
	    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss-SSS");
	    final String pathPattern =prop.getPropertyValue("report.outfile.path") + df.format(date) + ".xlsx";
	    excelout = new FileOutputStream(new File(pathPattern));
        
	}
	catch (IOException e) {
	    e.printStackTrace();
	}

    }

    /**
     * this function writes data to xls based on passed String array
     * 
     * @param data
     * @throws IOException
     */
    public void writeData(LinkedHashMap<Integer, String[]> data) throws IOException {
	Set<Integer> keyset = data.keySet();
	int rownum = 0;
	for (Integer key : keyset) {
	    Row row = workbook.getSheetAt(0).createRow(rownum++);
	    String[] objArr = data.get(key);
	    int cellnum = 0;
	    for (String obj : objArr) {
		Cell cell = row.createCell(cellnum++);
		cell.setCellValue(obj);
		if (rownum == 1)
		    cell.setCellStyle(style);
		else
			{
			vCreateStylesRow();
			cell.setCellStyle(style);
			
			}
	    }
	}
	try {
	    workbook.write(excelout);
	}
	catch (FileNotFoundException e) {
	    e.printStackTrace();
	}
	catch (IOException e) {
	    e.printStackTrace();
	}

	excelout.close();

    }

    public void vCreateStylesHeader() {

	style = workbook.createCellStyle();
	style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
	style.setFillPattern(CellStyle.SOLID_FOREGROUND);
	font = workbook.createFont();
	font.setColor(IndexedColors.DARK_BLUE.getIndex());

	style.setFont(font);
	sheet.setColumnWidth(0, 3000);
	sheet.setColumnWidth(1, 4000);
	sheet.setColumnWidth(2, 6000);
	sheet.setColumnWidth(3, 9000);
	sheet.setColumnWidth(4, 3000);
	sheet.setColumnWidth(5, 2000);
	sheet.setColumnWidth(6, 9000);
	sheet.setColumnWidth(7, 9000);
	sheet.setColumnWidth(8, 7000);
	style.setBorderLeft(CellStyle.BORDER_THICK);
	style.setBorderRight(CellStyle.BORDER_THICK);
	style.setBorderTop(CellStyle.BORDER_THICK);
	style.setBorderBottom(CellStyle.BORDER_THICK);

	/* We will use IndexedColors to specify colors to the border */
	/* bottom border color */
	style.setBottomBorderColor(IndexedColors.BLACK.getIndex());
	/* Top border color */
	style.setTopBorderColor(IndexedColors.BLACK.getIndex());
	/* Left border color */
	style.setLeftBorderColor(IndexedColors.BLACK.getIndex());
	/* Right border color */
	style.setRightBorderColor(IndexedColors.BLACK.getIndex());

    }

    public void vCreateStylesRow() {

	style = workbook.createCellStyle();
	style.setFillForegroundColor(IndexedColors.WHITE.getIndex());
	style.setFillPattern(CellStyle.SOLID_FOREGROUND);
	font = workbook.createFont();
	font.setColor(IndexedColors.BLACK.getIndex());
	style.setFont(font);
	
	
	style.setBorderLeft(CellStyle.BORDER_THIN);
	style.setBorderRight(CellStyle.BORDER_THIN);
	style.setBorderTop(CellStyle.BORDER_THIN);
	style.setBorderBottom(CellStyle.BORDER_THIN);
	
	
	style.setBottomBorderColor(IndexedColors.BLACK.getIndex());
	/* Top border color */
	style.setTopBorderColor(IndexedColors.BLACK.getIndex());
	/* Left border color */
	style.setLeftBorderColor(IndexedColors.BLACK.getIndex());
	/* Right border color */
	style.setRightBorderColor(IndexedColors.BLACK.getIndex());
	
	
    }

}
