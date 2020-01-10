package com.document.utility;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import com.agile.api.IAgileSession;
import com.agile.util.CommonUtil;
import com.agile.util.GenericUtilities;
import com.aspose.words.Document;
import com.aspose.words.FindReplaceDirection;
import com.aspose.words.FindReplaceOptions;
import com.aspose.words.HeaderFooter;
import com.aspose.words.HeaderFooterCollection;
import com.aspose.words.HeaderFooterType;
import com.aspose.words.HorizontalAlignment;
import com.aspose.words.NodeCollection;
import com.aspose.words.NodeType;
import com.aspose.words.Paragraph;
import com.aspose.words.RelativeHorizontalPosition;
import com.aspose.words.RelativeVerticalPosition;
import com.aspose.words.SaveFormat;
import com.aspose.words.Section;
import com.aspose.words.Shape;
import com.aspose.words.ShapeType;
import com.aspose.words.VerticalAlignment;
import com.aspose.words.WrapType;


public class DocumentUtilityAspose {

	static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(DocumentUtilityAspose.class);
	static boolean bUpdated = false;
	public String LICENSE_FILE;
	public  static final String awfMessagesListName = "AWFMessagesList";
	public static HashMap<Object, Object> awfMessagesList; 
	/**
	 * @param session
	 * @throws Exception
	 */
	public DocumentUtilityAspose(IAgileSession session) throws Exception {
		
		GenericUtilities.initializeLogger(session);
		 awfMessagesList = GenericUtilities.getAgileListValues(session, awfMessagesListName);
		 LICENSE_FILE = awfMessagesList.get("AWF_LICENSE_FILE").toString();
		//CommonUtil.initAppLogger(DocumentUtilityAspose.class, session);
		logger.info("Setting license for Aspose");
		com.aspose.words.License license = new com.aspose.words.License();
		license.setLicense(new java.io.FileInputStream(LICENSE_FILE));
        
		if (license.isLicensed())
			logger.info("License set");
	}

	
	/**
	 * @return
	 */
	public static String now() {
		String DATE_FORMAT_NOW = "MM-dd-yyyy";
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
		return sdf.format(cal.getTime());
	}
	/**
	 * @param doc
	 * @param watermarkText
	 * @throws Exception
	 */
	public static void insertWatermarkText(String filePath, String watermarkText) throws Exception {
		// Create a watermark shape. This will be a WordArt shape.
		// You are free to try other shape types as watermarks.
		Document doc = new Document(filePath);
		Shape watermark = new Shape(doc, ShapeType.TEXT_PLAIN_TEXT);

		// Set up the text of the watermark.
		watermark.getTextPath().setText(watermarkText);
		watermark.getTextPath().setFontFamily("Arial");
		watermark.setWidth(500);
		watermark.setHeight(100);
		// Text will be directed from the bottom-left to the top-right corner.
		watermark.setRotation(-40);
		// Remove the following two lines if you need a solid black text.
		watermark.getFill().setColor(Color.GRAY); // Try LightGray to get more Word-style watermark
		watermark.setStrokeColor(Color.GRAY); // Try LightGray to get more Word-style watermark

		// Place the watermark in the page center.
		watermark.setRelativeHorizontalPosition(RelativeHorizontalPosition.PAGE);
		watermark.setRelativeVerticalPosition(RelativeVerticalPosition.PAGE);
		watermark.setWrapType(WrapType.NONE);
		watermark.setVerticalAlignment(VerticalAlignment.CENTER);
		watermark.setHorizontalAlignment(HorizontalAlignment.CENTER);

		// Create a new paragraph and append the watermark to this paragraph.
		Paragraph watermarkPara = new Paragraph(doc);
		watermarkPara.appendChild(watermark);

		// Insert the watermark into all headers of each document section.
		for (Section sect : doc.getSections()) {
			// There could be up to three different headers in each section, since we want
			// the watermark to appear on all pages, insert into all headers.
			insertWatermarkIntoHeader(watermarkPara, sect, HeaderFooterType.HEADER_PRIMARY);
			insertWatermarkIntoHeader(watermarkPara, sect, HeaderFooterType.HEADER_FIRST);
			insertWatermarkIntoHeader(watermarkPara, sect, HeaderFooterType.HEADER_EVEN);
		}
	
	doc.save(filePath);
	}

	/**
	 * @param watermarkPara
	 * @param sect
	 * @param headerType
	 * @throws Exception
	 */
	private static void insertWatermarkIntoHeader(Paragraph watermarkPara, Section sect, int headerType)
			throws Exception {
		HeaderFooter header = sect.getHeadersFooters().getByHeaderFooterType(headerType);

		if (header == null) {
			// There is no header of the specified type in the current section, create it.
			header = new HeaderFooter(sect.getDocument(), headerType);
			sect.getHeadersFooters().add(header);
		}

		// Insert a clone of the watermark into the header.
		header.appendChild(watermarkPara.deepClone(true));
	}

	/**
	 * @param filePath
	 * @return
	 * @throws Exception
	 */
	public static String readHeaderText(String filePath) throws Exception {
        logger.info("File to read: "+filePath);
		Document doc = new Document(filePath);
		
		
        logger.info("Documen object created");
		HeaderFooterCollection headersFooters = doc.getFirstSection().getHeadersFooters();
		  logger.info("Header footer collection created");
		
		  HeaderFooter header = headersFooters.getByHeaderFooterType(HeaderFooterType.HEADER_PRIMARY);
		 logger.info("Header footer created");
		 		 
		String contentOfHeader = header.getText();
		 logger.info("Header read" +contentOfHeader);
		return contentOfHeader;
	}

	/**
	 * @param filePath
	 * @param toBeReplaced
	 * @param replaceText
	 * @throws Exception
	 */
	public static void replaceTextInHeader(String filePath, String toBeReplaced, String replaceText) throws Exception {

		Document doc = new Document(filePath);

		HeaderFooterCollection headersFooters = doc.getFirstSection().getHeadersFooters();
		HeaderFooter header = headersFooters.getByHeaderFooterType(HeaderFooterType.HEADER_PRIMARY);
		String contentOfHeader = header.getText();
		Map<String, String> map = new HashMap<String, String>();
		String[] part = contentOfHeader.split("\r");
		// System.out.println(part[0]);
		for (String s : part) {
			if (s.contains(":")) {
				String[] t = s.split(":");

				map.put(t[0].trim(), t[1]);

			}
		}
		FindReplaceOptions options = new FindReplaceOptions(FindReplaceDirection.FORWARD);
		options.setMatchCase(false);
		Pattern regex = Pattern.compile(toBeReplaced, Pattern.CASE_INSENSITIVE);

		header.getRange().replace(regex, replaceText, options);
		doc.save(filePath);

	}
   public static void vAcceptAllRevisions(String filepath) throws Exception
   {
	   Document doc = new Document(filepath);
	   doc.acceptAllRevisions();
		@SuppressWarnings("rawtypes")
		NodeCollection comments = doc.getChildNodes(NodeType.COMMENT, true);
		// Remove all comments.
		comments.clear();
	   
	   doc.save(filepath,SaveFormat.DOCX);
	 
	
	   	   
   }
 
}
