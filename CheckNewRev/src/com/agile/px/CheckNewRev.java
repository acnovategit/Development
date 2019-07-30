package com.agile.px;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.slf4j.Logger;

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
import com.agile.util.CommonUtil;

public class CheckNewRev implements ICustomAction {
	static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(CheckNewRev.class);
	
	public static final String CHECKEDOUT_FILEPATH = "C:\\AgileVault\\";
	public static final String STAGE_FILEPATH = "C:\\AgileVault\\staging\\";
	Iterator<?> attachmentsTableIterator;
	ArrayList<String> fileNameList = new ArrayList<String>();
	String concatenatedMessages = "";
	String msg = "";
	int i = 1,flag=0;

	public ActionResult doAction(IAgileSession session, INode node, IDataObject dataObject) {
		CommonUtil.initAppLogger(CheckNewRev.class, session);
		ActionResult actionResult = new ActionResult();
		try {

			InputStream inStream = null;
			IRow row = null;

			String fileName = "";
			IItem part = null;
			ICell newRev = null;
			IFileFolder fileFolder = null;

			File file = null;
			IChange eco = (IChange) dataObject;
			ITable affectedItems = eco.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);

			Iterator<?> itAffectedItemsIterator = affectedItems.iterator();

			while (itAffectedItemsIterator.hasNext()) {
				row = (IRow) itAffectedItemsIterator.next();
				part = (IItem) row.getReferent();
				logger.info("Part is :" + part);
				newRev = row.getCell(ChangeConstants.ATT_AFFECTED_ITEMS_NEW_REV);
				logger.info("New Revision is :" + newRev);

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
						/*
						 * String[] name = file.getName().split(" "); if
						 * (!(part.toString().equals(name[0]))) { msg =
						 * "Part Number " + part.toString() +
						 * " does not matches with Attachment Name " +
						 * file.getName(); fileNameList.add(msg); } else
						 */
						fileNameList.add(checkRevisionForEach(file, newRev, part));
						logger.info("function Execution ends for each document of " + part);

					} catch (IOException e) {
						e.printStackTrace();
						actionResult = new ActionResult(ActionResult.STRING,
								"IO Exception:" + e.getMessage().toString());
					} catch (Exception e) {
						actionResult = new ActionResult(ActionResult.STRING, "Exception:" + e.getMessage().toString());
						e.printStackTrace();
					} finally {
						try {
							inStream.close();
						} catch (IOException e) {

							e.printStackTrace();
							logger.error("Closing of Instream failed " + e.getMessage());
						}

					}
				} // end of while attachmentsTableIterator
			} // end of while itAffectedItemsIterator

		} catch (APIException e) {
			actionResult = new ActionResult(ActionResult.STRING, "APIException: " + e.getErrorCode().toString());
			e.printStackTrace();
			logger.error("Creation of Extension failed due to" + e.getMessage());
		}
		
		Iterator<String> itr = fileNameList.iterator();
		while (itr.hasNext()) {
			if(itr.next()!="")
			{
				flag=1;
				break;
			}
			
		}
		if(flag==1)
		{
			for(i=0;i<fileNameList.size();){
				if(fileNameList.get(i)!="")
				{
			concatenatedMessages+="# ";
			concatenatedMessages+=fileNameList.get(i);
			concatenatedMessages+="\t\t\t";
			}
				i++;
			}
			actionResult = new ActionResult(ActionResult.STRING, concatenatedMessages);
			
		}
		else
		{
			actionResult = new ActionResult(ActionResult.STRING, "Revision Matches for all the Attachments");
		}
		return actionResult;
	}

	public String checkRevisionForEach(File file, ICell newRev, IItem part) {
		logger.info("Inside checkRevisionForEach method");
		String message = "";
		String val="";
		try {
			FileInputStream fis = new FileInputStream(file);

			XWPFDocument xdoc = new XWPFDocument(OPCPackage.open(fis));
			logger.info("document1");
			XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(xdoc);
			logger.info("headerfooterpolicy");
			// read header
			XWPFHeader header = policy.getDefaultHeader();
			String headerData = header.getText().toString();
			logger.info("Header data in String" + headerData);

			Map<String, String> map = new HashMap<String, String>();
			String[] headerDataSplit = headerData.split("\n");
			for (String s : headerDataSplit) {
				if (s.contains(":")) {
					String[] t = s.split(":");
					map.put(t[0].trim(), t[1].trim());
				}
			}

			for (String s : map.keySet()) {
				logger.info(s + " is " + map.get(s));
			}
			// validate Document Number with Part Number
			val=map.get("Document Number").trim();
			if (val.equals(part.toString())) {
				logger.info("Document Number Matches for "+file.getName());
				
				//validate revision
				val=map.get("Rev").trim();
				if (val.equals(newRev.toString())) {
					
					
					logger.info("Revision Matches with attachment "+file.getName());
				} else {
					
					message = "Revision does not matches with document " + file.getName();
				}
			}
			else
			{
				
			message="Part Number "+part.toString()+" not Matching with document number "+val+" for "+file.getName();
			}
		}

		catch (Exception e) {
			e.printStackTrace();
			logger.error("Creation of Extension failed due to" + e.getMessage());
		}

		return message;
	}

}
