package com.cepheid.awf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    public static String ERROR_MSG_FINAL_EFF;
    public static String LIFECYCLE_OBSOLETE;
    public static HashMap<Object, Object> awfMessagesList; 
	public static String RELEASE_AUDIT_ERROR;
	public static String DOCUMENT_NUMBER_TYPES;
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
	
	
	IRow row = null;
    InputStream inStream;
	String sFilePath = "/ora01/APP/agilevault/";
	String sEffectiveDate;
	String sErrorMessage = "";
	String sFileHeaderText;
	String sLifecycle;
	IDataObject part;
	ActionResult actionResult;
	Map<String,List<String>> mapError= new HashMap<String,List<String>>();
	IChange eco = null;
	IObjectEventInfo info;
	String sUpdateMessage = null;

	try {
		logger.info("--------------------------------------------------------------------");
	
		GenericUtilities.initializeLogger(session);
	     awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);
		 vInitializeConstants(session);
		
		info = (IObjectEventInfo) eventinfo;
		eco = (IChange) info.getDataObject();
		logger.info("Processing :"+eco.getName() );
	    
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
			row = (IRow) affectedItemsIterator.next();
			part = row.getReferent();	
			logger.debug("Part we are processing  is :" +part);
			sEffectiveDate= DocumentUtilityAspose.now();
		    sLifecycle = row.getValue(ChangeConstants.ATT_AFFECTED_ITEMS_LIFECYCLE_PHASE).toString();
			logger.info("Lifecycle of the afected item" + part.getName() + " is:" +sLifecycle);	   
			//Set the Effective Date as todays Date
			
			String dateEffectivity = GenericUtilities.getDateBasedOnTimeZoneAndFormat(
					awfMessagesList.get("TIME_ZONE").toString(), awfMessagesList.get("DATE_FORMAT").toString(),
					new Date());
			logger.debug("Date based on PST Timezone is:" + dateEffectivity);

				
			String dateOldRevEnd = GenericUtilities.getDateBasedOnTimeZoneAndFormat(
					awfMessagesList.get("TIME_ZONE").toString(), awfMessagesList.get("DATE_FORMAT").toString(),
					yesterday());
			logger.debug("old Rev End date:" + dateOldRevEnd);
		
			List<String> lsErrorMessages = new ArrayList<String>();
			try {
            row.setValue(ChangeConstants.ATT_AFFECTED_ITEMS_OBSOLETE_DATE, dateOldRevEnd);
			row.setValue(ChangeConstants.ATT_AFFECTED_ITEMS_EFFECTIVE_DATE, dateEffectivity);
			}
			catch (Exception e)
			{
				lsErrorMessages.add("Exception while setting Old Rev End Date or Effective Date:"+e.getMessage().toString()+" for Part: "+part.getName());
				logger.error(e.getMessage()+e.getCause());
				sErrorMessage+="Exception while setting Old Rev End Date or Effective Date:"+e.getMessage().toString()+" for Part: "+part.getName();
				mapError.put(part.getName().toString(),lsErrorMessages);
				continue;
			}
			//Iterate Attachments Table
			ITable attachments=part.getTable(ItemConstants.TABLE_ATTACHMENTS);
			Iterator<?> attachmentsIterator=attachments.iterator();
	
			
			while(attachmentsIterator.hasNext()){
				sOldEffectiveDate ="";
				row = (IRow) attachmentsIterator.next();
				IFileFolder fileFolder = (IFileFolder)row.getReferent();
				String sFileName = row.getName();
				logger.debug("File Name is :" +sFileName);
				logger.info("Checking file type");
				//Check the File type. If invalid file type throw an error and skip this attachment
				if(!((sFileName.endsWith(".docx") ||(sFileName.endsWith(".doc")||sFileName.endsWith("DOCX")||sFileName.endsWith("DOC")))))
				{				
                	if(sFileName.endsWith(".xlsx")||sFileName.endsWith(".xls")||sFileName.endsWith(".pdf")||sFileName.endsWith("PDF")||sFileName.endsWith("XLS")||sFileName.endsWith("XLSX"))
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
					
					List<String> lsErrorMessages1 = new ArrayList<String>();
					lsErrorMessages1.add("File " + sFileName + " already checked out. This file is attached to part "+part.getName());
					mapError.put( eco.getName().toString(),lsErrorMessages1);
					
					
					String sHTML = GenericUtilities.sCreateHTMLtoSend(mapError, eco.getName(),MAIL_SUBJECT);
					String to = TO_ADDRESS;
					String from = FROM_ADDRESS;
					GenericUtilities.sendMail(session, from, to, sHTML,String.format(MAIL_HEADER, eco.getName()));
					
					
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
						String sDocuNumber="";
						DocumentUtilityAspose.vAcceptAllRevisions(sFilePath+sFileName);
						try {
						
						sFileHeaderText=DocumentUtilityAspose.readHeaderText(sFilePath+sFileName);
				        logger.info("File header is: "+sFileHeaderText);
				        			        
				        sDocuNumber =  sGetDocNumberAndOldEffectiveDate(sFileHeaderText,sFileName,part.getName());
						}
						catch(Exception e)
						{
							logger.debug("Error is"+e.getMessage()+e.getLocalizedMessage());
							String sError = String.format(ERROR_MSG_NO_DOC_NUMBER_INFILE,sFileName,part.getName());;
	                		lsErrorMessages.add(sError);
	                		sErrorMessage += String.format(ERROR_MSG_NO_DOC_NUMBER_INFILE,sFileName,part.getName())+".\n"; 
	                		((ICheckoutable) row).cancelCheckout();
				    	     logger.info("File cancel checked out");
				    		continue;
						}
				       
				       if(sDocuNumber.equals(""))
				       {
				    		String sError = String.format(ERROR_MSG_NO_DOC_NUMBER_INFILE,sFileName,part.getName());;
	                		lsErrorMessages.add(sError);
	                		sErrorMessage += String.format(ERROR_MSG_NO_DOC_NUMBER_INFILE,sFileName,part.getName())+".\n"; 
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
				       
				       //If the lifecycle phase of the affected item is Obsolete, then print Obsolete watermark
				       if(sLifecycle.equalsIgnoreCase(LIFECYCLE_OBSOLETE))
						{
							DocumentUtilityAspose.insertWatermarkText(sFilePath+sFileName, sLifecycle);
							logger.info(" Printing Obsolete Watermark for the file :"+sFileName);
						 	((IAttachmentFile)row).setFile(new File(sFilePath+sFileName));
							((ICheckoutable) row).checkIn();
							 logger.info("File checked in");
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
				     	       
				    	 sNewEffectiveDate = GenericUtilities.getDateBasedOnTimeZoneAndFormat(
									awfMessagesList.get("TIME_ZONE").toString(), awfMessagesList.get("DATE_FORMAT_WITHOUT_TIMEZONE").toString(),
									new Date());//DocumentUtilityAspose.now();
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
        
	
		  Map<?, ?> results = eco.audit();
		  Set set = results.entrySet();
		  // Get an iterator for the set
		  Iterator it = set.iterator();
		  // Iterate through the cells and print each cell name and exception
		  while (it.hasNext()) {
		  Map.Entry entry = (Map.Entry)it.next();
		  ICell cell = (ICell)entry.getKey();
		  if(cell != null) {
			  List<String> lsErrorMessages = new ArrayList<String>();
			  lsErrorMessages.add(RELEASE_AUDIT_ERROR);
			  mapError.put( eco.getName().toString(),lsErrorMessages);
			  sErrorMessage +=RELEASE_AUDIT_ERROR+ ".\n";
		  } else {
		 logger.info(" Audit succesfull.Cell : No associated data cell");
		  }
	  }
		  
		
		
		sUpdateMessage = "Effectivity Date updated and set to "+sNewEffectiveDate;
	
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
		 sUpdateMessage = String.format(ERROR_MSG_FINAL_EFF, sNewEffectiveDate);
		 
		  
		}
		else
		{
		eco.setValue(Integer.parseInt(ATTR_DOC_SUCCESS),DOC_UPDATE_ERROR_YES);
		eco.setValue(Integer.parseInt(ATTR_DOC_MSG), "");
		
		}
		
		
		
		String sEffectivityUpdateSucess = eco.getValue(Integer.parseInt(awfMessagesList.get("DOCUMENT_UPDATE_SUCCESFUL").toString())).toString();
		
		if(sEffectivityUpdateSucess.equalsIgnoreCase(DOC_UPDATE_ERROR_YES))
		{   
							
				Object [] nullObjectList = null;
				IStatus nextstatus = eco.getDefaultNextStatus();
				logger.info("Auto-promoting to"+ nextstatus.getName());

		
					try {
						logger.info("Releasing the AWF"+eco.getName());
						
		    		eco.changeStatus(nextstatus, false, null,false, false, nullObjectList, nullObjectList, nullObjectList, false);
				     logger.info("AWF Released"+eco.getName());
						}
					catch (Exception e)
							{
							logger.info("Exception while releasing AWF"+eco.getName());	
							logger.info("Exception::"+e.getMessage() + e.getCause() +e.getStackTrace());
							List<String> lsErrorMessages = new ArrayList<String>();
							lsErrorMessages.add(RELEASE_AUDIT_ERROR);
							mapError.put( eco.getName().toString(),lsErrorMessages);
							sErrorMessage +=RELEASE_AUDIT_ERROR+ ".\n";
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
					 sUpdateMessage = String.format(ERROR_MSG_FINAL_EFF, sNewEffectiveDate);
					}
				  
				  
			}
			
		}
		
				
		
	} catch (Exception e) {
	logger.info("Exception::"+e.getMessage() + e.getCause() +e.getStackTrace());
		try {
			if (((ICheckoutable) row).isCheckedOut()) {
				((ICheckoutable) row).cancelCheckout();
				logger.info("File cancel checked out");
			
			}
			logger.error("Exception :"+ e.getMessage() +e.toString()+e.getCause()+e.getStackTrace());
			sUpdateMessage = String.format(ERROR_MSG_FINAL_EFF, sNewEffectiveDate);
			logger.info("Update message");
			List<String> lsErrorMessages = new ArrayList<String>();
			logger.info("List created"+lsErrorMessages.size());
			lsErrorMessages.add(e.getMessage().toString());
			logger.info("Added in list");
			mapError.put( eco.getName().toString(),lsErrorMessages);
			logger.info("Added in map");
			sErrorMessage+= e.getMessage().toString();
			String sHTML = GenericUtilities.sCreateHTMLtoSend(mapError, eco.getName(),MAIL_SUBJECT);
			String to = TO_ADDRESS;
			String from = FROM_ADDRESS;
			logger.info("Sending mail");
			GenericUtilities.sendMail(session, from, to, sHTML,String.format(MAIL_HEADER, eco.getName()));
			 eco.setValue(Integer.parseInt(ATTR_DOC_SUCCESS),DOC_UPDATE_ERROR_NO);
			 eco.setValue(Integer.parseInt(ATTR_DOC_MSG), sErrorMessage);
			 
			
			}
			catch (APIException ap)
			{
				logger.error("API Exception :"+ e.getMessage() +e.toString()+e.getCause()+e.getStackTrace());
				actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(sUpdateMessage));
				return new EventActionResult(eventinfo, actionResult);
			}
			
		
		actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e.getMessage()));
		e.printStackTrace();
		logger.error("Error in execution of Effectivity Update Event:"+e.getMessage());
		return new EventActionResult(eventinfo, actionResult);
	}
	
	
	
	
	
	actionResult = new ActionResult(ActionResult.STRING,sUpdateMessage);
	
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
	 	ERROR_MSG_FINAL_EFF=awfMessagesList.get("ERROR_MSG_FINAL_EFF").toString();
	 	LIFECYCLE_OBSOLETE =awfMessagesList.get("OBSOLETE_LIFECYCLE_PHASE").toString();
	 	RELEASE_AUDIT_ERROR = awfMessagesList.get("RELEASE_AUDIT_ERROR").toString();
	 	DOCUMENT_NUMBER_TYPES =awfMessagesList.get("DOCUMENT_NUMBER_TYPES").toString();
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
	public String sGetDocNumberAndOldEffectiveDate(String sHeaderData, String sFileName, String sPart) throws Exception
	{
  boolean bDocupdate = false;
  String[] headerDataSplit = null;

  if(sHeaderData.contains("\r"))
	{
		logger.info("Header data contains \r");
		headerDataSplit = sHeaderData.split("\r");
	}
  else if (sHeaderData.contains("\n"))
	{
		logger.info("Header data contains \n");
		headerDataSplit = sHeaderData.split("\n");
	}
  else
	  headerDataSplit = sHeaderData.split("\r");

	String[] t = null, docarr = null;
	String sDocuNumber = "";
	for (String s : headerDataSplit) {
		logger.info(s);
		if(s.contains("Effectiv")){
			if (s.contains(":"))
			{
				t = s.split(":");
			}
			else
			{
				throw new Exception("Effective Date in the header of the attachment "+sFileName+"For part "+sPart+"  does not contain a ':' to distinguish");
			}
			sOldEffectiveDate = t[1].trim();
			logger.info("OldEffective date:"+sOldEffectiveDate);
	
		}
	 
		
String sDoctypes[] = DOCUMENT_NUMBER_TYPES.split(",");
   for(int i=0;i<sDoctypes.length;i++)
   {
	   	if(s.contains(sDoctypes[i])){
			if (s.contains(":"))
			{
				docarr = s.split(":");
			sDocuNumber = docarr[1];
			sDocuNumber = sDocuNumber.trim();
			logger.info("Document Number:"+sDocuNumber);
			bDocupdate = true;
			}
		} 
	
   }
   
   if (!bDocupdate)
	   throw new Exception("Couldnt find Document number in the attachment"+sFileName+" For part "+sPart);
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
	
	/**
	 * Returns Yesterdays Date
	 * @return
	 */
	private Date yesterday() {
	    final Calendar cal = Calendar.getInstance();
	    cal.add(Calendar.DATE, -1);
	    return cal.getTime();
	}
	
	
}
