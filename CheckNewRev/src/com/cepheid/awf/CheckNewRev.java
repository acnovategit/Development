package com.cepheid.awf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.Header;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.IAgileSession;
import com.agile.api.IAttachmentFile;
import com.agile.api.ICell;
import com.agile.api.IChange;
import com.agile.api.IDataObject;
import com.agile.api.IFileFolder;
import com.agile.api.IItem;
import com.agile.api.INode;
import com.agile.api.IRow;
import com.agile.api.ITable;
import com.agile.api.ItemConstants;
import com.agile.px.ActionResult;
import com.agile.px.EventActionResult;
import com.agile.px.IEventAction;
import com.agile.px.IEventInfo;
import com.agile.px.IObjectEventInfo;
import com.agile.util.GenericUtilities;

public class CheckNewRev implements IEventAction {

	static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(CheckNewRev.class);
	public static final String CHECKEDOUT_FILEPATH = "/ora01/APP/agilevault/";
	public static final String STAGE_FILEPATH = "/ora01/APP/agilevault/staging";
	Iterator<?> attachmentsTableIterator;
	ArrayList<String> fileNameList = new ArrayList<String>();
	String concatenatedMessages = "";
	String msg = "";
	int i = 1, flag = 0;

	/**Function which is invoked from the Event PX.
	 * Iterates the attachments of each of the affected items and checks the revision in the document matches
	 * with that mentioned in the NewRev attribute.
	 * returns - Exception if the rev does not match in the form of a concatenated string of 
	 * error messages for all items and attachments.
	 *  Success message if the rev matches.
	 */
	public EventActionResult doAction(IAgileSession session, INode node, IEventInfo eventinfo) {

		GenericUtilities.initializeLogger(session);
		ActionResult actionResult = new ActionResult();
		try {
			InputStream inStream = null;
			IRow row = null;
			String fileName = "";
			IItem part = null;
			ICell newRev = null;
			IFileFolder fileFolder = null;
			File file = null;

			IObjectEventInfo info = (IObjectEventInfo) eventinfo;
			IChange eco = (IChange) info.getDataObject();
			ITable affectedItems = eco.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);

			Iterator<?> itAffectedItemsIterator = affectedItems.iterator();

			while (itAffectedItemsIterator.hasNext()) {
				row = (IRow) itAffectedItemsIterator.next();
				part = (IItem) row.getReferent();
				logger.info("Part is :" + part);
				newRev = row.getCell(ChangeConstants.ATT_AFFECTED_ITEMS_NEW_REV);
				logger.info("New Revision is :" + newRev);
                if (newRev.toString().equals(""))
                {
                	throw new Exception("New Revision is null");
                }
				// Iterate Attachments Table
				ITable attachmentsTable = part.getTable(ItemConstants.TABLE_ATTACHMENTS);
				attachmentsTableIterator = attachmentsTable.iterator();

				while (attachmentsTableIterator.hasNext()) {

					row = (IRow) attachmentsTableIterator.next();
					fileFolder = (IFileFolder) row.getReferent();
					logger.info("File Folder is :" + fileFolder);
					fileName = row.getName();
					logger.info("File Folder Name is :" + fileName);

					inStream = ((IAttachmentFile) row).getFile();
					try {
						String sFilePath = CHECKEDOUT_FILEPATH + fileName;
						file = new File(sFilePath);
						FileUtils.copyInputStreamToFile(inStream, file);
						logger.info("File is copied from InputStream");
						fileNameList.add(checkRevisionForEach(file, newRev, part));
						logger.info("function Execution ends for each document of " + part);

					} catch (IOException e) {
						e.printStackTrace();
						logger.error(e.getMessage());
						actionResult = new ActionResult(ActionResult.EXCEPTION, e);

					} catch (Exception e) {
						e.printStackTrace();
						logger.error(e.getMessage());
						actionResult = new ActionResult(ActionResult.EXCEPTION, e);

					}
				} // end of while attachmentsTableIterator
			} // end of while itAffectedItemsIterator

		} catch (APIException e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, e);
			e.printStackTrace();
			logger.error("Creation of Extension failed due to" + e.getMessage());
			return new EventActionResult(eventinfo, actionResult);
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(e.getMessage());
			actionResult = new ActionResult(ActionResult.EXCEPTION, e);
			return new EventActionResult(eventinfo, actionResult);
		}

		if(!fileNameList.isEmpty())
		{
			logger.info("filenamelist retrieved-------");
			
		try {

			for (i = 0; i < fileNameList.size();) {
					if (fileNameList.get(i) != "") {
						concatenatedMessages += "# ";
						concatenatedMessages += fileNameList.get(i);
						concatenatedMessages += "\t\t\t";
					flag =1;
					}
					i++;
				}
			logger.info("Concatenated Message = "+concatenatedMessages);
             

			if (flag == 1) {
					 Exception e = new Exception(concatenatedMessages);
						throw e;
			} else {
				actionResult = new ActionResult(ActionResult.STRING,
						"Part/ Document Revision Matches for all the Attachments");
			}

		} catch (Exception e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, e);
		}
	}
		logger.info("actionresult is: " + actionResult.toString());
		return new EventActionResult(eventinfo, actionResult);
	}

	
	/**
	 * Function which reads the Header of excel files and returns the header text
	 * @param file
	 * @return Header Data which is a string of data containing the entire header text
	 * @throws APIException 
	 */
	public String sGetHeaderDataForExcel(File file,IItem part) throws APIException
	{
		
		String sHeaderData = "";
		Workbook wb;
		Sheet sheet;
		try {
			
		FileInputStream fileInput = new FileInputStream(file);
        if(file.getName().toString().endsWith("xls"))
        {
        	logger.info("Creating workbook for XLS file");
        	wb = new HSSFWorkbook(fileInput); 
        	logger.info("Workbook created");
        }
        else 
        {
        	logger.info("Creating workbook for XLSX file");
        	wb = new XSSFWorkbook(fileInput); 
        	logger.info("Workbook created");
        }
        	
		for (int i=0; i<wb.getNumberOfSheets();i++)
		{    
		logger.info("inside for loop");	
		sheet =  wb.getSheetAt(i);
		logger.info("got the sheet at:"+i);	
        Header header = sheet.getHeader();  
        logger.info("got the header");
		sHeaderData = header.getRight();
		logger.info("Got Header data:"+sHeaderData);		
		Map<String, String> map = new HashMap<String, String>();
		
		if(sHeaderData ==null|| sHeaderData.equals("")||!(sHeaderData.contains(":"))||!(sHeaderData.contains("\n")))
			continue;
		String[] headerDataSplit = sHeaderData.split("\n");
		for (String s : headerDataSplit) {
			if (s.contains(":")) {
				String[] t = s.split(":");
				map.put(t[0].trim(), t[1].trim());
			}
		}

		for (String s : map.keySet()) {
			if(s.contains("Document Number"))
			{String str = "Document Number";
			map.put(str, map.get(s));
			}
			//logger.info(s + " is " + map.get(s));
		}
		// validate Document Number with Part Number
		String val = map.get("Document Number").trim();
		if (val.equals(part.getName())) {
			logger.info("Document Number Matches for " + file.getName());
			wb.close();
			return sHeaderData;
		} 
		
				
		}
		
		wb.close();
		}
		catch(IOException io)
		{
			logger.debug(io);
			io.printStackTrace();
		}
		catch(Exception e)
		{
			logger.debug(e);
		}
		sHeaderData ="";
		return sHeaderData;
		
	}
	
	/**
	 * Function which reads the Header of Docx files and returns the header text
	 * @param file
	 * @return Header Data which is a string of data containing the entire header text
	 */
		public String sGetHeaderDataForDocX(File file) {
		FileInputStream fis;
		String sHeaderData = null;
		try {
			fis = new FileInputStream(file);

			XWPFDocument xdoc;
			xdoc = new XWPFDocument(OPCPackage.open(fis));
			XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(xdoc);
			logger.info("Headerfooterpolicy created");
			// read header
			XWPFHeader header = policy.getDefaultHeader();
			sHeaderData = header.getText().toString();
		} catch (FileNotFoundException e) {
			logger.error(e);
			e.printStackTrace();
		} catch (InvalidFormatException e) {
			logger.error(e);
			e.printStackTrace();
		} catch (IOException e) {
			logger.error(e);
			e.printStackTrace();
		}

		return sHeaderData;

	}

	/**
	 * Function Checks if the document is doc, xls or xlsx and reads its header information of Revision
	 * @param file - File attachment whose header needs to be read
	 * @param newRev - New revision mentioned for the affected item on the AWF
	 * @param part - Part or the affected item object for generating appropriate and entire error messages.
	 * @return - If the Document number do not match, log an error message in logfile and return null
	 *           If the Document number match, but the revision do not match return an error message to the calling function
	 *           If the Document number match, and the revision matches, return null   
	 */
	public String checkRevisionForEach(File file, ICell newRev, IItem part) {
		logger.info("Inside checkRevisionForEach method");
		String message = "";
		String val = "";
		String headerData="";
		try {
			
			if (file.getName().endsWith("xls")||file.getName().endsWith("xlsx"))
			headerData = sGetHeaderDataForExcel(file,part);
			else if(file.getName().endsWith("docx"))
			headerData = sGetHeaderDataForDocX(file);
			else 
			return message;
			
		if(headerData.equals(""))
		return message;	
			
			logger.info("Header data in String" + headerData);

			Map<String, String> map = new HashMap<String, String>();
			String[] headerDataSplit = headerData.split("\n");
			for (String s : headerDataSplit) {
				if (s.contains(":")) {
					String[] t = s.split(":");
					map.put(t[0], t[1]);
				}
			}

			String sKey = null;
			  for (String s : map.keySet()) {
			  logger.info(s + " is " + map.get(s));
			  if (s.contains("Document Number"))
			 {
				sKey = s; 
			 }
			  }
			 
			// validate Document Number with Part Number
			
			  
			  val = map.get(sKey).trim();
			val = val.trim();
			logger.info("The read document number is:"+val+"|");
			
			if (val.equals(part.getName())) {
				logger.info("Document Number Matches for " + file.getName());
			} else {

				String errmessage = "Part/Document Number " + part.getName() + " not Matching with document number "
						+ val + " mentioned in the header of attachment " + file.getName() + "\n";
				logger.info(errmessage);
				return message;

			}
			// validate revision
			val = map.get("Rev").trim();
			val = val.trim();
			if (val.equals(newRev.toString())) {

				logger.info("Revision Matches with attachment " + file.getName());
			} else {
				message += "Revision for item " + part.getName()
						+ " do not match with that mentioned in the header of its attachment " + file.getName() + "\n";
				logger.info(message);
			}

		}

		catch (Exception e) {
			e.printStackTrace();
			logger.error("Creation of Extension failed due to" + e.getMessage());
		}

		return message;
	}

}

