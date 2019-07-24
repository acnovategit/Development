package com.agile.px;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.log4j.PropertyConfigurator;
//import org.apache.log4j.Logger;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.IAgileSession;
import com.agile.api.IAttachmentFile;
import com.agile.api.ICell;
import com.agile.api.IChange;
import com.agile.api.ICheckoutable;
import com.agile.api.IDataObject;
import com.agile.api.IFileFolder;
import com.agile.api.INode;
import com.agile.api.IRow;
import com.agile.api.IStatus;
import com.agile.api.ITable;
import com.agile.api.ItemConstants;


public class UpdateEffectivityDate implements ICustomAction {
	static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UpdateEffectivityDate.class.getClass());

	public ActionResult doAction(IAgileSession session,INode node,IDataObject dataObject){
		ActionResult actionResult = new ActionResult();
		String sLine = "Started";
		try{
		//	CommonUtil.initAppLogger(UpdateEffectivityDate.class, session);
			// String log4jConfPath = "log4j.properties";
	    	// PropertyConfigurator.configure(log4jConfPath);
			
			InputStream inStream=null;
			IRow row=null;
			ICell oldRevisionCell=null;
			String fileName="",filePath="E:/AgileVault/",oldRevision="",oldEffectiveDate="",effectiveDate="";
			IDataObject part=null;
			IFileFolder fileFolder=null;
			OutputStream outStream=null;
			File file=null;
			IChange eco=(IChange)dataObject;
			IStatus ecoStatus=eco.getStatus();
			logger.debug("Current Status" +ecoStatus);
			
			//String headerData = null;
			if(ecoStatus.toString().equals("Implemented")){
				
								
				ITable affectedItems=eco.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
				logger.info("Before iterotor");
				Iterator<?> affectedItemsIterator;
				affectedItemsIterator=affectedItems.iterator();		
					
					sLine += " Before looping affected items";		 
				while(affectedItemsIterator.hasNext()){
							
					row = (IRow) affectedItemsIterator.next();
					part = row.getReferent();
					logger.debug("Part is :" +part);
					effectiveDate=row.getValue(ChangeConstants.ATT_AFFECTED_ITEMS_EFFECTIVE_DATE).toString();
				
					logger.debug("Effective Date" +effectiveDate);
					oldRevisionCell=row.getCell(ChangeConstants.ATT_AFFECTED_ITEMS_OLD_REV);
					oldRevision=oldRevisionCell.getValue().toString();
					logger.debug("Old Revision" +oldRevision);
				}
				//Iterate Attachments Table
				ITable attachments=part.getTable(ItemConstants.TABLE_ATTACHMENTS);
				Iterator<?> attachmentsIterator=attachments.iterator();
				
				sLine += "Before looping attachments";
				while(attachmentsIterator.hasNext()){
					row = (IRow) attachmentsIterator.next();
					fileFolder = (IFileFolder)row.getReferent();
					logger.debug("File Folder is :" +fileFolder);
					fileName=row.getName();
					logger.debug("File Folder Name is :" +fileName);

					if (!((ICheckoutable) row).isCheckedOut()) {
						// Check out the file
						((ICheckoutable)row).checkOutEx();
						logger.debug("Folder is Checked out");
						inStream= ((IAttachmentFile) row).getFile();
						try{
							file=getAttachmentFile(inStream, outStream,fileName,filePath);
							logger.debug("getAttachmentFile method executed successfully");
						}
						catch (IOException e) {
							e.printStackTrace();
							actionResult = new ActionResult(ActionResult.STRING,"IO Exception"+ e.getMessage()+ sLine);
							return actionResult;
						}
						catch (Exception e) {
							e.printStackTrace();
							actionResult = new ActionResult(ActionResult.STRING,"General Exception"+ e.getMessage()+ sLine);
							return actionResult;
							
						} 
						finally {	
							inStream.close();
							
						}
						FileInputStream fis = new FileInputStream(filePath+fileName);
						logger.debug("FIS " +fis.toString());
						XWPFDocument xdoc=new XWPFDocument(OPCPackage.open(fis));
						XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(xdoc);
						//read header
						XWPFHeader header = policy.getDefaultHeader();
					     String headerData=header.getText().toString();
						logger.debug("Header data in String" +headerData);
                        sLine += "Got header data";  
						Map<String, String> map = new HashMap<String, String>();
						String[] headerDataSplit = headerData.split("\n");
						for (String s : headerDataSplit) {
							if(s.contains("Effective Date")){
								String[] t = s.split(":");
								oldEffectiveDate=t[1];
								logger.debug("OldEffective date"+oldEffectiveDate);
								sLine = "Found effectivedate" +oldEffectiveDate + " New EffectiveDate:" +effectiveDate  ;
							}
						}
						oldEffectiveDate = oldEffectiveDate.trim();
						effectiveDate = effectiveDate.trim();
						effectiveDate = "Effective Date:" + effectiveDate;
						xdoc=setHeader(xdoc, oldEffectiveDate, effectiveDate);
						logger.debug("File path:" + filePath+fileName);
						OutputStream os = new FileOutputStream(new File(filePath+fileName));
						xdoc.write(os);
						//Set the new file
						((IAttachmentFile)row).setFile(new File(filePath+fileName));
						((ICheckoutable) row).checkIn();
					      sLine += " Folder is Checked in" +effectiveDate;
						break;
					}
					else{
						logger.debug("File Folder is already checked out.Please Cancel Checkout or check in");
						actionResult = new ActionResult(ActionResult.STRING,"File Folder is already checked out.Please Cancel Checkout or check in");
						return actionResult;
					}
				}
			}
			else{
				logger.debug("ECO status is not in implemeneted Status");
				actionResult = new ActionResult(ActionResult.STRING,"ECO status is not in implemeneted Status");
				return actionResult;
			}
			actionResult = new ActionResult(ActionResult.STRING,"Effective Date Updated"+sLine);
		}
		catch (APIException e) {
			actionResult = new ActionResult(ActionResult.STRING, "API Exception:"+e.getErrorCode()+ sLine);
			e.printStackTrace();
			logger.error("Creation of Extension failed due to"+e.getMessage());
		}
		catch(Exception e){
			actionResult = new ActionResult(ActionResult.STRING, "General Exception"+e.getMessage()+sLine);
			e.printStackTrace();
			logger.error("Creation of Extension failed due to"+e.getMessage());
		}
		return actionResult;
	}

	public File getAttachmentFile( InputStream inStream, OutputStream outStream,String fileName, String filePath)
			throws APIException, IOException{
		int read = 0;
		byte[] bytes = new byte[1024];

		logger.debug("InStream data:"+inStream.available());
		filePath=filePath+fileName;
		File targetFile = new File(filePath);
		outStream = new FileOutputStream(targetFile);

		while ((read = inStream.read(bytes)) != -1) {
			outStream.write(bytes, 0, read);
		}

		logger.debug("instream.available(): " +inStream.available());
		logger.debug("File Name in method: "+targetFile.getName());
		outStream.close();
		return targetFile;
	}
	
	public static XWPFDocument setHeader(XWPFDocument document, String token, String textToReplace){
		XWPFHeaderFooterPolicy policy= document.getHeaderFooterPolicy();
		XWPFHeader header = policy.getDefaultHeader();
		replaceInParagraphs(header.getParagraphs(), token, textToReplace);
		return document;
	}

	private static void replaceInParagraphs(List<XWPFParagraph> paragraphs, String placeHolder, String replaceText){
		for (XWPFParagraph xwpfParagraph : paragraphs) {
			List<XWPFRun> runs = xwpfParagraph.getRuns();
			for (XWPFRun run : runs) {
				String runText = run.getText(run.getTextPosition());
				logger.debug("inside for");
				if(placeHolder !="" && !placeHolder.isEmpty()){
					if(runText != null &&
							Pattern.compile(placeHolder, Pattern.CASE_INSENSITIVE).matcher(runText).find()){
						runText = replaceText;
					}
				}
				run.setText(runText, 0);
			}
		}
	}
}
