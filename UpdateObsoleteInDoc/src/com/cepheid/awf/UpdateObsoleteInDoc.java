package com.cepheid.awf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.ExceptionConstants;
import com.agile.api.IAgileSession;
import com.agile.api.IAttachmentFile;
import com.agile.api.ICell;
import com.agile.api.IChange;
import com.agile.api.ICheckoutable;
import com.agile.api.IDataObject;
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


public class UpdateObsoleteInDoc implements IEventAction {

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
    public static String RELEASE_AUDIT_ERROR;
    public static String DOC_HAS_REVISIONS_ERROR;
    public static String ERROR_MSG_FINAL_OBS;
    public static HashMap<Object, Object> awfMessagesList; 
	private String WATERMARK_TEXT ="Obsolete";
	String sOldEffectiveDate;
	static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(UpdateObsoleteInDoc.class);
	

	public EventActionResult doAction(IAgileSession session, INode node, IEventInfo eventinfo) {
		ActionResult actionResult = new ActionResult();
		InputStream inStream = null;
		IRow row = null;
    	String sFileName = "";
		String sFilePath = FILEPATH;
		IDataObject part = null, ecoNumber = null, item = null;
		String sErrorMessage = "";
		IChange eco = null;
		String sDocuNumber = "";
		String sFileHeaderText;
		Map<String, List<String>> mapError = new HashMap<String, List<String>>();
		String sUpdateMessage;

	
		
		try {
			
			logger.info("--------------------------------------------------------------------------------");
			GenericUtilities.initializeLogger(session);
			awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);
			 vInitializeConstants(session);
			 

			@SuppressWarnings("unused")
			DocumentUtilityAspose aspose = new DocumentUtilityAspose(session);
			IObjectEventInfo info = (IObjectEventInfo) eventinfo;
			eco = (IChange) info.getDataObject();
			IStatus ecoStatus = eco.getStatus();
			logger.info("Processing :"+eco.getName());
			
			HashMap<Object, Object> pendingSignOffDetails = GenericUtilities.getPendingSignOffDetails(eco, awfMessagesList, eco.getStatus().toString());
			
		    boolean bSignoff = (boolean) (pendingSignOffDetails.get("approvalPending"));
		    if(bSignoff)
		    {
		    	logger.info("Signoff Pending from approver"+String.join(",", pendingSignOffDetails.get("pendingApprovers").toString()));
		    	actionResult = new ActionResult(ActionResult.STRING, "Pending Approvals from "+ String.join(",", pendingSignOffDetails.get("pendingApprovers").toString()) );
		    	
		    	return new EventActionResult(eventinfo, actionResult);
		    }
			
			
		    if(!FILEPATH.equals(""))
				sFilePath = FILEPATH;
			

			if (!(ecoStatus.toString().equals(STATUS))) {
				logger.error("ECO status is not in Released Status");
				actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception("Current ECO status is "
						+ ecoStatus.toString() + " Please Release the AWF"));
				return new EventActionResult(eventinfo, actionResult);
			}

			ITable affectedItems = eco.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
			Iterator<?> affectedItemsIterator = affectedItems.iterator();

