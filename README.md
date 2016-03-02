
How to run the performance test:

mvn clean package exec:java -Dscenario=/path/to/scenarios/constant-speed.xml -DsearchScriptPath=/path/to/search/script/dir -DsearchScriptName=script.sh -DenvVars="DATASET=dataset;kNN=10;candidateSetSize=1000" -DcsvTarget=result.csv


