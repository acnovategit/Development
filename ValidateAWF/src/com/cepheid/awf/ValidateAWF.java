package com.cepheid.awf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import org.apache.log4j.Logger;
import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.IAgileClass;
import com.agile.api.IAgileList;
import com.agile.api.IAgileSession;
import com.agile.api.ICell;
import com.agile.api.IChange;
import com.agile.api.IItem;
import com.agile.api.INode;
import com.agile.api.IRow;
import com.agile.api.ITable;
import com.agile.api.ItemConstants;
import com.agile.px.ActionResult;
import com.agile.px.EventActionResult;
import com.agile.px.EventConstants;
import com.agile.px.IEventAction;
import com.agile.px.IEventInfo;
import com.agile.px.IWFChangeStatusEventInfo;
import com.agile.util.GenericUtilities;

/**
 * 
 * @author Supriya 
 * Pre Event: This PX displays a exception message to user during Status change from Pending to review
 *     -if any Impact assessment attributes are not filled on eCR 
 *     -If any Data check list attribute is filled as No
 *     -if affected item is not added
 *     -if one or more disposition attributes on affected items is not filled
 *     -if phase is not filled on affected items
 *     -if new Rev is OBS and phase is not updated to Obsolete
 * Post Event: This PX adds the eCR to the relationship tab of AWF during status change from Pending to Review
 *
 */
public class ValidateAWF implements IEventAction {

	static Logger logger = Logger.getLogger(ValidateAWF.class);
	ActionResult actionResult = new ActionResult();

	public static String eCRToAWFAttributeIdsMappingListName = "ECRAWFAttributeIDsMappingList";
	public static String awfMessagesListName = "AWFMessagesList";

