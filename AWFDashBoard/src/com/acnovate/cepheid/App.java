package com.acnovate.cepheid;

import java.io.IOException;
import java.sql.Date;
import java.text.DateFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import com.agile.api.APIException;
import com.agile.api.AgileSessionFactory;
import com.agile.api.ChangeConstants;
import com.agile.api.IAdmin;
import com.agile.api.IAgileSession;
import com.agile.api.ICell;
import com.agile.api.IChange;
import com.agile.api.IItem;
import com.agile.api.IRow;
import com.agile.api.ITable;
import com.agile.api.ITwoWayIterator;
import com.agile.api.ItemConstants;
import com.agile.util.GenericUtilities;


/**
 * Hello world!
 *
 */
public class App 
{

	IAgileSession m_session;
	IAdmin m_admin;
	AgileSessionFactory m_factory;
    int iCount =0;  	
	private static LinkedHashMap<Integer, String[]> data;
	int i = 1234;
	//private static Random random;
    public static void main( String[] args )
    {
       App obj = new App();
      
       PropertiesHandler prop = new PropertiesHandler();
    	try {
			obj.m_session = securelogin(prop.getPropertyValue("report.agile.username"),prop.getPropertyValue("report.agile.password"),prop.getPropertyValue("report.agile.url"));
		
       System.out.println("Session created");
		ExcelReader excel = new ExcelReader();
	    excel.createXLSFile();
	    excel.vCreateStylesHeader();
	   
	    data = new LinkedHashMap<>();
	    String[] header = prop.getPropertyValue("report.outfile.header").split(",");
	//    random = new Random();
	   
	    data.put(obj.i, header);
	    obj.i++;
	    System.out.println("Header added");
    	String[] changes = prop.getPropertyValue("report.outfile.changes").split(",");
	    for(int i=0; i<changes.length;i++)
	    obj.iterateAgileTable(changes[i]);
    	
	    excel.writeData(data);
    	} catch (APIException e) {
			// TODO Auto-generated catch block
			e.getMessage();
			e.getLocalizedMessage();
    		e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    System.out.println("Program executed");
    
    }
    
    private static IAgileSession securelogin(String username, String password, String url) throws
	APIException
	{
	 HashMap params = new HashMap();
	//Put username, password, and URL values into params
	 params.put(AgileSessionFactory.USERNAME, username);
	 params.put(AgileSessionFactory.PASSWORD, password);
	 params.put(AgileSessionFactory.URL,url);
	//Create the Agile PLM session and log in
	return AgileSessionFactory.createSessionEx(params);
	}
	
    
  //Iterating over table rows
  	private void iterateAgileTable(String sChangeNumber) throws APIException {

  	 
 	

 	IChange change = (IChange)m_session.getObject(ChangeConstants.CLASS_CHANGE_ORDERS_CLASS, sChangeNumber);
  	 
  	
  		// Get the Change workflow  table
  	System.out.println("Processing Change:"+change.getName());
  	 ITable changetable = change.getTable(ChangeConstants.TABLE_WORKFLOW);
  	 ITwoWayIterator i = changetable.getTableIterator();
  	IRow row = null;
	ICell[] cells = null;
  	  // Traverse forwards through the table
  	  while (i.hasNext()) {
  			   	String statusCode ="";
  			  	String workflowState="";	
  			  	String reviewer="";
  			  	String action="";
  			  	String required="";
  			  	String signoffuser="";
  			  	String statuschangedby="";
  			  	String localtime="";
  		        Date dLocaltime;
  		  iCount++;
  	  row = (IRow)i.next();
 	cells = row.getCells();
 	if (!(cells[0].getValue().toString()==null)) 
 	 	statusCode = cells[0].getValue().toString();
 	
 	if (!(cells[1].getValue().toString()==null)) 
 workflowState = cells[1].getValue().toString();
 	
 	if (!(cells[2].getValue().toString()==null)) 
	 action = cells[2].getValue().toString();
 	
 	if (!(cells[3].getValue().toString()==null)) 
	 required =cells[3].getValue().toString();
 	
 	if (!(cells[4].getValue().toString()==null)) 
	reviewer = cells[4].getValue().toString();
 
 	if (!(cells[5].getValue().toString()==null)) 
	 signoffuser = cells[5].getValue().toString();
 
 	if (!(cells[6].getValue().toString()==null)) 
	 statuschangedby = cells[6].getValue().toString();
 	 	
 	if(!(cells[7].getValue()==null))
 	localtime = cells[7].getValue().toString();
 	

    data.put(iCount,new String[] {sChangeNumber,statusCode,workflowState,reviewer,action,required,signoffuser,statuschangedby,localtime});
   

  	  }
  	 
  	}
    
  
    
}
