package dev.sunbirdrc.registry.controller;


import org.apache.commons.io.output.ByteArrayOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

@RestController
public class RegistryTemplateController{

    @Value("${certificate.templateFolderPath}")
    private String templatesFolderPath;

    @RequestMapping(value = "/api/v1/templates/{fileName}", method = RequestMethod.GET)
    public String getTemplate(@PathVariable String fileName) {
        String content = "";
        try {
            InputStream is = new ClassPathResource(templatesFolderPath + fileName).getInputStream();
            content = new String(readAllBytes(is));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return content;
    }
    
    public static byte[] readAllBytes(InputStream inputStream) throws IOException {
        final int bufLen = 1024;
        byte[] buf = new byte[bufLen];
        int readLen;
        IOException exception = null;

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            while ((readLen = inputStream.read(buf, 0, bufLen)) != -1)
                outputStream.write(buf, 0, readLen);

            return outputStream.toByteArray();
        } catch (IOException e) {
            exception = e;
            throw e;
        } finally {
            if (exception == null) inputStream.close();
            else try {
                inputStream.close();
            } catch (IOException e) {
                exception.addSuppressed(e);
            }
        }
    }
    
}
