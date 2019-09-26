package com.cepheid.awf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.IAgileSession;
import com.agile.api.IAttachmentFile;
import com.agile.api.IChange;
import com.agile.api.ICheckoutable;
import com.agile.api.IDataObject;
import com.agile.api.IFileFolder;
import com.agile.api.INode;
import com.agile.api.IRow;
import com.agile.api.IStatus;
import com.agile.api.ITable;
import com.agile.api.ItemConstants;
import com.agile.px.ActionResult;
import com.agile.px.EventActionResult;
import com.agile.px.IEventAction;
import com.agile.px.IEventInfo;
import com.agile.px.IObjectEventInfo;
import com.agile.util.GenericUtilities;
import com.document.utility.DocumentUtilityAspose;

/**
 * @author Anup
 *
 */
public class UpdateEffectivityInDoc implements IEventAction {

	static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(UpdateEffectivityInDoc.class);
	static boolean bUpdated=false;

	public static String attributesMappingOnECRAndAWFListName = "ECRAWFAttributeIDsMappingList";
	public static String awfMessagesListName = "AWFMessagesList";
	public static String STATUS;
	public static String ERROR_MSG_IMPROPER_FILETYPE ;
	public static String ERROR_MSG_NO_DOC_NUMBER_INFILE ;
	public static String ERROR_MSG_NO_EFFECTIVE_DATE_IN_FILE;
	public static String INFO_SKIP_DOCUMENT_FROM_UPDATE;
	public static String DOC_UPDATE_ERROR_YES;
	public static String DOC_UPDATE_ERROR_NO;
	public static String FROM_ADDRESS;
	public static String TO_ADDRESS;
    public static String FILEPATH;
    public static String ATTR_DOC_SUCCESS;
    public static String ATTR_DOC_MSG;
    public static String MAIL_SUBJECT;
    public static String MAIL_HEADER;
    public static HashMap<Object, Object> awfMessagesList; 
    
    String sNewEffectiveDate;
	String sOldEffectiveDate;
  
	/**
	 *The entry point of the Event process extension.
	 *Checks if the AWF is in the correct status, iterates over its affected items, scans its attachments and replaces the effectivity date in its header with
	 *the current date.
	 *
	 *
	 */
	@SuppressWarnings("unused")
	public EventActionResult doAction(IAgileSession session,INode node,IEventInfo eventinfo){
	
	
	
    InputStream inStream;
	String sFilePath = "/ora01/APP/agilevault/";
	String sEffectiveDate;
	String sErrorMessage = "";
	String sFileHeaderText;
	IDataObject part;
	ActionResult actionResult;
	Map<String,List<String>> mapError= new HashMap<String,List<String>>();
	IChange eco;
	IObjectEventInfo info;

	try {
		GenericUtilities.initializeLogger(session);
	     awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);
		 vInitializeConstants(session);
		
		info = (IObjectEventInfo) eventinfo;
		eco = (IChange) info.getDataObject();
		
	    
		HashMap<Object, Object> pendingSignOffDetails = GenericUtilities.getPendingSignOffDetails(eco, awfMessagesList, eco.getStatus().toString());
	
	    boolean bSignoff = (boolean) (pendingSignOffDetails.get("approvalPending"));
	    if(bSignoff)
	    {
	    	logger.info("Signoff Pending from approver"+String.join(",", pendingSignOffDetails.get("pendingApprovers").toString()));
	    	actionResult = new ActionResult(ActionResult.STRING, "Pending Approvals from"+ String.join(",", pendingSignOffDetails.get("pendingApprovers").toString()) );
	    	
	    	return new EventActionResult(eventinfo, actionResult);
	    }
	    	
	    	
	    if(!FILEPATH.equals(""))
			sFilePath = FILEPATH;
		
		DocumentUtilityAspose aspose = new DocumentUtilityAspose(session);
	
	
	   
		IStatus ecoStatus=eco.getStatus();
		logger.debug("Current Status of AWF" +ecoStatus);
		logger.info("Status in Config"+STATUS);
        if(!(ecoStatus.toString().equals(STATUS)))
        {
        	logger.error("ECO status is not in Implement-Review Status");
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception("Current ECO status is "+ecoStatus.toString()+ " Please move the AWF to Implement-Review Status"));
			return new EventActionResult(eventinfo, actionResult);
        }
        ITable affectedItems=eco.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
		Iterator<?> affectedItemsIterator;
		affectedItemsIterator=affectedItems.iterator();		
        
