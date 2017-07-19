package br.ufsc.inf.lapesd.linkedator.api.config;

import org.apache.commons.io.IOUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import javax.annotation.Nonnull;
import javax.annotation.WillClose;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public class ResourceExtractor {

    public static String toString(InputStream in) throws IOException {
        return IOUtils.toString(in, "UTF-8");
    }

    public static File extract(@WillClose InputStream in, String suffix) throws IOException {
        File file = Files.createTempFile("", suffix).toFile();
        try (FileOutputStream out = new FileOutputStream(file)) {
            IOUtils.copy(in, out);
        }
        in.close();
        file.deleteOnExit();
        return file;
    }

    public static Model model(@WillClose @Nonnull InputStream in, Lang lang) throws IOException {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, in, lang);
        in.close();
        return model;
    }
}