			while (affectedItemsIterator.hasNext()) {
				row = (IRow) affectedItemsIterator.next();
				part = (IDataObject) row.getReferent();

			
				ITable changeHistoryTable = part.getTable(ItemConstants.TABLE_CHANGEHISTORY);

				Iterator<?> changeHistoryIterator = changeHistoryTable.iterator();

				if (!(changeHistoryIterator.hasNext())) {
					logger.info("No previous revision for affected item" + part.getName());
					continue;

				}

				row = (IRow) changeHistoryIterator.next();
				ecoNumber = row.getReferent();

				logger.info("Previous change for the afftected item "+part.getName() + "is : "+ecoNumber.getName());
				
				ITable affectedItemsTable = ecoNumber.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
				Iterator<?> affectedItemsTableIterator = affectedItemsTable.iterator();

				while (affectedItemsTableIterator.hasNext()) {
					row = (IRow) affectedItemsTableIterator.next();
					item = row.getReferent();

					if (!(item.getName().toString().equalsIgnoreCase(part.getName().toString()))) {
						logger.info("Part " + item.getName() + " number on this change does not matches with Part "
								+ part.getName());
						continue;
					}
                logger.info("Processing part: "+item.getName());
					ITable attachmentsTable = item.getTable(ItemConstants.TABLE_ATTACHMENTS);
					Iterator<?> attachmentsTableIterator = attachmentsTable.iterator();

					List<String> lsErrorMessages = new ArrayList<String>();
					while (attachmentsTableIterator.hasNext()) {
						row = (IRow) attachmentsTableIterator.next();
						sFileName = row.getName();

						if (!((sFileName.endsWith(".docx") || (sFileName.endsWith(".doc"))))) {
							if (sFileName.endsWith(".xlsx") || sFileName.endsWith(".xls")
									|| sFileName.endsWith(".pdf")) {
								String sError = String.format(ERROR_MSG_IMPROPER_FILETYPE,sFileName,part.getName());
								lsErrorMessages.add(sError);
								sErrorMessage +=String.format(ERROR_MSG_IMPROPER_FILETYPE,sFileName,part.getName())+".\n";
							}
							continue;
						}
						
						
						

						if (((ICheckoutable) row).isCheckedOut()) {

							logger.error("File " + sFileName + " already checked out.");
							
							List<String> lsErrorMessages1 = new ArrayList<String>();
							lsErrorMessages1.add("File " + sFileName + " already checked out. This file is attached to part "+part.getName());
							mapError.put( eco.getName().toString(),lsErrorMessages1);
							
							
							String sHTML = GenericUtilities.sCreateHTMLtoSend(mapError, eco.getName(),MAIL_SUBJECT);
							String to = TO_ADDRESS;
							String from = FROM_ADDRESS;
							GenericUtilities.sendMail(session, from, to, sHTML,String.format(MAIL_HEADER, eco.getName()));
							
							
							actionResult = new ActionResult(ActionResult.EXCEPTION,
									new Exception("File " + sFileName + " already checked out."));
							return new EventActionResult(eventinfo, actionResult);

						} 
							// Check out the file
							((ICheckoutable) row).checkOutEx();
							logger.debug("Folder is Checked out");
						
							inStream = ((IAttachmentFile) row).getFile();
							
							// Check if the file is readable
							sFilePath=sFilePath.trim();
							sFileName = sFileName.trim();
							if (bCheckFile(inStream, sFileName, sFilePath)) {
                                if(DocumentUtilityAspose.bCheckDocHasRevisions(sFilePath + sFileName))
                                {
                                	String sError = DOC_HAS_REVISIONS_ERROR;
									lsErrorMessages.add(sError);
									sErrorMessage += DOC_HAS_REVISIONS_ERROR + ".\n";
									((ICheckoutable) row).cancelCheckout();
									logger.error(sFileName+" :"+DOC_HAS_REVISIONS_ERROR);
									logger.info("File cancel checked out");
									continue;
                                }
                                	
								sFileHeaderText = DocumentUtilityAspose.readHeaderText(sFilePath + sFileName);
								logger.info("File header is: " + sFileHeaderText);
                              
								sDocuNumber = sGetDocNumberAndOldEffectiveDate(sFileHeaderText);

								if (sDocuNumber.equals("")) {
									String sError = ERROR_MSG_NO_DOC_NUMBER_INFILE;
									lsErrorMessages.add(sError);
									sErrorMessage += ERROR_MSG_NO_DOC_NUMBER_INFILE + ".\n";
									((ICheckoutable) row).cancelCheckout();
									logger.info("File cancel checked out");
									continue;
								}

								if (!(part.getName().equalsIgnoreCase(sDocuNumber))) {
									logger.info("Skipping the attachment " + sFileName
											+ " since the Document number in header " + sDocuNumber
											+ "  does not match with the document number " + part.getName());
									((ICheckoutable) row).cancelCheckout();
									logger.info("File cancel checked out");
									continue;
								}

								 DocumentUtilityAspose.insertWatermarkText(sFilePath+sFileName, WATERMARK_TEXT);
								// Set the new file
								((IAttachmentFile) row).setFile(new File(sFilePath + sFileName));
								((ICheckoutable) row).checkIn();
								logger.debug("Folder is Checked in");

							}
							

						
						

					} // End of Attachments iterator while
					
					if (!(lsErrorMessages.isEmpty()))
						mapError.put( part.getName().toString(),lsErrorMessages);
					

				} // End of Affected items iterator for change history

			} // End of Affected items iteroator of the main change

	
			