	@Override
	public EventActionResult doAction(IAgileSession session, INode arg1, IEventInfo eventInfo) {

		try {
			// Initialize logger
			GenericUtilities.initializeLogger(session);

			// Get Agile List Values
			HashMap<Object, Object> eCRToAWFAttributeIdsMappingList = new HashMap<Object, Object>();
			HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();
			eCRToAWFAttributeIdsMappingList = GenericUtilities.getAgileListValues(session,
					eCRToAWFAttributeIdsMappingListName);
			awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);
			IWFChangeStatusEventInfo info = (IWFChangeStatusEventInfo) eventInfo;

			// Get AWF Object Number
			IChange AWF = (IChange) info.getDataObject();
			logger.debug("AWF Object is:" + AWF);

			if (AWF != null) {

				// Get ECR Object on AWF
				String strECR = GenericUtilities.getSingleListAttributeValue(AWF,
						awfMessagesList.get("ECR_ATTRID").toString());
				logger.debug("eCR attribute value is:" + strECR);

				IChange eCR = null;
				if (strECR != null) {
					eCR = (IChange) session.getObject(IChange.OBJECT_TYPE, strECR);
					logger.debug("ECR is:" + eCR);
				}

				if (eCR != null) {

					// On pre event,validate affected items and Impact assessmen attribute values on
					// eCR
					if (eventInfo.getEventTriggerType() == EventConstants.EVENT_TRIGGER_PRE) {

						// Get eCR Impact assessment attribute Values
						HashMap<Object, Object> impactAssessmentAttributeValues = new HashMap<Object, Object>();
						impactAssessmentAttributeValues = GenericUtilities.getImpactAssessmentAttrValues(session,eCR, eCRToAWFAttributeIdsMappingList);
						logger.debug("Impact assessment attribute values are:" + impactAssessmentAttributeValues);

						// Get count of empty Impact assessment attribute Values on associated eCR
						int countOfEmptyAttrs = 0;
						countOfEmptyAttrs = GenericUtilities.getCountOfImpactAssessmentAttributes(
								awfMessagesList.get("NULL").toString(), impactAssessmentAttributeValues, session);
						logger.debug("count of empty Attributes is:" + countOfEmptyAttrs);
						
						// if count>0,throw exception
						if (countOfEmptyAttrs > 0) {
							actionResult = new ActionResult(ActionResult.EXCEPTION,
									new Exception(String.format(awfMessagesList.get("VALUES_NOT_FILLED_DURING_STATUS_CHANGE_OF_AWF").toString(),eCR.toString())));
						} 
						else {

							// Get DataChecklist attribute Ids
							String[] dataCheckListAttrIds = awfMessagesList.get("DATACHECKLIST_ATTRIDS").toString()
									.split(",");
							logger.debug("Data check list attribute array contains:"+Arrays.toString(dataCheckListAttrIds));

							String dataCheckListAttrValue = "";
							boolean dataCheckListFlag = false;

							// Iterate through each attribute Id and fetch data check list value
							for (String attrId : dataCheckListAttrIds) {

								dataCheckListAttrValue = GenericUtilities.getSingleListAttributeValue(AWF, attrId);
								logger.debug(
										"Data Check list attribute Value of "+attrId+ " is:" + dataCheckListAttrValue);

								// If any value is filled as No,set the flag to true
								if (dataCheckListAttrValue != null && !dataCheckListAttrValue.equals("")
										&& dataCheckListAttrValue
												.equalsIgnoreCase(awfMessagesList.get("NO").toString())) {
									dataCheckListFlag = true;
									break;
								}
							}
							
							//If any value is filled as No,throw exception
							if (dataCheckListFlag == true) {
								actionResult = new ActionResult(ActionResult.EXCEPTION,
										new Exception(awfMessagesList.get("DATA_CHECKLIST_MSG").toString()));
							} else {

								// If no affected items are added,throw exception
								ITable affectedItems = AWF.getTable(ChangeConstants.TABLE_AFFECTEDITEMS);
								if (affectedItems != null) {
									logger.debug("Affected items size is:" + affectedItems.size());
									if (affectedItems.size() == 0) {
										actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(
												awfMessagesList.get("AFFECTED_ITEMS_MANDATORY").toString()));

									} else {

										// Get Subclasses under PARTS class
										ArrayList<IAgileClass> partsSubclassesList = new ArrayList<IAgileClass>();
										IAgileClass[] partsSubClasses = session.getAdminInstance()
												.getAgileClass(ItemConstants.CLASS_PARTS_CLASS).getSubclasses();
										logger.debug("Sub classes Length:" + partsSubClasses.length);
										int i = 0;
										while (i < partsSubClasses.length) {
											if (partsSubClasses[i] != null) {
												partsSubclassesList.add(partsSubClasses[i]);
												i++;
											}
										}
										logger.debug("Parts Subclasses List:" + partsSubclassesList);

										HashSet<String> itemsList = new HashSet<String>();
										HashSet<IItem> itemsWithoutPhase = new HashSet<IItem>();
										HashSet<IItem> obsoleteRevItemsList = new HashSet<IItem>();
										String stock = "";
										String onOrder = "";
										String workInProgress = "";
										String finishedGoods = "";
										String field = "";
										String newRev = "";
										String lifeCyclePhase = "";
										IRow row = null;
										String strItem = "";
										IItem item = null;

										// Iterate through all affected items
										@SuppressWarnings("unchecked")
										Iterator<IItem> it = affectedItems.iterator();
										while (it.hasNext()) {
											row = (IRow) it.next();
											if (row != null) {
												strItem = (String) row.getValue(Integer.parseInt(
														awfMessagesList.get("AFFECTEDITEM_NUM_ATTRID").toString()));
												logger.debug("Item is:" + strItem);

												if (strItem != null && !strItem.equals("")) {
													item = (IItem) session
															.getObject(ItemConstants.CLASS_ITEM_BASE_CLASS, strItem);
													logger.debug("Item is:" + item);
													logger.debug("Item agileclass is:" + item.getAgileClass());
												}
												
												if(item!=null) {
													// If the affected item belongs to PARTS class, fetch disposition fields
													if (partsSubclassesList.contains(item.getAgileClass())) {
														stock = getSingleListAttributeValueFromAffectedItems(row,
																awfMessagesList.get("AFFECTEDITEM_STOCK_ATTRID")
																		.toString());
														logger.debug("Stock is:" + stock);
														onOrder = getSingleListAttributeValueFromAffectedItems(row,
																awfMessagesList.get("AFFECTEDITEM_ONORDER_ATTRID")
																		.toString());
														logger.debug("onOrder is:" + onOrder);
														workInProgress = getSingleListAttributeValueFromAffectedItems(row,
																awfMessagesList.get("AFFECTEDITEM_WORKINPROGRESS_ATTRID")
																		.toString());
														logger.debug("workInProgress is:" + workInProgress);
														finishedGoods = getSingleListAttributeValueFromAffectedItems(row,
																awfMessagesList.get("AFFECTEDITEM_FINISHEDGOODS_ATTRID")
																		.toString());
														logger.debug("finishedGoods is:" + finishedGoods);
														field = getSingleListAttributeValueFromAffectedItems(row,
																awfMessagesList.get("AFFECTEDITEM_FIELD_ATTRID")
																		.toString());
														logger.debug("field is:" + field);
														if (stock == null || onOrder == null || workInProgress == null
																|| finishedGoods == null || field == null) {
															itemsList.add(strItem);
														}
													}

													// Get New Rev and Lifecycle Phase
													newRev = (String) row.getValue(Integer.parseInt(
															awfMessagesList.get("AFFECTEDITEM_NEWREV_ATTRID").toString()));
													logger.debug("New Rev is:" + newRev);
													if (newRev != null && !newRev.equals("")) {
														lifeCyclePhase = getSingleListAttributeValueFromAffectedItems(row,
																awfMessagesList.get("AFFECTEDITEM_LIFECYCLEPHASE_ATTRID")
																		.toString());
														logger.debug("LifeCycle Phase:" + lifeCyclePhase);

														if (lifeCyclePhase != null && !lifeCyclePhase.equals("")) {
															if (newRev.startsWith(awfMessagesList
																	.get("NEWREV_OBSOLETE_PREFIX").toString())) {
																if (!lifeCyclePhase.equalsIgnoreCase(awfMessagesList
																		.get("OBSOLETE_LIFECYCLE_PHASE").toString())) {
																	obsoleteRevItemsList.add(item);
																}
															}
														} else {
															itemsWithoutPhase.add(item);
														}

													}
												}

												

											}
										}
										logger.debug("Items list is:" + itemsList);
										logger.debug("Items without Phase list :" + itemsWithoutPhase);
										logger.debug("Obsolete revision items whose phase is not obsolete :" + obsoleteRevItemsList);

										// If disposition fields are empty,throw exception
										if (itemsList.size() > 0) {
											actionResult = new ActionResult(ActionResult.EXCEPTION, 
													new Exception(String.format(awfMessagesList.get("DISPOSITION_FIELDS_NOT_FILLED_MSG").toString(), itemsList)));
										}
										// If any affected item's phase is not filled,throw exception
										else if (itemsWithoutPhase.size() > 0) {
											actionResult = new ActionResult(ActionResult.EXCEPTION,
													new Exception(String.format(awfMessagesList.get("PHASE_NOT_FILLED_MSG").toString(), itemsWithoutPhase)));
														
										}
										// If any affected item's new Rev starts with OBS and phase is not updated to
										// Obsolete,throw exception
										else if (obsoleteRevItemsList.size() > 0) {
											actionResult = new ActionResult(ActionResult.EXCEPTION, 
													new Exception(String.format(awfMessagesList.get("UPDATE_PHASE_TO_OBSOLETE_MSG").toString(), obsoleteRevItemsList)));
										} else {
											actionResult = new ActionResult(ActionResult.STRING,
													awfMessagesList.get("VALIDATION_SUCCESS").toString());

										}
									}

								}

							}
						}
					}//On post event,add eCR to the relationship tab of AWF 
					else {

						// Iterate through the relationship tab and get all objects
						ITable relationshipTab = AWF.getTable(ChangeConstants.TABLE_RELATIONSHIPS);
						@SuppressWarnings("unchecked")
						Iterator<IChange> it = relationshipTab.getReferentIterator();
						HashSet<IChange> relationObjects = new HashSet<IChange>();
						IChange change = null;
						while (it.hasNext()) {
							change = it.next();
							relationObjects.add(change);

						}

						// If ECR is not added to relationship tab of AWF,Add ECR to relationship tab of
						// AWF
						if (relationObjects.contains(eCR)) {
							actionResult = new ActionResult(ActionResult.STRING,
									awfMessagesList.get("ECR_ALREADY_ADDED_TO_AWF").toString());

						} else {
							relationshipTab.createRow(eCR);
							actionResult = new ActionResult(ActionResult.STRING,
									awfMessagesList.get("ECR_ADDED_TO_AWF").toString());
						}
					}

				}
			}

		}

		catch (APIException e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Failed due to:" + e.getMessage());
		} catch (Exception e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Failed due to:" + e.getMessage());
		}
		return new EventActionResult(eventInfo, actionResult);
	}
	
	/**
	 * This method returns the single list attribute value from IRow
	 * @param row
	 * @param attrID
	 * @return
	 * @throws NumberFormatException
	 * @throws APIException
	 */
	public static String getSingleListAttributeValueFromAffectedItems(IRow row, String attrID)
			throws NumberFormatException, APIException {
		String cellValue = null;
		ICell cell = row.getCell(Integer.parseInt(attrID));
		IAgileList agileList = (IAgileList) cell.getValue();
		IAgileList[] listValues = agileList.getSelection();
		if (listValues != null && listValues.length > 0) {
			cellValue = (listValues[0].getValue()).toString();
			logger.debug("Cell Value is:" + cellValue);
		}
		return cellValue;
	}

}
