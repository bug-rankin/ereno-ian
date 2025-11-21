package br.ufu.facom.ereno;

import br.ufu.facom.ereno.config.ConfigLoader;

import java.io.File;
import java.io.IOException;

/**
 * Small CLI runner that loads the central JSON config and executes the
 * `SingleSource` generator once. Useful as a smoke test after refactor.
 */
public class RunFromConfig {
    public static void main(String[] args) {
        // Disable Weka's GenericPropertiesCreator to prevent ClassCastException in uber JAR
        System.setProperty("weka.gui.GenericPropertiesCreator.useDynamic", "false");
        
        try {
            // load central config (config/configparams.json)
            ConfigLoader.load();
            // determine desired output format/filename from config
            String format = ConfigLoader.output.format == null ? "arff" : ConfigLoader.output.format.toLowerCase();
            String out;
            if (ConfigLoader.output.filename != null && !ConfigLoader.output.filename.trim().isEmpty()) {
                out = ConfigLoader.output.filename;
            } else {
                String ext = "arff".equals(format) ? "arff" : "csv";
                out = System.getProperty("user.dir") + File.separator + "target" + File.separator + "ereno_generated." + ext;
            }
            System.out.println("Generating dataset to: " + out + " (format=" + format + ")");
            SingleSource.lightweightDataset(out, true, format);
            System.out.println("Done.");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(2);
        }
    }
}