			HashMap<Object, Object> awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);
			
			logger.info("Map error is -->" +mapError);
			logger.info("Error message"+sErrorMessage );
           sUpdateMessage = "Attachment of Previous revision updated with Obsolete Watermark";
			if (!mapError.isEmpty()) {

				String sHTML = GenericUtilities.sCreateHTMLtoSend(mapError, eco.getName(),MAIL_SUBJECT);
				String to = TO_ADDRESS;
				String from = FROM_ADDRESS;
				GenericUtilities.sendMail(session, from, to, sHTML,String.format(MAIL_HEADER, eco.getName()));

				String sEffectivityUpdateMsg = eco.getValue(Integer.parseInt(awfMessagesList.get("DOCUMENT_UPDATE_ERROR").toString())).toString();
				  eco.setValue(Integer.parseInt(awfMessagesList.get("DOCUMENT_UPDATE_SUCCESFUL").toString()),
				  DOC_UPDATE_ERROR_NO);
				  eco.setValue(Integer.parseInt(awfMessagesList.get("DOCUMENT_UPDATE_ERROR").toString()),
						  sEffectivityUpdateMsg+"\n"+ sErrorMessage);
				  
				  sUpdateMessage = ERROR_MSG_FINAL_OBS;  
				 

			} 
		
	
			

		} catch (Exception e) {
		
			try {
			if (((ICheckoutable) row).isCheckedOut()) {
				((ICheckoutable) row).cancelCheckout();
				logger.info("File cancel checked out");
			
			}
			
			List<String> lsErrorMessages = new ArrayList<String>();
			lsErrorMessages.add(e.getMessage()+e.getCause().toString()+e.getStackTrace().toString());
			mapError.put( eco.getName().toString(),lsErrorMessages);
			
			
			String sHTML = GenericUtilities.sCreateHTMLtoSend(mapError, eco.getName(),MAIL_SUBJECT);
			String to = TO_ADDRESS;
			String from = FROM_ADDRESS;
			GenericUtilities.sendMail(session, from, to, sHTML,String.format(MAIL_HEADER, eco.getName()));
			
			}
			catch (APIException ap)
			{
				logger.error("API Exception :"+ e.getMessage() +e.toString()+e.getCause()+e.getStackTrace());
				actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
				return new EventActionResult(eventinfo, actionResult);
			}
			
			
			
			
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error(" Execution of Extension failed due to" + e.getMessage() +e.toString()+e.getCause()+e.getStackTrace());
			return new EventActionResult(eventinfo, actionResult);
		}

		
		logger.info("Attachment of Previous revision updated with Obsolete Watermark");
		
		  actionResult = new ActionResult(ActionResult.STRING,sUpdateMessage);
		 

		
	/*	actionResult = new ActionResult(ActionResult.STRING,"Attachment of Previous revision updated with Obsolete Watermark");*/
		
		logger.debug(sErrorMessage);
		
		return new EventActionResult(eventinfo, actionResult);
	}

	

	
	
	/**
	 * @param session
	 * @throws APIException
	 */
	public  void vInitializeConstants(IAgileSession session) throws APIException
	{
		
		FILEPATH = awfMessagesList.get("VAULT_FILEPATH").toString();
		STATUS = awfMessagesList.get("AWF_RELEASED_STATUS").toString();
		ERROR_MSG_IMPROPER_FILETYPE = awfMessagesList.get("EROR_MSG_IMPROPER_FILETYPE_OBS").toString();
		ERROR_MSG_NO_DOC_NUMBER_INFILE = awfMessagesList.get("ERROR_MSG_NO_DOC_NUMBER_INFILE").toString();
		ERROR_MSG_NO_EFFECTIVE_DATE_IN_FILE = awfMessagesList.get("ERROR_MSG_NO_EFFECTIVE_DATE_IN_FILE").toString();
		INFO_SKIP_DOCUMENT_FROM_UPDATE = awfMessagesList.get("INFO_SKIP_DOCUMENT_FROM_UPDATE").toString();
		DOC_UPDATE_ERROR_YES = awfMessagesList.get("YES").toString();
		DOC_UPDATE_ERROR_NO = awfMessagesList.get("NO").toString();
		FROM_ADDRESS = awfMessagesList.get("FROM_ADDRESS").toString();
		TO_ADDRESS = awfMessagesList.get("TO_ADDRESS").toString();
	   ATTR_DOC_SUCCESS = awfMessagesList.get("DOCUMENT_UPDATE_SUCCESFUL").toString();
	    ATTR_DOC_MSG = awfMessagesList.get("DOCUMENT_UPDATE_ERROR").toString();
	 	MAIL_SUBJECT=	awfMessagesList.get("AWF_OBS_MAIL_SUBJECT").toString();
	 	MAIL_HEADER= awfMessagesList.get("AWF_MAIL_HEADER").toString();
	 	RELEASE_AUDIT_ERROR = awfMessagesList.get("RELEASE_AUDIT_ERROR").toString();
	 	DOC_HAS_REVISIONS_ERROR = awfMessagesList.get("DOC_HAS_REVISIONS_ERROR").toString();
	 	ERROR_MSG_FINAL_OBS =awfMessagesList.get("ERROR_MSG_FINAL_OBS").toString();
	}
	
	
	
	
	
	/**
	 * @param sHeaderData
	 * @return
	 */
	public String sGetDocNumberAndOldEffectiveDate(String sHeaderData) {

		if (sHeaderData.contains("\n")) {
			logger.info("Header data contains n");
		}
		if (sHeaderData.contains("\r")) {
			logger.info("Header data contains r");
		}

		String[] headerDataSplit = sHeaderData.split("\r");
		String[] t = null, docarr = null;
		String sDocuNumber = "";
		for (String s : headerDataSplit) {
			logger.info(s);
			if (s.contains("Effectiv")) {
				if (s.contains(":")) {
					t = s.split(":");
				}

				sOldEffectiveDate = t[1].trim();
				logger.info("OldEffective date:" + sOldEffectiveDate);

			}

			if (s.contains("Document Number")) {
				if (s.contains(":")) {
					docarr = s.split(":");
				}

				sDocuNumber = docarr[1];
				sDocuNumber = sDocuNumber.trim();
				logger.info("Document Number" + sDocuNumber);

			}

		}

		return sDocuNumber;
	}

	/**
	 * @param mapError
	 * @param sECONumber
	 * @return
	 */
	public static String sCreateHTMLtoSend(Map<String, List<String>> mapError, String sECONumber) {

		String html = "<html><head>" + "<title>" + "AWF:" + sECONumber + " not Released" + "</title>" + "</head>"
				+ "<LINK REL='stylesheet' HREF='stylesheet/fac_css.css' TYPE='text/css'>" + "<body>"
				+ "<table width='900' cellpadding='0' cellspacing='0' border='0'>"
				+ "<tr><td class ='text12' width='100%'><br>AWF: " + sECONumber
				+ " Obsolete Watermark Issues</td></tr><tr>" + "<td height='5'></td></tr>" + "<tr><td></td></tr>"
				+ "<tr><td height='5'></td></tr>"
				+ "<tr><td><table border='1' width='800' cellpadding='2' cellspacing='1' bgColor='#808080' style='border-collapse: collapse' bordercolor='#000000' align='left'>"
				+ "<tr bgColor=#808080 class='centerheading' align='left'>"
				+ "<td width='30' style='color: #FFFFFF;'><b>S.No.</b></td>"
				+ "<td width='30' style='color: #FFFFFF;'><b>Part</b></td>"
				+ "<td width='60' style='color: #FFFFFF;'><b>Message</b></td>"

				+ "</tr>";

		int i = 1;
		for (Map.Entry<String, List<String>> entry : mapError.entrySet()) {
			List<String> check = entry.getValue();
			Iterator<String> it = check.iterator();
			while (it.hasNext()) {

				html = html + "<tr align='center'>" + "<td width='30' style='color: #000000;'>" + i + "</b></td>"
						+ "<td width='30' style='color: #000000;'>" + entry.getKey() + "</td>"
						+ "<td width='60' style='color: #000000;'>" + it.next() + "</td>";

				i++;
			}
		}
		html = html + "</table>" + "</td>" + "</tr>" + "<tr>" + "<td height='6'></td>" + "</tr>"

				+ "<tr>" + "<td height='15'></td>" + "</tr>"

				+ "</table>" + "</body></html>";

		return html;

	}

	/**
	 * @param inStream
	 * @param fileName
	 * @param filePath
	 * @return
	 * @throws APIException
	 * @throws IOException
	 */
	public boolean bCheckFile(InputStream inStream, String fileName, String filePath) throws APIException, IOException {
		File file;
		OutputStream outStream = null;

		file = getAttachmentFile(inStream, outStream, fileName, filePath);
		logger.debug("getAttachmentFile method executed successfully");

		if (file.isFile())
			return true;

		return false;
	}

	/**
	 * Gets the file attachment from the File input stream, output stream
	 * 
	 * @param inStream
	 * @param outStream
	 * @param fileName
	 * @param filePath
	 * @return
	 * @throws APIException
	 * @throws IOException
	 */
	public File getAttachmentFile(InputStream inStream, OutputStream outStream, String fileName, String filePath)
			throws APIException, IOException {
		int read = 0;
		byte[] bytes = new byte[1024];

		filePath = filePath + fileName;
		File targetFile = new File(filePath);
		outStream = new FileOutputStream(targetFile);

		while ((read = inStream.read(bytes)) != -1) {
			outStream.write(bytes, 0, read);
			// logger.info(bytes);
		}

		outStream.close();
		return targetFile;
	}

}
