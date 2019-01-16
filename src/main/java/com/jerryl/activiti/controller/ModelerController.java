package com.jerryl.activiti.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jerryl.common.RestServiceController;
import com.jerryl.util.Status;
import com.jerryl.util.ToWeb;
import com.jerryl.util.XmlUtil;
import com.mysql.jdbc.StringUtils;

import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.constants.ModelDataJsonConstants;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Created by liuruijie on 2017/4/20.
 * 模型管理
 */
@RestController
@RequestMapping("models")
public class ModelerController implements RestServiceController<Model, String>{

    @Autowired
    RepositoryService repositoryService;
    @Autowired
    ObjectMapper objectMapper;

    /**
     * 新建一个空模型
     * @return
     * @throws UnsupportedEncodingException
     */
    @PostMapping("newModel")
    public Object newModel() throws UnsupportedEncodingException {
        //初始化一个空模型
        Model model = repositoryService.newModel();

        //设置一些默认信息
        String name = "new-process";
        String description = "";
        int revision = 1;
        String key = "process";

        ObjectNode modelNode = objectMapper.createObjectNode();
        modelNode.put(ModelDataJsonConstants.MODEL_NAME, name);
        modelNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, description);
        modelNode.put(ModelDataJsonConstants.MODEL_REVISION, revision);

        model.setName(name);
        model.setKey(key);
        model.setMetaInfo(modelNode.toString());

        repositoryService.saveModel(model);
        String id = model.getId();

