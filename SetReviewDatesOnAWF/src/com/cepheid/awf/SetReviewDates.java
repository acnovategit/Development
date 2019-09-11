package com.cepheid.awf;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;
import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.IAgileClass;
import com.agile.api.IAgileSession;
import com.agile.api.IChange;
import com.agile.api.IDataObject;
import com.agile.api.IItem;
import com.agile.api.INode;
import com.agile.api.IRow;
import com.agile.api.IStatus;
import com.agile.api.ITable;
import com.agile.api.ItemConstants;
import com.agile.px.ActionResult;
import com.agile.px.ICustomAction;
import com.agile.util.GenericUtilities;

/**
 * 
 * @author Supriya
 * This PX retrieves date on which AWF moved to Review/Approve/Implement-Review in PST timezone and sets it on 
 * Move to Review Date/Move to Approve Date/Move to Implement-Review Date respectively
 * 
 * This PX also updates the Last Reviewed date attribute on all affected items(which belongs to Documents class) with AWF release date
 *
 */

public class SetReviewDates implements ICustomAction {
	static Logger logger = Logger.getLogger(SetReviewDates.class);
	ActionResult actionResult = new ActionResult();
	public static String awfMessagesListName = "AWFMessagesList";

	@Override
	public ActionResult doAction(IAgileSession session, INode arg1, IDataObject dataObject) {
		String result = "";
		try {
			// Initialize logger
			GenericUtilities.initializeLogger(session);

			// Get Agile List Values
			HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();
			awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);

			// Get AWF Object
			IChange AWF = (IChange) dataObject;
			logger.debug("AWF is:" + AWF);

			if (AWF != null) {

				// Get Today's date based on PST Timezone and MM/dd/yyyy'T'HH:mm:ss'Z' format
				String dateBasedOnTimeZone = GenericUtilities.getDateBasedOnTimeZoneAndFormat(
						awfMessagesList.get("TIME_ZONE").toString(), awfMessagesList.get("DATE_FORMAT").toString(),
						new Date());
				logger.debug("Date based on PST Timezone is:" + dateBasedOnTimeZone);

				String dateInReqdFormat = "";
				if (dateBasedOnTimeZone != null && !dateBasedOnTimeZone.equals("")) {
					dateInReqdFormat = dateBasedOnTimeZone.substring(0, 10);
					logger.debug("Date in required format is:" + dateInReqdFormat);
				}

				// Get current status
				IStatus currentStatus = AWF.getStatus();
				logger.debug("Current Status is:" + currentStatus);

				if (currentStatus != null && dateInReqdFormat != null && !dateInReqdFormat.equals("")) {

					// If status is Review,Set Move to Review Date
					if (currentStatus.toString()
							.equalsIgnoreCase(awfMessagesList.get("AWF_REVIEW_STATUS").toString())) {
						AWF.setValue(Integer.parseInt(awfMessagesList.get("REVIEW_DATE_ATTRID").toString()),
								dateInReqdFormat);
						result = String.format(awfMessagesList.get("AWF_REVIEW_DATE_IS_SET").toString(),awfMessagesList.get("AWF_REVIEW_STATUS").toString());

					}
					// If status is Approve,Set Move to Approve Date
					else if (currentStatus.toString()
							.equalsIgnoreCase(awfMessagesList.get("AWF_APPROVE_STATUS").toString())) {
						AWF.setValue(Integer.parseInt(awfMessagesList.get("APPROVE_DATE_ATTRID").toString()),
								dateInReqdFormat);
						result = String.format(awfMessagesList.get("AWF_REVIEW_DATE_IS_SET").toString(),awfMessagesList.get("AWF_APPROVE_STATUS").toString());

					}
					// If status is Implement-Review,Set Move to Implement-Review Date
					else if (currentStatus.toString()
							.equalsIgnoreCase(awfMessagesList.get("AWF_IMPLEMENT_REVIEW_STATUS").toString())) {
						AWF.setValue(Integer.parseInt(awfMessagesList.get("IMPLEMENT_REVIEW_DATE_ATTRID").toString()),
								dateInReqdFormat);
						result = String.format(awfMessagesList.get("AWF_REVIEW_DATE_IS_SET").toString(),awfMessagesList.get("AWF_IMPLEMENT_REVIEW_STATUS").toString());

					} 
					//If status is released,Update Last Reviewed Date of all affected items(which belongs to document class) with AWF release date 
					else if (currentStatus.toString()
							.equalsIgnoreCase(awfMessagesList.get("AWF_RELEASED_STATUS").toString())) {

						//Get affected items table
						ITable affectedItems = AWF.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);

						if (affectedItems != null && affectedItems.size()>0) {

							// Get Subclasses under Documents class
							ArrayList<IAgileClass> documentSubclassesList = new ArrayList<IAgileClass>();
							IAgileClass[] documentsSubClasses = session.getAdminInstance()
									.getAgileClass(ItemConstants.CLASS_DOCUMENTS_CLASS).getSubclasses();
							logger.debug("Sub classes Length:" + documentsSubClasses.length);
							int i = 0;
							while (i < documentsSubClasses.length) {
								if (documentsSubClasses[i] != null) {
									documentSubclassesList.add(documentsSubClasses[i]);
									i++;
								}
							}
							logger.debug("Documents Subclasses List:" + documentSubclassesList);

							@SuppressWarnings("unchecked")
							Iterator<IItem> it = affectedItems.iterator();
							IRow row = null;
							String strItem = "";
							IItem item = null;
							boolean isDocument = false;
							String newRev = "";
							
							//Iterate through the affected items
							while (it.hasNext()) {
								row = (IRow) it.next();
								
								if (row != null) {
									
									strItem = (String) row.getValue(Integer
											.parseInt(awfMessagesList.get("AFFECTEDITEM_NUM_ATTRID").toString()));
									logger.debug("Item is:" + strItem);

									if (strItem != null && !strItem.equals("")) {
										
										item = (IItem) session.getObject(ItemConstants.CLASS_ITEM_BASE_CLASS, strItem);
										logger.debug("Item is:" + item);
										
										// If the affected item belongs to Documents class, set Last reviewed date
										if (item!=null) {
											logger.debug("Item class is:" + item.getAgileClass());
																	
											if(documentSubclassesList.contains(item.getAgileClass())) {
												
												// Get New Rev
												newRev = (String) row.getValue(Integer.parseInt(
														awfMessagesList.get("AFFECTEDITEM_NEWREV_ATTRID").toString()));
												logger.debug("New Rev is:" + newRev);
												
												if(newRev!=null && !newRev.equals("")) {
													item.setRevision(newRev);
													logger.debug("Item's revision is"+item.getRevision());
													
													item.setValue(Integer.parseInt(awfMessagesList.get("DOC_ITEM_LAST_REVIEWED_DATE_ATTRID").toString()),
															dateInReqdFormat);
													isDocument  = true;
												}
												
											
											}
										}
									}
								}

							}
							
							if(isDocument==true) {
								result = awfMessagesList.get("LAST_REVIEWED_DATE_IS_SET").toString();
								
							}else {
								result = awfMessagesList.get("NO_DOCUMENT").toString();
							}

						}
					}else {
						logger.info("PX triggered at Invalid Status");
					}
				} else {
					result = awfMessagesList.get("DATE_CONVERSION_FAILED").toString();
				}
				logger.debug("Result:" + result);
			}
		} catch (APIException e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Failed due to:" + e.getMessage());
		} catch (Exception e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Failed due to:" + e.getMessage());
		}

		return new ActionResult(ActionResult.STRING, result);
	}

}