		while(affectedItemsIterator.hasNext()){
			IRow row = (IRow) affectedItemsIterator.next();
			part = row.getReferent();	
			logger.debug("Part is :" +part);
			sEffectiveDate= DocumentUtilityAspose.now();
			logger.debug("Effective Date" +sEffectiveDate);
			
			//Iterate Attachments Table
			ITable attachments=part.getTable(ItemConstants.TABLE_ATTACHMENTS);
			Iterator<?> attachmentsIterator=attachments.iterator();
			List<String> lsErrorMessages = new ArrayList<String>();
			
			while(attachmentsIterator.hasNext()){
				sOldEffectiveDate ="";
				row = (IRow) attachmentsIterator.next();
				IFileFolder fileFolder = (IFileFolder)row.getReferent();
				String sFileName = row.getName();
				logger.debug("File Name is :" +sFileName);
				logger.info("Checking file type");
				//Check the File type. If invalid file type throw an error and skip this attachment
				if(!((sFileName.endsWith(".docx") ||(sFileName.endsWith(".doc")))))
				{				
                	if(sFileName.endsWith(".xlsx")||sFileName.endsWith(".xls")||sFileName.endsWith(".pdf"))
                	{
                		String sError = String.format(ERROR_MSG_IMPROPER_FILETYPE,sFileName,part.getName());
                		lsErrorMessages.add(sError);
                		sErrorMessage += String.format(ERROR_MSG_IMPROPER_FILETYPE,sFileName,part.getName()) +".\n"; 
                	}
                	continue;	
                }
				//Check if the attachment is already checked out
				if (((ICheckoutable)row).isCheckedOut()) {
					
					logger.error("File "+sFileName+" already checked out.");
					actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception("File "+sFileName+" already checked out."));
					return new EventActionResult(eventinfo, actionResult);
					
				}
				else {
				// Check out the file
					((ICheckoutable)row).checkOutEx();
					logger.debug("Folder is Checked out");
					inStream = ((IAttachmentFile) row).getFile();
				   //Check if the file is readable and store the file at the vault location
					if(bCheckFile(inStream,sFileName,sFilePath))
				    {
						DocumentUtilityAspose.vAcceptAllRevisions(sFilePath+sFileName);
				    	sFileHeaderText=DocumentUtilityAspose.readHeaderText(sFilePath+sFileName);
				        logger.info("File header is: "+sFileHeaderText);
				        
				       String sDocuNumber =  sGetDocNumberAndOldEffectiveDate(sFileHeaderText);
                       
				       if(sDocuNumber.equals(""))
				       {
				    		String sError = String.format(ERROR_MSG_NO_DOC_NUMBER_INFILE,part.getName());;
	                		lsErrorMessages.add(sError);
	                		sErrorMessage += String.format(ERROR_MSG_NO_DOC_NUMBER_INFILE,part.getName())+".\n"; 
	                		((ICheckoutable) row).cancelCheckout();
				    	     logger.info("File cancel checked out");
				    		continue;
				       }
				       
				       if(!(part.getName().equalsIgnoreCase(sDocuNumber)))
				    	{  
				    		logger.info("Skipping the attachment "+sFileName+" since the Document number in header "+sDocuNumber+"  does not match with the document number "+part.getName());
				       		((ICheckoutable) row).cancelCheckout();
				    	     logger.info("File cancel checked out");
				    		continue;	
				    	}
				       
				       if (sOldEffectiveDate.equals(""))
				       {

				    		String sError = String.format(ERROR_MSG_NO_EFFECTIVE_DATE_IN_FILE,part.getName());
	                		lsErrorMessages.add(sError);
	                		sErrorMessage += sError+".\n"; 
	                		((ICheckoutable) row).cancelCheckout();
				    	     logger.info("File cancel checked out");
				    		continue; 
				       }
				     	       
				    	 sNewEffectiveDate = DocumentUtilityAspose.now();
				    	logger.info("New Date: "+sNewEffectiveDate);
				    	
				    	DocumentUtilityAspose.replaceTextInHeader(sFilePath+sFileName, sOldEffectiveDate, sNewEffectiveDate);
				    	
				    	DocumentUtilityAspose.vAcceptAllRevisions(sFilePath+sFileName);
				    	((IAttachmentFile)row).setFile(new File(sFilePath+sFileName));
						((ICheckoutable) row).checkIn();
						 logger.info("File checked in");
				    
				    
				    }
				  
				
				}
				
			} //End of Attachments iterator
			
