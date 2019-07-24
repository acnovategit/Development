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

//import org.apache.log4j.Logger;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
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
import com.util.LoggerImpl;


public class UpdateAbsoluteInFile implements ICustomAction {
	static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UpdateAbsoluteInFile.class.getClass());

	
	public ActionResult doAction(IAgileSession session,INode node,IDataObject dataObject){
		ActionResult actionResult = new ActionResult();
		try{
			//LoggerImpl.initAppLogger(UpdateAbsoluteInFile.class, session);
			//CommonUtil.initAppLogger(UpdateAbsoluteInFile.class, session);
			InputStream inStream=null;
			IRow row=null;
			ICell oldRevisionCell=null;
			String fileName="",filePath="E:/AgileVault/",oldRevision="",rev="";
			String outfilePath ="E:\\staging\\";
			IDataObject part=null,ecoNumber=null,item=null;
			IFileFolder fileFolder=null;
			OutputStream outStream=null;
			File file=null;
			IChange eco=(IChange)dataObject;
			IStatus ecoStatus=eco.getStatus();
			String effectiveDate = null;
		 String sLine = "NOthing";
			
			logger.debug("Current Status" +ecoStatus);
			if(ecoStatus.toString().equals("Implemented")){
				ITable affectedItems=eco.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
				Iterator<?> affectedItemsIterator=affectedItems.iterator();
				
				while(affectedItemsIterator.hasNext()){
					row = (IRow) affectedItemsIterator.next();
					part = (IDataObject)row.getReferent();
					logger.debug("Part is :" +part);
					oldRevisionCell=row.getCell(ChangeConstants.ATT_AFFECTED_ITEMS_OLD_REV);
					oldRevision=oldRevisionCell.getValue().toString();
					effectiveDate = row.getCell(ChangeConstants.ATT_AFFECTED_ITEMS_EFFECTIVE_DATE).toString();
					ICell newRevisionCell = row.getCell(ChangeConstants.ATT_AFFECTED_ITEMS_NEW_REV);
					String newRevision = newRevisionCell.getValue().toString();
					
					logger.debug("Old Revision" +oldRevision);
				}
				
				
				//Update Absolute
				HashMap<IDataObject, String> partRevision=new HashMap<>();
				ITable changeHistoryTable=part.getTable(ItemConstants.TABLE_CHANGEHISTORY);
				
				Iterator<?> changeHistoryIterator=changeHistoryTable.iterator();
				
				while(changeHistoryIterator.hasNext()){
					row = (IRow) changeHistoryIterator.next();
					ecoNumber = row.getReferent();
					logger.debug("ECO Number in Change History Table" + ecoNumber);
					rev=row.getValue(1006).toString();
					
					logger.debug("Revision is" +rev);	
					partRevision.put(ecoNumber, rev);
					
					
					if(rev.equalsIgnoreCase(oldRevision)){
									
						
						logger.debug("inside");
						ITable affectedItemsTable=ecoNumber.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
						Iterator<?> affectedItemsTableIterator=affectedItemsTable.iterator();
																
						while(affectedItemsTableIterator.hasNext()){
							row = (IRow) affectedItemsTableIterator.next();
							item = row.getReferent();
							logger.debug("Item is :" +item);
							ITable attachmentsTable=item.getTable(ItemConstants.TABLE_ATTACHMENTS);
							Iterator<?> attachmentsTableIterator=attachmentsTable.iterator();
							while(attachmentsTableIterator.hasNext()){
								row = (IRow) attachmentsTableIterator.next();
								fileFolder = (IFileFolder)row.getReferent();
								logger.debug("File Folder is :" +fileFolder);
								fileName=row.getName();
								logger.debug("File Folder Name is :" +fileName);

								if (!((ICheckoutable) row).isCheckedOut()) {
									// Check out the file
									((ICheckoutable)row).checkOutEx();
									logger.debug("Folder is Checked out for old");
									inStream= ((IAttachmentFile) row).getFile();
									try{
										file=getAttachmentFile(inStream, outStream,fileName,filePath);
										logger.debug("getAttachmentFile method executed successfully for old ");
									}
									catch (IOException e) {
										e.printStackTrace();
									}
									catch (Exception e) {
										e.printStackTrace();
									} 
									finally {	
										inStream.close();
										logger.debug("Error in closing the Input Stream");
									}
									FileInputStream fis1 = new FileInputStream(filePath+fileName);
									logger.debug("FIS " +fis1.toString());
									XWPFDocument xdoc1=new XWPFDocument(OPCPackage.open(fis1));
									logger.debug("document1" );
									
									//Update Effective Date
									//CTSectPr sectPr = xdoc1.getDocument().getBody().addNewSectPr();
									XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(xdoc1);
								   
									
									policy.createWatermark("Obsolete1");
								    sLine ="Watermark" +fis1.toString();

									//write header content
									
									  CTP ctpHeader1 = CTP.Factory.newInstance(); 
									  CTR ctrHeader1 = ctpHeader1.addNewR(); 
									  CTText ctHeader1 = ctrHeader1.addNewT(); 
									  String headerText = "Obsolete"; 
									  ctHeader1.setStringValue(headerText);
									  logger.debug("String Set"); 
									  XWPFParagraph headerParagraph1 = new XWPFParagraph(ctpHeader1, xdoc1); 
									  XWPFParagraph[] parsHeader1 = new XWPFParagraph[1]; 
									  parsHeader1[0] = headerParagraph1;
									  policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT, parsHeader1);
									 
									 
									
								     OutputStream os1 = new FileOutputStream(new File(outfilePath+fileName));
									
								     xdoc1.write(os1);    
									 
									
									//Set the new file
									((IAttachmentFile)row).setFile(new File(outfilePath+fileName));
									((ICheckoutable) row).checkIn();
									logger.debug("Folder is Checked in");
									sLine +="Checkedin";
								}
								else{
									logger.debug("File Folder is already checked out.Please Cancel Checkout or check in");
									actionResult = new ActionResult(ActionResult.STRING,"File Folder is already checked out.Please Cancel Checkout or check in" );
									return actionResult;
								}
							}
						}
					}
					else{
						logger.debug("Revision not found");
						
						
					}
				}
			}
			else{
				logger.debug("ECO status is not in implemeneted Status");
			//	String sEffectiveDateUpdateResult = sUpdateEffectivityDate(effectiveDate, part);
				actionResult = new ActionResult(ActionResult.STRING,"ECO status is not in implemeneted Status" );
				return actionResult;
			}
			//String sEffectiveDateUpdateResult = sUpdateEffectivityDate(effectiveDate, part);
			actionResult = new ActionResult(ActionResult.STRING,"Attachment of Previous revision updated with Obsolete Watermark"+ sLine);
		}
		catch (APIException e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Creation of Extension failed due to"+e.getMessage());
		}
		catch(Exception e){
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
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

	
	

  


}
