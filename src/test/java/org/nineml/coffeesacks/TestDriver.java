package org.nineml.coffeesacks;


import net.sf.saxon.s9api.*;
import org.xml.sax.InputSource;

import javax.xml.transform.sax.SAXSource;
import java.io.*;

public class TestDriver {
    public static void main(String[] args) {
        TestDriver driver = new TestDriver();
        driver.run(args);
    }

    private void run(String[] args) {
        try {
            Processor processor = new Processor(false);

            RegisterCoffeeSacks register = new RegisterCoffeeSacks();
            register.initialize(processor.getUnderlyingConfiguration());

            DocumentBuilder builder = processor.newDocumentBuilder();
            XdmNode doc = builder.build(new File("src/test/resources/smoketest.xsl"));

            // Transform the graph into dot
            InputStream stylesheet = new FileInputStream(new File("src/test/resources/smoketest.xsl"));
            XsltCompiler compiler = processor.newXsltCompiler();
            compiler.setSchemaAware(false);
            XsltExecutable exec = compiler.compile(new SAXSource(new InputSource(stylesheet)));
            XsltTransformer transformer = exec.load();
            transformer.setInitialContextNode(doc);
            XdmDestination destination = new XdmDestination();
            transformer.setDestination(destination);
            transformer.transform();

            System.out.println(destination.getXdmNode());
        } catch (Exception ex) {
            System.err.println("Failed: " + ex.getMessage());
        }
    }
}
