package org.perfcake.message.sender;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.perfcake.PerfCakeException;
import org.perfcake.message.Message;
import org.perfcake.reporting.MeasurementUnit;

/**
 * TODO: document this
 *
 * @author Jiri Holusa (jholusa@redhat.com)
 */
public class SearchExecutorSender extends AbstractSender {

    private Logger logger = Logger.getLogger(SearchExecutorSender.class);

    // parameters passed as System properties using -Dproperty=value
    private String searchScriptPath = System.getProperty("searchScriptPath");
    private String searchScriptName = System.getProperty("searchScriptName", "search-test.sh");
    //private String resultFileProperty = System.getProperty("resultFile");
    //private String inputLocatorsFileProperty = System.getProperty("locatorsFile");
    private int querySetSize = Integer.parseInt(System.getProperty("querySetSize", "1000"));

    //passing environment variables to search script in format var1=value1;var2=value2;...
    private String envVars = System.getProperty("envVars", "");

    @Override
    public void doInit(Properties messageAttributes) throws PerfCakeException {
        // nop
    }

    @Override
    public void doClose() {
        // nop
    }

    /*
    --původní verze jen pro Infinispan refinement

    @Override
    public Serializable doSend(final Message message, final Map<String, String> properties, final MeasurementUnit mu) throws Exception {
        //TODO: document this
        String resultFile = resultFileProperty + Thread.currentThread().getId();
        String inputLocatorsFile = inputLocatorsFileProperty + Thread.currentThread().getId();

        List<String> tmpList = new ArrayList<>(Arrays.asList(envVars.split(";")));
        //this will pass the output file also to search script, therefore automatically wiring them together
        tmpList.add("outputFile=" + resultFile);
        tmpList.add("locatorsFile=" + inputLocatorsFile);
        String[] environmentVariables =  tmpList.toArray(new String[tmpList.size()]);

        Runtime.getRuntime().exec("./" + searchScriptName + " command", environmentVariables, new File(searchScriptPath));

        while (!new File(resultFile).exists()) {
            Thread.sleep(30);
        }

        String output = new String(Files.readAllBytes(Paths.get(resultFile)), Charset.defaultCharset());

        Pattern pattern = Pattern.compile("OperationTime: ([0-9]+)");
        Matcher matcher = pattern.matcher(output);
        if (matcher.find()) {
            mu.appendResult("Operation time", matcher.group(1));
        }

        return null;
    }*/

    @Override
    public Serializable doSend(final Message message, final Map<String, String> properties, final MeasurementUnit mu) throws Exception {
        List<String> tmpList = new ArrayList<>(Arrays.asList(envVars.split(";")));

        // we add random skip to simulate randomly picking one of the query objects
        Random random = new Random();
        int skip = random.nextInt(querySetSize);
        tmpList.add("skip=" + skip);

        String[] environmentVariables =  tmpList.toArray(new String[tmpList.size()]);

        Arrays.toString(environmentVariables);

        Process process = Runtime.getRuntime().exec("./" + searchScriptName + " command", environmentVariables, new File(searchScriptPath));
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        StringBuilder output = new StringBuilder();
        String line;
        while((line = reader.readLine()) != null) {
            output.append(line + "\n");
        }

        while((line = stdError.readLine()) != null) {
            output.append(line + "\n");
        }

//        System.out.println(output);

        Pattern pattern = Pattern.compile("OperationTime: ([0-9]+)");
        Matcher matcher = pattern.matcher(output);

        if (matcher.find()) {
            mu.appendResult("Operation time", matcher.group(1));
        }

        return null;
    }
}
