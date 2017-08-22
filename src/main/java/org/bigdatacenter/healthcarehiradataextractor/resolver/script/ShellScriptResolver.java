package org.bigdatacenter.healthcarehiradataextractor.resolver.script;

public interface ShellScriptResolver {
    void runReducePartsMerger(String hdfsLocation, String header, String homePath, String dataFileName);

    void runArchiveExtractedDataSet(String archiveFileName, String ftpLocation, String homePath);
}