			if(!(lsErrorMessages.isEmpty()))
				mapError.put(part.getName().toString(),lsErrorMessages);
		
		} //End of Affected items iterator
        
	
		if(!mapError.isEmpty())
		{
		
		 String sHTML = GenericUtilities.sCreateHTMLtoSend(mapError, eco.getName(),MAIL_SUBJECT);
		 String to = TO_ADDRESS;
		 String from = FROM_ADDRESS;
		 
		 logger.info("TO" +to);
		 logger.info("FROM"+from);
		 
		 
		 GenericUtilities.sendMail(session, from,to, sHTML, String.format(MAIL_HEADER, eco.getName()));
		
		 
		 eco.setValue(Integer.parseInt(ATTR_DOC_SUCCESS),DOC_UPDATE_ERROR_NO);
		 eco.setValue(Integer.parseInt(ATTR_DOC_MSG), sErrorMessage);
		 
		 
		  
		}
		else
		{
		eco.setValue(Integer.parseInt(ATTR_DOC_SUCCESS),DOC_UPDATE_ERROR_YES);
		eco.setValue(Integer.parseInt(ATTR_DOC_MSG), "");
		
		}
		
		
	} catch (Exception e) {
		actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e.getMessage()));
		e.printStackTrace();
		logger.error("Error in execution of Effectivity Update Event:"+e.getMessage());
		return new EventActionResult(eventinfo, actionResult);
	}
	
	actionResult = new ActionResult(ActionResult.STRING, "Effectivity Date updated and set to "+sNewEffectiveDate);
	
	return new EventActionResult(eventinfo, actionResult);
	}

	
	/**
	 * @param session
	 * @throws APIException
	 */
	public  void vInitializeConstants(IAgileSession session) throws APIException
	{
		
		FILEPATH = awfMessagesList.get("VAULT_FILEPATH").toString();
		STATUS = awfMessagesList.get("AWF_IMPLEMENT_REVIEW_STATUS").toString();
		ERROR_MSG_IMPROPER_FILETYPE = awfMessagesList.get("ERROR_MSG_IMPROPER_FILETYPE").toString();
		ERROR_MSG_NO_DOC_NUMBER_INFILE = awfMessagesList.get("ERROR_MSG_NO_DOC_NUMBER_INFILE").toString();
		ERROR_MSG_NO_EFFECTIVE_DATE_IN_FILE = awfMessagesList.get("ERROR_MSG_NO_EFFECTIVE_DATE_IN_FILE").toString();
		INFO_SKIP_DOCUMENT_FROM_UPDATE = awfMessagesList.get("INFO_SKIP_DOCUMENT_FROM_UPDATE").toString();
		DOC_UPDATE_ERROR_YES = awfMessagesList.get("YES").toString();
		DOC_UPDATE_ERROR_NO = awfMessagesList.get("NO").toString();
		FROM_ADDRESS = awfMessagesList.get("FROM_ADDRESS").toString();
		TO_ADDRESS = awfMessagesList.get("TO_ADDRESS").toString();
	   ATTR_DOC_SUCCESS = awfMessagesList.get("DOCUMENT_UPDATE_SUCCESFUL").toString();
	    ATTR_DOC_MSG = awfMessagesList.get("DOCUMENT_UPDATE_ERROR").toString();
	 	MAIL_SUBJECT=	awfMessagesList.get("AWF_EFF_MAIL_SUBJECT").toString();
	 	MAIL_HEADER= awfMessagesList.get("AWF_MAIL_HEADER").toString();
	}
	
	
	
	
	
	/**
	 * @param mapError
	 * @param sECONumber
	 * @return
	 */
	public static String sCreateHTMLtoSend(Map<String,List<String>> mapError, String sECONumber)
	{
		
		 String html="<html><head>"
                 + "<title>"+"AWF "+sECONumber+"</title>"
                 + "</head>"+"<LINK REL='stylesheet' HREF='stylesheet/fac_css.css' TYPE='text/css'>"
                 + "<body>"
                 +"<table width='900' cellpadding='0' cellspacing='0' border='0'>"
                 +"<tr><td class ='text12' width='100%'><br>Issues while update Effectivity Date</td></tr><tr>"
                 +"<td height='5'></td></tr>"
                 +"<tr><td></td></tr>"
                 +"<tr><td height='5'></td></tr>"
                 +"<tr><td><table border='1' width='800' cellpadding='2' cellspacing='1' bgColor='#808080' style='border-collapse: collapse' bordercolor='#EBDA2A' align='left'>"
                 +"<tr bgColor=#808080 class='centerheading' align='center'>"
                         +"<td width='30' style='color: #FFFFFF;'><b>S.No.</b></td>"
                         +"<td width='35' style='color: #FFFFFF;'><b>Part</b></td>"
                         +"<td width='35' style='color: #FFFFFF;'><b>Message</b></td>"
                    
                     
                + "</tr>";
		
 	 		
		  int i=1;
          for (Map.Entry<String,List<String>> entry : mapError.entrySet())
       	{
            List<String> check=entry.getValue();
	  			Iterator<String> it = check.iterator();
	  			while(it.hasNext()){
	  			
          	  html=html+"<tr align='center' bgColor=#FFFFFF>"+"<td width='30' style='color: #000000;'>"+i+"</b></td>"
          	  +"<td width='30' style='color: #000000;'>"+entry.getKey()+"</td>"
          	  +"<td width='60' style='color: #000000;'>"+it.next()+"</td>";
              
          	  i++;
	  				}
       		}
                 html=html  +"</table>"
              +"</td>"
      +"</tr>"
      +"<tr>"
           +"<td height='6'></td>"
      +"</tr>"
    
      +"<tr>"
           +"<td height='15'></td>"
      +"</tr>"
      
      +"</table>"
      +"</body></html>";
		  
		 
		  
		  return html;
		
		
	}
	
	
	
	
	/**
	 * @param sHeaderData
	 * @return
	 */
	public String sGetDocNumberAndOldEffectiveDate(String sHeaderData)
	{

	if(sHeaderData.contains("\n"))
	{
		logger.info("Header data contains \n");
	}
	if (sHeaderData.contains("\r"))
	{
		logger.info("Header data contains \r");
	}
	
	String[] headerDataSplit = sHeaderData.split("\r");
	String[] t = null, docarr = null;
	String sDocuNumber = "";
	for (String s : headerDataSplit) {
		logger.info(s);
		if(s.contains("Effectiv")){
			if (s.contains(":"))
			{
				t = s.split(":");
			}
			
			sOldEffectiveDate = t[1].trim();
			logger.info("OldEffective date:"+sOldEffectiveDate);
	
		}
	  
		if(s.contains("Document Number")){
			if (s.contains(":"))
			{
				docarr = s.split(":");
			}
			
			sDocuNumber = docarr[1];
			sDocuNumber = sDocuNumber.trim();
			logger.info("Document Number"+sDocuNumber);
	
		} 
	
	
	
	}
	
	return sDocuNumber;
	}
	
	
	/**
	 * @param inStream
	 * @param fileName
	 * @param filePath
	 * @return
	 * @throws APIException
	 * @throws IOException
	 */
	public boolean bCheckFile(InputStream inStream, String fileName, String filePath) throws APIException, IOException
	{
		File file;
		OutputStream outStream = null;
	
	
			file=getAttachmentFile(inStream, outStream,fileName,filePath);
			logger.debug("getAttachmentFile method executed successfully");
		
		if(file.isFile())
			return true;
	
		return false;
	}
	/**
	 * Gets the file attachment from the File input stream, output stream
	 * @param inStream
	 * @param outStream
	 * @param fileName
	 * @param filePath
	 * @return
	 * @throws APIException
	 * @throws IOException
	 */
	public File getAttachmentFile( InputStream inStream, OutputStream outStream,String fileName, String filePath)
			throws APIException, IOException{
		int read = 0;
		byte[] bytes = new byte[1024];


		filePath=filePath+fileName;
		File targetFile = new File(filePath);
		outStream = new FileOutputStream(targetFile);

		while ((read = inStream.read(bytes)) != -1) {
			outStream.write(bytes, 0, read);
			//logger.info(bytes);
		}

		outStream.close();
		return targetFile;
	}
	
}