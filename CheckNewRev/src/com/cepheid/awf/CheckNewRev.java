package com.cepheid.awf;

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
import com.agile.util.CommonUtil;

public class CheckNewRev implements IEventAction {
//	static Logger logger = org.slf4j.LoggerFactory.getLogger(CheckNewRev.class.getClass());
	static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(CheckNewRev.class);

	public static final String CHECKEDOUT_FILEPATH = "/ora01/APP/agilevault/";
	public static final String STAGE_FILEPATH = "/ora01/APP/agilevault/staging";
	Iterator<?> attachmentsTableIterator;
	ArrayList<String> fileNameList = new ArrayList<String>();
	String concatenatedMessages = "";
	String msg = "";
	int i = 1,flag=0;

	public EventActionResult doAction(IAgileSession session, INode node, IEventInfo eventinfo) {
		
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
						actionResult = new ActionResult(ActionResult.EXCEPTION,e);
					
						
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
		}
		
		Iterator<String> itr = fileNameList.iterator();
		logger.info("filenamelist retrieved-------");
		while (itr.hasNext()) {
			if(itr.next()!="")
			{
				flag=1;
				break;
			}
			
		}
		try{
			
		
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
			logger.info(concatenatedMessages);
			
			Exception e = new Exception(concatenatedMessages);
		    throw e;
			
			
		}
		else
		{
			actionResult = new ActionResult(ActionResult.STRING, "Part/ Document Revision Matches for all the Attachments");
		}
		
		}
		catch (Exception e)
		{
			actionResult = new ActionResult(ActionResult.EXCEPTION, e);
		}
		logger.info("actionresult is: "+actionResult.toString());
		return new EventActionResult(eventinfo,actionResult);
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
			if (val.equals(part.getName())) {
				logger.info("Document Number Matches for "+file.getName());
			}
			else
			{
			
			message="Part/Document Number "+part.getName()+" not Matching with document number "+val+" mentioned in the header of attachment "+file.getName()+"\n";
			logger.info(message);
			}
				//validate revision
			val=map.get("Rev").trim();
			if (val.equals(newRev.toString())) {
										
			logger.info("Revision Matches with attachment "+file.getName());
			} 
			else 
			{
			message += "Revision for item "+part.getName()+" do not match with that mentioned in the header of its attachment " + file.getName() + "\n";
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
