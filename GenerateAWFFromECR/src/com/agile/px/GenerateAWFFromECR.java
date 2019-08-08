package com.agile.px;

import java.util.HashMap;
import org.apache.log4j.Logger;
import com.agile.api.APIException;
import com.agile.api.ChangeConstants;
import com.agile.api.DataTypeConstants;
import com.agile.api.IAdmin;
import com.agile.api.IAgileClass;
import com.agile.api.IAgileList;
import com.agile.api.IAgileSession;
import com.agile.api.IAttribute;
import com.agile.api.IAutoNumber;
import com.agile.api.ICell;
import com.agile.api.IChange;
import com.agile.api.IDataObject;
import com.agile.api.INode;
import com.agile.api.ITable;
import com.agile.util.CommonUtil;

/**
 * 
 * @author Supriya Varada
 * This PX displays a exception message to user if any Impact assessment attributes are not filled on eCR before generating AWF.
 * If all Impact assessment attributes are filled,AWF is created and added to relationship tab of eCR.
 * All Impact assessment attribute values are copied from ECR to AWF. 
 *
 */

public class GenerateAWFFromECR implements ICustomAction {

	static Logger logger = Logger.getLogger(GenerateAWFFromECR.class);

	public static String attributesMappingOnECRAndAWFListName = "ECRAWFAttributeIDsMappingList";
	public static String awfMessagesListName = "AWFMessagesList";
	public static String ecrMessagesListName = "ECRMessagesList";

	@Override
	public ActionResult doAction(IAgileSession session, INode arg1, IDataObject dataObject) {

		ActionResult actionResult = null;
		String result = "";
		
		HashMap<Object, Object> attributesMappingOnECRAndAWFList = new HashMap<Object, Object>();
		HashMap<Object, Object> awfMessagesList = new HashMap<Object, Object>();
		HashMap<Object, Object> ecrMessagesList = new HashMap<Object, Object>();

		try {
			//Initialize logger
			CommonUtil.initAppLogger(GenerateAWFFromECR.class, session);

			// Load List Values
			attributesMappingOnECRAndAWFList = CommonUtil.loadListValues(session, attributesMappingOnECRAndAWFListName);
			awfMessagesList = CommonUtil.loadListValues(session, awfMessagesListName);
			ecrMessagesList = CommonUtil.loadListValues(session, ecrMessagesListName);

			// Get ECR Object
			IChange eCR = (IChange) dataObject;
			logger.debug("ECR is:" + eCR);

			if (eCR != null) {

				HashMap<Object, Object> eCRAttrValues = new HashMap<Object, Object>();
				String attrValue = "";

				for (Object key : attributesMappingOnECRAndAWFList.keySet()) {
					if (attributesMappingOnECRAndAWFList.get(key) != null) {

						attrValue = CommonUtil.getSingleListValue(eCR, key.toString());
						eCRAttrValues.put(key, attrValue);
					}
				}
				logger.debug("eCRAttrValues are:" + eCRAttrValues);

				int countOfEmptyAttrs = 0;
				String value = null;

				for (Object key : eCRAttrValues.keySet()) {
					value = (String) eCRAttrValues.get(key);
					if (value == null || value.equals("")) {
							countOfEmptyAttrs++;
					}
				}

				logger.debug("count of empty Attributes is" + countOfEmptyAttrs);
			
				if (countOfEmptyAttrs > 0) {
					actionResult = new ActionResult(ActionResult.EXCEPTION,
							new Exception(ecrMessagesList.get("VALUES_NOT_FILLED1").toString()+" "+eCR.toString()+"."
									+ecrMessagesList.get("VALUES_NOT_FILLED2").toString()
									));
				}  else {
					IAdmin admin = session.getAdminInstance();
					IAgileClass subClass = admin.getAgileClass(awfMessagesList.get("AWF_SUBCLASS_NAME").toString());
					IAutoNumber[] numSources = subClass.getAutoNumberSources();

					String nextNumber = "";
					int i = 0;
					IAutoNumber autoNumber = null;
					while (i < numSources.length) {
						autoNumber = numSources[i];
						logger.debug("autoNumber is:" + autoNumber);
							if (autoNumber.toString().equals(awfMessagesList.get("AWF_AUTONUM_NAME").toString())) {
								nextNumber = autoNumber.getNextNumber(subClass);
								break;
							}
							i++;
						
					}
					logger.debug("Next Autonmber is:" + nextNumber);

					HashMap<Object, Object> map = new HashMap<Object, Object>();
					map = copyAttributeValuesFromECRToAWF(eCR, attributesMappingOnECRAndAWFList, session,
							ecrMessagesList.get("ECR_SUBCLASS_NAME").toString(),
							awfMessagesList.get("AWF_SUBCLASS_NAME").toString());

					map.put(Integer.parseInt(awfMessagesList.get("AWFNUM_ATTRID").toString()), nextNumber);
					logger.debug("Map is:" + map);

					IChange awf = (IChange) session.createObject(awfMessagesList.get("AWF_SUBCLASS_NAME").toString(),
							map);
					logger.debug("AWF is:" + awf);

					if (awf != null) {
						awf.setValue(Integer.parseInt(awfMessagesList.get("ECR_ATTRID").toString()), eCR);

						// Add ECR to relationship tab of AWF
						ITable relationshipTab = eCR.getTable(ChangeConstants.TABLE_RELATIONSHIPS);
						relationshipTab.createRow(awf);
						result = awf.toString() + " " + ecrMessagesList.get("OBJ_CREATED_ADDED_RELTAB").toString() + " "
								+ eCR.toString();
						logger.debug("Result:"+result);
						actionResult = new ActionResult(ActionResult.STRING, result);
					}

				}
			}

		} catch (APIException e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Failed due to" + e.getMessage());
		} catch (Exception e) {
			actionResult = new ActionResult(ActionResult.EXCEPTION, new Exception(e));
			e.printStackTrace();
			logger.error("Failed due to" + e.getMessage());
		}

		return actionResult;
	}

