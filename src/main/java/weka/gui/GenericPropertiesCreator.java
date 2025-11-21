package weka.gui;

import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;

/**
 * Compatibility shim for Weka's GenericPropertiesCreator.
 *
 * Some Weka releases assume the system ClassLoader is a URLClassLoader and
 * perform an unsafe cast which fails on Java 9+. This lightweight replacement
 * provides the same public API (surface area) but avoids any ClassLoader
 * casts or GUI initialization. It is intentionally conservative (no-ops)
 * and safe for headless/CLI usage.
 */
public class GenericPropertiesCreator {
    public static final boolean VERBOSE = false;
    public static final String USE_DYNAMIC = "use.dynamic";

    protected static String CREATOR_FILE = "GenericPropertiesCreator.props";
    protected static String EXCLUDE_FILE = "GenericPropertiesCreator.excludes";
    protected static String EXCLUDE_INTERFACE = "EXCLUDE_INTERFACE";
    protected static String EXCLUDE_CLASS = "EXCLUDE_CLASS";
    protected static String EXCLUDE_SUPERCLASS = "EXCLUDE_SUPERCLASS";
    protected static String PROPERTY_FILE = "GenericPropertiesCreator.props";

    protected String m_InputFilename;
    protected String m_OutputFilename;
    protected Properties m_InputProperties;
    protected Properties m_OutputProperties;

    protected static GenericPropertiesCreator GLOBAL_CREATOR;
    protected static Properties GLOBAL_INPUT_PROPERTIES = new Properties();
    protected static Properties GLOBAL_OUTPUT_PROPERTIES = new Properties();

    protected boolean m_ExplicitPropsFile;
    protected Hashtable<String, Hashtable<String, Vector<String>>> m_Excludes =
            new Hashtable<String, Hashtable<String, Vector<String>>>();

    public static Properties getGlobalOutputProperties() {
        return GLOBAL_OUTPUT_PROPERTIES;
    }

    public static Properties getGlobalInputProperties() {
        return GLOBAL_INPUT_PROPERTIES;
    }

    public static void regenerateGlobalOutputProperties() {
        GLOBAL_OUTPUT_PROPERTIES = new Properties();
    }

    public GenericPropertiesCreator() throws Exception {
        m_InputProperties = new Properties();
        m_OutputProperties = new Properties();
    }

    public GenericPropertiesCreator(String inputFile) throws Exception {
        this();
        m_InputFilename = inputFile;
    }

    public void setExplicitPropsFile(boolean v) {
        m_ExplicitPropsFile = v;
    }

    public boolean getExplicitPropsFile() {
        return m_ExplicitPropsFile;
    }

    public String getOutputFilename() {
        return m_OutputFilename;
    }

    public void setOutputFilename(String s) {
        m_OutputFilename = s;
    }

    public String getInputFilename() {
        return m_InputFilename;
    }

    public void setInputFilename(String s) {
        m_InputFilename = s;
    }

    public Properties getInputProperties() {
        return m_InputProperties;
    }

    public Properties getOutputProperties() {
        return m_OutputProperties;
    }

    protected void loadInputProperties() {
        // no-op: safe headless behavior
    }

    public boolean useDynamic() {
        return false;
    }

    protected boolean isValidClassname(String name) {
        return name != null && name.length() > 0;
    }

    protected boolean isValidClassname(String name, String base) {
        return isValidClassname(name);
    }

    protected void generateOutputProperties() throws Exception {
        // no-op
    }

    protected void storeOutputProperties() throws Exception {
        // no-op
    }

    public void execute() throws Exception {
        // no-op
    }

    public void execute(boolean a) throws Exception {
        // no-op
    }

    public void execute(boolean a, boolean b) throws Exception {
        // no-op
    }

    public static void main(String[] args) throws Exception {
        // Minimal CLI entry: regenerate globals and exit successfully.
        regenerateGlobalOutputProperties();
    }

    static {
        // no static initialization that depends on ClassLoaders
    }
}
