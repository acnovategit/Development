package com.cepheid.awf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
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
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPicture;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
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
import com.agile.px.ActionResult;
import com.agile.px.EventActionResult;
import com.agile.px.IEventAction;
import com.agile.px.IEventInfo;
import com.agile.px.IObjectEventInfo;
import com.agile.util.CommonUtil;
import com.microsoft.schemas.office.office.CTLock;
import com.microsoft.schemas.office.office.STConnectType;
import com.microsoft.schemas.vml.CTFormulas;
import com.microsoft.schemas.vml.CTGroup;
import com.microsoft.schemas.vml.CTH;
import com.microsoft.schemas.vml.CTHandles;
import com.microsoft.schemas.vml.CTPath;
import com.microsoft.schemas.vml.CTShape;
import com.microsoft.schemas.vml.CTShapetype;
import com.microsoft.schemas.vml.CTTextPath;
import com.microsoft.schemas.vml.STExt;
import com.microsoft.schemas.vml.STTrueFalse;
//import com.util.LoggerImpl;


public class UpdateAbsoluteInFile implements IEventAction {


	static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(UpdateAbsoluteInFile.class);
	private static String log;
	public EventActionResult doAction(IAgileSession session,INode node,IEventInfo eventinfo){
		ActionResult actionResult = new ActionResult();
		CommonUtil.initAppLogger(UpdateAbsoluteInFile.class, session);
		InputStream inStream=null;
		IRow row=null;
		ICell oldRevisionCell=null;
		String fileName="",filePath="/ora01/APP/agilevault/",oldRevision="",rev="";
		String outfilePath ="/ora01/APP/agilevault/staging/";
		IDataObject part=null,ecoNumber=null,item=null;
		IFileFolder fileFolder=null;
		OutputStream outStream=null;
		String sErrorMessage = "";
		File file=null;
		IChange eco=null;
		String sDocuNumber = "";
		String oldEffectiveDate="";
		Map<String,List<String>> mapError= new HashMap<String,List<String>>();
	//	Map<String><String> hMessageMap;
		 
		try{
			
			IObjectEventInfo info = (IObjectEventInfo) eventinfo;
			eco = (IChange) info.getDataObject();
			

			//IChange eco=(IChange)dataObject;
			IStatus ecoStatus=eco.getStatus();
			String effectiveDate = null;
			XWPFHeaderFooterPolicy policy;
			XWPFParagraph[] pars;
			
		//	logger.debug("Current Status" +ecoStatus);
			if(ecoStatus.toString().equals("Implement-Review")){
				ITable affectedItems=eco.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
				Iterator<?> affectedItemsIterator=affectedItems.iterator();
				
				while(affectedItemsIterator.hasNext()){
					row = (IRow) affectedItemsIterator.next();
					part = (IDataObject)row.getReferent();
		         
				//Update Absolute
				HashMap<IDataObject, String> partRevision=new HashMap<>();
				ITable changeHistoryTable=part.getTable(ItemConstants.TABLE_CHANGEHISTORY);
				
				Iterator<?> changeHistoryIterator=changeHistoryTable.iterator();
				
				while(changeHistoryIterator.hasNext()){
					row = (IRow) changeHistoryIterator.next();
					ecoNumber = row.getReferent();
		
				
						ITable affectedItemsTable=ecoNumber.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
						Iterator<?> affectedItemsTableIterator=affectedItemsTable.iterator();
																
						while(affectedItemsTableIterator.hasNext()){
							row = (IRow) affectedItemsTableIterator.next();
							item = row.getReferent();
							
							if(!(item.getName().toString().equalsIgnoreCase(part.getName().toString())))
							{   logger.info("Part "+item.getName()+" number on this change does not matches with Part "+part.getName());
								continue;
							}
					//		logger.debug("Item is :" +item);
							ITable attachmentsTable=item.getTable(ItemConstants.TABLE_ATTACHMENTS);
							Iterator<?> attachmentsTableIterator=attachmentsTable.iterator();
							
							List<String> lsErrorMessages = new ArrayList<String>();
							while(attachmentsTableIterator.hasNext()){
								row = (IRow) attachmentsTableIterator.next();
								fileFolder = (IFileFolder)row.getReferent();
						//		logger.debug("File Folder is :" +fileFolder);
								fileName=row.getName();
						//		logger.debug("File Folder Name is :" +fileName);

								 if(!(fileName.endsWith(".docx")))
				                    {
				                    	if(fileName.endsWith(".xlsx")||fileName.endsWith(".xls")||fileName.endsWith(".pdf"))
				                    	{
				                    		String sError = "Due to improper file type, "+fileName+" for Part "+part.getName()+" is not updated with Obsolete Watermark in its header";
				                    		lsErrorMessages.add(sError);
				                    		sErrorMessage+="Due to improper file type, "+fileName+" for Part "+part.getName()+" is not updated with Obsolete Watermark in its header"+"</br>"; 
				                    	}
				                    	continue;	
				                    }
								
								
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
									//logger.debug("FIS " +fis1.toString());
									XWPFDocument xdoc1=new XWPFDocument(OPCPackage.open(fis1));
								    logger.debug("XWPF document created from FIS" );
									
									policy =  xdoc1.getHeaderFooterPolicy();// new XWPFHeaderFooterPolicy(xdoc1);
								    logger.info("Policy="+policy.toString());
								
								    XWPFHeader header = policy.getDefaultHeader();
								    String headerData=header.getText().toString();
									 
									Map<String, String> map = new HashMap<String, String>();
									String[] headerDataSplit = headerData.split("\n");
									String[] t = null, docarr = null;
									for (String s : headerDataSplit) {
										 logger.info(s);
																			  
										if(s.contains("Document Number")){
											if (s.contains(":"))
											{
												docarr = s.split(":");
											}
																					
											sDocuNumber = docarr[1];
											sDocuNumber= sDocuNumber.trim();
											logger.info("Document Number"+sDocuNumber);
									
										} 
									}
									
									if(!(part.getName().equalsIgnoreCase(sDocuNumber)))
									{  
										logger.info("Skipping the attachment "+fileName+" since the Document number in header "+sDocuNumber+"  does not match with the document number "+part.getName());
									//	((IAttachmentFile)row).setFile(new File(filePath+fileName));
										((ICheckoutable) row).cancelCheckout();
									     logger.info("File cancel checked out");
										continue;	
									}
									/*String sWatermark = "Obsolete";
									policy.createWatermark(sWatermark);
								
									logger.info("Watermark created" + sWatermark);*/
									
									
									pars = new XWPFParagraph[1];
									
									logger.debug("Pars created");
									pars[0] = getWatermarkParagraph("Obsolete", 1, xdoc1);
									logger.debug("After watermark"+ pars[0].getText());
									try {
										 policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT, pars);
											log += "create header1"+pars[0].getText();
											logger.debug(log);
									} catch (Exception e) {
										
										log += "Exception of default create header1";
										logger.debug(log);
										e.printStackTrace();
									}
									pars[0] = getWatermarkParagraph("Obsolete", 2, xdoc1);
									try {
										policy.createHeader(XWPFHeaderFooterPolicy.FIRST, pars);
										log += "create header2"+pars[0].getText();
										logger.debug(log);
									} catch (Exception e) {
										log += "Exception of First create header2";
										e.printStackTrace();
										logger.debug(log);
									}
									pars[0] = getWatermarkParagraph("Obsolete", 3, xdoc1);
									try {
										policy.createHeader(XWPFHeaderFooterPolicy.EVEN, pars);
										log += "create header3"+pars[0].getText();
										logger.debug(log);
									} catch (Exception e) {
										log += "Exception of even create header3";
										e.printStackTrace();
										logger.debug(log);
									}
																		
								     OutputStream os1 = new FileOutputStream(new File(outfilePath+fileName));
									 xdoc1.write(os1);    
									 
									
									//Set the new file
									((IAttachmentFile)row).setFile(new File(outfilePath+fileName));
									((ICheckoutable) row).checkIn();
							        logger.debug("Folder is Checked in");
									
								}
								else{
							//		logger.debug("File Folder is already checked out.Please Cancel Checkout or check in");
									actionResult = new ActionResult(ActionResult.EXCEPTION,new Exception("File Folder is already checked out.Please Cancel Checkout or check in") );
									return new EventActionResult(eventinfo, actionResult);
								}
							}
							if(!(lsErrorMessages.isEmpty()))
								mapError.put(ecoNumber.getName().toString()+"|"+part.getName().toString(),lsErrorMessages);
							
						}
						
				
				}
				
			}
			}
			else{
			logger.debug("ECO status is not in implemeneted Status");
			//	String sEffectiveDateUpdateResult = sUpdateEffectivityDate(effectiveDate, part);
				actionResult = new ActionResult(ActionResult.EXCEPTION,new Exception("ECO status is not in Implemented-Review Status") );
				return new EventActionResult(eventinfo, actionResult);
			}
			//String sEffectiveDateUpdateResult = sUpdateEffectivityDate(effectiveDate, part);
			logger.info("Attachment of Previous revision updated with Obsolete Watermark");
			actionResult = new ActionResult(ActionResult.STRING,"Attachment of Previous revision updated with Obsolete Watermark");
		}
		catch (APIException e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
	logger.error("API exception: Execution of Extension failed due to"+e.getMessage());
	return new EventActionResult(eventinfo, actionResult);
		}
		catch(Exception e){
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
	logger.error(" Exception Execution of Extension failed due to"+e.getMessage());
	return new EventActionResult(eventinfo, actionResult);
		}
		logger.debug(sErrorMessage);
		if(!(mapError.isEmpty()))
			try {
				
				sendMail("anup.khanvilkar@acnovate.com", "Harikishan.Annam@cepheid.com", mapError,eco.getName());
			} catch (APIException e) {
				e.printStackTrace();
				logger.debug(e);
			}
		
		return new EventActionResult(eventinfo, actionResult);
	}

	public File getAttachmentFile( InputStream inStream, OutputStream outStream,String fileName, String filePath)
			throws APIException, IOException{
		int read = 0;
		byte[] bytes = new byte[1024];

	//	logger.debug("InStream data:"+inStream.available());
		filePath=filePath+fileName;
		File targetFile = new File(filePath);
		outStream = new FileOutputStream(targetFile);

		while ((read = inStream.read(bytes)) != -1) {
			outStream.write(bytes, 0, read);
		}

	//	logger.debug("instream.available(): " +inStream.available());
	//	logger.debug("File Name in method: "+targetFile.getName());
		outStream.close();
		return targetFile;


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
			message.setSubject("AWF "+sECONumber+" Released: Setting Obsolete Watermark");
			MimeBodyPart messageBodyPart = new MimeBodyPart();
			// String [] errors=sMessage.split("</br>");
	          //Map<Integer,String> map = new HashMap<Integer,String>();
	      
	          //send message  
	          String html="<html><head>"
	                  + "<title>"+message.getSubject()+"</title>"
	                  + "</head>"+"<LINK REL='stylesheet' HREF='stylesheet/fac_css.css' TYPE='text/css'>"
	                  + "<body>"
	                  +"<table width='900' cellpadding='0' cellspacing='0' border='0'>"
	                  +"<tr><td class ='text12' width='100%'><br>AWF: "+sECONumber+" Setting Obsolete Issues</td></tr><tr>"
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

	public static XWPFParagraph getWatermarkParagraph(String text, int idx, XWPFDocument doc) {
		//String log="";
		CTP p = CTP.Factory.newInstance();
		log+="Instance Created";
		byte[] rsidr = doc.getDocument().getBody().getPArray(0).getRsidR();
		byte[] rsidrdefault = doc.getDocument().getBody().getPArray(0).getRsidRDefault();
		p.setRsidP(rsidr);
		p.setRsidRDefault(rsidrdefault);
		CTPPr pPr = p.addNewPPr();
		pPr.addNewPStyle().setVal("Header");
		// start watermark paragraph
		CTR r = p.addNewR();
		CTRPr rPr = r.addNewRPr();
		rPr.addNewNoProof();
		CTPicture pict = r.addNewPict();
		CTGroup group = CTGroup.Factory.newInstance();
		CTShapetype shapetype = group.addNewShapetype();
		shapetype.setId("_x0000_t136");
		shapetype.setCoordsize("1600,21600");
		shapetype.setSpt(136);
		shapetype.setAdj("10800");
		shapetype.setPath2("m@7,0l@8,0m@5,21600l@6,21600e");
		CTFormulas formulas = shapetype.addNewFormulas();
		formulas.addNewF().setEqn("sum #0 0 10800");
		formulas.addNewF().setEqn("prod #0 2 1");
		formulas.addNewF().setEqn("sum 21600 0 @1");
		formulas.addNewF().setEqn("sum 0 0 @2");
		formulas.addNewF().setEqn("sum 21600 0 @3");
		formulas.addNewF().setEqn("if @0 @3 0");
		formulas.addNewF().setEqn("if @0 21600 @1");
		formulas.addNewF().setEqn("if @0 0 @2");
		formulas.addNewF().setEqn("if @0 @4 21600");
		formulas.addNewF().setEqn("mid @5 @6");
		formulas.addNewF().setEqn("mid @8 @5");
		formulas.addNewF().setEqn("mid @7 @8");
		formulas.addNewF().setEqn("mid @6 @7");
		formulas.addNewF().setEqn("sum @6 0 @5");
		CTPath path = shapetype.addNewPath();
		path.setTextpathok(STTrueFalse.T);
		path.setConnecttype(STConnectType.CUSTOM);
		path.setConnectlocs("@9,0;@10,10800;@11,21600;@12,10800");
		path.setConnectangles("270,180,90,0");
		CTTextPath shapeTypeTextPath = shapetype.addNewTextpath();
		shapeTypeTextPath.setOn(STTrueFalse.T);
		shapeTypeTextPath.setFitshape(STTrueFalse.T);
		CTHandles handles = shapetype.addNewHandles();
		CTH h = handles.addNewH();
		h.setPosition("#0,bottomRight");
		h.setXrange("6629,14971");
		CTLock lock = shapetype.addNewLock();
		lock.setExt(STExt.EDIT);
		CTShape shape = group.addNewShape();
		shape.setId("PowerPlusWaterMarkObject" + idx);
		shape.setSpid("_x0000_s102" + (4 + idx));
		shape.setType("#_x0000_t136");
		shape.setStyle(
				"position:absolute;margin-left:0;margin-top:0;width:415pt;height:207.5pt;z-index:-251654144;mso-wrap-edited:f;mso-position-horizontal:center;mso-position-horizontal-relative:margin;mso-position-vertical:center;mso-position-vertical-relative:margin");
		shape.setWrapcoords(
				"616 5068 390 16297 39 16921 -39 17155 7265 17545 7186 17467 -39 17467 18904 17467 10507 17467 8710 17545 18904 17077 18787 16843 18358 16297 18279 12554 19178 12476 20701 11774 20779 11228 21131 10059 21248 8811 21248 7563 20975 6316 20935 5380 19490 5146 14022 5068 2616 5068");
		shape.setFillcolor("black");
		shape.setStroked(STTrueFalse.FALSE);
		CTTextPath shapeTextPath = shape.addNewTextpath();
		shapeTextPath.setStyle("font-family:&quot;Cambria&quot;;font-size:1pt");
		shapeTextPath.setString(text);
		pict.set(group);
		// end watermark paragraph
		log+="execution completed" +p.getRsidP().toString();
		return new XWPFParagraph(p, doc);
	}
	

  


}