	/**
	 * This method iterates through the list of attributes and copies the attribute
	 * values from ECR Object to AWF Object
	 * 
	 * @param sourceObj
	 * @param attributeIDsList
	 * @param session
	 * @param sourceObjectClassName
	 * @param targetObjectClassName
	 * @return
	 * @throws APIException
	 */
	public static HashMap<Object, Object> copyAttributeValuesFromECRToAWF(IDataObject ECR,
			HashMap<Object, Object> attributeIDsList, IAgileSession session, String sourceObjectClassName,
			String targetObjectClassName) throws APIException {
		HashMap<Object, Object> map = new HashMap<Object, Object>();
		int sourceObjAttrID = 0;
		int sourceObjDataType = 0;
		int targetObjAttrID = 0;
		int targetObjDataType = 0;

		IAttribute sourceObjAttribute = null;
		String value = null;
		String[] attributeIDs = null;
		String attributeID = null;
		IAttribute targetObjAttribute = null;

		for (Object key : attributeIDsList.keySet()) {

			if (attributeIDsList.get(key) != null) {

				/* Get the Source Object attribute IDs */
				sourceObjAttrID = Integer.parseInt(key.toString());
				sourceObjAttribute = session.getAdminInstance().getAgileClass(sourceObjectClassName)
						.getAttribute(sourceObjAttrID);
				sourceObjDataType = sourceObjAttribute.getDataType();
				logger.debug("Source Object data type  is" + sourceObjDataType);

				value = (String) attributeIDsList.get(key);
				attributeIDs = value.split(",");
				logger.debug("Attribute IDs are" + attributeIDs);

				for (int k = 0; k < attributeIDs.length; k++) {
					attributeID = attributeIDs[k];
					logger.debug("Attribute ID is" + attributeID);

					/* Get the target attribute IDs */
					targetObjAttrID = Integer.parseInt(attributeID);
					targetObjAttribute = session.getAdminInstance().getAgileClass(targetObjectClassName)
							.getAttribute(targetObjAttrID);
					targetObjDataType = targetObjAttribute.getDataType();
					logger.debug("Target Object data type  is" + targetObjDataType);

					/* Based on the data type set the values */
					if (sourceObjDataType == DataTypeConstants.TYPE_SINGLELIST) {
						ICell cell = ECR.getCell(sourceObjAttrID);
						IAgileList selection = (IAgileList) cell.getValue();
						String selectedvalue = "";
						IAgileList[] selected = selection.getSelection();
						if (selected != null && selected.length > 0) {
							selectedvalue = (selected[0].getValue()).toString();

							logger.debug("source Object cell value is" + selectedvalue);
							if (!selectedvalue.equals("") && !selectedvalue.equals(null)) {
								IAgileList availableValues = targetObjAttribute.getAvailableValues();

								logger.debug("List API name is" + availableValues.getAPIName());
								availableValues.setSelection(new Object[] { selectedvalue });
								map.put(targetObjAttribute, availableValues);

							}
						}
					}

				}
				

			}
		}
		logger.debug("Map values are:" + map);
		return map;
	}

}