        //完善ModelEditorSource
        ObjectNode editorNode = objectMapper.createObjectNode();
        editorNode.put("id", "canvas");
        editorNode.put("resourceId", "canvas");
        ObjectNode stencilSetNode = objectMapper.createObjectNode();
        stencilSetNode.put("namespace",
                "http://b3mn.org/stencilset/bpmn2.0#");
        editorNode.put("stencilset", stencilSetNode);
        repositoryService.addModelEditorSource(id,editorNode.toString().getBytes("utf-8"));
        return ToWeb.buildResult().redirectUrl("/editor?modelId="+id);
    }


    /**
     * 发布模型为流程定义
     * @param id
     * @return
     * @throws Exception
     */
    @PostMapping("{id}/deployment")
    public Object deploy(@PathVariable("id")String id) throws Exception {

        //获取模型
        Model modelData = repositoryService.getModel(id);
        byte[] bytes = repositoryService.getModelEditorSource(modelData.getId());

        if (bytes == null) {
            return ToWeb.buildResult().status(Status.FAIL)
                    .msg("模型数据为空，请先设计流程并成功保存，再进行发布。");
        }

        JsonNode modelNode = new ObjectMapper().readTree(bytes);

        BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
        if(model.getProcesses().size()==0){
            return ToWeb.buildResult().status(Status.FAIL)
                    .msg("数据模型不符要求，请至少设计一条主线流程。");
        }
        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);

        //发布流程
        String processName = modelData.getName() + ".bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment()
                .name(modelData.getName())
                .addString(processName, new String(bpmnBytes, "UTF-8"))
                .deploy();
        modelData.setDeploymentId(deployment.getId());
        repositoryService.saveModel(modelData);

        return ToWeb.buildResult().refresh();
    }

    @Override
    public Object getOne(@PathVariable("id") String id) {
        Model model = repositoryService.createModelQuery().modelId(id).singleResult();
        return ToWeb.buildResult().setObjData(model);
    }

    @Override
    public Object getList(@RequestParam(value = "rowSize", defaultValue = "1000", required = false) Integer rowSize, @RequestParam(value = "page", defaultValue = "1", required = false) Integer page) {
        List<Model> list = repositoryService.createModelQuery().listPage(rowSize * (page - 1)
                , rowSize);
        long count = repositoryService.createModelQuery().count();

        return ToWeb.buildResult().setRows(
                ToWeb.Rows.buildRows().setCurrent(page)
                        .setTotalPages((int) (count/rowSize+1))
                        .setTotalRows(count)
                        .setList(list)
                        .setRowSize(rowSize)
        );
    }

    @Override
    public Object deleteOne(@PathVariable("id")String id){
        repositoryService.deleteModel(id);
        return ToWeb.buildResult().refresh();
    }

    @Override
    public Object postOne(@RequestBody Model entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object putOne(@PathVariable("id") String s, @RequestBody Model entity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object patchOne(@PathVariable("id") String s, @RequestBody Model entity) {
        throw new UnsupportedOperationException();
    }

    @RequestMapping("show/{id}")
    public Object show(@PathVariable("id") String id) {
        final byte[] editorSourceExtra = this.repositoryService.getModelEditorSourceExtra(id);
        return Base64.encodeBase64String(editorSourceExtra);
    }
    
    @RequestMapping("export/{id}")
    public Object export(@PathVariable("id") String id) {
        JsonNode editorNode;
		try {
			editorNode = new ObjectMapper().readTree(this.repositoryService.getModelEditorSource(id));
	        BpmnJsonConverter jsonConverter = new BpmnJsonConverter();
	        BpmnModel model = jsonConverter.convertToBpmnModel(editorNode);
	        String filename = model.getMainProcess().getId() + ".bpmn20.xml";
	        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);
	        ByteArrayInputStream in = new ByteArrayInputStream(bpmnBytes);
	        InputStreamResource inputStreamResource = new InputStreamResource(in);
	         HttpHeaders headers = new HttpHeaders();
	         headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
	         headers.add("Content-Disposition", "attachment; filename=" + filename);
	         headers.add("Pragma", "no-cache");
	         headers.add("Expires", "0");
	         return ResponseEntity
	                 .ok()
	                 .headers(headers)
	                 .contentType(MediaType.parseMediaType("application/xml"))
	                 .body(inputStreamResource);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
    }
    
    @PostMapping("import")
    @ResponseBody
    public Object singleFileUpload(@RequestParam("file") MultipartFile file) {
    	Map<String, String> status = new HashMap<>();
        try {
            // Get the file and save it somewhere
	        if ((file.getOriginalFilename().endsWith(".bpmn20.xml")) || (file.getOriginalFilename().endsWith(".bpmn")))
	        {
	        	byte[] bytes = file.getBytes();
	            XMLInputFactory xif = XmlUtil.createSafeXmlInputFactory();
	            InputStreamReader in = new InputStreamReader(new ByteArrayInputStream(bytes), "UTF-8");
	            XMLStreamReader xtr = xif.createXMLStreamReader(in);
	            BpmnModel bpmnModel = new BpmnXMLConverter().convertToBpmnModel(xtr);
	            if ((bpmnModel.getMainProcess() == null) || (bpmnModel.getMainProcess().getId() == null))
	            {
	            	status.put("failed", "model.import.invalid.bpmn.explanation");
	            }
	            else if (bpmnModel.getLocationMap().isEmpty())
	            {
	            	status.put("failed", "model.import.invalid.bpmndi.explanation");
	            }
	            else
		        {
		            String processName = null;
		            if (StringUtils.isNullOrEmpty(bpmnModel.getMainProcess().getName())) {
		              processName = bpmnModel.getMainProcess().getName();
		            } else {
		              processName = bpmnModel.getMainProcess().getId();
		            }
		            Model modelData = this.repositoryService.newModel();
		            ObjectNode modelObjectNode = new ObjectMapper().createObjectNode();
		            modelObjectNode.put("name", processName);
		            modelObjectNode.put("revision", 1);
		            modelData.setMetaInfo(modelObjectNode.toString());
		            modelData.setName(processName);
		            
		            this.repositoryService.saveModel(modelData);
		            
		            BpmnJsonConverter jsonConverter = new BpmnJsonConverter();
		            ObjectNode editorNode = jsonConverter.convertToJson(bpmnModel);
		            
		            this.repositoryService.addModelEditorSource(modelData.getId(), editorNode.toString().getBytes("utf-8"));
		            status.put("success", "success");
		        }
	        }else {
	        	status.put("failed", "unknown error");
	        }
        } catch (Exception e) {
            e.printStackTrace();
            status.put("failed", "unknown error");
        }
        
        return status;
    	
    }
}
