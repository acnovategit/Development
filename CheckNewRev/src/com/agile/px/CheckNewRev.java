package com.agile.px;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import com.agile.util.CommonUtil;


public class CheckNewRev implements ICustomAction {
	static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(CheckNewRev.class);

	public static final String CHECKEDOUT_FILEPATH= "E:\\AgileVault\\" ;
	public static final String STAGE_FILEPATH = "E:\\AgileVault\\staging\\";
	Iterator<?> attachmentsTableIterator;
	public ActionResult doAction(IAgileSession session,INode node,IDataObject dataObject){
		CommonUtil.initAppLogger(CheckNewRev.class, session);
		ActionResult actionResult = new ActionResult();
		try{
			
			InputStream inStream=null;
			IRow row=null;
			
			String fileName="";
			IItem part=null;
			ICell newRev=null;
			IFileFolder fileFolder=null;
			//OutputStream outStream=null;
			File file =null;
			IChange eco=(IChange)dataObject;
			ITable affectedItems=eco.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
			
			Iterator<?> itAffectedItemsIterator=affectedItems.iterator();
			
			
			while(itAffectedItemsIterator.hasNext()){
				row = (IRow) itAffectedItemsIterator.next();
				part = (IItem)row.getReferent();
				logger.info("Part is :" +part);
				newRev=row.getCell(ChangeConstants.ATT_AFFECTED_ITEMS_NEW_REV);
				logger.info("New Revision is :" +newRev);	
					
			//Iterate Attachments Table
				ITable attachmentsTable=part.getTable(ItemConstants.TABLE_ATTACHMENTS);
				attachmentsTableIterator=attachmentsTable.iterator();
				
				
			while(attachmentsTableIterator.hasNext()){
				
				row = (IRow) attachmentsTableIterator.next();
				fileFolder = (IFileFolder)row.getReferent();
				logger.info("File Folder is :" +fileFolder);
				fileName=row.getName();
				logger.info("File Folder Name is :" +fileName);

				inStream= ((IAttachmentFile) row).getFile();
				try{
					String sFilePath = CHECKEDOUT_FILEPATH+fileName;
					file = new File(sFilePath);
					FileUtils.copyInputStreamToFile(inStream, file);
					//file=getAttachmentFile(inStream, outStream,fileName,filePath);
					logger.info("getAttachmentFile method executed successfully");
				}
				catch (IOException e) {
					e.printStackTrace();
					actionResult = new ActionResult(ActionResult.STRING,"IO Exception:"+ e.getMessage().toString());
				}
				catch (Exception e) {
					actionResult = new ActionResult(ActionResult.STRING,"Exception:"+ e.getMessage().toString());
					e.printStackTrace();
				} 
				finally {	
					inStream.close();
					
				}
			}
			}
			/*try{*/				
			/*
			 * FileInputStream fis = new FileInputStream(filePath+fileName);
			 * logger.info("FIS " +fis.toString());
			 */
		    	FileInputStream fis = new FileInputStream(file);
				XWPFDocument xdoc=new XWPFDocument(OPCPackage.open(fis));
				logger.info("document1" );
				XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(xdoc);
				logger.info("document2" );
				//read header
				XWPFHeader header = policy.getDefaultHeader();
				String headerData=header.getText().toString();
				logger.info("Header data in String" +headerData);

				Map<String, String> map = new HashMap<String, String>();
				String[] headerDataSplit = headerData.split("\n");

				for (String s : headerDataSplit) {
					if(s.contains(":")){
						String[] t = s.split(":");
						map.put(t[0], t[1]);
					}
				}
				
				for (String s : map.keySet()) {
					logger.info(s + " is " + map.get(s));
				}
				//Validate Revision
				if(map.get("Rev").trim().equals(newRev.toString())){
					logger.info("Attachment and Part Revision matches");
					actionResult = new ActionResult(ActionResult.STRING,"Attachment and Part Revision matches");

				}
				else{
					logger.info("Attachment and Part Revision does not matches");
					actionResult = new ActionResult(ActionResult.EXCEPTION,new Exception("Attachment and Part Revision does not matches"));
				}
			/*}
			catch (IOException e) {
				e.printStackTrace();
				logger.info("Error in closing the Stream");
			}
			catch (Exception e) {
				e.printStackTrace();
			} 
			actionResult = new ActionResult(ActionResult.STRING,"Revision check");*/
		}
		catch (APIException e) {
			actionResult = new ActionResult(ActionResult.STRING, "APIException: "+e.getErrorCode().toString());
			e.printStackTrace();
			logger.error("Creation of Extension failed due to"+e.getMessage());
		}
		catch(Exception e){
			actionResult = new ActionResult(ActionResult.STRING, "General Exception: " + e.getMessage().toString());
			e.printStackTrace();
			logger.error("Creation of Extension failed due to"+e.getMessage());
		}
		return actionResult;
}

}
