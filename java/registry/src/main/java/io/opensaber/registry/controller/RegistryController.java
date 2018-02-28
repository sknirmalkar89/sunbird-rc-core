package io.opensaber.registry.controller;

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.WriterDatasetRIOT;
import org.apache.jena.riot.system.PrefixMap;
import org.apache.jena.riot.system.RiotLib;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.riot.JsonLDWriteContext;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.opensaber.converters.JenaRDF4J;
import io.opensaber.pojos.Response;
import io.opensaber.pojos.ResponseParams;
import io.opensaber.registry.exception.DuplicateRecordException;
import io.opensaber.registry.exception.InvalidTypeException;
import io.opensaber.registry.exception.RecordNotFoundException;
import io.opensaber.registry.service.RegistryService;

@RestController
@SpringBootApplication
@ComponentScan({"io.opensaber.registry"})
public class RegistryController extends SpringBootServletInitializer {

	private static Logger logger = LoggerFactory.getLogger(RegistryController.class);

	@Autowired
	RegistryService registryService;

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(RegistryController.class);
	}

	@ResponseBody
	@RequestMapping(value="/addEntity",method=RequestMethod.POST)
	public ResponseEntity<Response> addEntity(@RequestAttribute Model rdf) throws JsonProcessingException, DuplicateRecordException, InvalidTypeException{
		Response response = new Response();
		ResponseParams responseParams = new ResponseParams();
		response.setId(UUID.randomUUID().toString());
		response.setEts(System.currentTimeMillis() / 1000L);
		response.setVer("1.0");
		response.setParams(responseParams);
		try{
			registryService.addEntity(rdf);
			responseParams.setStatus(Response.Status.SUCCCESSFUL);
		} catch (DuplicateRecordException | InvalidTypeException e) {
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		} catch (Exception e) {
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		}
		return new ResponseEntity<Response>(response, HttpStatus.OK);
	}

	@RequestMapping(value = "/getEntity/{id}", method = RequestMethod.GET)
	public ResponseEntity<Response> getEntity(@PathVariable("id") String id){
		Response response = new Response();
		ResponseParams responseParams = new ResponseParams();
		response.setId(UUID.randomUUID().toString());
		response.setEts(System.currentTimeMillis() / 1000L);
		response.setVer("1.0");
		response.setParams(responseParams);
		try {
			org.eclipse.rdf4j.model.Model entityModel;
			id = "http://example.com/voc/teacher/1.0.0/" + id;
			entityModel = registryService.getEntityById(id);
			Model jenaEntityModel = JenaRDF4J.asJenaModel(entityModel);
			DatasetGraph g = DatasetFactory.create(jenaEntityModel).asDatasetGraph();
			JsonLDWriteContext ctx = new JsonLDWriteContext();
			ClassPathResource res = new ClassPathResource("frame.json");
			File file = res.getFile();
			String fileString = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			logger.info(fileString);
			ctx.setFrame(fileString);
			WriterDatasetRIOT w = RDFDataMgr.createDatasetWriter(org.apache.jena.riot.RDFFormat.JSONLD_FRAME_FLAT) ;
			PrefixMap pm = RiotLib.prefixMap(g);
			String base = null;
			StringWriter sWriterJena = new StringWriter();
			w.write(sWriterJena, g, pm, base, ctx) ;
			String jenaJSON = sWriterJena.toString();
			logger.info("*************JSONLD_FRAME_PRETTY**********");
			logger.info(jenaJSON);
			
			StringWriter sWriterJena2 = new StringWriter();
			RDFDataMgr.write(sWriterJena2, jenaEntityModel, org.apache.jena.riot.RDFFormat.JSONLD_PRETTY);
			logger.info("*************JSONLD_PRETTY**********");
			String jenaJSON2 = sWriterJena2.toString();//.replaceAll("http://www.w3.org/1999/02/22-rdf-syntax-ns#type", "@type");
			logger.info(jenaJSON2);
			JSONObject jenaObj=new JSONObject(jenaJSON);
			responseParams.setResultMap(jenaObj.toMap());
//			logger.info("*************JENA**********");
//			StringWriter sWriter = new StringWriter();
//			Rio.write(entityModel, sWriter, RDFFormat.JSONLD);
//			String jsonresult = sWriter.toString();
//			logger.info("*************RDF4J**********");
//			logger.info(jsonresult);
//			logger.info("*************RDF4J**********");
//			JSONArray obj=new JSONArray(jsonresult);
//			responseParams.setResultList(obj.toList());
			responseParams.setStatus(Response.Status.SUCCCESSFUL);
		} catch (RecordNotFoundException e) {
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg(e.getMessage());
		} catch (Exception e) {
			responseParams.setStatus(Response.Status.UNSUCCESSFUL);
			responseParams.setErrmsg("Ding! You encountered an error!");
			logger.error("ERROR!", e);
		}
		return new ResponseEntity<Response>(response, HttpStatus.OK);
	}

}
