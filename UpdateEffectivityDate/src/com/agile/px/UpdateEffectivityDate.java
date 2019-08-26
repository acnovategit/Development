package com.cepheid.awf;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
import com.agile.px.ActionResult;
import com.agile.px.EventActionResult;
import com.agile.px.IEventAction;
import com.agile.px.IEventInfo;
import com.agile.px.IObjectEventInfo;
import com.agile.util.CommonUtil;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.Message.RecipientType;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;



public class UpdateEffectivityDate implements IEventAction {
	

	
	static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(UpdateEffectivityDate.class);
	static boolean bUpdated=false;

	
	public EventActionResult doAction(IAgileSession session,INode node,IEventInfo eventinfo){
		ActionResult actionResult = new ActionResult();
		IChange eco = null;
		String sErrorMessage ="";
		Map<String,List<String>> mapError= new HashMap<String,List<String>>();
		
		try{
			CommonUtil.initAppLogger(UpdateEffectivityDate.class, session);
			InputStream inStream=null;
			IRow row=null;
		
			String fileName="",filePath="/ora01/APP/agilevault/",oldEffectiveDate="",effectiveDate="";
			IDataObject part=null;
			IFileFolder fileFolder=null;
			OutputStream outStream=null;
			File file=null;
			IObjectEventInfo info = (IObjectEventInfo) eventinfo;
			eco = (IChange) info.getDataObject();
			IStatus ecoStatus=eco.getStatus();
			String oldRevision="";
			String sDocuNumber = "";
			logger.debug("Current Status" +ecoStatus);
		
			//String headerData = null;
			if(ecoStatus.toString().equals("Implement-Review")){
											
				ITable affectedItems=eco.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
			//	logger.info("Before iterotor");
				Iterator<?> affectedItemsIterator;
				affectedItemsIterator=affectedItems.iterator();		
					
				//	sLine += " Before looping affected items";		 
				while(affectedItemsIterator.hasNext()){
							
					row = (IRow) affectedItemsIterator.next();
					part = row.getReferent();
					logger.debug("Part is :" +part);
					effectiveDate= now();//row.getValue(ChangeConstants.ATT_AFFECTED_ITEMS_EFFECTIVE_DATE).toString();
				
					logger.debug("Effective Date" +effectiveDate);
					
				//Iterate Attachments Table
				ITable attachments=part.getTable(ItemConstants.TABLE_ATTACHMENTS);
				Iterator<?> attachmentsIterator=attachments.iterator();
				List<String> lsErrorMessages = new ArrayList<String>();
			
				while(attachmentsIterator.hasNext()){
					bUpdated = false;
					row = (IRow) attachmentsIterator.next();
					fileFolder = (IFileFolder)row.getReferent();
				
					fileName=row.getName();
					logger.debug("File Name is :" +fileName);
                    if(!(fileName.endsWith(".docx")))
                    {
                    	if(fileName.endsWith(".xlsx")||fileName.endsWith(".xls")||fileName.endsWith(".pdf"))
                    	{
                    		String sError = "Due to improper file type, "+fileName+" for Part "+part.getName()+" is not updated with effecitve date in its header";
                    		lsErrorMessages.add(sError);
                    		sErrorMessage+="Due to improper file type, "+fileName+" for Part "+part.getName()+" is not updated with effecitve date in its header"+"</br>"; 
                    	}
                    	continue;	
                    }
					
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
							actionResult = new ActionResult(ActionResult.EXCEPTION,e);
							logger.error(e.getMessage());
							return new EventActionResult(eventinfo, actionResult);
							
						}
						catch (Exception e) {
							e.printStackTrace();
							actionResult = new ActionResult(ActionResult.EXCEPTION,e);
							logger.error(e.getMessage());
							return new EventActionResult(eventinfo, actionResult);
							
						} 
						finally {	
							inStream.close();
							
						}
						FileInputStream fis = new FileInputStream(filePath+fileName);
					
						XWPFDocument xdoc=new XWPFDocument(OPCPackage.open(fis));
						XWPFHeaderFooterPolicy policy = new XWPFHeaderFooterPolicy(xdoc);
						//read header
						XWPFHeader header = policy.getDefaultHeader();
					    String headerData=header.getText().toString();
		 
						Map<String, String> map = new HashMap<String, String>();
						String[] headerDataSplit = headerData.split("\n");
						String[] t = null, docarr = null;
						for (String s : headerDataSplit) {
							logger.info(s);
							if(s.contains("Effectiv")){
								if (s.contains(":"))
								{
									t = s.split(":");
								}
								else if (s.contains(" "))
								{
									t = s.split(" ");
								}
								 oldEffectiveDate = t[1];
								logger.info("OldEffective date"+oldEffectiveDate);
						
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
						
						if(!(part.getName().equalsIgnoreCase(sDocuNumber)))
						{  
							logger.info("Skipping the attachment "+fileName+" since the Document number in header "+sDocuNumber+"  does not match with the document number "+part.getName());
							//((IAttachmentFile)row).setFile(new File(filePath+fileName));
							((ICheckoutable) row).cancelCheckout();
						     logger.info("File cancel checked out");
							continue;	
						}
							oldEffectiveDate = oldEffectiveDate.trim();
					
						effectiveDate = effectiveDate.trim();
						//effectiveDate = "Effective Date:" + effectiveDate;
						logger.debug("New Effective Date:"+effectiveDate);
						xdoc=setHeader(xdoc, oldEffectiveDate, effectiveDate);
				        if(!bUpdated)
				        {
				        String sError = "Could not update Effective Date for "	+fileName+" for Part "+part.getName()+"";
				        lsErrorMessages.add(sError);
				        sErrorMessage+= "Could not update Effective Date for "	+fileName+" for Part "+part.getName()+" </br>";
				        }
				        logger.info("File name:"+fileName+" bupdated="+bUpdated);
				        OutputStream os = new FileOutputStream(new File(filePath+fileName));
						xdoc.write(os);
						//Set the new file
						((IAttachmentFile)row).setFile(new File(filePath+fileName));
						((ICheckoutable) row).checkIn();
					     logger.info("File checked in");
						//break;
					}
					else{
						logger.debug("File Folder is already checked out.Please Cancel Checkout or check in");
						actionResult = new ActionResult(ActionResult.EXCEPTION,"File Folder is already checked out.Please Cancel Checkout or check in");
						//return actionResult;
					}
				}
				if(!(lsErrorMessages.isEmpty()))
				mapError.put(part.getName().toString(),lsErrorMessages);
				}
			}
			else{
				logger.debug("ECO status is not in implemenet-review Status");
				actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception("Current ECO status is "+ecoStatus.toString()+ " Please move the AWF to Implement-Review Status"));
				return new EventActionResult(eventinfo, actionResult);
				//return actionResult;
			}
			actionResult = new ActionResult(ActionResult.STRING,"Effective Date Updated and set to:"+effectiveDate);
		}
		catch (APIException e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception("API Exception:"+e.getRootCause()));
			e.printStackTrace();
			logger.error("Creation of Extension failed due to"+e.getMessage());
			return new EventActionResult(eventinfo, actionResult);
		}
		catch(Exception e){
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception("General Exception"+e.getCause()));
			e.printStackTrace();
			logger.error("Creation of Extension failed due to"+e.getMessage());
			return new EventActionResult(eventinfo, actionResult);
		}
		
		
		logger.info(sErrorMessage);
		
		
		if(!(mapError.isEmpty()))
			try {
				sendMail("anup.khanvilkar@acnovate.com","Harikishan.Annam@cepheid.com", mapError,eco.getName());
			} catch (APIException e) {
				// TODO Auto-generated catch block
				logger.debug(e);
			}
		return new EventActionResult(eventinfo, actionResult);
	}

	public File getAttachmentFile( InputStream inStream, OutputStream outStream,String fileName, String filePath)
			throws APIException, IOException{
		int read = 0;
		byte[] bytes = new byte[1024];


		filePath=filePath+fileName;
		File targetFile = new File(filePath);
		outStream = new FileOutputStream(targetFile);

		while ((read = inStream.read(bytes)) != -1) {
			outStream.write(bytes, 0, read);
		}

		outStream.close();
		return targetFile;
	}
	
	public static XWPFDocument setHeader(XWPFDocument document, String token, String textToReplace){
		XWPFHeaderFooterPolicy policy= document.getHeaderFooterPolicy();
		logger.info("Got policy");
		XWPFHeader header = policy.getDefaultHeader();
		logger.info("Got header");
		logger.info(textToReplace);
		bUpdated = replaceInParagraphs(header.getParagraphs(), token, textToReplace);
		
		 if(bUpdated)
		  {
		  logger.info("Replaced");
		  }
		 else
		{	
		bUpdated = 	 replaceInParagraphsMod(header.getParagraphs(), token, textToReplace);
		if(bUpdated)
		logger.info("Now replaced");
		}
		
		
		logger.info("replaced");
		return document;
	}

	private static boolean replaceInParagraphs(List<XWPFParagraph> paragraphs, String placeHolder, String replaceText){
		boolean bFlag = false;
		logger.info(placeHolder);
		for (XWPFParagraph xwpfParagraph : paragraphs) {
			List<XWPFRun> runs = xwpfParagraph.getRuns();
			
			for (XWPFRun run : runs) {
				
				String runText;
				try {		
				runText = run.getText(run.getTextPosition());
				}
				catch(Exception e)
				{
					logger.error(e);
					continue;
				}
				logger.info("Runtext is:"+runText+" Placeholder is:"+placeHolder );
				if(placeHolder !="" && !placeHolder.isEmpty() && placeHolder!= null){
					
					if(runText != null && Pattern.compile(placeHolder, Pattern.CASE_INSENSITIVE).matcher(runText).find()){
						
						if(runText.contains("Effectiv"))
						runText = "Effective Date: "+ replaceText;
						else
						runText =  replaceText;
						
						bFlag = true;
					}
				
				}
				logger.info("setting run text:"+runText);
				run.setText(runText, 0);
			}
		}
	return bFlag;
	}


	private static boolean replaceInParagraphsMod(List<XWPFParagraph> paragraphs, String placeHolder, String replaceText){
		boolean bFlag = false;
		logger.info(placeHolder);
		for (XWPFParagraph xwpfParagraph : paragraphs) {
			logger.info("Para text="+xwpfParagraph.getText());
			
			if(xwpfParagraph.getText().contains(placeHolder))
			{
				String paratext = xwpfParagraph.getText();
				String replacedPara = paratext.replace(placeHolder, replaceText);
				
				int size = xwpfParagraph.getRuns().size();
			    for (int i = 0; i < size; i++) {
			    	xwpfParagraph.removeRun(0);
			    }
				
			    
			    String[] replacementTextSplitOnCarriageReturn = replacedPara.split("\n");

			    for (int j = 0; j < replacementTextSplitOnCarriageReturn.length; j++) {
			        String part = replacementTextSplitOnCarriageReturn[j];
		            logger.info("line:"+part);
			        XWPFRun newRun = xwpfParagraph.insertNewRun(j);
			        newRun.setText(part);

			        if (j+1 < replacementTextSplitOnCarriageReturn.length) {
			            newRun.addCarriageReturn();
			        }
			    } 
			    bFlag = true;
			    
				
			}
				
			
		}
	return bFlag;
	}
	
	

	public String now() {
		String DATE_FORMAT_NOW = "MM-dd-yyyy";
	Calendar cal = Calendar.getInstance();
	SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
	return sdf.format(cal.getTime());
	}

	public static void sendMail(String from, String to, Map<String,List<String>> mapError, String sECONumber) {
		try {
			logger.info("inside send mail");
			Properties properties = System.getProperties();
			properties.setProperty("mail.smtp.host", "10.168.37.16");
			Session session = Session.getInstance(properties, (Authenticator) null);
			MimeMessage message = new MimeMessage(session);
			message.setFrom(new InternetAddress(from));
			InternetAddress[] addressTo = new InternetAddress[]{new InternetAddress(to)};
			message.setRecipients(RecipientType.TO, addressTo);
			message.setSubject("AWF "+sECONumber+" Released: Effective Date Update");
			MimeBodyPart messageBodyPart = new MimeBodyPart();
			// String [] errors=sMessage.split("</br>");
	          //Map<Integer,String> map = new HashMap<Integer,String>();
	      
	          //send message  
	          String html="<html><head>"
	                  + "<title>"+message.getSubject()+"</title>"
	                  + "</head>"+"<LINK REL='stylesheet' HREF='stylesheet/fac_css.css' TYPE='text/css'>"
	                  + "<body>"
	                  +"<table width='900' cellpadding='0' cellspacing='0' border='0'>"
	                  +"<tr><td class ='text12' width='100%'><br>AWF: "+sECONumber+" Effectivity Date Update Issues</td></tr><tr>"
	                  +"<td height='5'></td></tr>"
	                  +"<tr><td></td></tr>"
	                  +"<tr><td height='5'></td></tr>"
	                  +"<tr><td><table border='1' width='800' cellpadding='2' cellspacing='1' bgColor='#B6AFA9' style='border-collapse: collapse' bordercolor='#EBDA2A' align='left'>"
	                  +"<tr bgColor=#CD919E class='centerheading' align='left'>"
	                          +"<td width='30' style='color: #FFFFFF;'><b>S.No.</b></td>"
	                          +"<td width='30' style='color: #FFFFFF;'><b>Part</b></td>"
	                          +"<td width='60' style='color: #FFFFFF;'><b>Message</b></td>"
	                                             
	                 + "</tr>";
	          int i=1;
	          for (Map.Entry<String,List<String>> entry : mapError.entrySet())
           	{
                List<String> check=entry.getValue();
  	  			Iterator<String> it = check.iterator();
  	  			while(it.hasNext()){
  	  			
              	  html=html+"<tr align='center'>"+"<td width='30' style='color: #EEE9E9;'>"+i+"</b></td>"
              	  +"<td width='30' style='color: #EEE9E9;'>"+entry.getKey()+"</td>"
              	  +"<td width='60' style='color: #EEE9E9;'>"+it.next()+"</td>";
                  
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
	               message.setContent(html, "text/html");
			
			
			//messageBodyPart.setText(sMessage);
			//Multipart multipart = new MimeMultipart();
		//	multipart.addBodyPart(messageBodyPart);
			//message.setContent(multipart);
			Transport.send(message);
			logger.info("Message Send.....");
		} catch (AddressException var10) {
			var10.printStackTrace();
		} catch (MessagingException var11) {
			var11.printStackTrace();
		} catch (Exception var12) {
			var12.printStackTrace();
		}

	}

	






}
