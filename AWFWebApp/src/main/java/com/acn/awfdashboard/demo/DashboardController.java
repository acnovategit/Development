package com.acn.awfdashboard.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import com.acnovate.cepheid.App;
import com.acn.awfdashboard.demo.DashboardUtility;
import com.acn.awfdashboard.demo.MediaTypeUtils;


@Controller
public class DashboardController {
	 @Autowired
	    private ServletContext servletContext;
    @RequestMapping("/home")
    public String hello(Model model, @RequestParam(value="name", required=false, defaultValue="World") String name) {
        model.addAttribute("name", name);
        return "home";
    }
    @RequestMapping("/generateReport")
    public ModelAndView generate(HttpServletRequest req)
    {
    	String awfNumbers= req.getParameter("awfNumber");
    	System.out.println("inside generate with awfnumber = "+awfNumbers);
    	int status= App.Generate(awfNumbers);
    	String output;
    	if(status==1)
    	{
    	output="The file is generated !";
    	}
    	else
    	{
    		output="Some error occured!!!!";
    	}
    	ModelAndView model = new ModelAndView();
    	model.addObject("message",output);
    	model.setViewName("output");
    	return model;
    }
	
    @RequestMapping("/download")
	 public ResponseEntity<ByteArrayResource> downloadFile(HttpServletRequest req) throws IOException {
			

	        String fileName= DashboardUtility.getTheNewestFile();
	        System.out.println("fileName = "+fileName);
	        MediaType mediaType = MediaTypeUtils.getMediaTypeForFileName(this.servletContext, fileName);
	        System.out.println("Media Type = "+mediaType);
	        Path path = Paths.get(fileName);
	        byte[] data = Files.readAllBytes(path);
	        ByteArrayResource resource = new ByteArrayResource(data);
	 
	        return ResponseEntity.ok()
	                // Content-Disposition
	                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + path.getFileName().toString())
	                // Content-Type
	                .contentType(mediaType) //
	                // Content-Lengh
	                .contentLength(data.length) //
	                .body(resource);
	    }
}
